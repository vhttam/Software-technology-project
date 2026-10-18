#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FULL_MODE=false
BUILD_FLAG=""

for arg in "$@"; do
  case $arg in
    --full|-f)
      FULL_MODE=true
      shift
      ;;
    --build|-b)
      BUILD_FLAG="--build"
      shift
      ;;
  esac
done

echo "=========================================================="
echo "  HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ"
if [ "$FULL_MODE" = true ]; then
  echo "  Chế độ: FULL (SQL Server + RabbitMQ)"
else
  echo "  Chế độ: LITE (H2 + RabbitMQ, tối ưu RAM)"
fi
echo "=========================================================="

# 1. Kiểm tra Docker CLI và Daemon
if ! command -v docker &> /dev/null; then
  echo "[LỖI] Không tìm thấy lệnh 'docker'. Vui lòng cài đặt Docker và đảm bảo có trong PATH."
  exit 1
fi

if ! docker info &> /dev/null; then
  echo "[LỖI] Docker daemon chưa được khởi động hoặc chưa sẵn sàng."
  echo "-> Vui lòng mở ứng dụng Docker Desktop / dịch vụ docker và thử lại."
  exit 1
fi

# 2. Chuẩn bị file cấu hình .env
ENV_FILE="$REPO_ROOT/.env"
ENV_EXAMPLE="$REPO_ROOT/.env.example"
if [ ! -f "$ENV_FILE" ]; then
  echo "[INFO] Chưa tìm thấy file .env. Đang tự động tạo từ .env.example..."
  if [ -f "$ENV_EXAMPLE" ]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    RANDOM_SECRET=$(head -c 32 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 32)
    sed -i.bak "s/local-dev-signing-secret-change-me-32-characters/$RANDOM_SECRET/g" "$ENV_FILE" 2>/dev/null || sed -i "" "s/local-dev-signing-secret-change-me-32-characters/$RANDOM_SECRET/g" "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  fi
fi

# 3. Khởi động Docker Compose
cd "$REPO_ROOT"
COMPOSE_CMD="docker compose"

if [ "$FULL_MODE" = true ]; then
  $COMPOSE_CMD -f docker-compose.yml -f docker-compose.full.yml --profile full up -d $BUILD_FLAG
else
  $COMPOSE_CMD up -d $BUILD_FLAG
fi

echo "[INFO] Đang kiểm tra trạng thái hoạt động của hệ thống (liveness/readiness)..."
READY=false
for i in $(seq 1 90); do
  printf "."
  sleep 2
  STATUS=$(curl -s -f http://localhost:8080/health 2>/dev/null || true)
  if echo "$STATUS" | grep -q '"status":"UP"'; then
    READY=true
    break
  fi
done

echo ""
if [ "$READY" = true ]; then
  echo "=========================================================="
  echo "  HỆ THỐNG ĐÃ SẴN SÀNG HOẠT ĐỘNG!"
  echo "=========================================================="
  echo "  - Giao diện Quản trị Web:    http://localhost:3000"
  echo "  - Cổng API Gateway:          http://localhost:8080"
  echo "  - Bảng điều khiển RabbitMQ:  http://localhost:15672 (User: cnpm)"
  echo "  - Tài khoản đăng nhập mẫu:   admin (Mật khẩu được lưu trong file .env)"
  echo ""
  echo "Để theo dõi log:   ./logs.sh"
  echo "Để dừng hệ thống:  ./stop.sh"
else
  echo "[CẢNH BÁO] API Gateway chưa phản hồi UP sau thời gian chờ. Xem log bằng: ./logs.sh"
fi
