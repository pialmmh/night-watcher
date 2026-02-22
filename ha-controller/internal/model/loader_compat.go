package model

import (
	"strconv"
	"time"

	"github.com/telcobright/ha-controller/internal/config"
)

// FromLegacyConfig converts the old config.Config to the new TenantConfig model.
// Maps: NodeConfig → Server (1:1), GroupConfig → ServiceCluster (max_active=1).
func FromLegacyConfig(old *config.Config) *TenantConfig {
	tc := &TenantConfig{
		Tenant: Tenant{
			ID:   old.Cluster.Tenant,
			Name: old.Cluster.Name,
			Consul: ConsulConfig{
				Address:    old.Consul.Address,
				Datacenter: old.Consul.Datacenter,
				Token:      old.Consul.Token,
				SessionTTL: Duration{old.Consul.SessionTTL.Duration},
				LockDelay:  Duration{old.Consul.LockDelay.Duration},
			},
		},
	}

	// Map old nodes to servers (1:1 in legacy config).
	for _, n := range old.Nodes {
		srv := Server{
			ID:       n.ID,
			Address:  n.Address,
			FenceCmd: n.FenceCmd,
		}
		if n.SSH != nil {
			srv.SSH = &SSHConfig{
				User:    n.SSH.User,
				KeyPath: n.SSH.KeyPath,
				Port:    n.SSH.Port,
			}
		}
		tc.Servers = append(tc.Servers, srv)
	}

	// Map old groups to service clusters.
	for _, g := range old.Groups {
		sc := ServiceCluster{
			ID:          g.ID,
			TenantID:    old.Cluster.Tenant,
			ServiceType: g.ID, // best guess: group ID is the service type
			MaxActive:   1,    // legacy had no max_active concept
			FailoverPolicy: FailoverPolicy{
				Strategy:     StrategyActiveStandby,
				Quorum:       old.Cluster.Quorum,
				FenceEnabled: old.Cluster.FenceTimeout.Duration > 0,
				FenceTimeout: Duration{old.Cluster.FenceTimeout.Duration},
			},
		}

		// Extract VIP from resources.
		for _, r := range g.Resources {
			if r.Type == "vip" {
				vip := &VIPConfig{
					IP:        r.Attrs["ip"],
					Interface: r.Attrs["interface"],
				}
				if cidrStr := r.Attrs["cidr"]; cidrStr != "" {
					if n, err := strconv.Atoi(cidrStr); err == nil {
						vip.CIDR = n
					}
				}
				sc.VIP = vip
				break
			}
		}

		// Create a node per old NodeConfig referencing the same server.
		for i, n := range old.Nodes {
			node := Node{
				ID:       n.ID,
				ServerID: n.ID,
				Address:  n.Address,
				Priority: i + 1,
			}
			sc.Nodes = append(sc.Nodes, node)
		}

		// Map old checks.
		for _, ch := range g.Checks {
			hc := HealthCheckConfig{
				Name:     ch.Name,
				Target:   ch.Target,
				Expect:   ch.Expect,
				Interval: Duration{ch.Interval.Duration},
				Timeout:  Duration{ch.Timeout.Duration},
				Weight:   50, // default weight, legacy had no concept
				Critical: false,
			}
			if ct, ok := checkTypeValues[ch.Type]; ok {
				hc.Type = ct
			}
			sc.HealthChecks = append(sc.HealthChecks, hc)
		}

		tc.Clusters = append(tc.Clusters, sc)
	}

	// Backfill defaults for durations that were zero.
	if tc.Tenant.Consul.SessionTTL.Duration == 0 {
		tc.Tenant.Consul.SessionTTL = Duration{15 * time.Second}
	}
	if tc.Tenant.Consul.LockDelay.Duration == 0 {
		tc.Tenant.Consul.LockDelay = Duration{5 * time.Second}
	}

	return tc
}
