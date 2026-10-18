# GHI CHÚ GIAO DIỆN (UI NOTES) — CỔNG QUẢN TRỊ NỘI DUNG

Tài liệu này đặc tả các nguyên tắc thiết kế, quy chuẩn kiểu chữ (typography), bảng nhãn danh mục và quy ước xử lý lỗi trên toàn bộ ứng dụng web quản trị.

---

## 1. Quy chuẩn Kiểu chữ (Typography)

- **Phông chữ duy nhất**: `Times New Roman` (kèm dự phòng `Times, serif`).
- **Quy tắc bắt buộc**:
  - `body`: `font-family: "Times New Roman", Times, serif; font-size: 15px; line-height: 1.5;`
  - Form controls (`input`, `select`, `textarea`, `button`): Ép buộc kế thừa phông chữ và kích thước thông qua CSS:
    ```css
    input, select, textarea, button {
      font-family: inherit;
      font-size: inherit;
      color: inherit;
    }
    ```
  - **Tuyệt đối không sử dụng**: Google Fonts, `@font-face`, phông chữ trang trí bên ngoài, hay các kiểu biến thể đặc biệt (`letter-spacing` bất thường, `text-transform: uppercase` toàn bộ).
  - Ngôn ngữ giao diện: Tiếng Việt có dấu đầy đủ, chuẩn chính tả UTF-8.

---

## 2. Bố cục Quản trị Chuẩn (Standard Admin Layout)

- **Thanh bên trái (Sidebar Navigation)**:
  - Cố định bên trái (`width: 250px`), nền màu tối trung tính (`#1e293b`), chữ sáng rõ.
  - Phân nhóm chức năng logic:
    - **DANH MỤC CHÍNH**:
      - `Tổng quan` (`/`)
      - `Điểm tham quan` (`/pois`)
      - `Nội dung thuyết minh` (`/contents`)
    - **TÁC VỤ XỬ LÝ**:
      - `Tạo tác vụ mới` (`/jobs/new`)
      - `Lịch sử tác vụ` (`/jobs`)
    - **HỆ THỐNG**:
      - `Quản trị tài khoản` (`/accounts`) — hiển thị cho vai trò `CONTENT_ADMIN`
      - `Trạng thái hệ thống` (`/health`)
- **Thanh trên (Topbar)**:
  - Cao 56px, nền trắng viền xám nhẹ (`#e2e8f0`).
  - Hiển thị tên người dùng đang đăng nhập, vai trò hiển thị thân thiện ("Quản trị viên nội dung"), và nút **Đăng xuất** (kích hoạt `POST /api/v1/auth/logout` và chuyển hướng về `/login`).
- **Vùng nội dung (Main Content)**:
  - Độ rộng tối đa `1200px`, căn giữa, cuộn dọc độc lập.
  - Mỗi màn hình đều có tiêu đề lớn (`h1`), lời giải thích ngắn gọn, và nút làm mới dữ liệu.

---

## 3. Bảng Ánh xạ Nhãn Giao diện (Không dùng mã UC kỹ thuật)

| Đường dẫn (Route) | Nhãn hiển thị trên Menu / Header | Mã Use Case nghiệp vụ liên kết | Mục đích |
|---|---|---|---|
| `/login` | Đăng nhập hệ thống | UC7 | Xác thực người dùng, cấp access token và refresh token |
| `/` | Tổng quan | Tổng hợp từ các API | Báo cáo số liệu thực tế (tổng điểm, nội dung, tài khoản, trạng thái Gateway) |
| `/pois` | Điểm tham quan | UC8 | Quản lý danh sách điểm di tích, tạo, sửa, xóa, hiển thị mã QR |
| `/contents` | Nội dung thuyết minh | UC1 | Quản lý văn bản gốc, kiểm soát phiên bản (optimistic locking), xóa mềm |
| `/jobs/new` | Tạo tác vụ mới | UC2 | Chọn nội dung active, chọn giọng đọc, sinh `Idempotency-Key` |
| `/jobs/:id` | Chi tiết tiến độ tác vụ | UC2 (+ UC3, UC4) | Polling 3 giây, theo dõi trạng thái từng ngôn ngữ, phát audio, hủy tác vụ |
| `/jobs` | Lịch sử tác vụ | Giới hạn theo phiên | Liệt kê các `jobId` đã khởi tạo trong `sessionStorage` (ghi chú rõ giới hạn API) |
| `/accounts` | Quản trị tài khoản | UC9 | Danh sách tài khoản, tạo mới, đặt lại mật khẩu, mở khóa tài khoản |
| `/health` | Trạng thái hệ thống | UC6 (Health check) | Kiểm tra hoạt động độc lập của API Gateway qua `GET /health` |

