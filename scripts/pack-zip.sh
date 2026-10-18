#!/usr/bin/env bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_PATH="${1:-$REPO_ROOT/dist/he-thong-thuyet-minh.zip}"
OUTPUT_DIR="$(dirname "$OUTPUT_PATH")"

mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_PATH"

echo "=========================================================="
echo "  ĐÓNG GÓI DỰ ÁN THUYẾT MINH ĐA NGÔN NGỮ (ZIP)"
echo "=========================================================="
echo "[INFO] Thư mục nguồn: $REPO_ROOT"
echo "[INFO] File nén đích: $OUTPUT_PATH"

cd "$REPO_ROOT"

if command -v zip &> /dev/null; then
  zip -r "$OUTPUT_PATH" . \
    -x "*.git*" \
    -x "*.idea*" \
    -x "*.vscode*" \
    -x "*node_modules*" \
    -x "*target*" \
    -x "*dist*" \
    -x "*.local-data*" \
    -x "*.log" \
    -x ".env" \
    -x "*.pk8" \
    -x "*.key" \
    -x "*.pem" \
    -x "PROJECT_OVERVIEW.md"
else
  echo "[LỖI] Không tìm thấy lệnh 'zip'. Vui lòng cài đặt zip và thử lại."
  exit 1
fi

echo ""
echo "=========================================================="
echo "  ĐÓNG GÓI THÀNH CÔNG!"
echo "=========================================================="
echo "  - Đường dẫn: $OUTPUT_PATH"
if command -v sha256sum &> /dev/null; then
  echo "  - SHA256:    $(sha256sum "$OUTPUT_PATH" | cut -d' ' -f1)"
fi
