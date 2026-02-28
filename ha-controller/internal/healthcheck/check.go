package healthcheck

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/telcobright/ha-controller/internal/executor"
)

// CheckResult holds the outcome of a health check probe.
type CheckResult struct {
	Name   string
	Passed bool
	Output string
}

func (r CheckResult) String() string {
	status := "PASS"
	if !r.Passed {
		status = "FAIL"
	}
	return fmt.Sprintf("[%s] %s: %s", status, r.Name, r.Output)
}

// HealthCheck is a periodic probe that returns pass/fail.
type HealthCheck interface {
	Name() string
	Run() CheckResult
}

// PingCheck verifies ICMP reachability of a host.
type PingCheck struct {
	name string
	host string
	exec executor.Executor
}

func NewPingCheck(name, host string, exec executor.Executor) *PingCheck {
	return &PingCheck{name: name, host: host, exec: exec}
}

func (c *PingCheck) Name() string { return c.name }

func (c *PingCheck) Run() CheckResult {
	cmd := fmt.Sprintf("ping -c 1 -W 2 %s", c.host)
	result := c.exec.Run(context.Background(), cmd, 5*time.Second)
	return CheckResult{
		Name:   c.name,
		Passed: result.Success(),
		Output: strings.TrimSpace(result.Stdout + result.Stderr),
	}
}

// TcpCheck verifies that a TCP port is open.
type TcpCheck struct {
	name    string
	address string
	timeout time.Duration
}

func NewTcpCheck(name, address string, timeout time.Duration) *TcpCheck {
	return &TcpCheck{name: name, address: address, timeout: timeout}
}

func (c *TcpCheck) Name() string { return c.name }

func (c *TcpCheck) Run() CheckResult {
	conn, err := net.DialTimeout("tcp", c.address, c.timeout)
	if err != nil {
		return CheckResult{Name: c.name, Passed: false, Output: fmt.Sprintf("tcp connect failed: %v", err)}
	}
	conn.Close()
	return CheckResult{Name: c.name, Passed: true, Output: fmt.Sprintf("tcp connect to %s ok", c.address)}
}

// HttpCheck verifies an HTTP endpoint, optionally checking the response body.
type HttpCheck struct {
	name    string
	url     string
	expect  string // substring to look for in response body (empty = just check 2xx)
	timeout time.Duration
}

func NewHttpCheck(name, url string, expect string, timeout time.Duration) *HttpCheck {
	return &HttpCheck{name: name, url: url, expect: expect, timeout: timeout}
}

func (c *HttpCheck) Name() string { return c.name }

func (c *HttpCheck) Run() CheckResult {
	client := &http.Client{Timeout: c.timeout}
	resp, err := client.Get(c.url)
	if err != nil {
		return CheckResult{Name: c.name, Passed: false, Output: fmt.Sprintf("http request failed: %v", err)}
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return CheckResult{Name: c.name, Passed: false, Output: fmt.Sprintf("http status %d", resp.StatusCode)}
	}

	if c.expect != "" {
		buf := make([]byte, 4096)
		n, _ := resp.Body.Read(buf)
		body := string(buf[:n])
		if !strings.Contains(body, c.expect) {
			return CheckResult{Name: c.name, Passed: false, Output: fmt.Sprintf("expected %q not found in response", c.expect)}
		}
	}

	return CheckResult{Name: c.name, Passed: true, Output: fmt.Sprintf("http %s returned %d", c.url, resp.StatusCode)}
}

// ScriptCheck runs an arbitrary script and checks the output.
type ScriptCheck struct {
	name   string
	script string
	expect string
	exec   executor.Executor
}

func NewScriptCheck(name, script, expect string, exec executor.Executor) *ScriptCheck {
	return &ScriptCheck{name: name, script: script, expect: expect, exec: exec}
}

func (c *ScriptCheck) Name() string { return c.name }

func (c *ScriptCheck) Run() CheckResult {
	result := c.exec.Run(context.Background(), c.script, 30*time.Second)
	output := strings.TrimSpace(result.Stdout + result.Stderr)

	if !result.Success() {
		return CheckResult{Name: c.name, Passed: false, Output: fmt.Sprintf("script exited %d: %s", result.ExitCode, output)}
	}

	if c.expect != "" && !strings.Contains(result.Stdout, c.expect) {
		return CheckResult{Name: c.name, Passed: false, Output: fmt.Sprintf("expected %q not found in output", c.expect)}
	}

	return CheckResult{Name: c.name, Passed: true, Output: output}
}
