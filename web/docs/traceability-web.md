# Ma trận truy vết Web Frontend (Traceability Matrix)

Tài liệu này ánh xạ chi tiết giữa **Use Case**, **Màn hình giao diện**, **Endpoint REST**, **Mã lỗi xử lý (Error Codes)** và **Bộ test kiểm tra tự động**.

---

## Bảng ánh xạ tổng hợp

| Use Case | Màn hình giao diện (`Route`) | Endpoint backend được gọi (từ `api-contracts.md` V1.7) | Mã lỗi hợp đồng được bắt & xử lý (`errorCode`) | Tệp kiểm thử tự động (`src/test/*.test.ts(x)`) |
|---|---|---|---|---|
| **Tổng quan hệ thống** | `/`<br>([`OverviewPage.tsx`](../src/pages/OverviewPage.tsx)) | `GET /api/v1/pois`<br>`GET /api/v1/contents`<br>`GET /api/v1/auth/users`<br>`GET /health` | Xử lý nhẹ nhàng khi lỗi, hiển thị số liệu 0 hoặc UP/DOWN thực tế | [`apiClient.test.ts`](../src/test/apiClient.test.ts) |
| **UC7 – Xác thực đăng nhập** | `/login`<br>([`LoginPage.tsx`](../src/pages/LoginPage.tsx)) | `POST /api/v1/auth/login`<br>`POST /api/v1/auth/refresh`<br>`POST /api/v1/auth/logout` | `INVALID_CREDENTIALS` (401 — hiển thị thông báo đúng nghĩa: sai thông tin hoặc bị khóa 15 phút)<br>`AUTH_SERVICE_UNAVAILABLE` (503)<br>`INVALID_REFRESH_TOKEN` (401) | [`login.test.tsx`](../src/test/login.test.tsx)<br>[`apiClient.test.ts`](../src/test/apiClient.test.ts) |
| **UC8 – Quản lý điểm tham quan** | `/pois`<br>([`PoisPage.tsx`](../src/pages/PoisPage.tsx)) | `GET /api/v1/pois`<br>`POST /api/v1/pois`<br>`GET /api/v1/pois/{id}`<br>`PUT /api/v1/pois/{id}`<br>`DELETE /api/v1/pois/{id}` | `POI_NOT_FOUND` (404)<br>`POI_HAS_CONTENT` (409)<br>`QR_CODE_GENERATION_FAILED` (500)<br>`VALIDATION_ERROR` (400) | [`apiClient.test.ts`](../src/test/apiClient.test.ts) |
| **UC1 – Quản lý nội dung thuyết minh** | `/contents`<br>([`ContentsPage.tsx`](../src/pages/ContentsPage.tsx)) | `GET /api/v1/languages/source`<br>`GET /api/v1/contents`<br>`GET /api/v1/contents/{id}`<br>`POST /api/v1/pois/{poiId}/contents`<br>`PUT /api/v1/contents/{id}`<br>`DELETE /api/v1/contents/{id}` | `ACTIVE_CONTENT_EXISTS` (409)<br>`CONTENT_VERSION_CONFLICT` (409)<br>`CONTENT_NOT_FOUND` (404)<br>`VALIDATION_ERROR` (400) | [`apiClient.test.ts`](../src/test/apiClient.test.ts) |
| **UC2 – Tạo thuyết minh đa ngôn ngữ** | `/jobs/new`<br>([`CreateJobPage.tsx`](../src/pages/CreateJobPage.tsx)) | `GET /api/v1/voices`<br>`POST /api/v1/jobs`<br>*(kèm header `Idempotency-Key`)* | `IDEMPOTENCY_KEY_REQUIRED` (400)<br>`INVALID_TARGETS` (400)<br>`ACTIVE_JOB_EXISTS` (409)<br>`IDEMPOTENCY_KEY_REUSED` (409)<br>`CONTENT_VERSION_CHANGED` (409)<br>`INVALID_RETRY_REFERENCE` (409)<br>`CONTENT_NOT_FOUND` (404)<br>`CONTENT_SERVICE_UNAVAILABLE` (503) | [`createJob.test.tsx`](../src/test/createJob.test.tsx) |
| **UC2, UC3, UC4 – Tiến trình & Chi tiết Job** | `/jobs/:id`<br>([`JobDetailPage.tsx`](../src/pages/JobDetailPage.tsx)) | `GET /api/v1/jobs/{id}` *(polling 3s)*<br>`POST /api/v1/jobs/{id}/cancel` | `JOB_NOT_FOUND` (404)<br>`JOB_ALREADY_FINISHED` (409)<br>Mã lỗi target: `TIMEOUT`, `PROVIDER_ERROR`, `STORAGE_ERROR`, `INTERNAL_ERROR` | [`jobDetail.test.tsx`](../src/test/jobDetail.test.tsx)<br>[`polling.test.tsx`](../src/test/polling.test.tsx) |
| **UC2 – Lịch sử Job phiên hiện tại** | `/jobs`<br>([`JobHistoryPage.tsx`](../src/pages/JobHistoryPage.tsx)) | `GET /api/v1/jobs/{id}` *(khi bấm xem)* | `JOB_NOT_FOUND` (404) | [`jobDetail.test.tsx`](../src/test/jobDetail.test.tsx) |
| **UC9 – Quản lý tài khoản nội bộ** | `/accounts`<br>([`AccountsPage.tsx`](../src/pages/AccountsPage.tsx)) | `GET /api/v1/auth/users`<br>`POST /api/v1/auth/users`<br>`PUT /api/v1/auth/users/{id}/password`<br>`PUT /api/v1/auth/users/{id}/unlock` | `USERNAME_EXISTS` (409)<br>`USER_NOT_FOUND` (404)<br>`ACCOUNT_NOT_LOCKED` (409)<br>`FORBIDDEN` (403) | [`protectedRoute.test.tsx`](../src/test/protectedRoute.test.tsx) |
| **UC6 – Kiểm tra trạng thái hệ thống** | `/health`<br>([`HealthPage.tsx`](../src/pages/HealthPage.tsx)) | `GET /health` | `NETWORK_ERROR` (mã HTTP 0 khi Gateway không phản hồi) | [`apiClient.test.ts`](../src/test/apiClient.test.ts) |
| **UC5 – Nghe thuyết minh theo ngôn ngữ** | Modal trên `/pois` và `/jobs/:id`<br>([`AudioPreviewModal.tsx`](../src/components/AudioPreviewModal.tsx)) | `GET /api/v1/client/pois/{poiId}/narrations`<br>`GET /api/v1/client/pois/{poiId}/narrations/{lang}` | `POI_NOT_FOUND` (404)<br>`NARRATION_NOT_FOUND` (404)<br>`CONTENT_NOT_FOUND` (404)<br>`INTERNAL_ERROR` (503) | [`audioPreview.test.tsx`](../src/test/audioPreview.test.tsx) |
| **Bảo vệ tuyến đường (Auth Guard)** | Mọi route quản trị | N/A (Kiểm tra token & vai trò trong React Router) | `UNAUTHORIZED` (401 -> điều hướng về `/login`)<br>`FORBIDDEN` (403 -> hiển thị thông báo cấm truy cập) | [`protectedRoute.test.tsx`](../src/test/protectedRoute.test.tsx) |

---

## Ghi chú về việc tuân thủ quy tắc

1. **State Machine chuẩn**:
   - Job statuses: `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`.
   - Target statuses: `PENDING`, `TRANSLATING`, `SYNTHESIZING`, `PUBLISHED`, `FAILED`, `CANCELLED`.
2. **Không đưa token lên URL**: Tuyệt đối không dùng EventSource SSE với query param `?accessToken=...`. Tất cả cập nhật tiến độ đều thực hiện qua Polling `GET /api/v1/jobs/{jobId}` định kỳ 3 giây có `Authorization: Bearer` trong header.
3. **Idempotency-Key**: Gửi UUID ngẫu nhiên cho `POST /api/v1/jobs`. Nếu gặp lỗi mạng thì giữ nguyên đúng UUID này cho lượt thử lại; khi người dùng chủ động tạo job mới thì sinh UUID mới.
