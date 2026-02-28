package sentinel

import (
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/telcobright/ha-controller/internal/healthcheck"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

// mockCheck is a healthcheck.HealthCheck that returns a configurable result.
type mockCheck struct {
	name   string
	passed bool
}

func (c *mockCheck) Name() string { return c.name }
func (c *mockCheck) Run() healthcheck.CheckResult {
	return healthcheck.CheckResult{Name: c.name, Passed: c.passed, Output: "mock"}
}

func TestSentinel_SdownAfterThreshold(t *testing.T) {
	failCheck := &mockCheck{name: "svc", passed: false}

	s := NewSentinel("node1", "cluster1", nil,
		[]healthcheck.HealthCheck{failCheck}, nil,
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	// Run 1-2: not yet SDOWN.
	s.RunChecks()
	if s.IsSdown() {
		t.Fatal("should not be SDOWN after 1 failure")
	}
	s.RunChecks()
	if s.IsSdown() {
		t.Fatal("should not be SDOWN after 2 failures")
	}

	// Run 3: SDOWN.
	s.RunChecks()
	if !s.IsSdown() {
		t.Fatal("should be SDOWN after 3 failures")
	}
}

func TestSentinel_SdownRecovers(t *testing.T) {
	check := &mockCheck{name: "svc", passed: false}

	s := NewSentinel("node1", "cluster1", nil,
		[]healthcheck.HealthCheck{check}, nil,
		2, 2, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	// 2 failures → SDOWN.
	s.RunChecks()
	s.RunChecks()
	if !s.IsSdown() {
		t.Fatal("should be SDOWN after 2 failures")
	}

	// Check passes → clears SDOWN.
	check.passed = true
	s.RunChecks()
	if s.IsSdown() {
		t.Fatal("SDOWN should be cleared after recovery")
	}
}

func TestSentinel_NoClusterChecks(t *testing.T) {
	s := NewSentinel("node1", "cluster1", nil,
		nil, nil,
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	s.RunChecks()
	if s.IsSdown() {
		t.Fatal("should not be SDOWN with no cluster checks")
	}
}

func TestSentinel_SelfHealthy(t *testing.T) {
	selfCheck := &mockCheck{name: "local", passed: true}

	s := NewSentinel("node1", "cluster1", nil,
		nil, []healthcheck.HealthCheck{selfCheck},
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	s.RunChecks()
	if !s.IsSelfHealthy() {
		t.Fatal("should be self-healthy when self checks pass")
	}

	selfCheck.passed = false
	s.RunChecks()
	if s.IsSelfHealthy() {
		t.Fatal("should not be self-healthy when self checks fail")
	}
}

func TestSentinel_EvaluateConsensus_Odown(t *testing.T) {
	s := NewSentinel("node1", "cluster1", nil,
		nil, nil,
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	observations := []Observation{
		{NodeID: "node1", TargetNode: "active-node", SDOWN: true, At: time.Now()},
		{NodeID: "node2", TargetNode: "active-node", SDOWN: true, At: time.Now()},
		{NodeID: "node3", TargetNode: "active-node", SDOWN: false, At: time.Now()},
	}

	odown := s.EvaluateConsensus(observations)
	if !odown {
		t.Fatal("should be ODOWN when 2/3 nodes agree (quorum=2)")
	}
	if !s.IsOdown() {
		t.Fatal("IsOdown should return true")
	}
}

func TestSentinel_EvaluateConsensus_NoOdown(t *testing.T) {
	s := NewSentinel("node1", "cluster1", nil,
		nil, nil,
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	observations := []Observation{
		{NodeID: "node1", TargetNode: "active-node", SDOWN: true, At: time.Now()},
		{NodeID: "node2", TargetNode: "active-node", SDOWN: false, At: time.Now()},
		{NodeID: "node3", TargetNode: "active-node", SDOWN: false, At: time.Now()},
	}

	odown := s.EvaluateConsensus(observations)
	if odown {
		t.Fatal("should not be ODOWN when only 1/3 nodes agree (quorum=2)")
	}
}

func TestSentinel_EvaluateConsensus_WrongTarget(t *testing.T) {
	s := NewSentinel("node1", "cluster1", nil,
		nil, nil,
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	// All SDOWN but targeting a different node — should not count.
	observations := []Observation{
		{NodeID: "node1", TargetNode: "other-node", SDOWN: true, At: time.Now()},
		{NodeID: "node2", TargetNode: "other-node", SDOWN: true, At: time.Now()},
	}

	odown := s.EvaluateConsensus(observations)
	if odown {
		t.Fatal("should not be ODOWN when observations target a different node")
	}
}

func TestSentinel_ResetFailState(t *testing.T) {
	check := &mockCheck{name: "svc", passed: false}

	s := NewSentinel("node1", "cluster1", nil,
		[]healthcheck.HealthCheck{check}, nil,
		2, 2, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	s.RunChecks()
	s.RunChecks()
	if !s.IsSdown() {
		t.Fatal("should be SDOWN")
	}

	s.ResetFailState()
	if s.IsSdown() {
		t.Fatal("SDOWN should be cleared after reset")
	}
	if s.IsOdown() {
		t.Fatal("ODOWN should be cleared after reset")
	}
}

func TestSentinel_LastObservation(t *testing.T) {
	check := &mockCheck{name: "svc", passed: true}

	s := NewSentinel("node1", "cluster1", nil,
		[]healthcheck.HealthCheck{check}, nil,
		2, 3, 30*time.Second, testLogger())
	s.SetActiveNodeID("active-node")

	s.RunChecks()
	obs := s.LastObservation()

	if obs.NodeID != "node1" {
		t.Errorf("NodeID = %q, want %q", obs.NodeID, "node1")
	}
	if obs.TargetNode != "active-node" {
		t.Errorf("TargetNode = %q, want %q", obs.TargetNode, "active-node")
	}
	if obs.SDOWN {
		t.Error("should not be SDOWN")
	}
	if len(obs.Results) != 1 {
		t.Errorf("len(Results) = %d, want 1", len(obs.Results))
	}
}
