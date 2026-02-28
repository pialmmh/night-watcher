package sentinel

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"path"
	"strings"
	"sync"
	"time"

	"github.com/telcobright/ha-controller/internal/consul"
	"github.com/telcobright/ha-controller/internal/healthcheck"
)

// Observation is a node's view of the active node's health.
type Observation struct {
	NodeID      string                    `json:"nodeId"`
	TargetNode  string                    `json:"targetNode"`
	SDOWN       bool                      `json:"sdown"`
	FailCount   int                       `json:"failCount"`
	SelfHealthy bool                      `json:"selfHealthy"`
	Results     []healthcheck.CheckResult `json:"results"`
	At          time.Time                 `json:"at"`
}

// Sentinel runs health checks and publishes observations for quorum-based failover.
type Sentinel struct {
	nodeID      string
	clusterName string
	consul      *consul.Client

	clusterChecks []healthcheck.HealthCheck // scope=cluster: check the active node / VIP
	selfChecks    []healthcheck.HealthCheck // scope=self: check own readiness

	quorum           int
	failThreshold    int
	observationStale time.Duration

	mu           sync.RWMutex
	failCount    int
	sdown        bool
	odown        bool
	activeNodeID string
	lastResults  []healthcheck.CheckResult
	selfHealthy  bool

	logger *slog.Logger
}

// NewSentinel creates a sentinel for health consensus.
func NewSentinel(
	nodeID, clusterName string,
	consulClient *consul.Client,
	clusterChecks, selfChecks []healthcheck.HealthCheck,
	quorum, failThreshold int,
	observationStale time.Duration,
	logger *slog.Logger,
) *Sentinel {
	return &Sentinel{
		nodeID:           nodeID,
		clusterName:      clusterName,
		consul:           consulClient,
		clusterChecks:    clusterChecks,
		selfChecks:       selfChecks,
		quorum:           quorum,
		failThreshold:    failThreshold,
		observationStale: observationStale,
		logger:           logger.With("component", "sentinel", "node", nodeID),
	}
}

// kvPrefix returns the Consul KV prefix for this cluster.
func (s *Sentinel) kvPrefix() string {
	return fmt.Sprintf("ha-controller/%s", s.clusterName)
}

// ActiveNodeID returns the current active node ID.
func (s *Sentinel) ActiveNodeID() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.activeNodeID
}

// SetActiveNodeID sets the active node ID (used during initialization and failover).
func (s *Sentinel) SetActiveNodeID(id string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.activeNodeID = id
}

// IsSdown returns whether this node sees the active node as subjectively down.
func (s *Sentinel) IsSdown() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.sdown
}

// IsOdown returns whether quorum agrees the active node is down.
func (s *Sentinel) IsOdown() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.odown
}

// IsSelfHealthy returns whether this node's self-checks are passing.
func (s *Sentinel) IsSelfHealthy() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.selfHealthy
}

// LoadActiveNode reads the active node from Consul KV.
func (s *Sentinel) LoadActiveNode() (string, error) {
	key := s.kvPrefix() + "/active"
	val, err := s.consul.GetKV(key)
	if err != nil {
		return "", err
	}
	if val != "" {
		s.SetActiveNodeID(val)
	}
	return val, nil
}

// SetActiveNodeInConsul writes the active node to Consul KV.
func (s *Sentinel) SetActiveNodeInConsul(nodeID string) error {
	key := s.kvPrefix() + "/active"
	if err := s.consul.SetKV(key, nodeID); err != nil {
		return err
	}
	s.SetActiveNodeID(nodeID)
	return nil
}

