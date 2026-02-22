package model

// Node represents a service instance running on a server (possibly in a container).
type Node struct {
	ID             string           `yaml:"id"              json:"id"`
	ServerID       string           `yaml:"server_id"       json:"serverId"`
	Address        string           `yaml:"address"         json:"address"`
	Priority       int              `yaml:"priority"        json:"priority"`
	Container      *ContainerConfig `yaml:"container"       json:"container,omitempty"`
	HealthEndpoint string           `yaml:"health_endpoint" json:"healthEndpoint,omitempty"`
}

// ContainerConfig describes the container isolation for a node.
type ContainerConfig struct {
	Type ContainerType `yaml:"type" json:"type"`
	Name string        `yaml:"name" json:"name"`
}
