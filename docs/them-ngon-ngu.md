# Hướng Dẫn Thêm Ngôn Ngữ Mới (Zero Code Change)

Tài liệu này hướng dẫn quy trình vận hành chuẩn để thêm hoặc bật một ngôn ngữ thuyết minh mới vào hệ thống mà **hoàn toàn không cần sửa mã nguồn (zero code changes)**, chỉ thông qua tệp cấu hình trung tâm duy nhất `config/languages.json`.

---

## 1. Kiến Trúc Một Nguồn Cấu Hình Duy Nhất (Single Source of Truth)

Tất cả các dịch vụ trong hệ thống (Content Service, TTS Service, Narration Service, Background Worker và Giao diện Quản trị Web) đều đọc và chia sẻ chung một cấu hình ngôn ngữ:

```
                          ┌───────────────────────────┐
                          │   config/languages.json   │
                          │(Nguồn cấu hình duy nhất)  │
                          └─────────────┬─────────────┘
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           ▼                            ▼                            ▼
┌────────────────────┐       ┌────────────────────┐       ┌────────────────────┐
│  content-service   │       │    tts-service     │       │   LocalJobWorker   │
│  (Cung cấp API     │       │(Cung cấp danh mục  │       │(Xử lý dịch thuật   │
│   cấu hình web)    │       │ giọng đọc & TTS)   │       │ & tổng hợp âm thanh│
└──────────┬─────────┘       └────────────────────┘       └────────────────────┘
           │
           │ GET /api/v1/languages/config
           ▼
┌────────────────────┐
│   Web Admin UI     │
│ (Tự động thích ứng,│
│  hỗ trợ LTR / RTL) │
└────────────────────┘
```

### Cách các service trong Docker đọc chung tệp cấu hình
Trong môi trường container hóa (Docker Compose), mỗi container có context build riêng biệt. Để đảm bảo không nhân bản tệp cấu hình và cập nhật tức thì khi chỉnh sửa, tệp cấu hình được gắn kết (bind mount) ở chế độ **chỉ đọc (read-only)** qua `docker-compose.yml`:

```yaml
services:
  content-service:
    volumes:
      - ./config/languages.json:/app/config/languages.json:ro
    environment:
      - LANGUAGE_CONFIG_PATH=/app/config/languages.json

  tts-service:
    volumes:
      - ./config/languages.json:/app/config/languages.json:ro
    environment:
      - LANGUAGE_CONFIG_PATH=/app/config/languages.json

  narration-service:
    volumes:
      - ./config/languages.json:/app/config/languages.json:ro
    environment:
      - LANGUAGE_CONFIG_PATH=/app/config/languages.json
```

---

## 2. Cấu Trúc Khai Báo Ngôn Ngữ Trong `config/languages.json`

Mỗi ngôn ngữ là một đối tượng JSON với đầy đủ các thuộc tính quy định cả hành vi xử lý kỹ thuật và hiển thị giao diện:

| Thuộc tính | Kiểu | Ý nghĩa & Quy chuẩn |
| :--- | :--- | :--- |
| `code` | Chuỗi | Mã ngôn ngữ chuẩn ISO 639-1 (ví dụ: `vi`, `en`, `es`, `th`, `ar`). Dùng làm khóa chính. |
| `nameVi` | Chuỗi | Tên hiển thị bằng tiếng Việt thân thiện với người vận hành (ví dụ: `Tiếng Tây Ban Nha`). |
| `nativeName` | Chuỗi | Tên bản ngữ nguyên bản (ví dụ: `Español`, `ไทย`, `العربية`). |
| `translationLocale`| Chuỗi | Mã locale gửi tới nhà cung cấp dịch thuật (ví dụ: `es`, `zh-CN`, `th`). |
| `ttsLocale` | Chuỗi | Mã locale gửi tới nhà cung cấp giọng đọc TTS (ví dụ: `es-ES`, `th-TH`, `ar-XA`). |
| `direction` | Chuỗi | Hướng chữ viết: `ltr` (trái sang phải) hoặc `rtl` (phải sang trái). |
| `fontStack` | Chuỗi | Bộ font chữ CSS ưu tiên hiển thị tối ưu cho bảng chữ cái đó. |
| `sentenceDelimiters`| Chuỗi (Regex) | Bộ dấu ngắt câu để phân đoạn văn bản trước khi đọc (ví dụ: `[.!?;\n]`). |
| `clauseDelimiters` | Chuỗi (Regex) | Bộ dấu ngắt mệnh đề phụ khi câu quá dài (ví dụ: `[,]`, `[\\s]`). |
| `minReadingSpeed` | Số thực | Ngưỡng tốc độ đọc tối thiểu (ký tự/giây) dùng để kiểm định âm thanh. |
| `maxReadingSpeed` | Số thực | Ngưỡng tốc độ đọc tối đa (ký tự/giây) dùng để phát hiện lỗi âm thanh đọc quá nhanh/mất chữ. |
| `maxTtsChunkChars` | Số nguyên | Giới hạn số ký tự tối đa cho mỗi đoạn gửi tới TTS provider (chuẩn: 80). |
| `maxTtsChunkBytes` | Số nguyên | Giới hạn byte tối đa cho mỗi đoạn gửi tới TTS (chuẩn: 120). |
| `sortOrder` | Số nguyên | Thứ tự ưu tiên hiển thị trên giao diện quản trị. |
| `enabled` | Boolean | `true` để bật cho phép người dùng chọn; `false` để tạm tắt/ẩn hoàn toàn khỏi giao diện người dùng. |
| `note` | Chuỗi | Ghi chú vận hành kỹ thuật nội bộ (ví dụ: yêu cầu xử lý tách từ, RTL), tuyệt đối không hiển thị trên giao diện. |
| `voices` | Mảng | Danh sách giọng đọc khả dụng, gồm `voiceId`, `gender` (MALE/FEMALE), `type`, `label`, `isDefault`. |

