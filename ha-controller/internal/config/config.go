package config

import (
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config is the top-level HA controller configuration.
type Config struct {
	Cluster ClusterConfig `yaml:"cluster"`
	Consul  ConsulConfig  `yaml:"consul"`
	Nodes   []NodeConfig  `yaml:"nodes"`
	Groups  []GroupConfig `yaml:"groups"`
}

// ClusterConfig holds cluster-wide settings.
type ClusterConfig struct {
	Name             string   `yaml:"name"`
	Tenant           string   `yaml:"tenant"`
	Quorum           int      `yaml:"quorum"`
	FenceTimeout     Duration `yaml:"fence_timeout"`
	FailThreshold    int      `yaml:"fail_threshold"`    // consecutive failures before SDOWN (default 3)
	CheckInterval    Duration `yaml:"check_interval"`    // sentinel check interval (default 5s)
	AutoFailback     bool     `yaml:"auto_failback"`     // automatic failback (default false)
	MaxFailovers     int      `yaml:"max_failovers"`     // max failovers in window (default 3)
	FailoverWindow   Duration `yaml:"failover_window"`   // window for max_failovers (default 1h)
	ObservationStale Duration `yaml:"observation_stale"` // stale observation threshold (default 30s)
}

// ConsulConfig holds Consul connection settings.
type ConsulConfig struct {
	Address    string   `yaml:"address"`
	Datacenter string   `yaml:"datacenter"`
	Token      string   `yaml:"token"`
	SessionTTL Duration `yaml:"session_ttl"`
	LockDelay  Duration `yaml:"lock_delay"`
}

// NodeConfig describes a cluster node.
type NodeConfig struct {
	ID       string     `yaml:"id"`
	Address  string     `yaml:"address"`
	SSH      *SSHConfig `yaml:"ssh,omitempty"`
	FenceCmd string     `yaml:"fence_cmd,omitempty"`
	Priority int        `yaml:"priority,omitempty"` // lower = higher priority, default: array index + 1
}

// SSHConfig holds SSH connection details for remote nodes.
type SSHConfig struct {
	User    string `yaml:"user"`
	KeyPath string `yaml:"key_path"`
	Port    int    `yaml:"port"`
}

// GroupConfig describes a resource group.
type GroupConfig struct {
	ID        string           `yaml:"id"`
	Resources []ResourceConfig `yaml:"resources"`
	Checks    []CheckConfig    `yaml:"checks,omitempty"`
}

// ResourceConfig describes a single resource in a group.
type ResourceConfig struct {
	ID    string            `yaml:"id"`
	Type  string            `yaml:"type"`
	Attrs map[string]string `yaml:"attrs"`
}

// CheckConfig describes a health check.
type CheckConfig struct {
	Name     string   `yaml:"name"`
	Type     string   `yaml:"type"` // ping, tcp, http, script
	Target   string   `yaml:"target"`
	Expect   string   `yaml:"expect,omitempty"`
	Interval Duration `yaml:"interval"`
	Timeout  Duration `yaml:"timeout"`
	Scope    string   `yaml:"scope,omitempty"` // "cluster" (default) or "self"
}

// Duration wraps time.Duration for YAML unmarshalling from strings like "10s".
type Duration struct {
	time.Duration
}

func (d *Duration) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return err
	}
	dur, err := time.ParseDuration(s)
	if err != nil {
		return fmt.Errorf("invalid duration %q: %w", s, err)
	}
	d.Duration = dur
	return nil
}

func (d Duration) MarshalYAML() (interface{}, error) {
	return d.Duration.String(), nil
}

// Load reads and parses a YAML config file.
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config %s: %w", path, err)
	}

	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parse config %s: %w", path, err)
	}

	if err := cfg.Validate(); err != nil {
		return nil, fmt.Errorf("validate config %s: %w", path, err)
	}

	return &cfg, nil
}

