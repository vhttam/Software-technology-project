#!/usr/bin/env bash
set -eo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "=========================================================="
echo "    CHẠY KIỂM THỬ ĐẦU-CUỐI (E2E SMOKE TEST)"
echo "=========================================================="

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

# 1. Trích xuất thông tin đăng nhập từ THONG-TIN-DANG-NHAP.txt hoặc .env
ADMIN_USER=""
ADMIN_PASS=""

if [ -f "THONG-TIN-DANG-NHAP.txt" ]; then
    echo "Đang đọc thông tin đăng nhập từ THONG-TIN-DANG-NHAP.txt..."
    ADMIN_USER=$(grep -E '^\s*Tên đăng nhập\s*:' THONG-TIN-DANG-NHAP.txt | cut -d ':' -f2- | tr -d '\r\n ' || true)
    ADMIN_PASS=$(grep -E '^\s*Mật khẩu\s*:' THONG-TIN-DANG-NHAP.txt | cut -d ':' -f2- | tr -d '\r\n ' || true)
fi

if [ -z "$ADMIN_USER" ] && [ -f ".env" ]; then
    echo "Đang đọc thông tin đăng nhập từ .env..."
    ADMIN_USER=$(grep -E '^\s*BOOTSTRAP_ADMIN_USERNAME\s*=' .env | cut -d '=' -f2- | tr -d '\r\n ' || true)
    ADMIN_PASS=$(grep -E '^\s*BOOTSTRAP_ADMIN_PASSWORD\s*=' .env | cut -d '=' -f2- | tr -d '\r\n ' || true)
fi

if [ -z "$ADMIN_USER" ]; then
    ADMIN_USER="admin"
fi

if [ -z "$ADMIN_PASS" ]; then
    echo "[LỖI] Không tìm thấy mật khẩu admin trong THONG-TIN-DANG-NHAP.txt hoặc .env." >&2
    exit 1
fi

echo "Tài khoản kiểm thử: $ADMIN_USER"

# 2. Đăng nhập qua API Gateway
echo "=== [1/6] Đăng nhập vào API Gateway ($GATEWAY_URL/api/v1/auth/login) ==="
LOGIN_RES=$(curl -s -S -X POST "$GATEWAY_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" || true)

TOKEN=$(echo "$LOGIN_RES" | jq -r '.data.accessToken // empty' 2>/dev/null || true)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "[LỖI] Đăng nhập thất bại. Phản hồi máy chủ: $LOGIN_RES" >&2
    exit 1
fi
echo "Đăng nhập thành công. Đã nhận JWT token."

