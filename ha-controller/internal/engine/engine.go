package engine

import (
	"context"
	"log/slog"
	"time"

	"github.com/telcobright/ha-controller/internal/config"
	"github.com/telcobright/ha-controller/internal/consul"
	"github.com/telcobright/ha-controller/internal/resource"
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
// The leader runs health checks and manages resource groups.
// Followers wait to acquire leadership.
type Engine struct {
	cfg      *config.Config
	nodeID   string
	consul   *consul.Client
	election *consul.LeaderElection
	groups   []*resource.ResourceGroup
	state    EngineState
	logger   *slog.Logger
}

// NewEngine creates an Engine from config.
func NewEngine(cfg *config.Config, nodeID string, consulClient *consul.Client, election *consul.LeaderElection, groups []*resource.ResourceGroup, logger *slog.Logger) *Engine {
	return &Engine{
		cfg:      cfg,
		nodeID:   nodeID,
		consul:   consulClient,
		election: election,
		groups:   groups,
		state:    StateInit,
		logger:   logger.With("component", "engine", "node", nodeID),
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

	ticker := time.NewTicker(5 * time.Second)
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

// reconcile runs one iteration of the control loop.
func (e *Engine) reconcile(ctx context.Context) error {
	_ = ctx // will be used when health checks run with context

	acquired, err := e.election.TryAcquire()
	if err != nil {
		return err
	}

	wasLeader := e.state == StateLeader

	if acquired {
		if !wasLeader {
			e.logger.Info("promoted to leader")
			e.state = StateLeader
			return e.onBecomeLeader()
		}
		// Already leader — run health checks.
		return e.leaderLoop()
	}

	// Not leader.
	if wasLeader {
		e.logger.Warn("lost leadership")
		e.state = StateFollower
		return e.onLoseLeadership()
	}

	// Still follower — log current leader.
	leader, _ := e.election.CurrentLeader()
	e.logger.Debug("follower waiting", "leader", leader)
	return nil
}

// onBecomeLeader is called when this node first acquires leadership.
func (e *Engine) onBecomeLeader() error {
	e.logger.Info("activating resource groups")
	for _, g := range e.groups {
		if err := g.Activate(); err != nil {
			e.logger.Error("failed to activate group", "group", g.ID(), "err", err)
			return err
		}
	}
	return nil
}

// leaderLoop runs health checks and handles degradation.
func (e *Engine) leaderLoop() error {
	for _, g := range e.groups {
		results := g.CheckAll()
		for id, result := range results {
			if result.Status != resource.HealthHealthy {
				e.logger.Warn("resource unhealthy", "resource", id, "status", result.Status, "reason", result.Reason)
				// TODO: implement escalation policy (retry, failover, alert)
			}
		}
	}
	return nil
}

// onLoseLeadership is called when this node loses the leader lock.
func (e *Engine) onLoseLeadership() error {
	e.logger.Warn("deactivating resource groups")
	for _, g := range e.groups {
		if err := g.Deactivate(); err != nil {
			e.logger.Error("failed to deactivate group", "group", g.ID(), "err", err)
		}
	}
	return nil
}

// shutdown cleanly stops the engine.
func (e *Engine) shutdown() error {
	e.state = StateStopping
	e.logger.Info("engine shutting down")

	if e.election.IsLeader() {
		e.logger.Info("releasing leadership before shutdown")
		for _, g := range e.groups {
			if err := g.Deactivate(); err != nil {
				e.logger.Error("shutdown deactivation failed", "group", g.ID(), "err", err)
			}
		}
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

// Groups returns the resource groups managed by the engine.
func (e *Engine) Groups() []*resource.ResourceGroup {
	return e.groups
}

// CurrentLeader returns the current leader node ID, or empty string.
func (e *Engine) CurrentLeader() string {
	leader, _ := e.election.CurrentLeader()
	return leader
}
