package model

import "time"

// NodeRuntimeState holds the computed runtime state for a node.
type NodeRuntimeState struct {
	NodeID       string        `json:"nodeId"`
	Role         NodeRole      `json:"role"`
	HealthScore  float64       `json:"healthScore"`
	CheckResults []CheckResult `json:"checkResults"`
}

// CheckResult records the outcome of a single health check execution.
type CheckResult struct {
	Name   string    `json:"name"`
	Passed bool      `json:"passed"`
	Output string    `json:"output"`
	Weight int       `json:"weight"`
	At     time.Time `json:"at"`
}

// ClusterRuntimeState holds the computed runtime state for a cluster.
type ClusterRuntimeState struct {
	ClusterID   string             `json:"clusterId"`
	State       ClusterState       `json:"state"`
	Leader      string             `json:"leader,omitempty"`
	ActiveNodes []string           `json:"activeNodes"`
	NodeStates  []NodeRuntimeState `json:"nodeStates"`
}

// ComputeHealthScore returns a score from 0.0 to 1.0.
// If any critical check fails, returns 0.0.
// If there are no checks, returns 1.0.
// Otherwise: sum(passed_weight) / sum(total_weight).
func ComputeHealthScore(checks []HealthCheckConfig, results []CheckResult) float64 {
	if len(checks) == 0 {
		return 1.0
	}

	// Build result lookup by check name.
	resultMap := make(map[string]bool, len(results))
	for _, r := range results {
		resultMap[r.Name] = r.Passed
	}

	var totalWeight, passedWeight int
	for _, c := range checks {
		passed, exists := resultMap[c.Name]
		if !exists {
			// No result for this check means it hasn't run yet; treat as failed.
			if c.Critical {
				return 0.0
			}
			totalWeight += c.Weight
			continue
		}
		if c.Critical && !passed {
			return 0.0
		}
		totalWeight += c.Weight
		if passed {
			passedWeight += c.Weight
		}
	}

	if totalWeight == 0 {
		return 1.0
	}
	return float64(passedWeight) / float64(totalWeight)
}
