package sentinel

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/telcobright/ha-controller/internal/config"
	"github.com/telcobright/ha-controller/internal/executor"
	"github.com/telcobright/ha-controller/internal/node"
)

// mockExec implements executor.Executor for testing.
type mockExec struct {
	cmdsRun []string
	results map[string]executor.ExecResult
}

func newMockExec() *mockExec {
	return &mockExec{results: make(map[string]executor.ExecResult)}
}

func (m *mockExec) Run(_ context.Context, cmd string, _ time.Duration) executor.ExecResult {
	m.cmdsRun = append(m.cmdsRun, cmd)
	if r, ok := m.results[cmd]; ok {
		return r
	}
	// Default: success.
	return executor.ExecResult{ExitCode: 0, Stdout: "ok"}
}

func (m *mockExec) Reachable() bool { return true }
func (m *mockExec) String() string  { return "mockExec" }

func failoverTestLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

func TestSelectBestCandidate(t *testing.T) {
	logger := failoverTestLogger()
	exec1 := newMockExec()
	exec2 := newMockExec()
	exec3 := newMockExec()

	nodes := map[string]node.Node{
		"node1": node.NewBaseNode("node1", "10.0.0.1", exec1, logger),
		"node2": node.NewBaseNode("node2", "10.0.0.2", exec2, logger),
		"node3": node.NewBaseNode("node3", "10.0.0.3", exec3, logger),
	}

	nodeConfigs := []config.NodeConfig{
		{ID: "node1", Address: "10.0.0.1", Priority: 1},
		{ID: "node2", Address: "10.0.0.2", Priority: 2},
		{ID: "node3", Address: "10.0.0.3", Priority: 3},
	}

	fc := NewFailoverCoordinator(nil, nil, nodes, nodeConfigs, nil, "test",
		false, 3, time.Hour, logger)

	observations := []Observation{
		{NodeID: "node1", SelfHealthy: true},
		{NodeID: "node2", SelfHealthy: true},
		{NodeID: "node3", SelfHealthy: false},
	}

	// Active is node1, so candidate should be node2 (highest priority among remaining healthy).
	candidate, err := fc.SelectBestCandidate(observations, "node1")
	if err != nil {
		t.Fatalf("SelectBestCandidate error: %v", err)
	}
	if candidate != "node2" {
		t.Errorf("candidate = %q, want %q", candidate, "node2")
	}
}

func TestSelectBestCandidate_NoHealthy(t *testing.T) {
	logger := failoverTestLogger()
	exec1 := newMockExec()
	exec2 := newMockExec()

	nodes := map[string]node.Node{
		"node1": node.NewBaseNode("node1", "10.0.0.1", exec1, logger),
		"node2": node.NewBaseNode("node2", "10.0.0.2", exec2, logger),
	}

	nodeConfigs := []config.NodeConfig{
		{ID: "node1", Address: "10.0.0.1", Priority: 1},
		{ID: "node2", Address: "10.0.0.2", Priority: 2},
	}

	fc := NewFailoverCoordinator(nil, nil, nodes, nodeConfigs, nil, "test",
		false, 3, time.Hour, logger)

	observations := []Observation{
		{NodeID: "node1", SelfHealthy: true},
		{NodeID: "node2", SelfHealthy: false},
	}

	// Active is node1, node2 is not healthy.
	_, err := fc.SelectBestCandidate(observations, "node1")
	if err == nil {
		t.Fatal("expected error when no healthy candidate available")
	}
}

