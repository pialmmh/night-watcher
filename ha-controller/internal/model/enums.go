package model

import (
	"encoding/json"
	"fmt"

	"gopkg.in/yaml.v3"
)

// NodeRole represents the role of a node in a cluster.
type NodeRole int

const (
	NodeRoleUnknown NodeRole = iota
	NodeRoleActive
	NodeRoleStandby
	NodeRoleSpare
	NodeRoleFenced
)

var nodeRoleNames = map[NodeRole]string{
	NodeRoleUnknown: "unknown",
	NodeRoleActive:  "active",
	NodeRoleStandby: "standby",
	NodeRoleSpare:   "spare",
	NodeRoleFenced:  "fenced",
}

var nodeRoleValues = map[string]NodeRole{
	"unknown": NodeRoleUnknown,
	"active":  NodeRoleActive,
	"standby": NodeRoleStandby,
	"spare":   NodeRoleSpare,
	"fenced":  NodeRoleFenced,
}

func (r NodeRole) String() string {
	if s, ok := nodeRoleNames[r]; ok {
		return s
	}
	return "unknown"
}

func (r NodeRole) MarshalJSON() ([]byte, error)  { return json.Marshal(r.String()) }
func (r NodeRole) MarshalYAML() (interface{}, error) { return r.String(), nil }

func (r *NodeRole) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	v, ok := nodeRoleValues[s]
	if !ok {
		return fmt.Errorf("invalid NodeRole %q", s)
	}
	*r = v
	return nil
}

func (r *NodeRole) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return err
	}
	v, ok := nodeRoleValues[s]
	if !ok {
		return fmt.Errorf("invalid NodeRole %q", s)
	}
	*r = v
	return nil
}

// ClusterState represents the overall state of a service cluster.
type ClusterState int

const (
	ClusterStateUnknown  ClusterState = iota
	ClusterStateHealthy
	ClusterStateDegraded
	ClusterStateCritical
	ClusterStateDown
)

var clusterStateNames = map[ClusterState]string{
	ClusterStateUnknown:  "unknown",
	ClusterStateHealthy:  "healthy",
	ClusterStateDegraded: "degraded",
	ClusterStateCritical: "critical",
	ClusterStateDown:     "down",
}

var clusterStateValues = map[string]ClusterState{
	"unknown":  ClusterStateUnknown,
	"healthy":  ClusterStateHealthy,
	"degraded": ClusterStateDegraded,
	"critical": ClusterStateCritical,
	"down":     ClusterStateDown,
}

func (s ClusterState) String() string {
	if n, ok := clusterStateNames[s]; ok {
		return n
	}
	return "unknown"
}

func (s ClusterState) MarshalJSON() ([]byte, error)  { return json.Marshal(s.String()) }
func (s ClusterState) MarshalYAML() (interface{}, error) { return s.String(), nil }

func (s *ClusterState) UnmarshalJSON(b []byte) error {
	var str string
	if err := json.Unmarshal(b, &str); err != nil {
		return err
	}
	v, ok := clusterStateValues[str]
	if !ok {
		return fmt.Errorf("invalid ClusterState %q", str)
	}
	*s = v
	return nil
}

func (s *ClusterState) UnmarshalYAML(value *yaml.Node) error {
	var str string
	if err := value.Decode(&str); err != nil {
		return err
	}
	v, ok := clusterStateValues[str]
	if !ok {
		return fmt.Errorf("invalid ClusterState %q", str)
	}
	*s = v
	return nil
}

// FailoverStrategy describes how failover is handled.
type FailoverStrategy int

const (
	StrategyActiveStandby FailoverStrategy = iota
	StrategyActiveActive
)

var failoverStrategyNames = map[FailoverStrategy]string{
	StrategyActiveStandby: "active-standby",
	StrategyActiveActive:  "active-active",
}

var failoverStrategyValues = map[string]FailoverStrategy{
	"active-standby": StrategyActiveStandby,
	"active-active":  StrategyActiveActive,
}

func (f FailoverStrategy) String() string {
	if s, ok := failoverStrategyNames[f]; ok {
		return s
	}
	return "active-standby"
}

func (f FailoverStrategy) MarshalJSON() ([]byte, error)  { return json.Marshal(f.String()) }
func (f FailoverStrategy) MarshalYAML() (interface{}, error) { return f.String(), nil }

