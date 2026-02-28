package engine

import (
	"context"
	"log/slog"
	"time"

	"github.com/telcobright/ha-controller/internal/config"
	"github.com/telcobright/ha-controller/internal/consul"
	"github.com/telcobright/ha-controller/internal/resource"
	"github.com/telcobright/ha-controller/internal/sentinel"
)

// EngineState represents the controller's current role.
type EngineState int

const (
	StateInit EngineState = iota
	StateFollower
	StateLeader
	StateStopping
)

func (s EngineState) String() string {
	switch s {
	case StateInit:
		return "INIT"
	case StateFollower:
		return "FOLLOWER"
	case StateLeader:
		return "LEADER"
	case StateStopping:
		return "STOPPING"
	default:
		return "UNKNOWN"
	}
}

// Engine is the main reconciliation loop for the HA controller.
// All nodes run health checks and publish observations.
// The coordinator (Consul lock holder) triggers failover when quorum agrees.
type Engine struct {
	cfg         *config.Config
	nodeID      string
	consul      *consul.Client
	election    *consul.LeaderElection
	groups      []*resource.ResourceGroup // kept for API backward compat
	sentinel    *sentinel.Sentinel
	coordinator *sentinel.FailoverCoordinator
	state       EngineState
	initialized bool
	logger      *slog.Logger
}

// NewEngine creates an Engine with sentinel-based consensus.
func NewEngine(
	cfg *config.Config,
	nodeID string,
	consulClient *consul.Client,
	election *consul.LeaderElection,
	groups []*resource.ResourceGroup,
	sen *sentinel.Sentinel,
	coord *sentinel.FailoverCoordinator,
	logger *slog.Logger,
) *Engine {
	return &Engine{
		cfg:         cfg,
		nodeID:      nodeID,
		consul:      consulClient,
		election:    election,
		groups:      groups,
		sentinel:    sen,
		coordinator: coord,
		state:       StateInit,
		logger:      logger.With("component", "engine", "node", nodeID),
	}
}

// Run starts the engine loop. It blocks until the context is cancelled.
func (e *Engine) Run(ctx context.Context) error {
	e.logger.Info("engine starting", "cluster", e.cfg.Cluster.Name, "tenant", e.cfg.Cluster.Tenant)

	// Create Consul session for leader election.
	if err := e.election.CreateSession(); err != nil {
		return err
	}
	defer e.election.DestroySession()

	e.state = StateFollower
	e.logger.Info("entering reconciliation loop")

	interval := e.cfg.Cluster.CheckInterval.Duration
	if interval == 0 {
		interval = 5 * time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	renewTicker := time.NewTicker(10 * time.Second)
	defer renewTicker.Stop()

	for {
		select {
		case <-ctx.Done():
			return e.shutdown()

		case <-renewTicker.C:
			if err := e.election.RenewSession(); err != nil {
				e.logger.Error("session renewal failed", "err", err)
			}

		case <-ticker.C:
			if err := e.reconcile(ctx); err != nil {
				e.logger.Error("reconciliation error", "err", err)
			}
		}
	}
}

// reconcile runs one iteration of the sentinel-aware control loop.
// Every node: run checks → publish observation → read peers → evaluate ODOWN.
// Coordinator on ODOWN: select candidate → execute failover.
func (e *Engine) reconcile(ctx context.Context) error {
	_ = ctx

	// Step 1: Try to acquire coordinator lock.
	acquired, err := e.election.TryAcquire()
	if err != nil {
		return err
	}

	wasLeader := e.state == StateLeader
	if acquired && !wasLeader {
		e.logger.Info("promoted to coordinator")
		e.state = StateLeader
	} else if !acquired && wasLeader {
		e.logger.Warn("lost coordinator role")
		e.state = StateFollower
	} else if acquired {
		e.state = StateLeader
	}

	// Step 2: Coordinator initializes active node on first run.
	if acquired && !e.initialized {
		if _, err := e.coordinator.InitializeActiveNode(); err != nil {
			e.logger.Error("failed to initialize active node", "err", err)
			return err
		}
		e.initialized = true
	}

	// All nodes load the current active node from Consul (followers learn it too).
	if !acquired || e.initialized {
		if _, err := e.sentinel.LoadActiveNode(); err != nil {
			e.logger.Warn("failed to load active node", "err", err)
		}
	}

	// Step 3: Every node runs health checks.
	e.sentinel.RunChecks()

	// Step 4: Publish observation to Consul KV.
	if err := e.sentinel.PublishObservation(); err != nil {
		e.logger.Error("failed to publish observation", "err", err)
	}

	// Step 5: Read all peer observations.
	observations, err := e.sentinel.ReadAllObservations()
	if err != nil {
		e.logger.Error("failed to read observations", "err", err)
		return nil // non-fatal: continue next cycle
	}

	// Step 6: Evaluate consensus.
	odown := e.sentinel.EvaluateConsensus(observations)

	// Step 7: Coordinator handles ODOWN by triggering failover.
	if acquired && odown {
		activeNode := e.sentinel.ActiveNodeID()
		e.logger.Warn("ODOWN detected, coordinator evaluating failover",
			"active", activeNode)

		if !e.coordinator.CanFailover() {
			e.logger.Warn("failover suppressed: anti-flap limit reached")
			return nil
		}

		candidate, err := e.coordinator.SelectBestCandidate(observations, activeNode)
		if err != nil {
			e.logger.Error("no failover candidate available", "err", err)
			return nil
		}

		if err := e.coordinator.ExecuteFailover(activeNode, candidate); err != nil {
			e.logger.Error("failover execution failed", "err", err)
			return nil
		}
	}

	// Log status for followers.
	if !acquired {
		leader, _ := e.election.CurrentLeader()
		e.logger.Debug("follower status",
			"coordinator", leader,
			"active", e.sentinel.ActiveNodeID(),
			"sdown", e.sentinel.IsSdown(),
			"odown", e.sentinel.IsOdown(),
		)
	}

	return nil
}

// shutdown cleanly stops the engine.
func (e *Engine) shutdown() error {
	e.state = StateStopping
	e.logger.Info("engine shutting down")

	if e.election.IsLeader() {
		e.logger.Info("releasing coordinator lock before shutdown")
		if err := e.election.Release(); err != nil {
			e.logger.Error("release leadership failed", "err", err)
		}
	}

	e.logger.Info("engine stopped")
	return nil
}

// State returns the current engine state.
func (e *Engine) State() EngineState {
	return e.state
}

// Groups returns the resource groups (for API backward compat).
func (e *Engine) Groups() []*resource.ResourceGroup {
	return e.groups
}

// Sentinel returns the sentinel instance for API access.
func (e *Engine) Sentinel() *sentinel.Sentinel {
	return e.sentinel
}

// Coordinator returns the failover coordinator for API access.
func (e *Engine) Coordinator() *sentinel.FailoverCoordinator {
	return e.coordinator
}

// CurrentLeader returns the current coordinator node ID.
func (e *Engine) CurrentLeader() string {
	leader, _ := e.election.CurrentLeader()
	return leader
}
