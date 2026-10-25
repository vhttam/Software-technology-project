#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FULL_MODE=false
BUILD_FLAG="--build"
IS_DEMO=false
LAN_MODE=false

if [ "${DEMO_MODE,,}" = "true" ]; then
  IS_DEMO=true
fi
if [ "${LAN_MODE,,}" = "true" ]; then
  LAN_MODE=true
fi

for arg in "$@"; do
  case $arg in
    --demo|-d)
      IS_DEMO=true
      shift
      ;;
    --lan|-l)
      LAN_MODE=true
      shift
      ;;
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

DEMO_ADMIN_USER="admin"
DEMO_ADMIN_PASS="Admin12345!"

echo "=========================================================="
echo "  HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ"
if [ "$FULL_MODE" = true ]; then
  echo "  Cấu trúc: FULL (SQL Server + RabbitMQ)"
else
  echo "  Cấu trúc: LITE (H2 + RabbitMQ, tối ưu RAM)"
fi
if [ "$IS_DEMO" = true ]; then
  echo "  Chế độ:   DEMO (Mật khẩu cố định, nạp dữ liệu mẫu)"
else
  echo "  Chế độ:   TIÊU CHUẨN (Mật khẩu ngẫu nhiên an toàn)"
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

# 2. Chuẩn bị file cấu hình .env và xác định mật khẩu admin
ENV_FILE="$REPO_ROOT/.env"
ENV_EXAMPLE="$REPO_ROOT/.env.example"
ADMIN_USER="$DEMO_ADMIN_USER"
ADMIN_PASS=""

if [ ! -f "$ENV_FILE" ]; then
  echo "[INFO] Chưa tìm thấy file .env. Đang tự động tạo từ .env.example..."
  if [ -f "$ENV_EXAMPLE" ]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"

    ADMIN_PASS="$DEMO_ADMIN_PASS"
    sed -i.bak "s/BOOTSTRAP_ADMIN_PASSWORD=.*/BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASS/g" "$ENV_FILE" 2>/dev/null || sed -i "" "s/BOOTSTRAP_ADMIN_PASSWORD=.*/BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASS/g" "$ENV_FILE"
    sed -i.bak "s/DEMO_MODE=.*/DEMO_MODE=$IS_DEMO/g" "$ENV_FILE" 2>/dev/null || sed -i "" "s/DEMO_MODE=.*/DEMO_MODE=$IS_DEMO/g" "$ENV_FILE"

    RANDOM_SECRET=$(head -c 32 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 32)
    sed -i.bak "s/local-dev-signing-secret-change-me-32-characters/$RANDOM_SECRET/g" "$ENV_FILE" 2>/dev/null || sed -i "" "s/local-dev-signing-secret-change-me-32-characters/$RANDOM_SECRET/g" "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  fi
else
  echo "[INFO] Đọc cấu hình từ file .env hiện tại..."
  ADMIN_PASS=$(grep -E '^\s*BOOTSTRAP_ADMIN_PASSWORD\s*=' "$ENV_FILE" | cut -d '=' -f2- | tr -d '\r\n ' || true)
  ADMIN_USER_IN_ENV=$(grep -E '^\s*BOOTSTRAP_ADMIN_USERNAME\s*=' "$ENV_FILE" | cut -d '=' -f2- | tr -d '\r\n ' || true)
  if [ -n "$ADMIN_USER_IN_ENV" ]; then
    ADMIN_USER="$ADMIN_USER_IN_ENV"
  fi

  if [ "$IS_DEMO" = true ]; then
    if [ "$ADMIN_PASS" != "$DEMO_ADMIN_PASS" ]; then
      sed -i.bak "s/BOOTSTRAP_ADMIN_PASSWORD=.*/BOOTSTRAP_ADMIN_PASSWORD=$DEMO_ADMIN_PASS/g" "$ENV_FILE" 2>/dev/null || sed -i "" "s/BOOTSTRAP_ADMIN_PASSWORD=.*/BOOTSTRAP_ADMIN_PASSWORD=$DEMO_ADMIN_PASS/g" "$ENV_FILE"
      sed -i.bak "s/DEMO_MODE=.*/DEMO_MODE=true/g" "$ENV_FILE" 2>/dev/null || sed -i "" "s/DEMO_MODE=.*/DEMO_MODE=true/g" "$ENV_FILE"
      rm -f "$ENV_FILE.bak"
      ADMIN_PASS="$DEMO_ADMIN_PASS"
    fi
  elif [ -z "$ADMIN_PASS" ]; then
    ADMIN_PASS="$DEMO_ADMIN_PASS"
    echo "BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASS" >> "$ENV_FILE"
  fi
