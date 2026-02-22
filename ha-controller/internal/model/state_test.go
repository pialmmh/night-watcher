package model

import (
	"math"
	"testing"
	"time"
)

func TestComputeHealthScoreAllPass(t *testing.T) {
	checks := []HealthCheckConfig{
		{Name: "a", Weight: 50},
		{Name: "b", Weight: 50},
	}
	results := []CheckResult{
		{Name: "a", Passed: true, Weight: 50, At: time.Now()},
		{Name: "b", Passed: true, Weight: 50, At: time.Now()},
	}
	score := ComputeHealthScore(checks, results)
	if score != 1.0 {
		t.Errorf("all pass: got %f, want 1.0", score)
	}
}

func TestComputeHealthScoreAllFail(t *testing.T) {
	checks := []HealthCheckConfig{
		{Name: "a", Weight: 50},
		{Name: "b", Weight: 50},
	}
	results := []CheckResult{
		{Name: "a", Passed: false, Weight: 50, At: time.Now()},
		{Name: "b", Passed: false, Weight: 50, At: time.Now()},
	}
	score := ComputeHealthScore(checks, results)
	if score != 0.0 {
		t.Errorf("all fail: got %f, want 0.0", score)
	}
}

func TestComputeHealthScoreMixed(t *testing.T) {
	checks := []HealthCheckConfig{
		{Name: "a", Weight: 80},
		{Name: "b", Weight: 20},
	}
	results := []CheckResult{
		{Name: "a", Passed: true, Weight: 80, At: time.Now()},
		{Name: "b", Passed: false, Weight: 20, At: time.Now()},
	}
	score := ComputeHealthScore(checks, results)
	want := 80.0 / 100.0
	if math.Abs(score-want) > 0.001 {
		t.Errorf("mixed: got %f, want %f", score, want)
	}
}

func TestComputeHealthScoreCriticalFail(t *testing.T) {
	checks := []HealthCheckConfig{
		{Name: "a", Weight: 80, Critical: true},
		{Name: "b", Weight: 20},
	}
	results := []CheckResult{
		{Name: "a", Passed: false, Weight: 80, At: time.Now()},
		{Name: "b", Passed: true, Weight: 20, At: time.Now()},
	}
	score := ComputeHealthScore(checks, results)
	if score != 0.0 {
		t.Errorf("critical fail: got %f, want 0.0", score)
	}
}

func TestComputeHealthScoreNoChecks(t *testing.T) {
	score := ComputeHealthScore(nil, nil)
	if score != 1.0 {
		t.Errorf("no checks: got %f, want 1.0", score)
	}
}

func TestComputeHealthScoreMissingResult(t *testing.T) {
	checks := []HealthCheckConfig{
		{Name: "a", Weight: 50},
		{Name: "b", Weight: 50},
	}
	// Only provide result for "a"
	results := []CheckResult{
		{Name: "a", Passed: true, Weight: 50, At: time.Now()},
	}
	score := ComputeHealthScore(checks, results)
	want := 50.0 / 100.0
	if math.Abs(score-want) > 0.001 {
		t.Errorf("missing result: got %f, want %f", score, want)
	}
}

func TestComputeHealthScoreMissingCriticalResult(t *testing.T) {
	checks := []HealthCheckConfig{
		{Name: "a", Weight: 50, Critical: true},
		{Name: "b", Weight: 50},
	}
	// No results at all — critical check has no result → 0.0
	score := ComputeHealthScore(checks, nil)
	if score != 0.0 {
		t.Errorf("missing critical result: got %f, want 0.0", score)
	}
}
