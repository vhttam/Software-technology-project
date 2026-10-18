#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

if [ -n "$1" ]; then
  docker compose logs -f "$1"
else
  docker compose logs -f
fi
