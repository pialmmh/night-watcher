package resource

import "testing"

func TestResourceStateString(t *testing.T) {
	tests := []struct {
		state ResourceState
		want  string
	}{
		{StateUnknown, "UNKNOWN"},
		{StateActive, "ACTIVE"},
		{StateStandby, "STANDBY"},
		{StateStopped, "STOPPED"},
	}
	for _, tt := range tests {
		if got := tt.state.String(); got != tt.want {
			t.Errorf("ResourceState(%d).String() = %q, want %q", tt.state, got, tt.want)
		}
	}
}

func TestHealthStatusString(t *testing.T) {
	tests := []struct {
		status HealthStatus
		want   string
	}{
		{HealthUnknown, "UNKNOWN"},
		{HealthHealthy, "HEALTHY"},
		{HealthDegraded, "DEGRADED"},
		{HealthUnhealthy, "UNHEALTHY"},
	}
	for _, tt := range tests {
		if got := tt.status.String(); got != tt.want {
			t.Errorf("HealthStatus(%d).String() = %q, want %q", tt.status, got, tt.want)
		}
	}
}

func TestHealthResultString(t *testing.T) {
	r := HealthResult{Status: HealthHealthy, Reason: "all good"}
	if got := r.String(); got != "HEALTHY: all good" {
		t.Errorf("HealthResult.String() = %q, want %q", got, "HEALTHY: all good")
	}

	r2 := HealthResult{Status: HealthUnhealthy}
	if got := r2.String(); got != "UNHEALTHY" {
		t.Errorf("HealthResult.String() = %q, want %q", got, "UNHEALTHY")
	}
}
