# Web Frontend — Hệ thống Thuyết minh Đa ngôn ngữ

Bảng điều khiển tối thiểu phục vụ kiểm thử, vận hành và demo các Use Case (UC1–UC9) của hệ thống microservices thuyết minh tự động đa ngôn ngữ.

---

## 1. Yêu cầu môi trường

- **Node.js**: v20+ hoặc v24+
- **pnpm** (khuyến nghị v11+) hoặc **npm**

---

## 2. Cài đặt các gói phụ thuộc

Di chuyển vào thư mục `web/` và chạy:

```bash
cd web
npm install
# hoặc nếu dùng pnpm:
pnpm install
```

---

## 3. Cấu hình biến môi trường (`.env`)

Sao chép tệp mẫu `.env.example` thành `.env`:

```bash
cp .env.example .env
```

Nội dung mặc định:
```env
# Địa chỉ của API Gateway
VITE_API_BASE_URL=http://localhost:8080
```

*Lưu ý: Trong quá trình phát triển (Dev server), Vite đã được cấu hình proxy chuyển tiếp tự động các đường dẫn `/api` và `/health` về `http://localhost:8080` để tránh hoàn toàn lỗi CORS.*

---

## 4. Khởi chạy ứng dụng

### 4.1. Chế độ phát triển (Development)

Trong thư mục `web/`, bạn có thể chạy ngay bằng script tự nạp biến môi trường:

```powershell
.\dev.ps1
```

Hoặc nếu cửa sổ PowerShell đã nạp PATH (hoặc mở cửa sổ mới):

```bash
npm run dev
```

Ứng dụng sẽ khởi chạy tại: **`http://localhost:3000`**

### 4.2. Chạy cùng API Gateway và các Backend Services ở Local

1. Đảm bảo API Gateway đang chạy tại cổng `8080` (và các service Auth, Content, Narration, Translation, TTS đang hoạt động).
   - Có thể chạy nhanh toàn bộ hệ thống bằng script gốc:
     ```powershell
     cd ..
     .\scripts\run-local.ps1 -UseH2 -Build
     ```
2. Khởi chạy Web bằng `npm run dev` trong thư mục `web/`.
3. Mở trình duyệt tại `http://localhost:3000`:
   - Truy cập `/login` và đăng nhập với tài khoản quản trị:
     - **Tên đăng nhập**: `admin`
     - **Mật khẩu**: `password123`
   - Quản lý điểm tham quan tại `/pois` (UC8).
   - Quản lý nội dung thuyết minh tại `/contents` (UC1).
   - Tạo Job thuyết minh tại `/jobs/new` (UC2).
   - Theo dõi tiến độ thời gian thực (Polling 3s) và thử hủy Job tại `/jobs/:id` (UC2/3/4).
   - Mở màn hình khách tham quan tại `/visitor` (UC5) để nghe file âm thanh thuyết minh đã tạo.
   - Kiểm tra trạng thái Gateway tại `/health` (UC6).

---

## 5. Chạy kiểm thử tự động (Testing)

Bộ test bao gồm các kiểm thử đơn vị và tích hợp cho lớp `apiClient`, đăng nhập, tạo job với `Idempotency-Key`, polling định kỳ dừng ở trạng thái kết thúc, kiểm tra quyền `ProtectedRoute` và nút Hủy job.

```bash
npm run test
# hoặc chạy kiểm tra kiểu TypeScript trước:
npx tsc --noEmit
```

---

## 6. Đóng gói sản phẩm (Production Build)

```bash
npm run build
```

Kết quả đóng gói tối ưu hóa sẽ được tạo trong thư mục `web/dist/`.
Bạn có thể xem trước bản build bằng:

```bash
npm run preview
```
