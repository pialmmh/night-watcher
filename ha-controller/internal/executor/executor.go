package executor

import (
	"context"
	"fmt"
	"time"
)

// ExecResult holds the output from a command execution.
type ExecResult struct {
	Stdout   string
	Stderr   string
	ExitCode int
	Err      error
	Duration time.Duration
}

// Success returns true if the command exited with code 0 and no error.
func (r ExecResult) Success() bool {
	return r.ExitCode == 0 && r.Err == nil
}

func (r ExecResult) String() string {
	if r.Success() {
		return fmt.Sprintf("exit=0 dur=%s", r.Duration.Truncate(time.Millisecond))
	}
	return fmt.Sprintf("exit=%d err=%v dur=%s", r.ExitCode, r.Err, r.Duration.Truncate(time.Millisecond))
}

// Executor runs commands and reports reachability.
type Executor interface {
	// Run executes a shell command with the given timeout.
	Run(ctx context.Context, cmd string, timeout time.Duration) ExecResult

	// Reachable returns true if the execution target is reachable.
	Reachable() bool

	// String returns a human-readable description of the executor.
	String() string
}
