package model

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

const sampleConf = `
# BTCL HA Cluster Configuration
[common]
tenant_id = btcl
tenant_name = BTCL SMS Platform
inventory_tenant = btcl
consul_address = 127.0.0.1:8500
consul_datacenter = dc1
consul_session_ttl = 15s
consul_lock_delay = 5s

[cluster:btcl-sigtran]
service_type = sigtran
max_active = 1
strategy = active-standby
quorum = 1
fence_enabled = true
fence_timeout = 30s
auto_failback = false
vip_ip = 10.10.196.100
vip_cidr = 24
vip_interface = eth0

[cluster:btcl-sigtran:node:btcl-sigtran-1]
server = dell-sms-master
container_type = lxc
container_name = sigtran-btcl
address = 10.10.196.5
priority = 1
health_endpoint = http://10.10.196.5:8282/health

[cluster:btcl-sigtran:node:btcl-sigtran-2]
server = dell-sms-slave
container_type = lxc
container_name = sigtran-btcl
address = 10.10.195.5
priority = 2
health_endpoint = http://10.10.195.5:8282/health

[cluster:btcl-sigtran:check:sigtran-health]
type = http
target = http://${node.address}:8282/health
expect = UP
interval = 5s
timeout = 3s
weight = 80
critical = true

[cluster:btcl-sigtran:check:remote-ping]
type = http
target = http://${node.address}:8282/ping-remote
expect = reachable
interval = 10s
timeout = 5s
weight = 30
critical = false
`

func TestLoadFromConf(t *testing.T) {
	path := writeTempConf(t, sampleConf)

	tc, err := LoadFromConf(path, "")
	if err != nil {
		t.Fatalf("LoadFromConf: %v", err)
	}

	// Tenant
	if tc.Tenant.ID != "btcl" {
		t.Errorf("tenant.ID = %q, want %q", tc.Tenant.ID, "btcl")
	}
	if tc.Tenant.Name != "BTCL SMS Platform" {
		t.Errorf("tenant.Name = %q, want %q", tc.Tenant.Name, "BTCL SMS Platform")
	}
	if tc.Tenant.Consul.Address != "127.0.0.1:8500" {
		t.Errorf("consul.Address = %q", tc.Tenant.Consul.Address)
	}
	if tc.Tenant.Consul.Datacenter != "dc1" {
		t.Errorf("consul.Datacenter = %q", tc.Tenant.Consul.Datacenter)
	}
	if tc.Tenant.Consul.SessionTTL.Duration != 15*time.Second {
		t.Errorf("consul.SessionTTL = %v", tc.Tenant.Consul.SessionTTL)
	}
	if tc.Tenant.Consul.LockDelay.Duration != 5*time.Second {
		t.Errorf("consul.LockDelay = %v", tc.Tenant.Consul.LockDelay)
	}

	// Servers
	if len(tc.Servers) != 2 {
		t.Fatalf("len(servers) = %d, want 2", len(tc.Servers))
	}
	if tc.Servers[0].ID != "dell-sms-master" {
		t.Errorf("server[0].ID = %q", tc.Servers[0].ID)
	}

	// Clusters
	if len(tc.Clusters) != 1 {
		t.Fatalf("len(clusters) = %d, want 1", len(tc.Clusters))
	}

	c := tc.Clusters[0]
	if c.ID != "btcl-sigtran" {
		t.Errorf("cluster.ID = %q", c.ID)
	}
	if c.ServiceType != "sigtran" {
		t.Errorf("cluster.ServiceType = %q", c.ServiceType)
	}
	if c.MaxActive != 1 {
		t.Errorf("cluster.MaxActive = %d", c.MaxActive)
	}

	// VIP
	if c.VIP == nil {
		t.Fatal("cluster.VIP is nil")
	}
	if c.VIP.IP != "10.10.196.100" {
		t.Errorf("vip.IP = %q", c.VIP.IP)
	}
	if c.VIP.CIDR != 24 {
		t.Errorf("vip.CIDR = %d", c.VIP.CIDR)
	}
	if c.VIP.Interface != "eth0" {
		t.Errorf("vip.Interface = %q", c.VIP.Interface)
	}

	// Failover policy
	if c.FailoverPolicy.Strategy != StrategyActiveStandby {
		t.Errorf("strategy = %v", c.FailoverPolicy.Strategy)
	}
	if c.FailoverPolicy.Quorum != 1 {
		t.Errorf("quorum = %d", c.FailoverPolicy.Quorum)
	}
	if !c.FailoverPolicy.FenceEnabled {
		t.Error("fence_enabled should be true")
	}
	if c.FailoverPolicy.FenceTimeout.Duration != 30*time.Second {
		t.Errorf("fence_timeout = %v", c.FailoverPolicy.FenceTimeout)
	}
	if c.FailoverPolicy.AutoFailback {
		t.Error("auto_failback should be false")
	}

	// Nodes
	if len(c.Nodes) != 2 {
		t.Fatalf("len(nodes) = %d, want 2", len(c.Nodes))
	}
	n1 := c.Nodes[0]
	if n1.ID != "btcl-sigtran-1" {
		t.Errorf("node[0].ID = %q", n1.ID)
	}
	if n1.ServerID != "dell-sms-master" {
		t.Errorf("node[0].ServerID = %q", n1.ServerID)
	}
	if n1.Address != "10.10.196.5" {
		t.Errorf("node[0].Address = %q", n1.Address)
	}
	if n1.Priority != 1 {
		t.Errorf("node[0].Priority = %d", n1.Priority)
	}
	if n1.Container == nil || n1.Container.Type != ContainerLXC {
		t.Error("node[0] should have LXC container")
	}
	if n1.Container.Name != "sigtran-btcl" {
		t.Errorf("node[0].Container.Name = %q", n1.Container.Name)
	}
	if n1.HealthEndpoint != "http://10.10.196.5:8282/health" {
		t.Errorf("node[0].HealthEndpoint = %q", n1.HealthEndpoint)
	}

	// Health checks
	if len(c.HealthChecks) != 2 {
		t.Fatalf("len(health_checks) = %d, want 2", len(c.HealthChecks))
	}
	hc1 := c.HealthChecks[0]
	if hc1.Name != "sigtran-health" {
		t.Errorf("check[0].Name = %q", hc1.Name)
	}
	if hc1.Type != CheckHTTP {
		t.Errorf("check[0].Type = %v", hc1.Type)
	}
	if hc1.Weight != 80 {
		t.Errorf("check[0].Weight = %d", hc1.Weight)
	}
	if !hc1.Critical {
		t.Error("check[0] should be critical")
	}
	if hc1.Interval.Duration != 5*time.Second {
		t.Errorf("check[0].Interval = %v", hc1.Interval)
	}
}

