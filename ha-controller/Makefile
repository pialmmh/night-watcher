VERSION ?= 0.1.0
COMMIT  := $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")
LDFLAGS := -ldflags "-X main.version=$(VERSION) -X main.commit=$(COMMIT)"

.PHONY: build test fmt vet clean

build:
	go build $(LDFLAGS) -o bin/hactl ./cmd/hactl

test:
	go test ./... -v -count=1

fmt:
	gofmt -w .

vet:
	go vet ./...

clean:
	rm -rf bin/

lint: fmt vet

all: lint test build
