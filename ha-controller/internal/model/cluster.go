package model

// ServiceCluster groups nodes that provide the same service with failover semantics.
type ServiceCluster struct {
	ID             string              `yaml:"id"              json:"id"`
	TenantID       string              `yaml:"tenant_id"       json:"tenantId"`
	ServiceType    string              `yaml:"service_type"    json:"serviceType"`
	MaxActive      int                 `yaml:"max_active"      json:"maxActive"`
	VIP            *VIPConfig          `yaml:"vip"             json:"vip,omitempty"`
	Nodes          []Node              `yaml:"nodes"           json:"nodes"`
	HealthChecks   []HealthCheckConfig `yaml:"health_checks"   json:"healthChecks"`
	FailoverPolicy FailoverPolicy      `yaml:"failover_policy" json:"failoverPolicy"`
}

// VIPConfig describes a virtual IP address resource.
type VIPConfig struct {
	IP        string `yaml:"ip"        json:"ip"`
	CIDR      int    `yaml:"cidr"      json:"cidr"`
	Interface string `yaml:"interface" json:"interface"`
}
