# HA Controller

## Language & Runtime
- Go 1.23+
- Build: `make build` produces `bin/hactl`
- Test: `make test`
- Lint: `make lint` (fmt + vet)

## Project Layout
- `cmd/hactl/` — CLI entry point
- `internal/` — all packages (not importable externally)
  - `config/` — YAML config loading and validation
  - `consul/` — Consul API wrapper and leader election
  - `node/` — Node interface (machine abstraction)
  - `resource/` — Resource interface + implementations (VIP, etc.)
  - `healthcheck/` — Health check probes (ping, tcp, http, script)
  - `executor/` — Command execution (local, SSH)
  - `engine/` — Main reconciliation loop

## Conventions
- Structured logging via `log/slog` (JSON to stderr)
- No external logging framework
- Interfaces defined in their own package, implementations alongside
- YAML config, not TOML or JSON
- Do not git push until asked

## Dependencies
- `github.com/hashicorp/consul/api` — Consul client
- `golang.org/x/crypto` — SSH client
- `gopkg.in/yaml.v3` — YAML parsing
- No other external deps

## Running
```bash
# Build
make build

# Show version
./bin/hactl --version

# Run (requires Consul)
./bin/hactl --config configs/ha-controller.yml --node bdcom1
```
