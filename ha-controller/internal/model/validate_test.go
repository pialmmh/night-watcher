package model

import (
	"strings"
	"testing"
	"time"
)

func TestTenantValidate(t *testing.T) {
	tests := []struct {
		name    string
		tenant  Tenant
		wantErr string
	}{
		{"valid", Tenant{ID: "t1", Name: "T1", Consul: ConsulConfig{Address: "127.0.0.1:8500"}}, ""},
		{"missing id", Tenant{Name: "T1", Consul: ConsulConfig{Address: "x"}}, "tenant.id"},
		{"missing name", Tenant{ID: "t1", Consul: ConsulConfig{Address: "x"}}, "tenant.name"},
		{"missing consul", Tenant{ID: "t1", Name: "T1"}, "consul.address"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.tenant.Validate()
			checkErr(t, err, tt.wantErr)
		})
	}
}

func TestServerValidate(t *testing.T) {
	tests := []struct {
		name    string
		server  Server
		wantErr string
	}{
		{"valid", Server{ID: "s1", Address: "10.0.0.1"}, ""},
		{"missing id", Server{Address: "10.0.0.1"}, "server.id"},
		{"missing addr", Server{ID: "s1"}, "address is required"},
		{"negative port", Server{ID: "s1", Address: "10.0.0.1", SSH: &SSHConfig{Port: -1}}, "ssh port"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.server.Validate()
			checkErr(t, err, tt.wantErr)
		})
	}
}

func TestNodeValidate(t *testing.T) {
	tests := []struct {
		name    string
		node    Node
		wantErr string
	}{
		{"valid", Node{ID: "n1", ServerID: "s1", Address: "10.0.0.1", Priority: 1}, ""},
		{"missing id", Node{ServerID: "s1", Address: "10.0.0.1", Priority: 1}, "node.id"},
		{"missing server", Node{ID: "n1", Address: "10.0.0.1", Priority: 1}, "server_id"},
		{"missing addr", Node{ID: "n1", ServerID: "s1", Priority: 1}, "address is required"},
		{"zero priority", Node{ID: "n1", ServerID: "s1", Address: "10.0.0.1", Priority: 0}, "priority must be >= 1"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.node.Validate()
			checkErr(t, err, tt.wantErr)
		})
	}
}

func TestVIPValidate(t *testing.T) {
	tests := []struct {
		name    string
		vip     VIPConfig
		wantErr string
	}{
		{"valid", VIPConfig{IP: "10.0.0.100", CIDR: 24, Interface: "eth0"}, ""},
		{"missing ip", VIPConfig{CIDR: 24, Interface: "eth0"}, "vip.ip"},
		{"cidr 0", VIPConfig{IP: "10.0.0.100", CIDR: 0, Interface: "eth0"}, "cidr must be 1-32"},
		{"cidr 33", VIPConfig{IP: "10.0.0.100", CIDR: 33, Interface: "eth0"}, "cidr must be 1-32"},
		{"missing iface", VIPConfig{IP: "10.0.0.100", CIDR: 24}, "vip.interface"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.vip.Validate()
			checkErr(t, err, tt.wantErr)
		})
	}
}

func TestHealthCheckValidate(t *testing.T) {
	valid := HealthCheckConfig{
		Name:     "hc1",
		Target:   "http://x",
		Weight:   50,
		Interval: Duration{5 * time.Second},
		Timeout:  Duration{3 * time.Second},
	}
	if err := valid.Validate(); err != nil {
		t.Fatalf("valid check: %v", err)
	}

	tests := []struct {
		name    string
		modify  func(*HealthCheckConfig)
		wantErr string
	}{
		{"missing name", func(h *HealthCheckConfig) { h.Name = "" }, "healthcheck.name"},
		{"missing target", func(h *HealthCheckConfig) { h.Target = "" }, "target is required"},
		{"weight -1", func(h *HealthCheckConfig) { h.Weight = -1 }, "weight must be 0-100"},
		{"weight 101", func(h *HealthCheckConfig) { h.Weight = 101 }, "weight must be 0-100"},
		{"zero interval", func(h *HealthCheckConfig) { h.Interval = Duration{0} }, "interval must be > 0"},
		{"zero timeout", func(h *HealthCheckConfig) { h.Timeout = Duration{0} }, "timeout must be > 0"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			hc := valid
			tt.modify(&hc)
			checkErr(t, hc.Validate(), tt.wantErr)
		})
	}
}

func TestServiceClusterValidate(t *testing.T) {
	validCluster := func() ServiceCluster {
		return ServiceCluster{
			ID:          "c1",
			TenantID:    "t1",
			ServiceType: "sigtran",
			MaxActive:   1,
			Nodes: []Node{
				{ID: "n1", ServerID: "s1", Address: "10.0.0.1", Priority: 1},
			},
			HealthChecks: []HealthCheckConfig{
				{Name: "hc1", Target: "http://x", Weight: 50, Interval: Duration{5 * time.Second}, Timeout: Duration{3 * time.Second}},
			},
		}
	}

	vc := validCluster()
	if err := vc.Validate(); err != nil {
		t.Fatalf("valid cluster: %v", err)
	}

	tests := []struct {
		name    string
		modify  func(*ServiceCluster)
		wantErr string
	}{
		{"missing id", func(c *ServiceCluster) { c.ID = "" }, "cluster.id"},
		{"missing tenant", func(c *ServiceCluster) { c.TenantID = "" }, "tenant_id"},
		{"missing svc type", func(c *ServiceCluster) { c.ServiceType = "" }, "service_type"},
		{"max_active 0", func(c *ServiceCluster) { c.MaxActive = 0 }, "max_active must be >= 1"},
		{"no nodes", func(c *ServiceCluster) { c.Nodes = nil }, "at least 1 node"},
		{"bad vip", func(c *ServiceCluster) { c.VIP = &VIPConfig{} }, "vip.ip"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := validCluster()
			tt.modify(&c)
			checkErr(t, c.Validate(), tt.wantErr)
		})
	}
}

func TestValidateRefs(t *testing.T) {
	servers := []Server{{ID: "s1", Address: "10.0.0.1"}}
	clusters := []ServiceCluster{
		{
			ID: "c1",
			Nodes: []Node{
				{ID: "n1", ServerID: "s1", Address: "10.0.0.1", Priority: 1},
				{ID: "n2", ServerID: "s_missing", Address: "10.0.0.2", Priority: 2},
			},
		},
	}

	err := ValidateRefs(servers, clusters)
	if err == nil {
		t.Fatal("expected error for missing server ref")
	}
	if !strings.Contains(err.Error(), "s_missing") {
		t.Errorf("error should mention s_missing: %v", err)
	}

	// Fix the ref.
	clusters[0].Nodes[1].ServerID = "s1"
	if err := ValidateRefs(servers, clusters); err != nil {
		t.Fatalf("expected no error: %v", err)
	}
}

func checkErr(t *testing.T, err error, wantSubstr string) {
	t.Helper()
	if wantSubstr == "" {
		if err != nil {
			t.Errorf("unexpected error: %v", err)
		}
		return
	}
	if err == nil {
		t.Errorf("expected error containing %q, got nil", wantSubstr)
		return
	}
	if !strings.Contains(err.Error(), wantSubstr) {
		t.Errorf("error %q should contain %q", err.Error(), wantSubstr)
	}
}
