package api

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/telcobright/ha-controller/internal/engine"
	"github.com/telcobright/ha-controller/internal/resource"
)

// StatusResponse is the JSON shape returned by GET /status.
type StatusResponse struct {
	NodeID string        `json:"nodeId"`
	State  string        `json:"state"`
	Leader string        `json:"leader"`
	Uptime string        `json:"uptime"`
	Groups []GroupStatus `json:"groups"`
}

// GroupStatus describes a resource group's current state.
type GroupStatus struct {
	ID        string           `json:"id"`
	Resources []ResourceStatus `json:"resources"`
	Checks    []CheckStatus    `json:"checks"`
}

// ResourceStatus describes a single resource's current state.
type ResourceStatus struct {
	ID     string `json:"id"`
	Type   string `json:"type"`
	State  string `json:"state"`
	Health string `json:"health"`
	Reason string `json:"reason,omitempty"`
}

// CheckStatus describes a health check result.
type CheckStatus struct {
	Name   string `json:"name"`
	Passed bool   `json:"passed"`
	Output string `json:"output"`
}

// Server serves the hactl status API.
type Server struct {
	engine    *engine.Engine
	nodeID    string
	startTime time.Time
	logger    *slog.Logger
}

// NewServer creates the API server.
func NewServer(eng *engine.Engine, nodeID string, logger *slog.Logger) *Server {
	return &Server{
		engine:    eng,
		nodeID:    nodeID,
		startTime: time.Now(),
		logger:    logger.With("component", "api"),
	}
}

// Start runs the HTTP server on the given port. Blocks until error.
func (s *Server) Start(port int) error {
	mux := http.NewServeMux()
	mux.HandleFunc("/status", s.handleStatus)
	mux.HandleFunc("/", s.handleStatus)

	addr := fmt.Sprintf(":%d", port)
	s.logger.Info("starting status API", "addr", addr)
	return http.ListenAndServe(addr, mux)
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	resp := StatusResponse{
		NodeID: s.nodeID,
		State:  s.engine.State().String(),
		Leader: s.engine.CurrentLeader(),
		Uptime: time.Since(s.startTime).Truncate(time.Second).String(),
		Groups: s.buildGroupStatus(),
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(resp)
}

func (s *Server) buildGroupStatus() []GroupStatus {
	var groups []GroupStatus
	for _, g := range s.engine.Groups() {
		gs := GroupStatus{ID: g.ID()}

		for _, res := range g.Resources() {
			health := res.Check()
			rs := ResourceStatus{
				ID:     res.ID(),
				Type:   res.Type(),
				State:  res.Status().String(),
				Health: health.Status.String(),
				Reason: health.Reason,
			}
			gs.Resources = append(gs.Resources, rs)
		}

		// Resource-level checks are also exposed as group checks
		results := g.CheckAll()
		for id, result := range results {
			cs := CheckStatus{
				Name:   id,
				Passed: result.Status == resource.HealthHealthy,
				Output: result.Reason,
			}
			gs.Checks = append(gs.Checks, cs)
		}

		groups = append(groups, gs)
	}
	return groups
}
