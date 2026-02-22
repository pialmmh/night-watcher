package model

// FailoverPolicy controls how and when failover happens.
type FailoverPolicy struct {
	Strategy       FailoverStrategy `yaml:"strategy"        json:"strategy"`
	Quorum         int              `yaml:"quorum"          json:"quorum"`
	FenceEnabled   bool             `yaml:"fence_enabled"   json:"fenceEnabled"`
	FenceTimeout   Duration         `yaml:"fence_timeout"   json:"fenceTimeout"`
	AutoFailback   bool             `yaml:"auto_failback"   json:"autoFailback"`
	FailbackDelay  Duration         `yaml:"failback_delay"  json:"failbackDelay"`
	MaxFailovers   int              `yaml:"max_failovers"   json:"maxFailovers"`
	FailoverWindow Duration         `yaml:"failover_window" json:"failoverWindow"`
}
