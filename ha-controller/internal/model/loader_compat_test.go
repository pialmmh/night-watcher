package model

import (
	"testing"
	"time"

	"github.com/telcobright/ha-controller/internal/config"
)

func TestFromLegacyConfig(t *testing.T) {
	old := &config.Config{
		Cluster: config.ClusterConfig{
			Name:         "btcl-ha",
			Tenant:       "btcl",
			Quorum:       2,
			FenceTimeout: config.Duration{Duration: 30 * time.Second},
		},
		Consul: config.ConsulConfig{
			Address:    "127.0.0.1:8500",
			Datacenter: "dc1",
			Token:      "secret",
			SessionTTL: config.Duration{Duration: 15 * time.Second},
			LockDelay:  config.Duration{Duration: 5 * time.Second},
		},
		Nodes: []config.NodeConfig{
			{
				ID:      "node1",
				Address: "10.0.0.1",
				SSH: &config.SSHConfig{
					User:    "deploy",
					KeyPath: "/root/.ssh/id_rsa",
					Port:    22,
				},
				FenceCmd: "fence_node1",
			},
			{
				ID:      "node2",
				Address: "10.0.0.2",
			},
		},
		Groups: []config.GroupConfig{
			{
				ID: "sigtran",
				Resources: []config.ResourceConfig{
					{
						ID:   "vip1",
						Type: "vip",
						Attrs: map[string]string{
							"ip":        "10.0.0.100",
							"cidr":      "24",
							"interface": "eth0",
						},
					},
				},
				Checks: []config.CheckConfig{
					{
						Name:     "http-check",
						Type:     "http",
						Target:   "http://10.0.0.1:8282/health",
						Expect:   "UP",
						Interval: config.Duration{Duration: 5 * time.Second},
						Timeout:  config.Duration{Duration: 3 * time.Second},
					},
				},
			},
		},
	}

	tc := FromLegacyConfig(old)

	// Tenant
	if tc.Tenant.ID != "btcl" {
		t.Errorf("tenant.ID = %q", tc.Tenant.ID)
	}
	if tc.Tenant.Name != "btcl-ha" {
		t.Errorf("tenant.Name = %q", tc.Tenant.Name)
	}
	if tc.Tenant.Consul.Address != "127.0.0.1:8500" {
		t.Errorf("consul.Address = %q", tc.Tenant.Consul.Address)
	}
	if tc.Tenant.Consul.Token != "secret" {
		t.Errorf("consul.Token should be preserved")
	}
	if tc.Tenant.Consul.SessionTTL.Duration != 15*time.Second {
		t.Errorf("consul.SessionTTL = %v", tc.Tenant.Consul.SessionTTL)
	}

	// Servers from old nodes
	if len(tc.Servers) != 2 {
		t.Fatalf("len(servers) = %d, want 2", len(tc.Servers))
	}
	if tc.Servers[0].ID != "node1" {
		t.Errorf("server[0].ID = %q", tc.Servers[0].ID)
	}
	if tc.Servers[0].FenceCmd != "fence_node1" {
		t.Errorf("server[0].FenceCmd = %q", tc.Servers[0].FenceCmd)
	}
	if tc.Servers[0].SSH == nil {
		t.Fatal("server[0].SSH is nil")
	}
	if tc.Servers[0].SSH.User != "deploy" {
		t.Errorf("server[0].SSH.User = %q", tc.Servers[0].SSH.User)
	}
	if tc.Servers[1].SSH != nil {
		t.Error("server[1].SSH should be nil")
	}

	// Clusters from old groups
	if len(tc.Clusters) != 1 {
		t.Fatalf("len(clusters) = %d, want 1", len(tc.Clusters))
	}
	c := tc.Clusters[0]
	if c.ID != "sigtran" {
		t.Errorf("cluster.ID = %q", c.ID)
	}
	if c.MaxActive != 1 {
		t.Errorf("cluster.MaxActive = %d", c.MaxActive)
	}
	if c.TenantID != "btcl" {
		t.Errorf("cluster.TenantID = %q", c.TenantID)
	}

	// VIP extracted from resource
	if c.VIP == nil {
		t.Fatal("cluster.VIP is nil")
	}
	if c.VIP.IP != "10.0.0.100" {
		t.Errorf("vip.IP = %q", c.VIP.IP)
	}
	if c.VIP.CIDR != 24 {
		t.Errorf("vip.CIDR = %d", c.VIP.CIDR)
	}
	if c.VIP.Interface != "eth0" {
		t.Errorf("vip.Interface = %q", c.VIP.Interface)
	}

	// Nodes created from old NodeConfig
	if len(c.Nodes) != 2 {
		t.Fatalf("len(nodes) = %d, want 2", len(c.Nodes))
	}
	if c.Nodes[0].ID != "node1" {
		t.Errorf("node[0].ID = %q", c.Nodes[0].ID)
	}
	if c.Nodes[0].ServerID != "node1" {
		t.Errorf("node[0].ServerID = %q", c.Nodes[0].ServerID)
	}
	if c.Nodes[0].Priority != 1 {
		t.Errorf("node[0].Priority = %d", c.Nodes[0].Priority)
	}

	// Checks mapped
	if len(c.HealthChecks) != 1 {
		t.Fatalf("len(checks) = %d, want 1", len(c.HealthChecks))
	}
	hc := c.HealthChecks[0]
	if hc.Name != "http-check" {
		t.Errorf("check.Name = %q", hc.Name)
	}
	if hc.Type != CheckHTTP {
		t.Errorf("check.Type = %v", hc.Type)
	}
	if hc.Weight != 50 {
		t.Errorf("check.Weight = %d", hc.Weight)
	}

	// Failover policy
	if c.FailoverPolicy.Strategy != StrategyActiveStandby {
		t.Errorf("strategy = %v", c.FailoverPolicy.Strategy)
	}
	if c.FailoverPolicy.Quorum != 2 {
		t.Errorf("quorum = %d", c.FailoverPolicy.Quorum)
	}
	if !c.FailoverPolicy.FenceEnabled {
		t.Error("fence should be enabled (timeout > 0)")
	}
}
