package resource

import "fmt"

// ResourceState represents the current state of a resource.
type ResourceState int

const (
	StateUnknown ResourceState = iota
	StateActive
	StateStandby
	StateStopped
)

func (s ResourceState) String() string {
	switch s {
	case StateActive:
		return "ACTIVE"
	case StateStandby:
		return "STANDBY"
	case StateStopped:
		return "STOPPED"
	default:
		return "UNKNOWN"
	}
}

// HealthStatus represents the health of a resource.
type HealthStatus int

const (
	HealthUnknown HealthStatus = iota
	HealthHealthy
	HealthDegraded
	HealthUnhealthy
)

func (h HealthStatus) String() string {
	switch h {
	case HealthHealthy:
		return "HEALTHY"
	case HealthDegraded:
		return "DEGRADED"
	case HealthUnhealthy:
		return "UNHEALTHY"
	default:
		return "UNKNOWN"
	}
}

// HealthResult holds the outcome of a resource health check.
type HealthResult struct {
	Status HealthStatus
	Reason string
}

func (r HealthResult) String() string {
	if r.Reason != "" {
		return fmt.Sprintf("%s: %s", r.Status, r.Reason)
	}
	return r.Status.String()
}

// Resource represents a manageable cluster resource (VIP, service, etc.).
type Resource interface {
	// ID returns the unique resource identifier.
	ID() string

	// Type returns the resource type (e.g., "vip", "sigtran", "sip").
	Type() string

	// Status returns the current resource state.
	Status() ResourceState

	// Check performs a health check and returns the result.
	Check() HealthResult

	// Activate brings the resource into active state on this node.
	Activate() error

	// Standby puts the resource into standby (ready but not serving).
	Standby() error

	// Deactivate stops the resource completely.
	Deactivate() error
}
