#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

command -v docker >/dev/null 2>&1 || {
  printf 'ERROR: Required command not found: docker\n' >&2
  exit 1
}
docker info >/dev/null

printf 'Building local Docker images from the current source tree...\n'
docker build -t erp-backend:local ./erp-backend
docker build -t erp-base-frontend:jsp-local ./erp-base-frontend
docker build -t erp-remote-warehouse:local ./erp-remote-warehouse
printf 'Local Docker images built successfully.\n'
