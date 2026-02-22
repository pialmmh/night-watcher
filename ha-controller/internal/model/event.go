package model

import "time"

// HAEvent records a significant HA lifecycle event.
type HAEvent struct {
	ID        string    `json:"id"`
	Timestamp time.Time `json:"timestamp"`
	Type      EventType `json:"type"`
	ClusterID string    `json:"clusterId"`
	NodeID    string    `json:"nodeId,omitempty"`
	Message   string    `json:"message"`
}
