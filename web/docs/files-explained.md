# Giải thích các file và thư mục mã nguồn (`web/`)

Tài liệu này giải thích lý do tồn tại, công dụng và các lưu ý kỹ thuật quan trọng của từng tệp trong thư mục `web/` sau khi tinh gọn theo đúng phạm vi: **Chỉ làm Cổng Quản trị (Admin Dashboard)** và chuẩn hóa phông chữ **Times New Roman**.

---

## 1. Cấu trúc tổng quan

| Đường dẫn tệp / thư mục | Lý do tồn tại & Công dụng | Lưu ý kỹ thuật |
|---|---|---|
| `src/api/types.ts` | Khai báo các kiểu dữ liệu TypeScript ánh xạ 1:1 theo tài liệu **API & Event Contracts V1.7**. | Tuyệt đối không tự bịa thêm trường hoặc trạng thái. Trạng thái Job và Target tuân thủ đúng state machine. Đã loại bỏ các kiểu công khai của khách tham quan. |
| `src/api/apiClient.ts` | Lớp HTTP client duy nhất của ứng dụng để gọi API Gateway. Tự động bóc tách envelope `{status: "success", data}`, ném lỗi `ApiError` có kiểu, quản lý token trong bộ nhớ (kèm backup `sessionStorage`) và tự động thử refresh một lần khi gặp HTTP 401. | Chỉ gửi `Authorization: Bearer <JWT>` và `Idempotency-Key` (khi cần). Không tự gửi các header nội bộ `X-User-*`, `X-Correlation-ID`, `X-Service-Token`. |
| `src/api/authApi.ts` | Chứa các hàm gọi REST endpoint của **Auth Service** (`/api/v1/auth/login`, `/refresh`, `/logout`, `/users`, `/users/{id}/password`, `/users/{id}/unlock`). | Login và Refresh không yêu cầu gửi access token (`auth: false`). Logout gửi `refreshToken` để thu hồi. |
| `src/api/contentApi.ts` | Chứa các hàm gọi REST endpoint của **Content Service** (`/api/v1/pois`, `/contents`, `/languages/source`). | Hỗ trợ tìm kiếm, lọc theo POI, kiểm tra khóa lạc quan `baseVersion` và xóa mềm (soft delete). |
| `src/api/jobsApi.ts` | Chứa các hàm gọi REST endpoint của **Narration Service** (`/api/v1/voices`, `/jobs`, `/jobs/{id}`, `/jobs/{id}/cancel`) và tiện ích lưu trữ lịch sử `jobId` trong `sessionStorage`. | Yêu cầu tạo job bắt buộc phải gửi header `Idempotency-Key`. Khi retry do lỗi mạng thì giữ nguyên key, khi tạo job mới thì sinh UUID mới. |
| `src/api/healthApi.ts` | Gọi endpoint kiểm tra hoạt động độc lập `GET /health` của Gateway. | Trả về `{ status: "UP" }` mà không phụ thuộc vào Database hay RabbitMQ. |
| `src/auth/AuthContext.tsx` | React Context quản lý phiên đăng nhập, thông tin người dùng và cung cấp các hàm `login`, `logout`. Lắng nghe sự kiện lỗi 401 unrecoverable từ `apiClient` để xóa phiên. | Token được lưu trong bộ nhớ ứng dụng và đồng bộ nhẹ vào `sessionStorage` để giữ phiên qua F5/reload. |
| `src/auth/ProtectedRoute.tsx` | Component bảo vệ tuyến đường (route guard). Tự động chuyển hướng về `/login` nếu chưa đăng nhập, hoặc hiển thị lỗi `403 FORBIDDEN` nếu không đủ vai trò `CONTENT_ADMIN`. | Luôn truyền `location.state.from` để sau khi đăng nhập có thể quay lại đúng trang người dùng muốn vào. |
| `src/components/Layout.tsx` | Khung giao diện quản trị chuẩn (Thanh bên trái - Sidebar Menu, Thanh trên - Topbar hiển thị người dùng và Đăng xuất, vùng nội dung `<Outlet />`). | Toàn bộ nhãn bằng tiếng Việt tự nhiên, không chứa nhãn mã kỹ thuật "UC" hay "Liveness". |
| `src/components/ErrorBanner.tsx` | Component hiển thị lỗi chuẩn hóa. Luôn hiển thị `errorCode` (UPPER_SNAKE_CASE), `message`, `correlationId` (để tra log phân tán), và chi tiết `details` (nếu có). | Không phân nhánh giao diện theo chuỗi thông điệp `message` mà dựa trên `errorCode`. |
| `src/components/AudioPreviewModal.tsx` | Hộp thoại (Modal) pop-up nghe thử thuyết minh đa ngôn ngữ. Tải danh sách ngôn ngữ qua `GET /api/v1/client/pois/{poiId}/narrations`, tải chi tiết phụ đề và signed `audioUrl` qua `GET /api/v1/client/pois/{poiId}/narrations/{lang}`, và nhúng trình phát HTML5 `<audio controls>`. | Cho phép nghe thử thuyết minh ngay trên trang Quản lý điểm tham quan (`/pois`) và trang Chi tiết tác vụ (`/jobs/:id`). |
| `src/components/StatusBadge.tsx` | Hiển thị huy hiệu trạng thái cho Job (`PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`) và Target (`PENDING`, `TRANSLATING`, `SYNTHESIZING`, `PUBLISHED`, `FAILED`, `CANCELLED`). | Màu sắc phân biệt rõ trạng thái đang chạy, thành công và thất bại. |
| `src/components/Spinner.tsx` | Component hiển thị trạng thái đang tải dữ liệu. | Phục vụ yêu cầu luôn có trạng thái Loading cho mọi màn hình. |
| `src/hooks/usePolling.ts` | Custom hook thực hiện polling định kỳ 3 giây (`GET /api/v1/jobs/{id}`). Tự động dừng khi đạt trạng thái kết thúc (terminal state), dừng khi rời màn hình (unmount), và có giới hạn thời gian tối đa (3 phút). | Tuân thủ nghiêm ngặt quy tắc không dùng SSE qua URL token. |
| `src/pages/LoginPage.tsx` | Màn hình đăng nhập quản trị (UC7). | Hiển thị thông báo rõ ràng cho mã 401 `INVALID_CREDENTIALS`: "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đang bị khóa tạm thời (khóa 15 phút sau 5 lần nhập sai)." kèm `correlationId`. |
| `src/pages/OverviewPage.tsx` | Màn hình Tổng quan hệ thống. | Thu thập số liệu thống kê thực tế từ các API (`contentApi.listPois`, `contentApi.listContents`, `authApi.listUsers`, `healthApi.checkHealth`) mà không vẽ biểu đồ giả. Cung cấp các thẻ điều hướng nhanh đến các khu vực quản trị. |
| `src/pages/PoisPage.tsx` | Màn hình quản lý điểm tham quan (UC8). | Cho phép tìm kiếm, phân trang, thêm, sửa, xóa điểm tham quan (kèm hộp thoại xác nhận) và hiển thị mã QR. |
| `src/pages/ContentsPage.tsx` | Màn hình quản lý nội dung thuyết minh (UC1). | Hỗ trợ lọc nội dung theo POI, quản lý phiên bản bất biến (`baseVersion`), và nút chuyển nhanh sang tạo tác vụ cho nội dung đang hoạt động. |
| `src/pages/CreateJobPage.tsx` | Màn hình tạo tác vụ thuyết minh đa ngôn ngữ (UC2). | Cho phép chọn nội dung từ danh sách dropdown (không bắt nhập UUID), chọn ngôn ngữ và voice từ danh mục, tự sinh và duy trì `Idempotency-Key` khi thử lại. |
| `src/pages/JobDetailPage.tsx` | Màn hình chi tiết tiến độ tác vụ (UC2, UC3, UC4). | Hiển thị trạng thái tổng hợp và trạng thái từng target theo thời gian thực (polling 3s). Nút Hủy chỉ bật khi trạng thái là `PENDING` hoặc `PROCESSING`. Hỗ trợ nghe audio khi hoàn tất. |
| `src/pages/JobHistoryPage.tsx` | Màn hình lịch sử các tác vụ gần đây (UC2). | Do hợp đồng không có `GET /api/v1/jobs`, màn này hiển thị danh sách các `jobId` đã tạo trong phiên làm việc hiện tại (`sessionStorage`) kèm ghi chú rõ ràng về giới hạn của API hợp đồng. |
| `src/pages/AccountsPage.tsx` | Màn hình quản lý tài khoản nội bộ (UC9). | Dành riêng cho `CONTENT_ADMIN`. Cho phép tạo tài khoản mới, đặt lại mật khẩu và mở khóa tài khoản sau khi bị khóa do đăng nhập sai 5 lần (`ACCOUNT_NOT_LOCKED`, `USER_NOT_FOUND`). |
| `src/pages/HealthPage.tsx` | Màn hình giám sát trạng thái hệ thống (UC6). | Gọi `GET /health` của Gateway và hiển thị trực quan trạng thái UP/DOWN độc lập. |
| `src/App.tsx` | Cấu hình cây định tuyến (React Router v6) và bao bọc `AuthProvider`. | Đặt `/` là trang Tổng quan (bảo vệ bởi `ProtectedRoute`). Tuyến đường công khai duy nhất là `/login`. |
| `src/main.tsx` | Điểm khởi đầu của ứng dụng React DOM. | Render `App` vào `#root` và nạp stylesheet `style.css`. |
| `src/style.css` | Tệp định dạng CSS thuần tuân thủ 100% phông chữ `Times New Roman` cho `body` và ép kế thừa cho mọi `input`, `select`, `textarea`, `button`. | Thiết kế chuẩn quản trị với Sidebar trái và Topbar, loại bỏ mọi hình minh họa trang trí, sun/arch và phông chữ ngoài. |
| `docs/ui-notes.md` | Tài liệu đặc tả kiểu chữ, bố cục admin, bảng nhãn menu và quy ước thông báo lỗi. | Hướng dẫn vận hành và tra cứu quy chuẩn thiết kế. |
| `docs/traceability-web.md` | Bảng ma trận đối soát giữa các Use Case (UC) và các file mã nguồn giao diện. | Minh chứng truy vết đầy đủ giữa yêu cầu nghiệp vụ và mã thực thi. |

