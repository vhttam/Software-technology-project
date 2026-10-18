#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

echo "[INFO] Đang dừng các container của hệ thống..."
docker compose -f docker-compose.yml -f docker-compose.full.yml --profile full down 2>/dev/null || true
docker compose down

echo "[THÀNH CÔNG] Toàn bộ dịch vụ đã được dừng an toàn (dữ liệu được bảo toàn trong volumes)."