---

## 3. Checklist Từng Bước Thêm Ngôn Ngữ Mới

### Bước 1: Khai báo cấu hình vào `config/languages.json`
Mở `config/languages.json`, thêm khối khai báo ngôn ngữ hoặc sửa `enabled: true`.

### Bước 2: Chạy kiểm định tự động nhà cung cấp (Automated Provider Verification)
Chạy script kiểm tra để xác nhận nhà cung cấp dịch thuật và TTS phản hồi đạt tiêu chuẩn:

```bash
# Kiểm tra riêng ngôn ngữ mới thêm (ví dụ: tiếng Tây Ban Nha es)
node scripts/check-languages.js --lang=es

# Hoặc kiểm tra qua npm script trong thư mục web
pnpm --dir web languages:check
```

Script sẽ tự động:
1. Gửi văn bản mẫu qua Translation Provider và kiểm tra độ chính xác bản dịch.
2. Tải đoạn âm thanh phát âm mẫu từ TTS Provider.
3. Đo thời lượng thực tế và kiểm tra vận tốc đọc có nằm trong ngưỡng `[minReadingSpeed, maxReadingSpeed]` không.
4. Quét mảng byte để đo tỷ lệ im lặng (phải nhỏ hơn 40%).
5. Phân biệt:
   - **Lỗi không hỗ trợ (Hard Failure)**: Provider từ chối mã locale. Cần đặt `enabled: false` kèm ghi chú lý do.
   - **Lỗi mạng tạm thời (Network Warning)**: Mất kết nối internet hoặc timeout. **Không tắt ngôn ngữ**, chỉ thông báo thử lại.

### Bước 3: Khởi động lại dịch vụ hoặc kích hoạt nạp lại
- Chế độ Local: Khởi động lại lệnh `scripts/run-local.ps1`.
- Chế độ Docker: `docker compose restart`.
- Backend tự động nạp cấu hình mới nhất ngay khi khởi động.

### Bước 4: Kiểm tra trên giao diện quản trị (Web Admin)
- Vào mục **Tạo tác vụ thuyết minh**: Ngôn ngữ mới xuất hiện với tên tiếng Việt và tên bản ngữ, kèm danh sách giọng đọc mặc định.
- Nếu ngôn ngữ có `enabled: false`: Giao diện hiển thị biểu tượng khóa 🔒 kèm ghi chú, không cho phép chọn.
- Mở modal **Nghe thử âm thanh**: Ngôn ngữ mới tự động áp dụng đúng `fontStack` và thuộc tính hướng chữ `dir="rtl"` (nếu là chữ Ả Rập).

### Bước 5: Thực hiện "Dịch bổ sung hàng loạt" (Batch Translation)
Khi một ngôn ngữ mới được kích hoạt, các nội dung tham quan đã tạo trước đó chưa có bản thuyết minh bằng ngôn ngữ này. Người vận hành chỉ cần:
1. Vào trang **Nội dung thuyết minh**.
2. Bấm nút **Dịch bổ sung hàng loạt**.
3. Chọn ngôn ngữ đích mới và giọng đọc mong muốn.
4. Bấm **Bắt đầu dịch hàng loạt**:
   - Hệ thống tự động lọc ra các nội dung chưa có bản thuyết minh ở phiên bản hiện tại.
   - Bỏ qua các nội dung đã có hoặc đang có tác vụ xử lý dở dang (chống tạo trùng lặp).
   - Tiến trình chạy hoàn toàn trên server nền: người vận hành có thể đóng trình duyệt mà không ảnh hưởng tiến trình.

