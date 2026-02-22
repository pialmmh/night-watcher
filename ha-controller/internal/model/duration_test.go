package model

import (
	"encoding/json"
	"testing"
	"time"

	"gopkg.in/yaml.v3"
)

func TestDurationYAMLRoundTrip(t *testing.T) {
	type wrapper struct {
		D Duration `yaml:"d"`
	}

	input := `d: "15s"`
	var w wrapper
	if err := yaml.Unmarshal([]byte(input), &w); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if w.D.Duration != 15*time.Second {
		t.Errorf("got %v, want 15s", w.D.Duration)
	}

	out, err := yaml.Marshal(&w)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if got := string(out); got != "d: 15s\n" {
		t.Errorf("marshal = %q, want %q", got, "d: 15s\n")
	}
}

func TestDurationJSONRoundTrip(t *testing.T) {
	type wrapper struct {
		D Duration `json:"d"`
	}

	input := `{"d":"5m0s"}`
	var w wrapper
	if err := json.Unmarshal([]byte(input), &w); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if w.D.Duration != 5*time.Minute {
		t.Errorf("got %v, want 5m0s", w.D.Duration)
	}

	out, err := json.Marshal(&w)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if got := string(out); got != `{"d":"5m0s"}` {
		t.Errorf("marshal = %q, want %q", got, `{"d":"5m0s"}`)
	}
}

func TestDurationInvalidYAML(t *testing.T) {
	type wrapper struct {
		D Duration `yaml:"d"`
	}
	input := `d: "notaduration"`
	var w wrapper
	if err := yaml.Unmarshal([]byte(input), &w); err == nil {
		t.Fatal("expected error for invalid duration")
	}
}

func TestDurationInvalidJSON(t *testing.T) {
	type wrapper struct {
		D Duration `json:"d"`
	}
	input := `{"d":"xyz"}`
	var w wrapper
	if err := json.Unmarshal([]byte(input), &w); err == nil {
		t.Fatal("expected error for invalid duration")
	}
}
