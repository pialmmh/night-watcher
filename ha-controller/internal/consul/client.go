package consul

import (
	"fmt"
	"log/slog"

	consulapi "github.com/hashicorp/consul/api"

	"github.com/telcobright/ha-controller/internal/config"
)

// Client wraps the Consul API client with HA-specific operations.
type Client struct {
	api    *consulapi.Client
	kv     *consulapi.KV
	session *consulapi.Session
	logger *slog.Logger
	cfg    config.ConsulConfig
}

// NewClient creates a Consul client from the HA controller config.
func NewClient(cfg config.ConsulConfig, logger *slog.Logger) (*Client, error) {
	apiCfg := consulapi.DefaultConfig()
	apiCfg.Address = cfg.Address

	if cfg.Datacenter != "" {
		apiCfg.Datacenter = cfg.Datacenter
	}
	if cfg.Token != "" {
		apiCfg.Token = cfg.Token
	}

	api, err := consulapi.NewClient(apiCfg)
	if err != nil {
		return nil, fmt.Errorf("consul client: %w", err)
	}

	return &Client{
		api:     api,
		kv:      api.KV(),
		session: api.Session(),
		logger:  logger.With("component", "consul"),
		cfg:     cfg,
	}, nil
}

// Ping checks connectivity to Consul by reading the leader address.
func (c *Client) Ping() error {
	leader, err := c.api.Status().Leader()
	if err != nil {
		return fmt.Errorf("consul ping: %w", err)
	}
	if leader == "" {
		return fmt.Errorf("consul has no leader")
	}
	c.logger.Info("consul connected", "leader", leader)
	return nil
}

// API returns the underlying Consul API client for advanced usage.
func (c *Client) API() *consulapi.Client {
	return c.api
}

// KV returns the KV store handle.
func (c *Client) KV() *consulapi.KV {
	return c.kv
}

// RegisterService registers this HA controller as a Consul service.
func (c *Client) RegisterService(nodeID, clusterName string, port int) error {
	reg := &consulapi.AgentServiceRegistration{
		ID:   fmt.Sprintf("hactl-%s-%s", clusterName, nodeID),
		Name: fmt.Sprintf("hactl-%s", clusterName),
		Tags: []string{"ha-controller", clusterName, nodeID},
		Port: port,
		Check: &consulapi.AgentServiceCheck{
			TTL:                            "15s",
			DeregisterCriticalServiceAfter: "1m",
		},
	}

	if err := c.api.Agent().ServiceRegister(reg); err != nil {
		return fmt.Errorf("register service: %w", err)
	}

	c.logger.Info("registered consul service", "id", reg.ID)
	return nil
}

// SetKV writes a key-value pair to the Consul KV store.
func (c *Client) SetKV(key, value string) error {
	p := &consulapi.KVPair{Key: key, Value: []byte(value)}
	_, err := c.kv.Put(p, nil)
	if err != nil {
		return fmt.Errorf("consul kv put %s: %w", key, err)
	}
	return nil
}

// GetKV reads a value from the Consul KV store.
func (c *Client) GetKV(key string) (string, error) {
	pair, _, err := c.kv.Get(key, nil)
	if err != nil {
		return "", fmt.Errorf("consul kv get %s: %w", key, err)
	}
	if pair == nil {
		return "", nil
	}
	return string(pair.Value), nil
}
