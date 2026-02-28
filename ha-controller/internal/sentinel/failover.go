package sentinel

import (
	"fmt"
	"log/slog"
	"sort"
	"strconv"
	"sync"
	"time"

	"github.com/telcobright/ha-controller/internal/config"
	"github.com/telcobright/ha-controller/internal/consul"
	"github.com/telcobright/ha-controller/internal/executor"
	"github.com/telcobright/ha-controller/internal/node"
	"github.com/telcobright/ha-controller/internal/resource"
)

// FailoverCoordinator manages failover execution when ODOWN is detected.
// Only the Consul lock holder (coordinator) executes failovers.
type FailoverCoordinator struct {
	sentinel       *Sentinel
	consul         *consul.Client
	nodes          map[string]node.Node
	nodeConfigs    []config.NodeConfig
	groupConfigs   []config.GroupConfig
	clusterName    string
	autoFailback   bool
	maxFailovers   int
	failoverWindow time.Duration
	logger         *slog.Logger

	mu              sync.Mutex
	failoverHistory []time.Time
}

// NewFailoverCoordinator creates a failover coordinator.
func NewFailoverCoordinator(
	sentinel *Sentinel,
	consulClient *consul.Client,
	nodes map[string]node.Node,
	nodeConfigs []config.NodeConfig,
	groupConfigs []config.GroupConfig,
	clusterName string,
	autoFailback bool,
	maxFailovers int,
	failoverWindow time.Duration,
	logger *slog.Logger,
) *FailoverCoordinator {
	return &FailoverCoordinator{
		sentinel:       sentinel,
		consul:         consulClient,
		nodes:          nodes,
		nodeConfigs:    nodeConfigs,
		groupConfigs:   groupConfigs,
		clusterName:    clusterName,
		autoFailback:   autoFailback,
		maxFailovers:   maxFailovers,
		failoverWindow: failoverWindow,
		logger:         logger.With("component", "failover-coordinator"),
	}
}

// CanFailover checks the anti-flap window. Returns false if too many failovers recently.
func (fc *FailoverCoordinator) CanFailover() bool {
	fc.mu.Lock()
	defer fc.mu.Unlock()

	cutoff := time.Now().Add(-fc.failoverWindow)
	// Prune old entries.
	var recent []time.Time
	for _, t := range fc.failoverHistory {
		if t.After(cutoff) {
			recent = append(recent, t)
		}
	}
	fc.failoverHistory = recent

	return len(recent) < fc.maxFailovers
}

// SelectBestCandidate picks the highest-priority self-healthy standby node.
// Excludes the current active node. Lower priority number = higher priority.
func (fc *FailoverCoordinator) SelectBestCandidate(observations []Observation, activeNodeID string) (string, error) {
	// Build a map of node → selfHealthy from observations.
	selfHealthMap := make(map[string]bool)
	for _, obs := range observations {
		selfHealthMap[obs.NodeID] = obs.SelfHealthy
	}

	// Sort node configs by priority (lower = higher priority).
	sorted := make([]config.NodeConfig, len(fc.nodeConfigs))
	copy(sorted, fc.nodeConfigs)
	sort.Slice(sorted, func(i, j int) bool {
		return sorted[i].Priority < sorted[j].Priority
	})

	for _, nc := range sorted {
		if nc.ID == activeNodeID {
			continue
		}

		// Check if the node reported itself as healthy.
		healthy, hasObs := selfHealthMap[nc.ID]
		if !hasObs {
			fc.logger.Warn("no observation from candidate node", "node", nc.ID)
			continue
		}
		if !healthy {
			fc.logger.Warn("candidate not self-healthy", "node", nc.ID)
			continue
		}

		// Check if the node is reachable.
		n, ok := fc.nodes[nc.ID]
		if !ok {
			continue
		}
		if !n.Reachable() {
			fc.logger.Warn("candidate not reachable", "node", nc.ID)
			continue
		}

		return nc.ID, nil
	}

	return "", fmt.Errorf("no healthy candidate available for failover")
}

