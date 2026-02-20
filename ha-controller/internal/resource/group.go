package resource

import (
	"fmt"
	"log/slog"
)

// ResourceGroup manages an ordered set of resources.
// Activation proceeds in order (index 0 first); deactivation proceeds in reverse.
type ResourceGroup struct {
	id        string
	resources []Resource
	logger    *slog.Logger
}

func NewResourceGroup(id string, logger *slog.Logger, resources ...Resource) *ResourceGroup {
	return &ResourceGroup{
		id:        id,
		resources: resources,
		logger:    logger.With("group", id),
	}
}

func (g *ResourceGroup) ID() string { return g.id }

// Activate brings all resources up in order. On failure, it rolls back
// by deactivating already-activated resources in reverse.
func (g *ResourceGroup) Activate() error {
	g.logger.Info("activating resource group", "count", len(g.resources))

	for i, r := range g.resources {
		g.logger.Info("activating resource", "resource", r.ID(), "type", r.Type(), "step", i+1)
		if err := r.Activate(); err != nil {
			g.logger.Error("resource activation failed, rolling back", "resource", r.ID(), "err", err)
			// Roll back already-activated resources in reverse.
			for j := i - 1; j >= 0; j-- {
				g.logger.Info("rollback: deactivating", "resource", g.resources[j].ID())
				if derr := g.resources[j].Deactivate(); derr != nil {
					g.logger.Error("rollback deactivation failed", "resource", g.resources[j].ID(), "err", derr)
				}
			}
			return fmt.Errorf("activate group %s: resource %s failed: %w", g.id, r.ID(), err)
		}
	}

	g.logger.Info("resource group activated")
	return nil
}

// Deactivate stops all resources in reverse order. Collects errors but
// attempts all resources.
func (g *ResourceGroup) Deactivate() error {
	g.logger.Info("deactivating resource group", "count", len(g.resources))

	var firstErr error
	for i := len(g.resources) - 1; i >= 0; i-- {
		r := g.resources[i]
		g.logger.Info("deactivating resource", "resource", r.ID(), "type", r.Type())
		if err := r.Deactivate(); err != nil {
			g.logger.Error("resource deactivation failed", "resource", r.ID(), "err", err)
			if firstErr == nil {
				firstErr = fmt.Errorf("deactivate group %s: resource %s failed: %w", g.id, r.ID(), err)
			}
		}
	}

	if firstErr != nil {
		return firstErr
	}
	g.logger.Info("resource group deactivated")
	return nil
}

// CheckAll runs health checks on all resources and returns a summary.
func (g *ResourceGroup) CheckAll() map[string]HealthResult {
	results := make(map[string]HealthResult, len(g.resources))
	for _, r := range g.resources {
		results[r.ID()] = r.Check()
	}
	return results
}

// Resources returns the ordered slice of resources in this group.
func (g *ResourceGroup) Resources() []Resource {
	return g.resources
}
