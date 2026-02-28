package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/telcobright/ha-controller/internal/api"
	"github.com/telcobright/ha-controller/internal/config"
	"github.com/telcobright/ha-controller/internal/consul"
	"github.com/telcobright/ha-controller/internal/engine"
	"github.com/telcobright/ha-controller/internal/executor"
	"github.com/telcobright/ha-controller/internal/healthcheck"
	"github.com/telcobright/ha-controller/internal/node"
	"github.com/telcobright/ha-controller/internal/resource"
	"github.com/telcobright/ha-controller/internal/sentinel"
)

var (
	version = "dev"
	commit  = "unknown"
)

func main() {
	var (
		configPath  string
		nodeID      string
		showVersion bool
		logLevel    string
		apiPort     int
	)

	flag.StringVar(&configPath, "config", "", "path to config file")
	flag.StringVar(&nodeID, "node", "", "this node's ID (must match a node in config)")
	flag.BoolVar(&showVersion, "version", false, "print version and exit")
	flag.StringVar(&logLevel, "log-level", "info", "log level (debug, info, warn, error)")
	flag.IntVar(&apiPort, "api-port", 7102, "port for status API server")
	flag.Parse()

	if showVersion {
		fmt.Printf("hactl %s (%s)\n", version, commit)
		os.Exit(0)
	}

	// Set up structured logging.
	var level slog.Level
	switch logLevel {
	case "debug":
		level = slog.LevelDebug
	case "warn":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}
	logger := slog.New(slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{Level: level}))
	slog.SetDefault(logger)

	if configPath == "" {
		logger.Error("--config is required")
		os.Exit(1)
	}
	if nodeID == "" {
		logger.Error("--node is required")
		os.Exit(1)
	}

	// Load and validate config.
	cfg, err := config.Load(configPath)
	if err != nil {
		logger.Error("failed to load config", "err", err)
		os.Exit(1)
	}

	// Verify this node is in the config.
	if _, err := cfg.FindNode(nodeID); err != nil {
		logger.Error("node not found in config", "node", nodeID, "err", err)
		os.Exit(1)
	}

	logger.Info("config loaded",
		"cluster", cfg.Cluster.Name,
		"tenant", cfg.Cluster.Tenant,
		"node", nodeID,
		"nodes", len(cfg.Nodes),
		"groups", len(cfg.Groups),
	)

	// Connect to Consul.
	consulClient, err := consul.NewClient(cfg.Consul, logger)
	if err != nil {
		logger.Error("failed to create consul client", "err", err)
		os.Exit(1)
	}

	if err := consulClient.Ping(); err != nil {
		logger.Error("cannot reach consul", "addr", cfg.Consul.Address, "err", err)
		os.Exit(1)
	}

	// Build nodes with SSH executors.
	nodes := buildNodes(cfg.Nodes, logger)

	// Build health checks from group configs.
	localExec := executor.NewLocalExecutor()
	var clusterChecks, selfChecks []healthcheck.HealthCheck
	for _, gc := range cfg.Groups {
		cc, sc := buildHealthChecks(gc.Checks, localExec)
		clusterChecks = append(clusterChecks, cc...)
		selfChecks = append(selfChecks, sc...)
	}

	// Build resource groups for API backward compat (local executor, shows status).
	var groups []*resource.ResourceGroup
	for _, gc := range cfg.Groups {
		resources := buildResources(gc.Resources, localExec, logger)
		group := resource.NewResourceGroup(gc.ID, logger, resources...)
		groups = append(groups, group)
	}

	// Create sentinel.
	sen := sentinel.NewSentinel(
		nodeID,
		cfg.Cluster.Name,
		consulClient,
		clusterChecks,
		selfChecks,
		cfg.Cluster.Quorum,
		cfg.Cluster.FailThreshold,
		cfg.Cluster.ObservationStale.Duration,
		logger,
	)

	// Create failover coordinator.
	coord := sentinel.NewFailoverCoordinator(
		sen,
		consulClient,
		nodes,
		cfg.Nodes,
		cfg.Groups,
		cfg.Cluster.Name,
		cfg.Cluster.AutoFailback,
		cfg.Cluster.MaxFailovers,
		cfg.Cluster.FailoverWindow.Duration,
		logger,
	)

	// Set up leader election (coordinator role, not active-node role).
	election := consul.NewLeaderElection(consulClient, nodeID, cfg.Cluster.Name, cfg.Consul, logger)

	// Create engine with sentinel awareness.
	eng := engine.NewEngine(cfg, nodeID, consulClient, election, groups, sen, coord, logger)

	// Start status API server.
	apiServer := api.NewServer(eng, nodeID, logger)
	go func() {
		if err := apiServer.Start(apiPort); err != nil {
			logger.Error("API server failed", "err", err)
		}
	}()

	// Run with graceful shutdown.
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigCh
		logger.Info("received signal, shutting down", "signal", sig)
		cancel()
	}()

	logger.Info("starting ha-controller engine")
	if err := eng.Run(ctx); err != nil {
		logger.Error("engine exited with error", "err", err)
		os.Exit(1)
	}
}

