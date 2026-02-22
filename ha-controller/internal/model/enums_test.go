package model

import (
	"encoding/json"
	"testing"

	"gopkg.in/yaml.v3"
)

func TestNodeRoleStringAndRoundTrip(t *testing.T) {
	tests := []struct {
		role NodeRole
		str  string
	}{
		{NodeRoleUnknown, "unknown"},
		{NodeRoleActive, "active"},
		{NodeRoleStandby, "standby"},
		{NodeRoleSpare, "spare"},
		{NodeRoleFenced, "fenced"},
	}
	for _, tt := range tests {
		if got := tt.role.String(); got != tt.str {
			t.Errorf("NodeRole(%d).String() = %q, want %q", tt.role, got, tt.str)
		}
		// JSON round-trip
		b, _ := json.Marshal(tt.role)
		var got NodeRole
		if err := json.Unmarshal(b, &got); err != nil {
			t.Fatalf("JSON unmarshal NodeRole %q: %v", tt.str, err)
		}
		if got != tt.role {
			t.Errorf("JSON round-trip: got %v, want %v", got, tt.role)
		}
		// YAML round-trip
		yb, _ := yaml.Marshal(tt.role)
		var ygot NodeRole
		if err := yaml.Unmarshal(yb, &ygot); err != nil {
			t.Fatalf("YAML unmarshal NodeRole %q: %v", tt.str, err)
		}
		if ygot != tt.role {
			t.Errorf("YAML round-trip: got %v, want %v", ygot, tt.role)
		}
	}
}

func TestNodeRoleInvalid(t *testing.T) {
	var r NodeRole
	if err := json.Unmarshal([]byte(`"bogus"`), &r); err == nil {
		t.Fatal("expected error for invalid NodeRole")
	}
	if err := r.UnmarshalYAML(&yaml.Node{Kind: yaml.ScalarNode, Value: "bogus"}); err == nil {
		t.Fatal("expected error for invalid NodeRole YAML")
	}
}

func TestClusterStateRoundTrip(t *testing.T) {
	tests := []struct {
		state ClusterState
		str   string
	}{
		{ClusterStateUnknown, "unknown"},
		{ClusterStateHealthy, "healthy"},
		{ClusterStateDegraded, "degraded"},
		{ClusterStateCritical, "critical"},
		{ClusterStateDown, "down"},
	}
	for _, tt := range tests {
		if got := tt.state.String(); got != tt.str {
			t.Errorf("ClusterState(%d).String() = %q, want %q", tt.state, got, tt.str)
		}
		b, _ := json.Marshal(tt.state)
		var got ClusterState
		if err := json.Unmarshal(b, &got); err != nil {
			t.Fatalf("JSON unmarshal ClusterState %q: %v", tt.str, err)
		}
		if got != tt.state {
			t.Errorf("JSON round-trip: got %v, want %v", got, tt.state)
		}
	}
}

func TestClusterStateInvalid(t *testing.T) {
	var s ClusterState
	if err := json.Unmarshal([]byte(`"bogus"`), &s); err == nil {
		t.Fatal("expected error")
	}
}

func TestFailoverStrategyRoundTrip(t *testing.T) {
	tests := []struct {
		s   FailoverStrategy
		str string
	}{
		{StrategyActiveStandby, "active-standby"},
		{StrategyActiveActive, "active-active"},
	}
	for _, tt := range tests {
		if got := tt.s.String(); got != tt.str {
			t.Errorf("got %q, want %q", got, tt.str)
		}
		b, _ := json.Marshal(tt.s)
		var got FailoverStrategy
		if err := json.Unmarshal(b, &got); err != nil {
			t.Fatalf("JSON unmarshal: %v", err)
		}
		if got != tt.s {
			t.Errorf("JSON round-trip: got %v, want %v", got, tt.s)
		}
	}
}

func TestFailoverStrategyInvalid(t *testing.T) {
	var s FailoverStrategy
	if err := json.Unmarshal([]byte(`"bogus"`), &s); err == nil {
		t.Fatal("expected error")
	}
}

func TestContainerTypeRoundTrip(t *testing.T) {
	tests := []struct {
		ct  ContainerType
		str string
	}{
		{ContainerProcess, "process"},
		{ContainerLXC, "lxc"},
		{ContainerDocker, "docker"},
		{ContainerVM, "vm"},
	}
	for _, tt := range tests {
		if got := tt.ct.String(); got != tt.str {
			t.Errorf("got %q, want %q", got, tt.str)
		}
		b, _ := json.Marshal(tt.ct)
		var got ContainerType
		if err := json.Unmarshal(b, &got); err != nil {
			t.Fatalf("JSON unmarshal: %v", err)
		}
		if got != tt.ct {
			t.Errorf("round-trip: got %v, want %v", got, tt.ct)
		}
	}
}

func TestContainerTypeInvalid(t *testing.T) {
	var c ContainerType
	if err := json.Unmarshal([]byte(`"bogus"`), &c); err == nil {
		t.Fatal("expected error")
	}
}

func TestCheckTypeRoundTrip(t *testing.T) {
	tests := []struct {
		ct  CheckType
		str string
	}{
		{CheckPing, "ping"},
		{CheckTCP, "tcp"},
		{CheckHTTP, "http"},
		{CheckScript, "script"},
	}
	for _, tt := range tests {
		if got := tt.ct.String(); got != tt.str {
			t.Errorf("got %q, want %q", got, tt.str)
		}
		b, _ := json.Marshal(tt.ct)
		var got CheckType
		if err := json.Unmarshal(b, &got); err != nil {
			t.Fatalf("JSON unmarshal: %v", err)
		}
		if got != tt.ct {
			t.Errorf("round-trip: got %v, want %v", got, tt.ct)
		}
	}
}

func TestCheckTypeInvalid(t *testing.T) {
	var c CheckType
	if err := json.Unmarshal([]byte(`"bogus"`), &c); err == nil {
		t.Fatal("expected error")
	}
}

func TestEventTypeRoundTrip(t *testing.T) {
	tests := []struct {
		et  EventType
		str string
	}{
		{EventFailover, "failover"},
		{EventFencing, "fencing"},
		{EventPromotion, "promotion"},
		{EventDemotion, "demotion"},
		{EventHealthFail, "health_fail"},
		{EventHealthRecover, "health_recover"},
	}
	for _, tt := range tests {
		if got := tt.et.String(); got != tt.str {
			t.Errorf("got %q, want %q", got, tt.str)
		}
		b, _ := json.Marshal(tt.et)
		var got EventType
		if err := json.Unmarshal(b, &got); err != nil {
			t.Fatalf("JSON unmarshal: %v", err)
		}
		if got != tt.et {
			t.Errorf("round-trip: got %v, want %v", got, tt.et)
		}
	}
}

func TestEventTypeInvalid(t *testing.T) {
	var e EventType
	if err := json.Unmarshal([]byte(`"bogus"`), &e); err == nil {
		t.Fatal("expected error")
	}
}