func TestSelectBestCandidate_PriorityOrder(t *testing.T) {
	logger := failoverTestLogger()
	exec1 := newMockExec()
	exec2 := newMockExec()
	exec3 := newMockExec()

	nodes := map[string]node.Node{
		"node1": node.NewBaseNode("node1", "10.0.0.1", exec1, logger),
		"node2": node.NewBaseNode("node2", "10.0.0.2", exec2, logger),
		"node3": node.NewBaseNode("node3", "10.0.0.3", exec3, logger),
	}

	// node3 has highest priority (lowest number).
	nodeConfigs := []config.NodeConfig{
		{ID: "node1", Address: "10.0.0.1", Priority: 3},
		{ID: "node2", Address: "10.0.0.2", Priority: 2},
		{ID: "node3", Address: "10.0.0.3", Priority: 1},
	}

	fc := NewFailoverCoordinator(nil, nil, nodes, nodeConfigs, nil, "test",
		false, 3, time.Hour, logger)

	observations := []Observation{
		{NodeID: "node1", SelfHealthy: true},
		{NodeID: "node2", SelfHealthy: true},
		{NodeID: "node3", SelfHealthy: true},
	}

	// Active is node3 (priority 1), so next best is node2 (priority 2).
	candidate, err := fc.SelectBestCandidate(observations, "node3")
	if err != nil {
		t.Fatalf("SelectBestCandidate error: %v", err)
	}
	if candidate != "node2" {
		t.Errorf("candidate = %q, want %q (priority 2)", candidate, "node2")
	}
}

func TestCanFailover_AntiFlap(t *testing.T) {
	logger := failoverTestLogger()

	fc := NewFailoverCoordinator(nil, nil, nil, nil, nil, "test",
		false, 2, time.Hour, logger)

	if !fc.CanFailover() {
		t.Fatal("should allow failover initially")
	}

	// Simulate 2 recent failovers.
	fc.mu.Lock()
	fc.failoverHistory = []time.Time{time.Now(), time.Now()}
	fc.mu.Unlock()

	if fc.CanFailover() {
		t.Fatal("should not allow failover after reaching max (2) in window")
	}
}

func TestCanFailover_OldEntriesPruned(t *testing.T) {
	logger := failoverTestLogger()

	fc := NewFailoverCoordinator(nil, nil, nil, nil, nil, "test",
		false, 2, time.Hour, logger)

	// Old entries outside the window.
	fc.mu.Lock()
	fc.failoverHistory = []time.Time{
		time.Now().Add(-2 * time.Hour),
		time.Now().Add(-3 * time.Hour),
	}
	fc.mu.Unlock()

	if !fc.CanFailover() {
		t.Fatal("should allow failover when old entries are outside the window")
	}
}

func TestBuildGroupsForNode(t *testing.T) {
	logger := failoverTestLogger()

	exec := newMockExec()
	groupConfigs := []config.GroupConfig{
		{
			ID: "test-group",
			Resources: []config.ResourceConfig{
				{
					ID:   "assign-vip",
					Type: "vip",
					Attrs: map[string]string{
						"ip":        "10.0.0.100",
						"cidr":      "24",
						"interface": "br0",
					},
				},
				{
					ID:   "manage-svc",
					Type: "action",
					Attrs: map[string]string{
						"activate":   "start-svc",
						"deactivate": "stop-svc",
						"check":      "check-svc",
						"timeout":    "15s",
					},
				},
				{
					ID:   "dummy",
					Type: "noop",
				},
			},
		},
	}

	fc := NewFailoverCoordinator(nil, nil, nil, nil, groupConfigs, "test",
		false, 3, time.Hour, logger)

	groups := fc.buildGroupsForNode(exec)
	if len(groups) != 1 {
		t.Fatalf("len(groups) = %d, want 1", len(groups))
	}

	resources := groups[0].Resources()
	if len(resources) != 3 {
		t.Fatalf("len(resources) = %d, want 3", len(resources))
	}

	if resources[0].Type() != "vip" {
		t.Errorf("resources[0].Type() = %q, want %q", resources[0].Type(), "vip")
	}
	if resources[1].Type() != "action" {
		t.Errorf("resources[1].Type() = %q, want %q", resources[1].Type(), "action")
	}
	if resources[2].Type() != "noop" {
		t.Errorf("resources[2].Type() = %q, want %q", resources[2].Type(), "noop")
	}

	// Test that the action resource's activate executes on our mock executor.
	fmt.Sscanf("ok", "%s") // reference fmt to avoid unused import
	if err := resources[1].Activate(); err != nil {
		t.Fatalf("action Activate() error: %v", err)
	}

	// Verify the command was routed to our mock executor.
	found := false
	for _, cmd := range exec.cmdsRun {
		if cmd == "start-svc" {
			found = true
			break
		}
	}
	if !found {
		t.Error("activate command 'start-svc' was not run on the executor")
	}
}
