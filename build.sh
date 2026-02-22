#!/bin/bash
set -e

# Security Bundle - build Docker image
# Usage: ./build.sh [tag]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TAG="${1:-latest}"
IMAGE="telcobright/security-bundle:${TAG}"

echo "Building security bundle image: $IMAGE"
echo "Context: $SCRIPT_DIR"

cd "$SCRIPT_DIR"

docker build \
    --tag "$IMAGE" \
    --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    .

echo ""
echo "Build complete: $IMAGE"
echo "Image size: $(docker image inspect "$IMAGE" --format='{{.Size}}' | awk '{printf "%.0f MB\n", $1/1024/1024}')"
echo ""
echo "To save as tar: docker save $IMAGE | gzip > security-bundle-${TAG}.tar.gz"
