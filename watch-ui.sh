#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_dir"

snapshot() {
  {
    find erp-base-nextjs2/src erp-base-nextjs2/public erp-base-nextjs2/templates \
      -type f -printf '%p:%T@\n'
    stat -c '%n:%Y' erp-base-nextjs2/package.json erp-base-nextjs2/package-lock.json \
      erp-base-nextjs2/next.config.ts erp-base-nextjs2/tsconfig.json \
      erp-base-nextjs2/Dockerfile erp-base-nextjs2/.dockerignore erp-base-nextjs2/.env.example
    stat -c '%n:%Y' docker-compose.yml
  } | sort
}

echo "Spouštím ERP a sleduji změny v erp-base-nextjs2..."
docker compose up --build -d
last_snapshot="$(snapshot)"

cleanup() {
  echo
  echo "Watcher ukončen. Kontejnery zůstávají spuštěné."
}
trap cleanup EXIT INT TERM

while true; do
  sleep 1
  current_snapshot="$(snapshot)"
  if [[ "$current_snapshot" != "$last_snapshot" ]]; then
    echo "Změna UI nalezena, znovu sestavuji Docker Compose..."
    docker compose up --build -d
    last_snapshot="$current_snapshot"
  fi
done