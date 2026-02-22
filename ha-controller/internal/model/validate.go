package model

import (
	"fmt"
	"strings"
)

// Validate checks the Tenant for required fields.
func (t *Tenant) Validate() error {
	if t.ID == "" {
		return fmt.Errorf("tenant.id is required")
	}
	if t.Name == "" {
		return fmt.Errorf("tenant.name is required")
	}
	if t.Consul.Address == "" {
		return fmt.Errorf("tenant.consul.address is required")
	}
	return nil
}

// Validate checks the Server for required fields.
func (s *Server) Validate() error {
	if s.ID == "" {
		return fmt.Errorf("server.id is required")
	}
	if s.Address == "" {
		return fmt.Errorf("server %s: address is required", s.ID)
	}
	if s.SSH != nil && s.SSH.Port < 0 {
		return fmt.Errorf("server %s: ssh port must be >= 0", s.ID)
	}
	return nil
}

// Validate checks the Node for required fields.
func (n *Node) Validate() error {
	if n.ID == "" {
		return fmt.Errorf("node.id is required")
	}
	if n.ServerID == "" {
		return fmt.Errorf("node %s: server_id is required", n.ID)
	}
	if n.Address == "" {
		return fmt.Errorf("node %s: address is required", n.ID)
	}
	if n.Priority < 1 {
		return fmt.Errorf("node %s: priority must be >= 1, got %d", n.ID, n.Priority)
	}
	return nil
}

// Validate checks the VIPConfig for required fields.
func (v *VIPConfig) Validate() error {
	if v.IP == "" {
		return fmt.Errorf("vip.ip is required")
	}
	if v.CIDR < 1 || v.CIDR > 32 {
		return fmt.Errorf("vip.cidr must be 1-32, got %d", v.CIDR)
	}
	if v.Interface == "" {
		return fmt.Errorf("vip.interface is required")
	}
	return nil
}

// Validate checks the HealthCheckConfig for required fields and valid ranges.
func (h *HealthCheckConfig) Validate() error {
	if h.Name == "" {
		return fmt.Errorf("healthcheck.name is required")
	}
	if h.Target == "" {
		return fmt.Errorf("healthcheck %s: target is required", h.Name)
	}
	if h.Weight < 0 || h.Weight > 100 {
		return fmt.Errorf("healthcheck %s: weight must be 0-100, got %d", h.Name, h.Weight)
	}
	if h.Interval.Duration <= 0 {
		return fmt.Errorf("healthcheck %s: interval must be > 0", h.Name)
	}
	if h.Timeout.Duration <= 0 {
		return fmt.Errorf("healthcheck %s: timeout must be > 0", h.Name)
	}
	return nil
}

// Validate checks the FailoverPolicy for valid ranges.
func (p *FailoverPolicy) Validate() error {
	if p.Quorum < 0 {
		return fmt.Errorf("failover_policy.quorum must be >= 0, got %d", p.Quorum)
	}
	return nil
}

// Validate checks the ServiceCluster for required fields and sub-structs.
func (c *ServiceCluster) Validate() error {
	if c.ID == "" {
		return fmt.Errorf("cluster.id is required")
	}
	if c.TenantID == "" {
		return fmt.Errorf("cluster %s: tenant_id is required", c.ID)
	}
	if c.ServiceType == "" {
		return fmt.Errorf("cluster %s: service_type is required", c.ID)
	}
	if c.MaxActive < 1 {
		return fmt.Errorf("cluster %s: max_active must be >= 1, got %d", c.ID, c.MaxActive)
	}
	if len(c.Nodes) == 0 {
		return fmt.Errorf("cluster %s: at least 1 node required", c.ID)
	}
	if c.VIP != nil {
		if err := c.VIP.Validate(); err != nil {
			return fmt.Errorf("cluster %s: %w", c.ID, err)
		}
	}
	for i := range c.Nodes {
		if err := c.Nodes[i].Validate(); err != nil {
			return fmt.Errorf("cluster %s: %w", c.ID, err)
		}
	}
	for i := range c.HealthChecks {
		if err := c.HealthChecks[i].Validate(); err != nil {
			return fmt.Errorf("cluster %s: %w", c.ID, err)
		}
	}
	if err := c.FailoverPolicy.Validate(); err != nil {
		return fmt.Errorf("cluster %s: %w", c.ID, err)
	}
	return nil
}

// ValidateRefs checks cross-references: every node's ServerID must exist in servers.
func ValidateRefs(servers []Server, clusters []ServiceCluster) error {
	serverIDs := make(map[string]bool, len(servers))
	for _, s := range servers {
		serverIDs[s.ID] = true
	}

	var errs []string
	for _, c := range clusters {
		for _, n := range c.Nodes {
			if !serverIDs[n.ServerID] {
				errs = append(errs, fmt.Sprintf("cluster %s: node %s references unknown server %q", c.ID, n.ID, n.ServerID))
			}
		}
	}
	if len(errs) > 0 {
		return fmt.Errorf("reference errors: %s", strings.Join(errs, "; "))
	}
	return nil
}
