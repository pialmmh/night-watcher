package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadValidConfig(t *testing.T) {
	yaml := `
cluster:
  name: test-ha
  tenant: testco
  quorum: 2
  fence_timeout: 30s

consul:
  address: "127.0.0.1:8500"
  session_ttl: 15s
  lock_delay: 5s

nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2

groups:
  - id: test-group
    resources:
      - id: vip1
        type: vip
        attrs:
          ip: "10.0.0.100"
          cidr: "24"
          interface: eth0
    checks:
      - name: ping-check
        type: ping
        target: "10.0.0.1"
        interval: 5s
        timeout: 3s
`
	path := writeTempConfig(t, yaml)
	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error: %v", err)
	}

	if cfg.Cluster.Name != "test-ha" {
		t.Errorf("cluster.name = %q, want %q", cfg.Cluster.Name, "test-ha")
	}
	if cfg.Cluster.Tenant != "testco" {
		t.Errorf("cluster.tenant = %q, want %q", cfg.Cluster.Tenant, "testco")
	}
	if len(cfg.Nodes) != 2 {
		t.Errorf("len(nodes) = %d, want 2", len(cfg.Nodes))
	}
	if len(cfg.Groups) != 1 {
		t.Errorf("len(groups) = %d, want 1", len(cfg.Groups))
	}
	if cfg.Groups[0].Resources[0].Attrs["ip"] != "10.0.0.100" {
		t.Errorf("vip ip = %q, want %q", cfg.Groups[0].Resources[0].Attrs["ip"], "10.0.0.100")
	}
}

func TestLoadMissingClusterName(t *testing.T) {
	yaml := `
cluster:
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
`
	path := writeTempConfig(t, yaml)
	_, err := Load(path)
	if err == nil {
		t.Fatal("expected error for missing cluster.name")
	}
}

func TestLoadTooFewNodes(t *testing.T) {
	yaml := `
cluster:
  name: test
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
`
	path := writeTempConfig(t, yaml)
	_, err := Load(path)
	if err == nil {
		t.Fatal("expected error for too few nodes")
	}
}

func TestLoadScriptCheckRequiresExpect(t *testing.T) {
	yaml := `
cluster:
  name: test
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
    checks:
      - name: bad-script
        type: script
        target: "echo hello"
        interval: 5s
        timeout: 3s
`
	path := writeTempConfig(t, yaml)
	_, err := Load(path)
	if err == nil {
		t.Fatal("expected error for script check without expect")
	}
}

func TestLoadDuplicateNodeID(t *testing.T) {
	yaml := `
cluster:
  name: test
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node1
    address: 10.0.0.2
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
`
	path := writeTempConfig(t, yaml)
	_, err := Load(path)
	if err == nil {
		t.Fatal("expected error for duplicate node ID")
	}
}

func TestFindNode(t *testing.T) {
	cfg := &Config{
		Cluster: ClusterConfig{Name: "test", Tenant: "t"},
		Consul:  ConsulConfig{Address: "127.0.0.1:8500"},
		Nodes: []NodeConfig{
			{ID: "a", Address: "10.0.0.1"},
			{ID: "b", Address: "10.0.0.2"},
		},
		Groups: []GroupConfig{
			{ID: "g1", Resources: []ResourceConfig{{ID: "r1", Type: "vip"}}},
		},
	}

	n, err := cfg.FindNode("b")
	if err != nil {
		t.Fatalf("FindNode(b): %v", err)
	}
	if n.Address != "10.0.0.2" {
		t.Errorf("address = %q, want %q", n.Address, "10.0.0.2")
	}

	_, err = cfg.FindNode("nonexistent")
	if err == nil {
		t.Fatal("expected error for nonexistent node")
	}
}

func TestLoadFileNotFound(t *testing.T) {
	_, err := Load("/nonexistent/path/config.yml")
	if err == nil {
		t.Fatal("expected error for missing file")
	}
}

func TestLoadActionResource(t *testing.T) {
	yaml := `
cluster:
  name: test-ha
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: test-group
    resources:
      - id: manage-svc
        type: action
        attrs:
          activate: "start-svc"
          deactivate: "stop-svc"
          check: "check-svc"
          timeout: "30s"
`
	path := writeTempConfig(t, yaml)
	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error: %v", err)
	}

	if cfg.Groups[0].Resources[0].Type != "action" {
		t.Errorf("type = %q, want %q", cfg.Groups[0].Resources[0].Type, "action")
	}
	if cfg.Groups[0].Resources[0].Attrs["activate"] != "start-svc" {
		t.Errorf("activate = %q, want %q", cfg.Groups[0].Resources[0].Attrs["activate"], "start-svc")
	}
}