func TestLoadFromConfMissingCommon(t *testing.T) {
	conf := `
[cluster:x]
service_type = test
`
	path := writeTempConf(t, conf)
	_, err := LoadFromConf(path, "")
	if err == nil {
		t.Fatal("expected error for missing [common]")
	}
}

func TestLoadFromConfFileNotFound(t *testing.T) {
	_, err := LoadFromConf("/nonexistent/file.conf", "")
	if err == nil {
		t.Fatal("expected error for missing file")
	}
}

func TestFindServerAndCluster(t *testing.T) {
	path := writeTempConf(t, sampleConf)
	tc, err := LoadFromConf(path, "")
	if err != nil {
		t.Fatalf("LoadFromConf: %v", err)
	}

	// FindServer
	s, err := tc.FindServer("dell-sms-master")
	if err != nil {
		t.Fatalf("FindServer: %v", err)
	}
	if s.Address != "10.10.196.5" {
		t.Errorf("server address = %q", s.Address)
	}

	_, err = tc.FindServer("nonexistent")
	if err == nil {
		t.Fatal("expected error for nonexistent server")
	}

	// FindCluster
	c, err := tc.FindCluster("btcl-sigtran")
	if err != nil {
		t.Fatalf("FindCluster: %v", err)
	}
	if c.ServiceType != "sigtran" {
		t.Errorf("cluster service_type = %q", c.ServiceType)
	}

	_, err = tc.FindCluster("nonexistent")
	if err == nil {
		t.Fatal("expected error for nonexistent cluster")
	}
}

func TestServerForNode(t *testing.T) {
	path := writeTempConf(t, sampleConf)
	tc, err := LoadFromConf(path, "")
	if err != nil {
		t.Fatalf("LoadFromConf: %v", err)
	}

	s, err := tc.ServerForNode("btcl-sigtran-1")
	if err != nil {
		t.Fatalf("ServerForNode: %v", err)
	}
	if s.ID != "dell-sms-master" {
		t.Errorf("server = %q, want dell-sms-master", s.ID)
	}

	_, err = tc.ServerForNode("nonexistent")
	if err == nil {
		t.Fatal("expected error for nonexistent node")
	}
}

func writeTempConf(t *testing.T, content string) string {
	t.Helper()
	dir := t.TempDir()
	path := filepath.Join(dir, "test.conf")
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
		t.Fatalf("write temp conf: %v", err)
	}
	return path
}
