package resource

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/telcobright/ha-controller/internal/executor"
)

// ActionResource is a generic resource that runs configurable shell commands
// for activate, deactivate, and check operations via an Executor (typically SSH to host).
type ActionResource struct {
	id            string
	activateCmd   string
	deactivateCmd string
	checkCmd      string
	exec          executor.Executor
	state         ResourceState
	cmdTimeout    time.Duration
	logger        *slog.Logger
}

type ActionOption func(*ActionResource)

func WithActionTimeout(d time.Duration) ActionOption {
	return func(a *ActionResource) { a.cmdTimeout = d }
}

// NewActionResource creates a generic action resource.
// activateCmd is required. deactivateCmd and checkCmd are optional (empty = no-op).
func NewActionResource(id, activateCmd, deactivateCmd, checkCmd string, exec executor.Executor, logger *slog.Logger, opts ...ActionOption) *ActionResource {
	a := &ActionResource{
		id:            id,
		activateCmd:   activateCmd,
		deactivateCmd: deactivateCmd,
		checkCmd:      checkCmd,
		exec:          exec,
		state:         StateUnknown,
		logger:        logger.With("resource", id, "type", "action"),
		cmdTimeout:    30 * time.Second,
	}
	for _, o := range opts {
		o(a)
	}
	return a
}

func (a *ActionResource) ID() string            { return a.id }
func (a *ActionResource) Type() string          { return "action" }
func (a *ActionResource) Status() ResourceState { return a.state }

// Check runs the checkCmd if configured. Returns healthy if exit == 0 or if no checkCmd.
func (a *ActionResource) Check() HealthResult {
	if a.checkCmd == "" {
		return HealthResult{Status: HealthHealthy, Reason: "no check command configured"}
	}

	ctx := context.Background()
	result := a.exec.Run(ctx, a.checkCmd, a.cmdTimeout)

	if result.Success() {
		return HealthResult{Status: HealthHealthy, Reason: "check passed"}
	}

	return HealthResult{
		Status: HealthUnhealthy,
		Reason: fmt.Sprintf("check failed (exit %d): %s", result.ExitCode, result.Stderr),
	}
}

// Activate runs the activateCmd via the executor.
func (a *ActionResource) Activate() error {
	a.logger.Info("activating action", "cmd", a.activateCmd)
	ctx := context.Background()

	result := a.exec.Run(ctx, a.activateCmd, a.cmdTimeout)
	if !result.Success() {
		return fmt.Errorf("activate command failed (exit %d): %s", result.ExitCode, result.Stderr)
	}

	a.state = StateActive
	a.logger.Info("action activated", "duration", result.Duration)
	return nil
}

// Standby calls Deactivate.
func (a *ActionResource) Standby() error {
	return a.Deactivate()
}

// Deactivate runs the deactivateCmd if configured. No-op if empty.
func (a *ActionResource) Deactivate() error {
	if a.deactivateCmd == "" {
		a.state = StateStopped
		return nil
	}

	a.logger.Info("deactivating action", "cmd", a.deactivateCmd)
	ctx := context.Background()

	result := a.exec.Run(ctx, a.deactivateCmd, a.cmdTimeout)
	if !result.Success() {
		return fmt.Errorf("deactivate command failed (exit %d): %s", result.ExitCode, result.Stderr)
	}

	a.state = StateStopped
	a.logger.Info("action deactivated", "duration", result.Duration)
	return nil
}

// SetExecutor replaces the executor. Used when building resource groups dynamically
// for failover to different nodes.
func (a *ActionResource) SetExecutor(exec executor.Executor) {
	a.exec = exec
}
