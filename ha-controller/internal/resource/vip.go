package resource

import (
	"context"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/telcobright/ha-controller/internal/executor"
)

// VipResource manages a floating virtual IP address.
// It adds/removes the IP from a network interface and sends gratuitous ARPs.
type VipResource struct {
	id        string
	ip        string
	cidr      int
	iface     string
	exec      executor.Executor
	state     ResourceState
	logger    *slog.Logger
	cmdTimeout time.Duration
}

type VipOption func(*VipResource)

func WithCmdTimeout(d time.Duration) VipOption {
	return func(v *VipResource) { v.cmdTimeout = d }
}

// NewVipResource creates a VIP resource.
// ip is the address (e.g., "10.255.246.174"), cidr is the prefix length (e.g., 24),
// iface is the network interface (e.g., "eth0").
func NewVipResource(id, ip string, cidr int, iface string, exec executor.Executor, logger *slog.Logger, opts ...VipOption) *VipResource {
	v := &VipResource{
		id:         id,
		ip:         ip,
		cidr:       cidr,
		iface:      iface,
		exec:       exec,
		state:      StateUnknown,
		logger:     logger.With("resource", id, "type", "vip"),
		cmdTimeout: 10 * time.Second,
	}
	for _, o := range opts {
		o(v)
	}
	return v
}

func (v *VipResource) ID() string   { return v.id }
func (v *VipResource) Type() string { return "vip" }
func (v *VipResource) Status() ResourceState { return v.state }

func (v *VipResource) ipCidr() string {
	return fmt.Sprintf("%s/%d", v.ip, v.cidr)
}

// Check verifies whether the VIP is currently assigned to the interface.
func (v *VipResource) Check() HealthResult {
	ctx := context.Background()
	cmd := fmt.Sprintf("ip addr show dev %s", v.iface)
	result := v.exec.Run(ctx, cmd, v.cmdTimeout)

	if !result.Success() {
		v.state = StateUnknown
		return HealthResult{
			Status: HealthUnhealthy,
			Reason: fmt.Sprintf("ip addr show failed: %s", result.Stderr),
		}
	}

	if strings.Contains(result.Stdout, v.ipCidr()) {
		v.state = StateActive
		return HealthResult{Status: HealthHealthy, Reason: "VIP assigned"}
	}

	v.state = StateStopped
	return HealthResult{
		Status: HealthUnhealthy,
		Reason: fmt.Sprintf("VIP %s not found on %s", v.ipCidr(), v.iface),
	}
}

// Activate adds the VIP to the interface and sends gratuitous ARPs.
func (v *VipResource) Activate() error {
	v.logger.Info("activating VIP", "ip", v.ipCidr(), "iface", v.iface)
	ctx := context.Background()

	// Add the IP address.
	addCmd := fmt.Sprintf("ip addr add %s dev %s", v.ipCidr(), v.iface)
	result := v.exec.Run(ctx, addCmd, v.cmdTimeout)
	if !result.Success() {
		// Check if it's already assigned (not an error).
		if strings.Contains(result.Stderr, "File exists") {
			v.logger.Info("VIP already assigned, continuing")
		} else {
			return fmt.Errorf("ip addr add failed: %s (exit %d)", result.Stderr, result.ExitCode)
		}
	}

	// Send gratuitous ARP to update neighbor caches.
	arpCmd := fmt.Sprintf("arping -U -c 3 -I %s %s", v.iface, v.ip)
	arpResult := v.exec.Run(ctx, arpCmd, v.cmdTimeout)
	if !arpResult.Success() {
		v.logger.Warn("arping failed (non-fatal)", "stderr", arpResult.Stderr)
	}

	v.state = StateActive
	v.logger.Info("VIP activated")
	return nil
}

// Standby is a no-op for VIP — a VIP is either active or stopped.
func (v *VipResource) Standby() error {
	return v.Deactivate()
}

// Deactivate removes the VIP from the interface.
func (v *VipResource) Deactivate() error {
	v.logger.Info("deactivating VIP", "ip", v.ipCidr(), "iface", v.iface)
	ctx := context.Background()

	delCmd := fmt.Sprintf("ip addr del %s dev %s", v.ipCidr(), v.iface)
	result := v.exec.Run(ctx, delCmd, v.cmdTimeout)
	if !result.Success() {
		// Not an error if it's already gone.
		if strings.Contains(result.Stderr, "Cannot assign requested address") {
			v.logger.Info("VIP already removed")
		} else {
			return fmt.Errorf("ip addr del failed: %s (exit %d)", result.Stderr, result.ExitCode)
		}
	}

	v.state = StateStopped
	v.logger.Info("VIP deactivated")
	return nil
}