// Validate checks the config for required fields and consistency.
func (c *Config) Validate() error {
	if c.Cluster.Name == "" {
		return fmt.Errorf("cluster.name is required")
	}
	if c.Cluster.Tenant == "" {
		return fmt.Errorf("cluster.tenant is required")
	}
	if c.Consul.Address == "" {
		return fmt.Errorf("consul.address is required")
	}
	if len(c.Nodes) < 2 {
		return fmt.Errorf("at least 2 nodes required, got %d", len(c.Nodes))
	}

	nodeIDs := make(map[string]bool)
	for i := range c.Nodes {
		n := &c.Nodes[i]
		if n.ID == "" {
			return fmt.Errorf("node.id is required")
		}
		if n.Address == "" {
			return fmt.Errorf("node %s: address is required", n.ID)
		}
		if nodeIDs[n.ID] {
			return fmt.Errorf("duplicate node id %q", n.ID)
		}
		nodeIDs[n.ID] = true
		// Default priority: array index + 1
		if n.Priority == 0 {
			n.Priority = i + 1
		}
	}

	if len(c.Groups) == 0 {
		return fmt.Errorf("at least 1 resource group required")
	}

	validResourceTypes := map[string]bool{"vip": true, "noop": true, "action": true}

	for _, g := range c.Groups {
		if g.ID == "" {
			return fmt.Errorf("group.id is required")
		}
		if len(g.Resources) == 0 {
			return fmt.Errorf("group %s: at least 1 resource required", g.ID)
		}
		for _, r := range g.Resources {
			if r.ID == "" {
				return fmt.Errorf("group %s: resource.id is required", g.ID)
			}
			if r.Type == "" {
				return fmt.Errorf("group %s: resource %s: type is required", g.ID, r.ID)
			}
			if !validResourceTypes[r.Type] {
				return fmt.Errorf("group %s: resource %s: invalid type %q (want vip, noop, action)", g.ID, r.ID, r.Type)
			}
			if r.Type == "action" {
				if r.Attrs["activate"] == "" {
					return fmt.Errorf("group %s: resource %s: action type requires 'activate' attr", g.ID, r.ID)
				}
			}
		}
		for _, ch := range g.Checks {
			if ch.Name == "" {
				return fmt.Errorf("group %s: check.name is required", g.ID)
			}
			if ch.Type == "" {
				return fmt.Errorf("group %s: check %s: type is required", g.ID, ch.Name)
			}
			validCheckTypes := map[string]bool{"ping": true, "tcp": true, "http": true, "script": true}
			if !validCheckTypes[ch.Type] {
				return fmt.Errorf("group %s: check %s: invalid type %q (want ping, tcp, http, script)", g.ID, ch.Name, ch.Type)
			}
			if ch.Type == "script" && ch.Expect == "" {
				return fmt.Errorf("group %s: check %s: script type requires expect", g.ID, ch.Name)
			}
			if ch.Scope != "" && ch.Scope != "cluster" && ch.Scope != "self" {
				return fmt.Errorf("group %s: check %s: invalid scope %q (want cluster, self)", g.ID, ch.Name, ch.Scope)
			}
		}
	}

	// Apply defaults.
	c.applyDefaults()

	return nil
}

// applyDefaults sets default values for unset fields.
func (c *Config) applyDefaults() {
	if c.Cluster.FailThreshold == 0 {
		c.Cluster.FailThreshold = 3
	}
	if c.Cluster.CheckInterval.Duration == 0 {
		c.Cluster.CheckInterval.Duration = 5 * time.Second
	}
	if c.Cluster.MaxFailovers == 0 {
		c.Cluster.MaxFailovers = 3
	}
	if c.Cluster.FailoverWindow.Duration == 0 {
		c.Cluster.FailoverWindow.Duration = 1 * time.Hour
	}
	if c.Cluster.ObservationStale.Duration == 0 {
		c.Cluster.ObservationStale.Duration = 30 * time.Second
	}
	if c.Cluster.Quorum == 0 {
		c.Cluster.Quorum = len(c.Nodes)/2 + 1
	}
}

// FindNode returns the NodeConfig for the given node ID, or an error if not found.
func (c *Config) FindNode(id string) (*NodeConfig, error) {
	for i := range c.Nodes {
		if c.Nodes[i].ID == id {
			return &c.Nodes[i], nil
		}
	}
	return nil, fmt.Errorf("node %q not found in config", id)
}
