package resource

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/telcobright/ha-controller/internal/executor"
)

// mockExecutor implements executor.Executor for testing.
type mockExecutor struct {
	results map[string]executor.ExecResult
}

func newMockExecutor() *mockExecutor {
	return &mockExecutor{results: make(map[string]executor.ExecResult)}
}

func (m *mockExecutor) SetResult(cmd string, result executor.ExecResult) {
	m.results[cmd] = result
}

func (m *mockExecutor) Run(_ context.Context, cmd string, _ time.Duration) executor.ExecResult {
	if r, ok := m.results[cmd]; ok {
		return r
	}
	return executor.ExecResult{ExitCode: -1, Err: fmt.Errorf("unexpected command: %s", cmd)}
}

func (m *mockExecutor) Reachable() bool { return true }
func (m *mockExecutor) String() string  { return "MockExecutor" }

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

func TestActionResource_Activate(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("start-service", executor.ExecResult{ExitCode: 0, Stdout: "ok"})

	a := NewActionResource("svc1", "start-service", "stop-service", "check-service", exec, testLogger())

	if err := a.Activate(); err != nil {
		t.Fatalf("Activate() error: %v", err)
	}
	if a.Status() != StateActive {
		t.Errorf("state = %v, want ACTIVE", a.Status())
	}
}

func TestActionResource_ActivateFail(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("start-service", executor.ExecResult{ExitCode: 1, Stderr: "permission denied"})

	a := NewActionResource("svc1", "start-service", "", "", exec, testLogger())

	err := a.Activate()
	if err == nil {
		t.Fatal("expected error on failed activate")
	}
}

func TestActionResource_Deactivate(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("stop-service", executor.ExecResult{ExitCode: 0})

	a := NewActionResource("svc1", "start-service", "stop-service", "", exec, testLogger())

	if err := a.Deactivate(); err != nil {
		t.Fatalf("Deactivate() error: %v", err)
	}
	if a.Status() != StateStopped {
		t.Errorf("state = %v, want STOPPED", a.Status())
	}
}

func TestActionResource_DeactivateNoCmd(t *testing.T) {
	exec := newMockExecutor()
	a := NewActionResource("svc1", "start-service", "", "", exec, testLogger())

	if err := a.Deactivate(); err != nil {
		t.Fatalf("Deactivate() error: %v", err)
	}
	if a.Status() != StateStopped {
		t.Errorf("state = %v, want STOPPED", a.Status())
	}
}

func TestActionResource_CheckHealthy(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("check-service", executor.ExecResult{ExitCode: 0, Stdout: "healthy"})

	a := NewActionResource("svc1", "start", "stop", "check-service", exec, testLogger())

	result := a.Check()
	if result.Status != HealthHealthy {
		t.Errorf("check status = %v, want HEALTHY", result.Status)
	}
}

func TestActionResource_CheckUnhealthy(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("check-service", executor.ExecResult{ExitCode: 1, Stderr: "service down"})

	a := NewActionResource("svc1", "start", "stop", "check-service", exec, testLogger())

	result := a.Check()
	if result.Status != HealthUnhealthy {
		t.Errorf("check status = %v, want UNHEALTHY", result.Status)
	}
}

func TestActionResource_CheckNoCmd(t *testing.T) {
	exec := newMockExecutor()
	a := NewActionResource("svc1", "start", "", "", exec, testLogger())

	result := a.Check()
	if result.Status != HealthHealthy {
		t.Errorf("check status = %v, want HEALTHY (no check cmd)", result.Status)
	}
}

func TestActionResource_Type(t *testing.T) {
	exec := newMockExecutor()
	a := NewActionResource("svc1", "start", "", "", exec, testLogger())

	if a.Type() != "action" {
		t.Errorf("Type() = %q, want %q", a.Type(), "action")
	}
	if a.ID() != "svc1" {
		t.Errorf("ID() = %q, want %q", a.ID(), "svc1")
	}
}

func TestActionResource_Timeout(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("start", executor.ExecResult{ExitCode: 0})

	a := NewActionResource("svc1", "start", "", "", exec, testLogger(), WithActionTimeout(60*time.Second))

	if a.cmdTimeout != 60*time.Second {
		t.Errorf("cmdTimeout = %v, want 60s", a.cmdTimeout)
	}
}

func TestActionResource_Standby(t *testing.T) {
	exec := newMockExecutor()
	exec.SetResult("stop-service", executor.ExecResult{ExitCode: 0})

	a := NewActionResource("svc1", "start-service", "stop-service", "", exec, testLogger())

	if err := a.Standby(); err != nil {
		t.Fatalf("Standby() error: %v", err)
	}
	if a.Status() != StateStopped {
		t.Errorf("state = %v, want STOPPED", a.Status())
	}
}