---

## 4. Quy ước Xử lý Thông báo Lỗi

- Mọi lỗi từ API đều được bóc tách từ ApiResponse Envelope (`status: error`):
  - Hiển thị mã lỗi `[errorCode]` chuẩn của hợp đồng.
  - Thông báo tiếng Việt rõ ràng.
  - Luôn kèm mã định danh theo vết `correlationId` để hỗ trợ đối soát nhật ký hệ thống.
- **Trường hợp Đăng nhập thất bại (HTTP 401)**:
  - Do hợp đồng bảo mật không phân biệt giữa "sai thông tin" và "tài khoản bị khóa", thông báo lỗi hiển thị chính xác:
    > *"Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đang bị khóa tạm thời (khóa 15 phút sau 5 lần nhập sai)."*
- **Các trường hợp lỗi khác**:
  - `409 ACTIVE_JOB_EXISTS`: Thông báo nội dung đang có tiến trình xử lý, cung cấp liên kết xem trực tiếp tác vụ đang chạy.
  - `409 CONTENT_VERSION_CHANGED`: Thông báo nội dung đã được cập nhật phiên bản mới, yêu cầu tải lại để xác nhận `baseVersion`.
  - `409 INVALID_RETRY_REFERENCE`: Thông báo tác vụ tham chiếu không hợp lệ hoặc đã thành công.
  - `429 RATE_LIMITED`: Thông báo vượt quá tần suất yêu cầu, vui lòng đợi giây lát.
  - `503 AUTH_SERVICE_UNAVAILABLE`: Thông báo dịch vụ xác thực tạm thời không phản hồi.
  - `0 NETWORK_ERROR`: Thông báo mất kết nối mạng hoặc cổng API Gateway chưa được khởi động.

---

## 5. Tính năng Nghe thử Thuyết minh Đa ngôn ngữ (Audio Preview)

- **Vị trí tích hợp**:
  - Tại bảng danh sách **Điểm tham quan** (`/pois`): Mỗi hàng POI cung cấp nút hành động `▶ Nghe thử`.
  - Tại trang **Chi tiết tác vụ** (`/jobs/:id`): Khi một mục tiêu ngôn ngữ đạt trạng thái `PUBLISHED`, xuất hiện nút `▶ Nghe thử`.
- **Luồng dữ liệu**:
  1. Khi mở cửa sổ pop-up (Modal), client gọi `GET /api/v1/client/pois/{poiId}/narrations` để lấy danh sách các ngôn ngữ đã xuất bản (`PUBLISHED`).
  2. Nếu điểm tham quan chưa có bản thuyết minh nào được xuất bản, modal hiển thị thông báo trạng thái rỗng thân thiện, hướng dẫn tạo tác vụ mới.
  3. Khi người dùng chọn ngôn ngữ qua các tab, client gọi `GET /api/v1/client/pois/{poiId}/narrations/{lang}` để nhận:
     - `subtitle`: Phụ đề văn bản đã dịch.
     - `audioUrl`: Đường dẫn âm thanh MP3 có kèm chữ ký xác thực (`expires`, `signature`).
     - `audioUrlExpiresAt`: Thời điểm hết hạn của chữ ký.
  4. Trình phát HTML5 `<audio controls autoPlay src={audioUrl}>` phát tệp MP3 nhị phân trực tiếp từ dịch vụ lưu trữ media.
- **Tuân thủ quy chuẩn thiết kế**: 100% Times New Roman, hiển thị hộp phụ đề viền tinh gọn, thẻ phát âm thanh tương thích mọi trình duyệt hiện đại.
