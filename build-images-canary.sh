#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

command -v docker >/dev/null 2>&1 || {
  printf 'ERROR: Required command not found: docker\n' >&2
  exit 1
}
docker info >/dev/null

printf 'Building canary Docker images from the current source tree...\n'
docker build -t erp-backend:canary ./erp-backend
docker build -t erp-base-frontend:jsp-canary ./erp-base-frontend
printf 'Canary Docker images built successfully.\n'
