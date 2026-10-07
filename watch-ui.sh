#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_dir"

snapshot() {
  {
    find erp-base-php/src erp-base-php/templates erp-base-php/config erp-base-php/public \
      -type f -printf '%p:%T@\n'
    stat -c '%n:%Y' erp-base-php/composer.json erp-base-php/composer.lock \
      erp-base-php/Dockerfile erp-base-php/.dockerignore erp-base-php/.env.dist
    stat -c '%n:%Y' docker-compose.yml
  } | sort
}

echo "Spouštím ERP a sleduji změny v erp-base-php..."
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