# 3. Tạo Điểm tham quan (POI) - Chú ý: Schema chuẩn chỉ gồm name và description (không có lat/long)
echo "=== [2/6] Tạo Điểm tham quan (POST /api/v1/pois) ==="
POI_RES=$(curl -s -S -X POST "$GATEWAY_URL/api/v1/pois" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Bảo tàng Lịch sử (Smoke Test)","description":"Khu trưng bày mẫu phục vụ kiểm thử luồng tự động đa ngôn ngữ."}' || true)

POI_ID=$(echo "$POI_RES" | jq -r '.data.poiId // empty' 2>/dev/null || true)
if [ -z "$POI_ID" ] || [ "$POI_ID" = "null" ]; then
    echo "[LỖI] Tạo POI thất bại. Phản hồi: $POI_RES" >&2
    exit 1
fi
echo "Đã tạo POI thành công. ID: $POI_ID"

# 4. Tạo Nội dung thuyết minh (Content)
echo "=== [3/6] Tạo Nội dung thuyết minh gốc tiếng Việt (POST /api/v1/pois/$POI_ID/contents) ==="
CONTENT_RES=$(curl -s -S -X POST "$GATEWAY_URL/api/v1/pois/$POI_ID/contents" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Thuyết minh gian khánh tiết","sourceLang":"vi","textContent":"Khu vực trưng bày những hiện vật lịch sử quý báu của dân tộc qua các thời kỳ phát triển văn hóa."}' || true)

CONTENT_ID=$(echo "$CONTENT_RES" | jq -r '.data.contentId // empty' 2>/dev/null || true)
if [ -z "$CONTENT_ID" ] || [ "$CONTENT_ID" = "null" ]; then
    echo "[LỖI] Tạo nội dung thất bại. Phản hồi: $CONTENT_RES" >&2
    exit 1
fi
echo "Đã tạo Nội dung thành công. ID: $CONTENT_ID"

# 5. Nộp tác vụ thuyết minh 6 ngôn ngữ (Job)
echo "=== [4/6] Nộp tác vụ thuyết minh 6 ngôn ngữ (POST /api/v1/jobs) ==="
IDEMPOTENCY_KEY="smoke-test-$(date +%s)-$RANDOM"
JOB_RES=$(curl -s -S -X POST "$GATEWAY_URL/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "contentId": "'"$CONTENT_ID"'",
    "targets": [
      {"lang":"vi","voiceId":"vi-VN-Standard-A"},
      {"lang":"en","voiceId":"en-US-Journey-F"},
      {"lang":"fr","voiceId":"fr-FR-Standard-A"},
      {"lang":"ja","voiceId":"ja-JP-Standard-A"},
      {"lang":"zh","voiceId":"cmn-CN-Standard-A"},
      {"lang":"ko","voiceId":"ko-KR-Standard-A"}
    ]
  }' || true)

JOB_ID=$(echo "$JOB_RES" | jq -r '.data.jobId // empty' 2>/dev/null || true)
if [ -z "$JOB_ID" ] || [ "$JOB_ID" = "null" ]; then
    echo "[LỖI] Nộp tác vụ thất bại. Phản hồi: $JOB_RES" >&2
    exit 1
fi
echo "Đã nộp Job thành công. ID: $JOB_ID (gồm 6 ngôn ngữ)"

# 6. Chờ tác vụ hoàn thành (Poll GET /api/v1/jobs/{jobId})
echo "=== [5/6] Chờ tác vụ hoàn thành (Poll GET /api/v1/jobs/$JOB_ID) ==="
JOB_DONE=0
for i in $(seq 1 60); do
    DETAIL=$(curl -s -S "$GATEWAY_URL/api/v1/jobs/$JOB_ID" -H "Authorization: Bearer $TOKEN" || true)
    STATUS=$(echo "$DETAIL" | jq -r '.data.status // empty' 2>/dev/null || true)
    echo "  Lần kiểm tra $i: Trạng thái Job = $STATUS"
    if [ "$STATUS" = "PUBLISHED" ] || [ "$STATUS" = "COMPLETED" ]; then
        JOB_DONE=1
        break
    fi
    if [ "$STATUS" = "FAILED" ] || [ "$STATUS" = "CANCELLED" ]; then
        echo "[LỖI] Tác vụ kết thúc ở trạng thái lỗi: $STATUS" >&2
        echo "Chi tiết: $DETAIL" >&2
        exit 1
    fi
    sleep 2
done

if [ "$JOB_DONE" -ne 1 ]; then
    echo "[LỖI] Tác vụ không hoàn thành trong thời gian cho phép (120s)." >&2
    echo "Trạng thái cuối: $DETAIL" >&2
    exit 1
fi
echo "Tác vụ đã hoàn tất thành công!"

# 7. Kiểm tra tải âm thanh qua API khách tham quan (/api/v1/client/pois/{poiId}/narrations/{lang})
echo "=== [6/6] Tải và kiểm tra tệp âm thanh 6 ngôn ngữ qua API khách tham quan ==="
TMP_AUDIO_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_AUDIO_DIR"' EXIT

LANGS=("vi" "en" "fr" "ja" "zh" "ko")
for l in "${LANGS[@]}"; do
    NARR_RES=$(curl -s -S "$GATEWAY_URL/api/v1/client/pois/$POI_ID/narrations/$l" || true)
    AUDIO_URL=$(echo "$NARR_RES" | jq -r '.data.audioUrl // empty' 2>/dev/null || true)
    if [ -z "$AUDIO_URL" ] || [ "$AUDIO_URL" = "null" ]; then
        echo "[LỖI] Không lấy được audioUrl cho ngôn ngữ $l. Phản hồi: $NARR_RES" >&2
        exit 1
    fi
    OUT_FILE="$TMP_AUDIO_DIR/audio_$l.mp3"
    curl -s -S -f -o "$OUT_FILE" "$AUDIO_URL"
    FILE_SIZE=$(wc -c < "$OUT_FILE" | tr -d ' ')
    echo "  [OK] Ngôn ngữ $l: Tải thành công ($FILE_SIZE bytes) -> $AUDIO_URL"
    if [ "$FILE_SIZE" -lt 500 ]; then
        echo "[LỖI] Tệp âm thanh $l quá nhỏ (< 500 bytes), nghi ngờ tệp rỗng hoặc lỗi." >&2
        exit 1
    fi
done

echo "=========================================================="
echo "    TOÀN BỘ 6 BƯỚC SMOKE TEST ĐẦU-CUỐI ĐỀU THÀNH CÔNG!"
echo "=========================================================="
exit 0