// ExecuteFailover performs the full failover sequence:
// 1. CAS increment failover epoch (prevent double failover)
// 2. Deactivate on old node (reverse order)
// 3. Activate on new node (forward order)
// 4. Update active node in Consul KV
func (fc *FailoverCoordinator) ExecuteFailover(fromNodeID, toNodeID string) error {
	fc.logger.Warn("FAILOVER: starting",
		"from", fromNodeID, "to", toNodeID)

	// Step 1: CAS increment failover epoch.
	epochKey := fmt.Sprintf("ha-controller/%s/failover-epoch", fc.clusterName)
	epochStr, modIdx, err := fc.consul.GetKVWithIndex(epochKey)
	if err != nil {
		return fmt.Errorf("read failover epoch: %w", err)
	}
	epoch := 0
	if epochStr != "" {
		epoch, _ = strconv.Atoi(epochStr)
	}
	newEpoch := strconv.Itoa(epoch + 1)

	ok, err := fc.consul.CAS(epochKey, newEpoch, modIdx)
	if err != nil {
		return fmt.Errorf("cas failover epoch: %w", err)
	}
	if !ok {
		return fmt.Errorf("failover epoch CAS failed: another coordinator may have triggered failover")
	}
	fc.logger.Info("failover epoch incremented", "epoch", newEpoch)

	// Step 2: Deactivate on old node (best-effort if node unreachable).
	fromNode, ok := fc.nodes[fromNodeID]
	if ok && fromNode.Reachable() {
		fc.logger.Info("deactivating resources on old node", "node", fromNodeID)
		groups := fc.buildGroupsForNode(fromNode.Executor())
		for _, g := range groups {
			if err := g.Deactivate(); err != nil {
				fc.logger.Error("deactivation failed on old node (continuing)", "group", g.ID(), "node", fromNodeID, "err", err)
			}
		}
	} else {
		fc.logger.Warn("old node unreachable, skipping deactivation", "node", fromNodeID)
	}

	// Step 3: Activate on new node.
	toNode, ok := fc.nodes[toNodeID]
	if !ok {
		return fmt.Errorf("target node %s not found", toNodeID)
	}

	fc.logger.Info("activating resources on new node", "node", toNodeID)
	groups := fc.buildGroupsForNode(toNode.Executor())
	for _, g := range groups {
		if err := g.Activate(); err != nil {
			return fmt.Errorf("activation failed on new node %s, group %s: %w", toNodeID, g.ID(), err)
		}
	}

	// Step 4: Update active node in Consul.
	if err := fc.sentinel.SetActiveNodeInConsul(toNodeID); err != nil {
		return fmt.Errorf("update active node in consul: %w", err)
	}

	// Record failover time for anti-flap.
	fc.mu.Lock()
	fc.failoverHistory = append(fc.failoverHistory, time.Now())
	fc.mu.Unlock()

	// Reset sentinel SDOWN/ODOWN state.
	fc.sentinel.ResetFailState()

	fc.logger.Warn("FAILOVER: completed", "from", fromNodeID, "to", toNodeID, "epoch", newEpoch)
	return nil
}

// ExecuteManualFailover performs a manual failover to a specified target node.
func (fc *FailoverCoordinator) ExecuteManualFailover(toNodeID string) error {
	activeNode := fc.sentinel.ActiveNodeID()
	if activeNode == "" {
		return fmt.Errorf("no active node set")
	}
	if activeNode == toNodeID {
		return fmt.Errorf("target node %s is already active", toNodeID)
	}
	if _, ok := fc.nodes[toNodeID]; !ok {
		return fmt.Errorf("unknown target node %s", toNodeID)
	}

	return fc.ExecuteFailover(activeNode, toNodeID)
}

// buildGroupsForNode creates ResourceGroup instances using the given executor.
// Each resource is instantiated with the target node's executor so commands
// run via SSH on that host.
func (fc *FailoverCoordinator) buildGroupsForNode(exec executor.Executor) []*resource.ResourceGroup {
	var groups []*resource.ResourceGroup

	for _, gc := range fc.groupConfigs {
		var resources []resource.Resource
		for _, rc := range gc.Resources {
			switch rc.Type {
			case "vip":
				ip := rc.Attrs["ip"]
				cidr := 24
				if c, ok := rc.Attrs["cidr"]; ok {
					fmt.Sscanf(c, "%d", &cidr)
				}
				iface := rc.Attrs["interface"]
				if iface == "" {
					iface = "eth0"
				}
				vip := resource.NewVipResource(rc.ID, ip, cidr, iface, exec, fc.logger)
				resources = append(resources, vip)

			case "action":
				activateCmd := rc.Attrs["activate"]
				deactivateCmd := rc.Attrs["deactivate"]
				checkCmd := rc.Attrs["check"]
				var opts []resource.ActionOption
				if t, ok := rc.Attrs["timeout"]; ok {
					if d, err := time.ParseDuration(t); err == nil {
						opts = append(opts, resource.WithActionTimeout(d))
					}
				}
				action := resource.NewActionResource(rc.ID, activateCmd, deactivateCmd, checkCmd, exec, fc.logger, opts...)
				resources = append(resources, action)

			case "noop":
				noop := resource.NewNoopResource(rc.ID, fc.logger)
				resources = append(resources, noop)
			}
		}
		group := resource.NewResourceGroup(gc.ID, fc.logger, resources...)
		groups = append(groups, group)
	}

	return groups
}

// InitializeActiveNode sets the initial active node if none is set.
// Picks the highest-priority node.
func (fc *FailoverCoordinator) InitializeActiveNode() (string, error) {
	// Check if already set in Consul.
	active, err := fc.sentinel.LoadActiveNode()
	if err != nil {
		return "", err
	}
	if active != "" {
		fc.logger.Info("active node loaded from consul", "active", active)
		return active, nil
	}

	// No active node set — pick highest priority.
	sorted := make([]config.NodeConfig, len(fc.nodeConfigs))
	copy(sorted, fc.nodeConfigs)
	sort.Slice(sorted, func(i, j int) bool {
		return sorted[i].Priority < sorted[j].Priority
	})

	if len(sorted) == 0 {
		return "", fmt.Errorf("no nodes configured")
	}

	firstNode := sorted[0].ID
	fc.logger.Info("initializing active node", "node", firstNode)

	// Activate resources on the first node.
	n, ok := fc.nodes[firstNode]
	if !ok {
		return "", fmt.Errorf("node %s not found", firstNode)
	}

	groups := fc.buildGroupsForNode(n.Executor())
	for _, g := range groups {
		if err := g.Activate(); err != nil {
			return "", fmt.Errorf("initial activation failed on %s, group %s: %w", firstNode, g.ID(), err)
		}
	}

	if err := fc.sentinel.SetActiveNodeInConsul(firstNode); err != nil {
		return "", err
	}

	return firstNode, nil
}
