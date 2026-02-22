package model

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// TenantConfig is the fully loaded, validated configuration for a tenant.
type TenantConfig struct {
	Tenant   Tenant           `json:"tenant"`
	Servers  []Server         `json:"servers"`
	Clusters []ServiceCluster `json:"clusters"`
}

// Validate runs all validation on the tenant config.
func (tc *TenantConfig) Validate() error {
	if err := tc.Tenant.Validate(); err != nil {
		return err
	}
	for i := range tc.Servers {
		if err := tc.Servers[i].Validate(); err != nil {
			return err
		}
	}
	for i := range tc.Clusters {
		if err := tc.Clusters[i].Validate(); err != nil {
			return err
		}
	}
	return ValidateRefs(tc.Servers, tc.Clusters)
}

// FindServer returns the Server with the given ID.
func (tc *TenantConfig) FindServer(id string) (*Server, error) {
	for i := range tc.Servers {
		if tc.Servers[i].ID == id {
			return &tc.Servers[i], nil
		}
	}
	return nil, fmt.Errorf("server %q not found", id)
}

// FindCluster returns the ServiceCluster with the given ID.
func (tc *TenantConfig) FindCluster(id string) (*ServiceCluster, error) {
	for i := range tc.Clusters {
		if tc.Clusters[i].ID == id {
			return &tc.Clusters[i], nil
		}
	}
	return nil, fmt.Errorf("cluster %q not found", id)
}

// ServerForNode returns the Server that a node belongs to, searching all clusters.
func (tc *TenantConfig) ServerForNode(nodeID string) (*Server, error) {
	for _, c := range tc.Clusters {
		for _, n := range c.Nodes {
			if n.ID == nodeID {
				return tc.FindServer(n.ServerID)
			}
		}
	}
	return nil, fmt.Errorf("node %q not found in any cluster", nodeID)
}

// iniSection holds key-value pairs for one INI section.
type iniSection struct {
	name   string
	values map[string]string
}

// parseINI parses an INI file into ordered sections.
func parseINI(path string) ([]iniSection, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	defer f.Close()

	var sections []iniSection
	var current *iniSection

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if strings.HasPrefix(line, "[") && strings.HasSuffix(line, "]") {
			name := line[1 : len(line)-1]
			sections = append(sections, iniSection{name: name, values: make(map[string]string)})
			current = &sections[len(sections)-1]
			continue
		}
		if current == nil {
			continue // skip keys before any section
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			continue
		}
		key := strings.TrimSpace(parts[0])
		val := strings.TrimSpace(parts[1])
		current.values[key] = val
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scan %s: %w", path, err)
	}
	return sections, nil
}