func (f *FailoverStrategy) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	v, ok := failoverStrategyValues[s]
	if !ok {
		return fmt.Errorf("invalid FailoverStrategy %q", s)
	}
	*f = v
	return nil
}

func (f *FailoverStrategy) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return err
	}
	v, ok := failoverStrategyValues[s]
	if !ok {
		return fmt.Errorf("invalid FailoverStrategy %q", s)
	}
	*f = v
	return nil
}

// ContainerType describes the isolation type for a node.
type ContainerType int

const (
	ContainerProcess ContainerType = iota
	ContainerLXC
	ContainerDocker
	ContainerVM
)

var containerTypeNames = map[ContainerType]string{
	ContainerProcess: "process",
	ContainerLXC:     "lxc",
	ContainerDocker:  "docker",
	ContainerVM:      "vm",
}

var containerTypeValues = map[string]ContainerType{
	"process": ContainerProcess,
	"lxc":     ContainerLXC,
	"docker":  ContainerDocker,
	"vm":      ContainerVM,
}

func (c ContainerType) String() string {
	if s, ok := containerTypeNames[c]; ok {
		return s
	}
	return "process"
}

func (c ContainerType) MarshalJSON() ([]byte, error)  { return json.Marshal(c.String()) }
func (c ContainerType) MarshalYAML() (interface{}, error) { return c.String(), nil }

func (c *ContainerType) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	v, ok := containerTypeValues[s]
	if !ok {
		return fmt.Errorf("invalid ContainerType %q", s)
	}
	*c = v
	return nil
}

func (c *ContainerType) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return err
	}
	v, ok := containerTypeValues[s]
	if !ok {
		return fmt.Errorf("invalid ContainerType %q", s)
	}
	*c = v
	return nil
}

// CheckType describes the type of health check.
type CheckType int

const (
	CheckPing CheckType = iota
	CheckTCP
	CheckHTTP
	CheckScript
)

var checkTypeNames = map[CheckType]string{
	CheckPing:   "ping",
	CheckTCP:    "tcp",
	CheckHTTP:   "http",
	CheckScript: "script",
}

var checkTypeValues = map[string]CheckType{
	"ping":   CheckPing,
	"tcp":    CheckTCP,
	"http":   CheckHTTP,
	"script": CheckScript,
}

func (c CheckType) String() string {
	if s, ok := checkTypeNames[c]; ok {
		return s
	}
	return "ping"
}

func (c CheckType) MarshalJSON() ([]byte, error)  { return json.Marshal(c.String()) }
func (c CheckType) MarshalYAML() (interface{}, error) { return c.String(), nil }

func (c *CheckType) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	v, ok := checkTypeValues[s]
	if !ok {
		return fmt.Errorf("invalid CheckType %q", s)
	}
	*c = v
	return nil
}

func (c *CheckType) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return err
	}
	v, ok := checkTypeValues[s]
	if !ok {
		return fmt.Errorf("invalid CheckType %q", s)
	}
	*c = v
	return nil
}

// EventType describes the type of HA event.
type EventType int

const (
	EventFailover EventType = iota
	EventFencing
	EventPromotion
	EventDemotion
	EventHealthFail
	EventHealthRecover
)

var eventTypeNames = map[EventType]string{
	EventFailover:      "failover",
	EventFencing:       "fencing",
	EventPromotion:     "promotion",
	EventDemotion:      "demotion",
	EventHealthFail:    "health_fail",
	EventHealthRecover: "health_recover",
}

var eventTypeValues = map[string]EventType{
	"failover":       EventFailover,
	"fencing":        EventFencing,
	"promotion":      EventPromotion,
	"demotion":       EventDemotion,
	"health_fail":    EventHealthFail,
	"health_recover": EventHealthRecover,
}

func (e EventType) String() string {
	if s, ok := eventTypeNames[e]; ok {
		return s
	}
	return "failover"
}

func (e EventType) MarshalJSON() ([]byte, error)  { return json.Marshal(e.String()) }
func (e EventType) MarshalYAML() (interface{}, error) { return e.String(), nil }

func (e *EventType) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	v, ok := eventTypeValues[s]
	if !ok {
		return fmt.Errorf("invalid EventType %q", s)
	}
	*e = v
	return nil
}

func (e *EventType) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return err
	}
	v, ok := eventTypeValues[s]
	if !ok {
		return fmt.Errorf("invalid EventType %q", s)
	}
	*e = v
	return nil
}
