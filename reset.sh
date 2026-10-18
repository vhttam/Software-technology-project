#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

FORCE=false
if [ "$1" = "-f" ] || [ "$1" = "--force" ]; then
  FORCE=true
fi

if [ "$FORCE" = false ]; then
  read -p "CẢNH BÁO: Thao tác này sẽ xóa toàn bộ CSDL và file âm thanh cục bộ. Bạn có chắc chắn muốn tiếp tục? (y/N) " confirm
  if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "Hủy bỏ thao tác."
    exit 0
  fi
fi

echo "[INFO] Đang dừng hệ thống và xóa sạch volumes..."
docker compose -f docker-compose.yml -f docker-compose.full.yml --profile full down -v --remove-orphans 2>/dev/null || true
docker compose down -v --remove-orphans
rm -rf .local-data

echo "[THÀNH CÔNG] Toàn bộ dữ liệu và container đã được dọn sạch hoàn toàn."
