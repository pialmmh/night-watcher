package model

// Tenant identifies a tenant deployment.
type Tenant struct {
	ID     string       `yaml:"id"     json:"id"`
	Name   string       `yaml:"name"   json:"name"`
	Consul ConsulConfig `yaml:"consul" json:"consul"`
}

// ConsulConfig holds Consul connection settings.
type ConsulConfig struct {
	Address    string   `yaml:"address"     json:"address"`
	Datacenter string   `yaml:"datacenter"  json:"datacenter"`
	Token      string   `yaml:"token"       json:"-"`
	SessionTTL Duration `yaml:"session_ttl" json:"sessionTtl"`
	LockDelay  Duration `yaml:"lock_delay"  json:"lockDelay"`
}
