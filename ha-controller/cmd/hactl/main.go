package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/telcobright/ha-controller/internal/api"
	"github.com/telcobright/ha-controller/internal/config"
	"github.com/telcobright/ha-controller/internal/consul"
	"github.com/telcobright/ha-controller/internal/engine"
	"github.com/telcobright/ha-controller/internal/executor"
	"github.com/telcobright/ha-controller/internal/resource"
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

	// Build resource groups from config.
	localExec := executor.NewLocalExecutor()
	var groups []*resource.ResourceGroup

	for _, gc := range cfg.Groups {
		var resources []resource.Resource
		for _, rc := range gc.Resources {
			switch rc.Type {
			case "vip":
				ip := rc.Attrs["ip"]
				cidr := 24 // default
				if c, ok := rc.Attrs["cidr"]; ok {
					fmt.Sscanf(c, "%d", &cidr)
				}
				iface := rc.Attrs["interface"]
				if iface == "" {
					iface = "eth0"
				}
				vip := resource.NewVipResource(rc.ID, ip, cidr, iface, localExec, logger)
				resources = append(resources, vip)
			case "noop":
				noop := resource.NewNoopResource(rc.ID, logger)
				resources = append(resources, noop)
			default:
				logger.Warn("unknown resource type, skipping", "type", rc.Type, "id", rc.ID)
			}
		}
		group := resource.NewResourceGroup(gc.ID, logger, resources...)
		groups = append(groups, group)
	}

	// Set up leader election.
	election := consul.NewLeaderElection(consulClient, nodeID, cfg.Cluster.Name, cfg.Consul, logger)

	// Create engine.
	eng := engine.NewEngine(cfg, nodeID, consulClient, election, groups, logger)

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