---

## 2. Giải thích vì sao chọn các thư viện phụ thuộc

1. **`react` & `react-dom` (v18.3.1)**: Thư viện nền tảng theo yêu cầu đề tài.
2. **`react-router-dom` (v6.28.0)**: Thư viện điều hướng trang chuẩn xác, hỗ trợ Nested Routes (`<Outlet />`), Route Guards và tham số URL (`useParams`, `useSearchParams`).
3. **`typescript` (v5.x)**: Đảm bảo độ tin cậy của mã nguồn, kiểm soát chặt chẽ kiểu dữ liệu của hợp đồng API V1.7 và phát hiện lỗi ngay từ thời điểm biên dịch.
4. **`vite` & `@vitejs/plugin-react`**: Bộ công cụ phát triển nhanh, khởi động dev server tức thì và hỗ trợ cấu hình proxy chuyển tiếp request sang Gateway tránh lỗi CORS.
5. **`vitest`**: Test runner hiện đại, tương thích hoàn hảo với Vite, tốc độ thực thi nhanh hơn Jest.
6. **`@testing-library/react` & `@testing-library/user-event` & `@testing-library/jest-dom`**: Bộ thư viện kiểm thử chuẩn mực theo góc nhìn của người dùng thực tế.
7. **`jsdom`**: Giả lập môi trường trình duyệt DOM trong Node.js để chạy test UI.
8. **`msw` (Mock Service Worker v2)**: Giả lập tầng mạng ở mức request/response, cho phép kiểm thử trung thực các mã lỗi HTTP, header `Idempotency-Key` và cơ chế refresh token 401 mà không phụ thuộc vào backend thật.