---

## 4. Các Ví Dụ Cụ Thể

### Ví dụ 1: Tiếng Tây Ban Nha (`es`) — Chữ Latin tiêu chuẩn

```json
{
  "code": "es",
  "nameVi": "Tiếng Tây Ban Nha",
  "nativeName": "Español",
  "translationLocale": "es",
  "ttsLocale": "es-ES",
  "direction": "ltr",
  "fontStack": "'Times New Roman', Times, serif",
  "sentenceDelimiters": "[.!?;\n]",
  "clauseDelimiters": "[,]",
  "minReadingSpeed": 12.0,
  "maxReadingSpeed": 22.0,
  "maxTtsChunkChars": 80,
  "maxTtsChunkBytes": 120,
  "sortOrder": 7,
  "enabled": true,
  "note": "Ngôn ngữ phổ biến tại Châu Âu và Châu Mỹ La-tinh",
  "voices": [
    {
      "voiceId": "es-ES-Standard-A",
      "gender": "FEMALE",
      "type": "Standard",
      "label": "Giọng nữ (khuyên dùng)",
      "isDefault": true
    },
    {
      "voiceId": "es-ES-Standard-B",
      "gender": "MALE",
      "type": "Standard",
      "label": "Giọng nam",
      "isDefault": false
    }
  ]
}
```

### Ví dụ 2: Tiếng Thái (`th`) — Chữ không phân cách từ bằng dấu cách

Đặc điểm: Tiếng Thái không có khoảng trắng giữa các từ trong câu. Dấu câu ngắt câu thường là xuống dòng hoặc khoảng trắng dài.

```json
{
  "code": "th",
  "nameVi": "Tiếng Thái",
  "nativeName": "ไทย",
  "translationLocale": "th",
  "ttsLocale": "th-TH",
  "direction": "ltr",
  "fontStack": "'Sarabun', 'Leelawadee UI', 'Noto Sans Thai', sans-serif",
  "sentenceDelimiters": "[\n]",
  "clauseDelimiters": "[\\s]",
  "minReadingSpeed": 6.0,
  "maxReadingSpeed": 14.0,
  "maxTtsChunkChars": 80,
  "maxTtsChunkBytes": 120,
  "sortOrder": 8,
  "enabled": false,
  "note": "Chưa kiểm tra (cần xử lý tách từ/word segmentation)",
  "voices": [
    {
      "voiceId": "th-TH-Standard-A",
      "gender": "FEMALE",
      "type": "Standard",
      "label": "Giọng nữ (khuyên dùng)",
      "isDefault": true
    }
  ]
}
```

### Ví dụ 3: Tiếng Ả Rập (`ar`) — Hướng chữ từ phải sang trái (RTL)

Đặc điểm: Hướng hiển thị `direction: "rtl"`, dấu câu Ả Rập đặc thù như dấu hỏi ngược `؟` và dấu chấm phẩy ngược `؛`.

```json
{
  "code": "ar",
  "nameVi": "Tiếng Ả Rập",
  "nativeName": "العربية",
  "translationLocale": "ar",
  "ttsLocale": "ar-XA",
  "direction": "rtl",
  "fontStack": "'Amiri', 'Traditional Arabic', 'Segoe UI', Tahoma, sans-serif",
  "sentenceDelimiters": "[.!?;\n؟؛]",
  "clauseDelimiters": "[,،]",
  "minReadingSpeed": 8.0,
  "maxReadingSpeed": 16.0,
  "maxTtsChunkChars": 80,
  "maxTtsChunkBytes": 120,
  "sortOrder": 10,
  "enabled": false,
  "note": "Chưa kiểm tra (cần xử lý hướng chữ RTL và dấu câu Ả Rập)",
  "voices": [
    {
      "voiceId": "ar-XA-Standard-A",
      "gender": "FEMALE",
      "type": "Standard",
      "label": "Giọng nữ (khuyên dùng)",
      "isDefault": true
    },
    {
      "voiceId": "ar-XA-Standard-B",
      "gender": "MALE",
      "type": "Standard",
      "label": "Giọng nam",
      "isDefault": false
    }
  ]
}
```
