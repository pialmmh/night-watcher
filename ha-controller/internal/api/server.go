package api

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/telcobright/ha-controller/internal/engine"
	"github.com/telcobright/ha-controller/internal/resource"
	"github.com/telcobright/ha-controller/internal/sentinel"
)

// StatusResponse is the JSON shape returned by GET /status.
type StatusResponse struct {
	NodeID       string              `json:"nodeId"`
	State        string              `json:"state"`
	Coordinator  string              `json:"coordinator"`
	ActiveNode   string              `json:"activeNode"`
	Uptime       string              `json:"uptime"`
	Sdown        bool                `json:"sdown"`
	Odown        bool                `json:"odown"`
	SelfHealthy  bool                `json:"selfHealthy"`
	Observations []ObservationStatus `json:"observations,omitempty"`
	Groups       []GroupStatus       `json:"groups"`
}

// ObservationStatus is a peer's observation as seen in the API.
type ObservationStatus struct {
	NodeID      string `json:"nodeId"`
	TargetNode  string `json:"targetNode"`
	Sdown       bool   `json:"sdown"`
	FailCount   int    `json:"failCount"`
	SelfHealthy bool   `json:"selfHealthy"`
	Age         string `json:"age"`
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
	mux.HandleFunc("/failover", s.handleFailover)
	mux.HandleFunc("/", s.handleStatus)

	addr := fmt.Sprintf(":%d", port)
	s.logger.Info("starting status API", "addr", addr)
	return http.ListenAndServe(addr, mux)
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	sen := s.engine.Sentinel()

	resp := StatusResponse{
		NodeID:      s.nodeID,
		State:       s.engine.State().String(),
		Coordinator: s.engine.CurrentLeader(),
		ActiveNode:  "",
		Uptime:      time.Since(s.startTime).Truncate(time.Second).String(),
		Groups:      s.buildGroupStatus(),
	}

	if sen != nil {
		resp.ActiveNode = sen.ActiveNodeID()
		resp.Sdown = sen.IsSdown()
		resp.Odown = sen.IsOdown()
		resp.SelfHealthy = sen.IsSelfHealthy()

		// Read observations for display.
		if observations, err := sen.ReadAllObservations(); err == nil {
			resp.Observations = s.buildObservationStatus(observations)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(resp)
}

func (s *Server) handleFailover(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method == "OPTIONS" {
		w.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		w.WriteHeader(http.StatusOK)
		return
	}

	if r.Method != "POST" {
		w.WriteHeader(http.StatusMethodNotAllowed)
		json.NewEncoder(w).Encode(map[string]string{"error": "method not allowed, use POST"})
		return
	}

	// Only the coordinator can trigger failover.
	if s.engine.State() != engine.StateLeader {
		w.WriteHeader(http.StatusConflict)
		json.NewEncoder(w).Encode(map[string]string{"error": "this node is not the coordinator"})
		return
	}

	coord := s.engine.Coordinator()
	if coord == nil {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]string{"error": "coordinator not initialized"})
		return
	}

	// Parse optional target node from request body.
	var req struct {
		TargetNode string `json:"targetNode"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	if req.TargetNode == "" {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{"error": "targetNode is required"})
		return
	}

	s.logger.Info("manual failover requested", "target", req.TargetNode)

	if err := coord.ExecuteManualFailover(req.TargetNode); err != nil {
		s.logger.Error("manual failover failed", "err", err)
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}

	json.NewEncoder(w).Encode(map[string]string{
		"status":     "ok",
		"activeNode": req.TargetNode,
	})
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

		// Resource-level checks are also exposed as group checks.
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

func (s *Server) buildObservationStatus(observations []sentinel.Observation) []ObservationStatus {
	now := time.Now()
	var result []ObservationStatus
	for _, obs := range observations {
		result = append(result, ObservationStatus{
			NodeID:      obs.NodeID,
			TargetNode:  obs.TargetNode,
			Sdown:       obs.SDOWN,
			FailCount:   obs.FailCount,
			SelfHealthy: obs.SelfHealthy,
			Age:         now.Sub(obs.At).Truncate(time.Second).String(),
		})
	}
	return result
}
