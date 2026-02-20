package executor

import (
	"context"
	"testing"
	"time"
)

func TestLocalExecutorSuccess(t *testing.T) {
	e := NewLocalExecutor()
	result := e.Run(context.Background(), "echo hello", 5*time.Second)

	if !result.Success() {
		t.Fatalf("expected success, got: %v", result)
	}
	if result.Stdout != "hello\n" {
		t.Errorf("stdout = %q, want %q", result.Stdout, "hello\n")
	}
	if result.ExitCode != 0 {
		t.Errorf("exit code = %d, want 0", result.ExitCode)
	}
}

func TestLocalExecutorFailure(t *testing.T) {
	e := NewLocalExecutor()
	result := e.Run(context.Background(), "exit 42", 5*time.Second)

	if result.Success() {
		t.Fatal("expected failure")
	}
	if result.ExitCode != 42 {
		t.Errorf("exit code = %d, want 42", result.ExitCode)
	}
}

func TestLocalExecutorTimeout(t *testing.T) {
	e := NewLocalExecutor()
	result := e.Run(context.Background(), "sleep 10", 500*time.Millisecond)

	if result.Success() {
		t.Fatal("expected failure due to timeout")
	}
}

func TestLocalExecutorStderr(t *testing.T) {
	e := NewLocalExecutor()
	result := e.Run(context.Background(), "echo error >&2", 5*time.Second)

	if result.Stderr != "error\n" {
		t.Errorf("stderr = %q, want %q", result.Stderr, "error\n")
	}
}

func TestLocalExecutorReachable(t *testing.T) {
	e := NewLocalExecutor()
	if !e.Reachable() {
		t.Error("local executor should always be reachable")
	}
}

func TestExecResultString(t *testing.T) {
	r := ExecResult{ExitCode: 0, Duration: 100 * time.Millisecond}
	s := r.String()
	if s != "exit=0 dur=100ms" {
		t.Errorf("String() = %q, want %q", s, "exit=0 dur=100ms")
	}
}