// LoadFromConf loads a TenantConfig from an INI .conf file.
// It parses [common], [cluster:*], [cluster:*:node:*], and [cluster:*:check:*] sections.
// inventoryDir is optional — if non-empty, server addresses can be resolved from
// SSH inventory host files at {inventoryDir}/{tenant}/hosts/{server}.
func LoadFromConf(confPath, inventoryDir string) (*TenantConfig, error) {
	sections, err := parseINI(confPath)
	if err != nil {
		return nil, err
	}

	tc := &TenantConfig{}

	// Find [common] section.
	var common map[string]string
	for _, s := range sections {
		if s.name == "common" {
			common = s.values
			break
		}
	}
	if common == nil {
		return nil, fmt.Errorf("%s: [common] section is required", confPath)
	}

	tc.Tenant = Tenant{
		ID:   common["tenant_id"],
		Name: common["tenant_name"],
		Consul: ConsulConfig{
			Address:    common["consul_address"],
			Datacenter: common["consul_datacenter"],
			Token:      common["consul_token"],
		},
	}
	if v := common["consul_session_ttl"]; v != "" {
		d, err := time.ParseDuration(v)
		if err != nil {
			return nil, fmt.Errorf("consul_session_ttl: %w", err)
		}
		tc.Tenant.Consul.SessionTTL = Duration{d}
	}
	if v := common["consul_lock_delay"]; v != "" {
		d, err := time.ParseDuration(v)
		if err != nil {
			return nil, fmt.Errorf("consul_lock_delay: %w", err)
		}
		tc.Tenant.Consul.LockDelay = Duration{d}
	}

	// Collect clusters, nodes, checks by cluster ID.
	type clusterData struct {
		section iniSection
		nodes   []iniSection
		checks  []iniSection
	}
	clusterMap := make(map[string]*clusterData)
	clusterOrder := []string{}

	for _, s := range sections {
		if s.name == "common" {
			continue
		}
		parts := strings.SplitN(s.name, ":", 4)
		if len(parts) < 2 || parts[0] != "cluster" {
			continue
		}
		clusterID := parts[1]

		if _, exists := clusterMap[clusterID]; !exists {
			clusterMap[clusterID] = &clusterData{}
			clusterOrder = append(clusterOrder, clusterID)
		}
		cd := clusterMap[clusterID]

		switch {
		case len(parts) == 2:
			// [cluster:id] — cluster-level config
			cd.section = s
		case len(parts) == 4 && parts[2] == "node":
			// [cluster:id:node:nodeid]
			cd.nodes = append(cd.nodes, s)
		case len(parts) == 4 && parts[2] == "check":
			// [cluster:id:check:checkname]
			cd.checks = append(cd.checks, s)
		}
	}

	// Track servers we've seen (deduplicate).
	serverSeen := make(map[string]bool)

	for _, clusterID := range clusterOrder {
		cd := clusterMap[clusterID]
		cv := cd.section.values

		sc := ServiceCluster{
			ID:          clusterID,
			TenantID:    tc.Tenant.ID,
			ServiceType: cv["service_type"],
			MaxActive:   1,
		}

		if v := cv["max_active"]; v != "" {
			n, err := strconv.Atoi(v)
			if err != nil {
				return nil, fmt.Errorf("cluster %s: max_active: %w", clusterID, err)
			}
			sc.MaxActive = n
		}

		// VIP config.
		if ip := cv["vip_ip"]; ip != "" {
			vip := &VIPConfig{
				IP:        ip,
				Interface: cv["vip_interface"],
			}
			if v := cv["vip_cidr"]; v != "" {
				n, err := strconv.Atoi(v)
				if err != nil {
					return nil, fmt.Errorf("cluster %s: vip_cidr: %w", clusterID, err)
				}
				vip.CIDR = n
			}
			sc.VIP = vip
		}

		// Failover policy.
		fp := FailoverPolicy{}
		if v := cv["strategy"]; v != "" {
			sv, ok := failoverStrategyValues[v]
			if !ok {
				return nil, fmt.Errorf("cluster %s: invalid strategy %q", clusterID, v)
			}
			fp.Strategy = sv
		}
		if v := cv["quorum"]; v != "" {
			n, err := strconv.Atoi(v)
			if err != nil {
				return nil, fmt.Errorf("cluster %s: quorum: %w", clusterID, err)
			}
			fp.Quorum = n
		}
		fp.FenceEnabled = cv["fence_enabled"] == "true"
		if v := cv["fence_timeout"]; v != "" {
			d, err := time.ParseDuration(v)
			if err != nil {
				return nil, fmt.Errorf("cluster %s: fence_timeout: %w", clusterID, err)
			}
			fp.FenceTimeout = Duration{d}
		}
		fp.AutoFailback = cv["auto_failback"] == "true"
		if v := cv["failback_delay"]; v != "" {
			d, err := time.ParseDuration(v)
			if err != nil {
				return nil, fmt.Errorf("cluster %s: failback_delay: %w", clusterID, err)
			}
			fp.FailbackDelay = Duration{d}
		}
		if v := cv["max_failovers"]; v != "" {
			n, err := strconv.Atoi(v)
			if err != nil {
				return nil, fmt.Errorf("cluster %s: max_failovers: %w", clusterID, err)
			}
			fp.MaxFailovers = n
		}
		if v := cv["failover_window"]; v != "" {
			d, err := time.ParseDuration(v)
			if err != nil {
				return nil, fmt.Errorf("cluster %s: failover_window: %w", clusterID, err)
			}
			fp.FailoverWindow = Duration{d}
		}
		sc.FailoverPolicy = fp

		// Parse nodes.
		for _, ns := range cd.nodes {
			parts := strings.SplitN(ns.name, ":", 4)
			nodeID := parts[3]

			nv := ns.values
			node := Node{
				ID:             nodeID,
				ServerID:       nv["server"],
				Address:        nv["address"],
				HealthEndpoint: nv["health_endpoint"],
			}
			if v := nv["priority"]; v != "" {
				n, err := strconv.Atoi(v)
				if err != nil {
					return nil, fmt.Errorf("cluster %s: node %s: priority: %w", clusterID, nodeID, err)
				}
				node.Priority = n
			}
			if ct := nv["container_type"]; ct != "" {
				cv, ok := containerTypeValues[ct]
				if !ok {
					return nil, fmt.Errorf("cluster %s: node %s: invalid container_type %q", clusterID, nodeID, ct)
				}
				node.Container = &ContainerConfig{
					Type: cv,
					Name: nv["container_name"],
				}
			}

			sc.Nodes = append(sc.Nodes, node)

			// Register server if not seen yet.
			serverID := node.ServerID
			if serverID != "" && !serverSeen[serverID] {
				serverSeen[serverID] = true
				tc.Servers = append(tc.Servers, Server{
					ID:      serverID,
					Address: node.Address,
				})
			}
		}

		// Parse health checks.
		for _, cs := range cd.checks {
			parts := strings.SplitN(cs.name, ":", 4)
			checkName := parts[3]

			cv := cs.values
			hc := HealthCheckConfig{
				Name:   checkName,
				Target: cv["target"],
				Expect: cv["expect"],
			}
			if v := cv["type"]; v != "" {
				ct, ok := checkTypeValues[v]
				if !ok {
					return nil, fmt.Errorf("cluster %s: check %s: invalid type %q", clusterID, checkName, v)
				}
				hc.Type = ct
			}
			if v := cv["interval"]; v != "" {
				d, err := time.ParseDuration(v)
				if err != nil {
					return nil, fmt.Errorf("cluster %s: check %s: interval: %w", clusterID, checkName, err)
				}
				hc.Interval = Duration{d}
			}
			if v := cv["timeout"]; v != "" {
				d, err := time.ParseDuration(v)
				if err != nil {
					return nil, fmt.Errorf("cluster %s: check %s: timeout: %w", clusterID, checkName, err)
				}
				hc.Timeout = Duration{d}
			}
			if v := cv["weight"]; v != "" {
				n, err := strconv.Atoi(v)
				if err != nil {
					return nil, fmt.Errorf("cluster %s: check %s: weight: %w", clusterID, checkName, err)
				}
				hc.Weight = n
			}
			hc.Critical = cv["critical"] == "true"

			sc.HealthChecks = append(sc.HealthChecks, hc)
		}

		tc.Clusters = append(tc.Clusters, sc)
	}

	return tc, nil
}