// buildNodes creates Node instances with SSH executors for each configured node.
func buildNodes(nodeConfigs []config.NodeConfig, logger *slog.Logger) map[string]node.Node {
	nodes := make(map[string]node.Node, len(nodeConfigs))

	for _, nc := range nodeConfigs {
		var exec executor.Executor

		if nc.SSH != nil {
			port := nc.SSH.Port
			if port == 0 {
				port = 22
			}
			sshExec, err := executor.NewSshExecutor(nc.Address, nc.SSH.User, nc.SSH.KeyPath, executor.WithPort(port))
			if err != nil {
				logger.Error("failed to create SSH executor for node", "node", nc.ID, "err", err)
				// Fall back to local executor.
				exec = executor.NewLocalExecutor()
			} else {
				exec = sshExec
			}
		} else {
			exec = executor.NewLocalExecutor()
		}

		var opts []node.BaseNodeOption
		if nc.FenceCmd != "" {
			opts = append(opts, node.WithFenceCmd(nc.FenceCmd))
		}

		n := node.NewBaseNode(nc.ID, nc.Address, exec, logger, opts...)
		nodes[nc.ID] = n
	}

	return nodes
}

// buildHealthChecks creates HealthCheck instances from config, split by scope.
func buildHealthChecks(checks []config.CheckConfig, exec executor.Executor) (cluster, self []healthcheck.HealthCheck) {
	for _, cc := range checks {
		scope := cc.Scope
		if scope == "" {
			scope = "cluster"
		}

		timeout := cc.Timeout.Duration
		if timeout == 0 {
			timeout = 3 * time.Second
		}

		var hc healthcheck.HealthCheck
		switch cc.Type {
		case "ping":
			hc = healthcheck.NewPingCheck(cc.Name, cc.Target, exec)
		case "tcp":
			hc = healthcheck.NewTcpCheck(cc.Name, cc.Target, timeout)
		case "http":
			hc = healthcheck.NewHttpCheck(cc.Name, cc.Target, cc.Expect, timeout)
		case "script":
			hc = healthcheck.NewScriptCheck(cc.Name, cc.Target, cc.Expect, exec)
		}

		if hc == nil {
			continue
		}

		if scope == "self" {
			self = append(self, hc)
		} else {
			cluster = append(cluster, hc)
		}
	}
	return
}

// buildResources creates Resource instances from config (for local API status display).
func buildResources(resourceConfigs []config.ResourceConfig, exec executor.Executor, logger *slog.Logger) []resource.Resource {
	var resources []resource.Resource

	for _, rc := range resourceConfigs {
		switch rc.Type {
		case "vip":
			ip := rc.Attrs["ip"]
			cidr := 24
			if c, ok := rc.Attrs["cidr"]; ok {
				fmt.Sscanf(c, "%d", &cidr)
			}
			iface := rc.Attrs["interface"]
			if iface == "" {
				iface = "eth0"
			}
			vip := resource.NewVipResource(rc.ID, ip, cidr, iface, exec, logger)
			resources = append(resources, vip)

		case "action":
			activateCmd := rc.Attrs["activate"]
			deactivateCmd := rc.Attrs["deactivate"]
			checkCmd := rc.Attrs["check"]
			var opts []resource.ActionOption
			if t, ok := rc.Attrs["timeout"]; ok {
				if d, err := time.ParseDuration(t); err == nil {
					opts = append(opts, resource.WithActionTimeout(d))
				}
			}
			action := resource.NewActionResource(rc.ID, activateCmd, deactivateCmd, checkCmd, exec, logger, opts...)
			resources = append(resources, action)

		case "noop":
			noop := resource.NewNoopResource(rc.ID, logger)
			resources = append(resources, noop)

		default:
			logger.Warn("unknown resource type, skipping", "type", rc.Type, "id", rc.ID)
		}
	}

	return resources
}
