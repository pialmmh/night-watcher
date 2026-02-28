package node

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/telcobright/ha-controller/internal/executor"
)

// Node represents a machine in the cluster.
type Node interface {
	// ID returns the unique node identifier (e.g., "bdcom1").
	ID() string

	// Address returns the management IP or hostname.
	Address() string

	// Reachable returns true if the node can be reached.
	Reachable() bool

	// Fence powers off or isolates the node to prevent split-brain.
	Fence() error

	// Executor returns the executor for running commands on this node.
	Executor() executor.Executor
}

// BaseNode is a standard Node implementation.
type BaseNode struct {
	id       string
	address  string
	fenceCmd string
	exec     executor.Executor
	logger   *slog.Logger
}

type BaseNodeOption func(*BaseNode)

func WithFenceCmd(cmd string) BaseNodeOption {
	return func(n *BaseNode) { n.fenceCmd = cmd }
}

func NewBaseNode(id, address string, exec executor.Executor, logger *slog.Logger, opts ...BaseNodeOption) *BaseNode {
	n := &BaseNode{
		id:      id,
		address: address,
		exec:    exec,
		logger:  logger.With("node", id),
	}
	for _, o := range opts {
		o(n)
	}
	return n
}

func (n *BaseNode) ID() string                  { return n.id }
func (n *BaseNode) Address() string             { return n.address }
func (n *BaseNode) Executor() executor.Executor { return n.exec }

func (n *BaseNode) Reachable() bool {
	return n.exec.Reachable()
}

func (n *BaseNode) Fence() error {
	if n.fenceCmd == "" {
		return fmt.Errorf("no fence command configured for node %s", n.id)
	}
	n.logger.Warn("fencing node", "cmd", n.fenceCmd)
	// Fence commands run locally (from the controller's perspective) against the target node.
	localExec := executor.NewLocalExecutor()
	result := localExec.Run(context.Background(), n.fenceCmd, 30*time.Second)
	if !result.Success() {
		return fmt.Errorf("fence node %s failed: %s (exit %d)", n.id, result.Stderr, result.ExitCode)
	}
	n.logger.Warn("node fenced successfully")
	return nil
}