func TestLoadActionResourceRequiresActivate(t *testing.T) {
	yaml := `
cluster:
  name: test
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: g1
    resources:
      - id: r1
        type: action
        attrs:
          deactivate: "stop"
`
	path := writeTempConfig(t, yaml)
	_, err := Load(path)
	if err == nil {
		t.Fatal("expected error for action resource without activate attr")
	}
}

func TestLoadCheckScope(t *testing.T) {
	yaml := `
cluster:
  name: test-ha
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: test-group
    resources:
      - id: vip1
        type: vip
        attrs:
          ip: "10.0.0.100"
    checks:
      - name: cluster-check
        type: http
        target: "http://10.0.0.100:8080/health"
        interval: 5s
        timeout: 3s
        scope: cluster
      - name: self-check
        type: http
        target: "http://127.0.0.1:8080/health"
        interval: 5s
        timeout: 3s
        scope: self
`
	path := writeTempConfig(t, yaml)
	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error: %v", err)
	}

	if cfg.Groups[0].Checks[0].Scope != "cluster" {
		t.Errorf("check[0].scope = %q, want %q", cfg.Groups[0].Checks[0].Scope, "cluster")
	}
	if cfg.Groups[0].Checks[1].Scope != "self" {
		t.Errorf("check[1].scope = %q, want %q", cfg.Groups[0].Checks[1].Scope, "self")
	}
}

func TestLoadInvalidCheckScope(t *testing.T) {
	yaml := `
cluster:
  name: test
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
        attrs:
          ip: "10.0.0.1"
    checks:
      - name: c1
        type: ping
        target: "10.0.0.1"
        interval: 5s
        timeout: 3s
        scope: invalid
`
	path := writeTempConfig(t, yaml)
	_, err := Load(path)
	if err == nil {
		t.Fatal("expected error for invalid check scope")
	}
}

func TestDefaultsApplied(t *testing.T) {
	yaml := `
cluster:
  name: test-ha
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
  - id: node2
    address: 10.0.0.2
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
        attrs:
          ip: "10.0.0.100"
`
	path := writeTempConfig(t, yaml)
	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error: %v", err)
	}

	if cfg.Cluster.FailThreshold != 3 {
		t.Errorf("FailThreshold = %d, want 3", cfg.Cluster.FailThreshold)
	}
	if cfg.Cluster.MaxFailovers != 3 {
		t.Errorf("MaxFailovers = %d, want 3", cfg.Cluster.MaxFailovers)
	}
	if cfg.Cluster.Quorum != 2 {
		t.Errorf("Quorum = %d, want 2 (majority of 2 nodes)", cfg.Cluster.Quorum)
	}

	// Node priority defaults.
	if cfg.Nodes[0].Priority != 1 {
		t.Errorf("node1.Priority = %d, want 1", cfg.Nodes[0].Priority)
	}
	if cfg.Nodes[1].Priority != 2 {
		t.Errorf("node2.Priority = %d, want 2", cfg.Nodes[1].Priority)
	}
}

func TestNodePriorityExplicit(t *testing.T) {
	yaml := `
cluster:
  name: test-ha
  tenant: testco
consul:
  address: "127.0.0.1:8500"
nodes:
  - id: node1
    address: 10.0.0.1
    priority: 5
  - id: node2
    address: 10.0.0.2
    priority: 1
groups:
  - id: g1
    resources:
      - id: r1
        type: vip
        attrs:
          ip: "10.0.0.100"
`
	path := writeTempConfig(t, yaml)
	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error: %v", err)
	}

	if cfg.Nodes[0].Priority != 5 {
		t.Errorf("node1.Priority = %d, want 5", cfg.Nodes[0].Priority)
	}
	if cfg.Nodes[1].Priority != 1 {
		t.Errorf("node2.Priority = %d, want 1", cfg.Nodes[1].Priority)
	}
}

func writeTempConfig(t *testing.T, content string) string {
	t.Helper()
	dir := t.TempDir()
	path := filepath.Join(dir, "test-config.yml")
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
		t.Fatalf("write temp config: %v", err)
	}
	return path
}
