package model

// HealthCheckConfig describes a health check probe for a service cluster.
type HealthCheckConfig struct {
	Name     string    `yaml:"name"     json:"name"`
	Type     CheckType `yaml:"type"     json:"type"`
	Target   string    `yaml:"target"   json:"target"`
	Expect   string    `yaml:"expect"   json:"expect,omitempty"`
	Interval Duration  `yaml:"interval" json:"interval"`
	Timeout  Duration  `yaml:"timeout"  json:"timeout"`
	Weight   int       `yaml:"weight"   json:"weight"`
	Critical bool      `yaml:"critical" json:"critical"`
}