fi

# 3. Ghi thông tin đăng nhập ra THONG-TIN-DANG-NHAP.txt
CRED_FILE="$REPO_ROOT/THONG-TIN-DANG-NHAP.txt"
NOW_STR=$(date '+%Y-%m-%d %H:%M:%S')

if [ "$IS_DEMO" = true ]; then
  MODE_DESC="CHẾ ĐỘ DEMO (Mật khẩu cố định, nạp dữ liệu mẫu)"
  CRED_NOTE="CẢNH BÁO DEMO: Mật khẩu '$DEMO_ADMIN_PASS' chỉ dùng cho mục đích trình diễn/giảng dạy. TUYỆT ĐỐI KHÔNG dùng trên môi trường thật!"
else
  MODE_DESC="CHẾ ĐỘ TIÊU CHUẨN (Mật khẩu ngẫu nhiên an toàn)"
  CRED_NOTE="Mật khẩu admin được sinh ngẫu nhiên an toàn riêng cho phiên bản triển khai này."
fi

cat <<EOF > "$CRED_FILE"
==========================================================
THÔNG TIN ĐĂNG NHẬP HỆ THỐNG THUYẾT MINH TỰ ĐỘNG ĐA NGÔN NGỮ
==========================================================
Thời gian khởi động: $NOW_STR
Chế độ hoạt động:    $MODE_DESC

1. ĐỊA CHỈ TRUY CẬP:
   - Giao diện Quản trị Web:    http://localhost:3000
   - Cổng API Gateway:          http://localhost:8080
   - Bảng điều khiển RabbitMQ:  http://localhost:15672 (User: cnpm)

2. TÀI KHOẢN ĐĂNG NHẬP QUẢN TRỊ VIÊN:
   - Tên đăng nhập:             $ADMIN_USER
   - Mật khẩu hiện tại:         $ADMIN_PASS
   - Vai trò:                   SUPER_ADMIN (Toàn quyền quản trị)

3. LỆNH ĐIỀU HÀNH THƯỜNG DÙNG:
   - Xem nhật ký hệ thống:      ./logs.sh  (hoặc .\logs.ps1)
   - Dừng toàn bộ hệ thống:     ./stop.sh  (hoặc .\stop.ps1)
   - Khôi phục / Xóa sạch:      ./reset.sh (hoặc .\reset.ps1)

==========================================================
LƯU Ý BẢO MẬT:
- $CRED_NOTE
- File này và file .env đã được tự động loại trừ khỏi kho Git và file ZIP đóng gói.
==========================================================
EOF

# 4. Khởi động Docker Compose
cd "$REPO_ROOT"
COMPOSE_CMD="docker compose"
export DEMO_MODE=$( [ "$IS_DEMO" = true ] && echo "true" || echo "false" )
if [ -n "$CODESPACES" ] || [ "$LAN_MODE" = true ] || [ "$IS_DEMO" != true ]; then
  export BIND_IP="0.0.0.0"
else
  export BIND_IP="127.0.0.1"
fi

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
  echo "  - Giao diện Khách tham quan:  http://localhost:3000"
  echo "  - Giao diện Quản trị Web:    http://localhost:3000/admin.html"
  echo "  - Cổng API Gateway:          http://localhost:8080"
  echo "  - Bảng điều khiển RabbitMQ:  http://localhost:15672 (User: cnpm)"
  echo ""
  echo "  TÀI KHOẢN ĐĂNG NHẬP QUẢN TRỊ VIÊN (ADMIN):"
  echo "  - Tên đăng nhập:             $ADMIN_USER"
  echo "  - Mật khẩu hiện tại:         $ADMIN_PASS"
  echo "  - Vị trí file thông tin:     $CRED_FILE"
  if [ "$IS_DEMO" = true ]; then
    echo ""
    echo "  [CẢNH BÁO DEMO] Hệ thống đang chạy ở CHẾ ĐỘ DEMO với mật khẩu cố định."
    echo "  -> Chỉ dùng cho mục đích trình diễn/giảng dạy, tuyệt đối không dùng trên môi trường thật!"
    echo "  -> Đã nạp sẵn 2 điểm tham quan và 1 nội dung mẫu để trình diễn ngay trên Web."
  fi
  echo ""
  echo "Để theo dõi log:   ./logs.sh"
  echo "Để dừng hệ thống:  ./stop.sh"
else
  echo "[CẢNH BÁO] API Gateway chưa phản hồi UP sau thời gian chờ. Xem log bằng: ./logs.sh"
fi