// RunChecks executes all cluster-scope and self-scope health checks.
// Updates internal SDOWN state.
func (s *Sentinel) RunChecks() {
	// Run cluster checks (check the active node / VIP services).
	var clusterResults []healthcheck.CheckResult
	allPassed := true
	for _, c := range s.clusterChecks {
		r := c.Run()
		clusterResults = append(clusterResults, r)
		if !r.Passed {
			allPassed = false
		}
	}

	// Run self checks (check this node's own readiness).
	selfHealthy := true
	for _, c := range s.selfChecks {
		r := c.Run()
		if !r.Passed {
			selfHealthy = false
		}
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	s.lastResults = clusterResults
	s.selfHealthy = selfHealthy

	if len(s.clusterChecks) == 0 {
		// No cluster checks configured: no SDOWN detection possible.
		s.failCount = 0
		s.sdown = false
		return
	}

	if allPassed {
		if s.failCount > 0 {
			s.logger.Info("cluster checks recovered", "prev_fail_count", s.failCount)
		}
		s.failCount = 0
		s.sdown = false
	} else {
		s.failCount++
		s.logger.Warn("cluster checks failed", "fail_count", s.failCount, "threshold", s.failThreshold)
		if s.failCount >= s.failThreshold {
			if !s.sdown {
				s.logger.Warn("SDOWN: marking active node as subjectively down", "active", s.activeNodeID)
			}
			s.sdown = true
		}
	}
}

// PublishObservation writes this node's observation to Consul KV.
func (s *Sentinel) PublishObservation() error {
	s.mu.RLock()
	obs := Observation{
		NodeID:      s.nodeID,
		TargetNode:  s.activeNodeID,
		SDOWN:       s.sdown,
		FailCount:   s.failCount,
		SelfHealthy: s.selfHealthy,
		Results:     s.lastResults,
		At:          time.Now(),
	}
	s.mu.RUnlock()

	data, err := json.Marshal(obs)
	if err != nil {
		return fmt.Errorf("marshal observation: %w", err)
	}

	key := fmt.Sprintf("%s/observations/%s", s.kvPrefix(), s.nodeID)
	return s.consul.SetKV(key, string(data))
}

// ReadAllObservations reads all peer observations from Consul KV, filtering stale ones.
func (s *Sentinel) ReadAllObservations() ([]Observation, error) {
	prefix := fmt.Sprintf("%s/observations/", s.kvPrefix())
	kvPairs, err := s.consul.ListKVPrefix(prefix)
	if err != nil {
		return nil, err
	}

	now := time.Now()
	var observations []Observation
	for key, val := range kvPairs {
		var obs Observation
		if err := json.Unmarshal([]byte(val), &obs); err != nil {
			nodeID := path.Base(key)
			s.logger.Warn("failed to unmarshal observation", "key", key, "node", nodeID, "err", err)
			continue
		}
		if now.Sub(obs.At) > s.observationStale {
			s.logger.Debug("stale observation, skipping", "node", obs.NodeID, "age", now.Sub(obs.At))
			continue
		}
		observations = append(observations, obs)
	}

	return observations, nil
}

// EvaluateConsensus checks if quorum nodes agree the active node is SDOWN → ODOWN.
func (s *Sentinel) EvaluateConsensus(observations []Observation) bool {
	s.mu.RLock()
	activeNode := s.activeNodeID
	s.mu.RUnlock()

	sdownCount := 0
	for _, obs := range observations {
		if obs.TargetNode == activeNode && obs.SDOWN {
			sdownCount++
		}
	}

	odown := sdownCount >= s.quorum

	s.mu.Lock()
	prevOdown := s.odown
	s.odown = odown
	s.mu.Unlock()

	if odown && !prevOdown {
		s.logger.Warn("ODOWN: quorum reached for active node failure",
			"active", activeNode, "sdown_votes", sdownCount, "quorum", s.quorum)
	} else if !odown && prevOdown {
		s.logger.Info("ODOWN cleared", "active", activeNode, "sdown_votes", sdownCount)
	}

	return odown
}

// ResetFailState clears SDOWN/ODOWN counters after a successful failover.
func (s *Sentinel) ResetFailState() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.failCount = 0
	s.sdown = false
	s.odown = false
}

// LastObservation returns a snapshot of this node's current observation.
func (s *Sentinel) LastObservation() Observation {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return Observation{
		NodeID:      s.nodeID,
		TargetNode:  s.activeNodeID,
		SDOWN:       s.sdown,
		FailCount:   s.failCount,
		SelfHealthy: s.selfHealthy,
		Results:     s.lastResults,
		At:          time.Now(),
	}
}

// ParseObservationNodeID extracts the node ID from a full Consul KV key.
func ParseObservationNodeID(key string) string {
	parts := strings.Split(key, "/")
	if len(parts) > 0 {
		return parts[len(parts)-1]
	}
	return key
}
