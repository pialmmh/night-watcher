package consul

import (
	"fmt"
	"log/slog"
	"sync"
	"time"

	consulapi "github.com/hashicorp/consul/api"

	"github.com/telcobright/ha-controller/internal/config"
)

// LeaderElection manages session-based leader election via Consul.
type LeaderElection struct {
	client    *Client
	nodeID    string
	lockKey   string
	sessionID string
	isLeader  bool
	mu        sync.RWMutex
	cfg       config.ConsulConfig
	logger    *slog.Logger
	stopCh    chan struct{}
}

// NewLeaderElection creates a leader election manager.
func NewLeaderElection(client *Client, nodeID, clusterName string, cfg config.ConsulConfig, logger *slog.Logger) *LeaderElection {
	return &LeaderElection{
		client:  client,
		nodeID:  nodeID,
		lockKey: fmt.Sprintf("ha-controller/%s/leader", clusterName),
		cfg:     cfg,
		logger:  logger.With("component", "leader-election", "node", nodeID),
		stopCh:  make(chan struct{}),
	}
}

// CreateSession creates a Consul session for leader election.
func (le *LeaderElection) CreateSession() error {
	ttl := "15s"
	if le.cfg.SessionTTL.Duration > 0 {
		ttl = le.cfg.SessionTTL.Duration.String()
	}

	lockDelay := 5 * time.Second
	if le.cfg.LockDelay.Duration > 0 {
		lockDelay = le.cfg.LockDelay.Duration
	}

	entry := &consulapi.SessionEntry{
		Name:      fmt.Sprintf("hactl-%s", le.nodeID),
		TTL:       ttl,
		Behavior:  "delete",
		LockDelay: lockDelay,
	}

	id, _, err := le.client.session.Create(entry, nil)
	if err != nil {
		return fmt.Errorf("create session: %w", err)
	}

	le.sessionID = id
	le.logger.Info("consul session created", "session_id", id, "ttl", ttl)
	return nil
}

// TryAcquire attempts to acquire the leader lock. Returns true if this node is now the leader.
func (le *LeaderElection) TryAcquire() (bool, error) {
	if le.sessionID == "" {
		return false, fmt.Errorf("no session created")
	}

	kv := &consulapi.KVPair{
		Key:     le.lockKey,
		Value:   []byte(le.nodeID),
		Session: le.sessionID,
	}

	acquired, _, err := le.client.kv.Acquire(kv, nil)
	if err != nil {
		return false, fmt.Errorf("acquire lock: %w", err)
	}

	le.mu.Lock()
	le.isLeader = acquired
	le.mu.Unlock()

	if acquired {
		le.logger.Info("acquired leadership")
	} else {
		le.logger.Debug("leadership not acquired, another node is leader")
	}

	return acquired, nil
}

// Release releases the leader lock.
func (le *LeaderElection) Release() error {
	if le.sessionID == "" {
		return nil
	}

	kv := &consulapi.KVPair{
		Key:     le.lockKey,
		Session: le.sessionID,
	}

	_, _, err := le.client.kv.Release(kv, nil)
	if err != nil {
		return fmt.Errorf("release lock: %w", err)
	}

	le.mu.Lock()
	le.isLeader = false
	le.mu.Unlock()

	le.logger.Info("released leadership")
	return nil
}

// RenewSession renews the Consul session to keep it alive.
func (le *LeaderElection) RenewSession() error {
	if le.sessionID == "" {
		return fmt.Errorf("no session to renew")
	}

	_, _, err := le.client.session.Renew(le.sessionID, nil)
	if err != nil {
		return fmt.Errorf("renew session: %w", err)
	}
	return nil
}

// IsLeader returns whether this node currently holds the leader lock.
func (le *LeaderElection) IsLeader() bool {
	le.mu.RLock()
	defer le.mu.RUnlock()
	return le.isLeader
}

// CurrentLeader returns the node ID of the current leader, or empty string if none.
func (le *LeaderElection) CurrentLeader() (string, error) {
	pair, _, err := le.client.kv.Get(le.lockKey, nil)
	if err != nil {
		return "", fmt.Errorf("get leader: %w", err)
	}
	if pair == nil || pair.Session == "" {
		return "", nil
	}
	return string(pair.Value), nil
}

// DestroySession destroys the Consul session.
func (le *LeaderElection) DestroySession() error {
	if le.sessionID == "" {
		return nil
	}

	_, err := le.client.session.Destroy(le.sessionID, nil)
	if err != nil {
		return fmt.Errorf("destroy session: %w", err)
	}

	le.logger.Info("session destroyed", "session_id", le.sessionID)
	le.sessionID = ""
	return nil
}

// Stop signals the leader election to stop.
func (le *LeaderElection) Stop() {
	close(le.stopCh)
}
