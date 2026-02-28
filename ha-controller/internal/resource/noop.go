package resource

import "log/slog"

// NoopResource is a dummy resource for testing that always reports healthy.
type NoopResource struct {
	id     string
	state  ResourceState
	logger *slog.Logger
}

// NewNoopResource creates a no-op resource that always succeeds.
func NewNoopResource(id string, logger *slog.Logger) *NoopResource {
	return &NoopResource{
		id:     id,
		state:  StateStopped,
		logger: logger.With("resource", id, "type", "noop"),
	}
}

func (n *NoopResource) ID() string            { return n.id }
func (n *NoopResource) Type() string          { return "noop" }
func (n *NoopResource) Status() ResourceState { return n.state }

func (n *NoopResource) Check() HealthResult {
	if n.state == StateActive {
		return HealthResult{Status: HealthHealthy, Reason: "noop resource active"}
	}
	return HealthResult{Status: HealthUnknown, Reason: "not active"}
}

func (n *NoopResource) Activate() error {
	n.logger.Info("noop resource activated")
	n.state = StateActive
	return nil
}

func (n *NoopResource) Standby() error {
	n.state = StateStandby
	return nil
}

func (n *NoopResource) Deactivate() error {
	n.logger.Info("noop resource deactivated")
	n.state = StateStopped
	return nil
}
