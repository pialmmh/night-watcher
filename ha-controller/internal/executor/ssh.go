package executor

import (
	"bytes"
	"context"
	"fmt"
	"net"
	"os"
	"time"

	"golang.org/x/crypto/ssh"
)

// SshExecutor runs commands on a remote host via SSH.
type SshExecutor struct {
	host       string
	port       int
	user       string
	keyPath    string
	clientConf *ssh.ClientConfig
}

// SshOption configures an SshExecutor.
type SshOption func(*SshExecutor)

func WithPort(port int) SshOption {
	return func(e *SshExecutor) { e.port = port }
}

func NewSshExecutor(host, user, keyPath string, opts ...SshOption) (*SshExecutor, error) {
	e := &SshExecutor{
		host:    host,
		port:    22,
		user:    user,
		keyPath: keyPath,
	}
	for _, o := range opts {
		o(e)
	}

	key, err := os.ReadFile(keyPath)
	if err != nil {
		return nil, fmt.Errorf("read ssh key %s: %w", keyPath, err)
	}

	signer, err := ssh.ParsePrivateKey(key)
	if err != nil {
		return nil, fmt.Errorf("parse ssh key %s: %w", keyPath, err)
	}

	e.clientConf = &ssh.ClientConfig{
		User:            user,
		Auth:            []ssh.AuthMethod{ssh.PublicKeys(signer)},
		HostKeyCallback: ssh.InsecureIgnoreHostKey(),
		Timeout:         10 * time.Second,
	}

	return e, nil
}

func (e *SshExecutor) addr() string {
	return fmt.Sprintf("%s:%d", e.host, e.port)
}

func (e *SshExecutor) Run(ctx context.Context, command string, timeout time.Duration) ExecResult {
	start := time.Now()

	client, err := ssh.Dial("tcp", e.addr(), e.clientConf)
	if err != nil {
		return ExecResult{Err: fmt.Errorf("ssh dial %s: %w", e.addr(), err), ExitCode: -1, Duration: time.Since(start)}
	}
	defer client.Close()

	session, err := client.NewSession()
	if err != nil {
		return ExecResult{Err: fmt.Errorf("ssh session: %w", err), ExitCode: -1, Duration: time.Since(start)}
	}
	defer session.Close()

	var stdout, stderr bytes.Buffer
	session.Stdout = &stdout
	session.Stderr = &stderr

	// Use a channel to handle timeout since SSH sessions don't accept context directly.
	done := make(chan error, 1)
	go func() {
		done <- session.Run(command)
	}()

	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	var runErr error
	select {
	case <-ctx.Done():
		_ = session.Signal(ssh.SIGKILL)
		runErr = ctx.Err()
	case runErr = <-done:
	}

	duration := time.Since(start)
	result := ExecResult{
		Stdout:   stdout.String(),
		Stderr:   stderr.String(),
		Duration: duration,
	}

	if runErr != nil {
		result.Err = runErr
		if exitErr, ok := runErr.(*ssh.ExitError); ok {
			result.ExitCode = exitErr.ExitStatus()
		} else {
			result.ExitCode = -1
		}
	}

	return result
}

func (e *SshExecutor) Reachable() bool {
	conn, err := net.DialTimeout("tcp", e.addr(), 5*time.Second)
	if err != nil {
		return false
	}
	conn.Close()
	return true
}

func (e *SshExecutor) String() string {
	return fmt.Sprintf("SshExecutor{%s@%s}", e.user, e.addr())
}
