# API và Event Contracts — V2.0

Khớp với tài liệu Use Cases v3.0 (docs/source/UC-v3.0.docx). Tài liệu định nghĩa hợp đồng REST và RabbitMQ cho hệ thống thuyết minh đa ngôn ngữ. API và event dùng camelCase; tên cột database dùng snake_case.

## Lịch sử thay đổi V2.0 (23-10-2026)

- **Theo dõi lượng khách truy cập thời gian thực (Presence Tracking - UC10, BR-19, §4)**:
  - Chuẩn hóa cơ chế tính toán sang Redis Sorted Set (`ZSET`) với cửa sổ trượt (sliding window) chuẩn 120 giây (loại bỏ cơ chế key đơn lẻ TTL 45 giây mâu thuẫn).
  - Bổ sung endpoint công khai `POST /api/v1/client/presence/heartbeat` (không cần auth) định kỳ mỗi 30 giây; request body hỗ trợ `clientId` (bắt buộc), `isListening`, `poiId`, `lang` (tùy chọn) để theo dõi số người nghe audio.
  - Bổ sung endpoint công khai `GET /api/v1/client/presence/online-count` hiển thị số lượng khách đang truy cập trực tiếp trên giao diện người dùng qua `activeUsers`.
  - Bổ sung endpoint quản trị `GET /api/v1/presence/metrics` (cần quyền `CONTENT_ADMIN`) phục vụ thống kê chi tiết trên Admin Dashboard (`currentActiveUsers`, `peakToday`, `totalVisitsToday`, `activeListeningUsers`, `windowSeconds: 120`).
  - Xác định rõ Content Service kết nối với Redis Cache để quản lý các API Presence, API Gateway định tuyến thẳng tới Content Service.
- **Tích hợp bản đồ số & vị trí vào Quản lý Điểm tham quan (POI - UC8, BR-18, §3.2)**:
  - Nâng cấp mô hình dữ liệu POI: thêm các trường tọa độ không gian `latitude` (vĩ độ), `longitude` (kinh độ) và `triggerRadius` (bán kính kích hoạt geofence, mặc định 30m, cho phép từ 10m đến 500m).
  - Cập nhật `POST /api/v1/pois` và `PUT /api/v1/pois/{poiId}` hỗ trợ lưu tọa độ điểm click trực tiếp trên bản đồ.
  - Bổ sung endpoint tổng hợp `POST /api/v1/pois/quick-create-with-job`: Tạo điểm tham quan trên bản đồ + Nhập nội dung gốc + Tự động kích hoạt job thuyết minh đa ngôn ngữ trong 1 thao tác duy nhất.
  - Sửa lỗi kiến trúc & giao dịch phân tán ở quick-create (BR-22): Content Service thực thi 1 local transaction tạo POI và Content v1, commit transaction rồi mới gọi REST nội bộ `POST /internal/jobs` sang Narration Service (không giữ open DB transaction để chống deadlock); nếu Narration Service lỗi 503, thực hiện compensating transaction xóa POI và Content v1 vừa tạo. Khẳng định không có sự kiện `POI_CREATED` qua RabbitMQ.
  - Cập nhật bảng `§1.1.1` khai báo endpoint nội bộ `Content Service → Narration Service: POST /internal/jobs`.
- **Sửa lỗi phân quyền 401 khi tải ảnh mã QR (UC13, BR-20, §3.2 & §5.3)**:
  - Mở thêm endpoint công khai `GET /api/v1/client/pois/{poiId}/qr-image?format=png|svg&size=300|600|1200` (không cần access token) phục vụ thẻ `<img>` của khách tải ảnh mã QR to rõ không bị lỗi 401.
  - Cập nhật response của `GET /api/v1/client/pois/{poiId}/card` trả `qrImageUrl` trỏ về endpoint client `/api/v1/client/pois/{poiId}/qr-image?size=600`.
  - Giữ nguyên `GET /api/v1/pois/{poiId}/qr-image` cho Quản trị viên (cần `CONTENT_ADMIN`) để tải bản in ấn độ phân giải cao.
- **Frontend Use Cases cho Khách tham quan (UC11, UC12, UC13, UC14, §5)**:
  - Bổ sung endpoint tìm kiếm và gợi ý điểm gần: `GET /api/v1/client/pois/nearby?lat={lat}&lng={lng}&radius={radius}` dựa trên vị trí GPS của khách (mặc định bán kính 1500m).
  - Nâng cấp endpoint `GET /api/v1/client/pois` hỗ trợ tham số tìm kiếm `query` và trả kèm tọa độ địa lý `latitude`, `longitude`.
  - Chuẩn hóa endpoint xem card điểm tham quan `GET /api/v1/client/pois/{poiId}/card`.
  - Hỗ trợ endpoint tra cứu POI theo mã QR: `GET /api/v1/client/pois/by-qr/{qrCode}` (kèm alias `GET /api/v1/client/qr/{qrCode}`).
  - Chuẩn hóa cấu trúc dữ liệu trả về cho Hộp thuyết minh đa ngôn ngữ (Narration Box) tích hợp phụ đề văn bản (`subtitle`) và audio MP3 qua `GET /api/v1/client/pois/{poiId}/narrations/{lang}` (include từ UC5, giữ nguyên lỗi 404 NARRATION_NOT_FOUND kèm `details.availableLangs[]`).
- **Khôi phục toàn vẹn 100% nội dung gốc từ V1.7**:
  - Giữ trọn vẹn đặc tả Auth Service (§2.1–§2.5) với quy tắc JWT RS256, kid, sub, role, iat, exp, khóa nguyên tử sau 5 lần sai (BR-12), toàn bộ mã lỗi.
  - Giữ trọn vẹn đặc tả Nội dung thuyết minh (§3.1, §3.3) với optimistic locking `baseVersion`, 409 `CONTENT_VERSION_CONFLICT`.
  - Giữ trọn vẹn các API nội bộ đọc nội dung (§3.4 cũ / §3.5 mới).
  - Giữ trọn vẹn đặc tả Narration Service (§4.1–§4.4, §4.6 cũ / §6 mới) với thứ tự 3 bước kiểm tra retryOfJobId, toàn bộ mã lỗi tạo job, xem tiến độ, hủy job, SSE stream, callback nội bộ.
  - Giữ trọn vẹn RabbitMQ Event Schemas và Queue Bindings (§5 cũ / §7 mới).
  - Đồng bộ chuẩn hóa toàn bộ Business Rules từ BR-01 đến BR-22 (§8) khớp 100% với Phụ lục C của tài liệu Use Case.

## Lịch sử thay đổi V1.7

- Đồng bộ UC v2.8 (docs/source/UC.docx); không thay đổi hợp đồng REST/event.

## Lịch sử thay đổi V1.6

- Bổ sung lỗi 404 POI_NOT_FOUND cho GET /api/v1/pois/{poiId}.
- Làm rõ GET /api/v1/client/pois/{poiId}/narrations trả 200 với danh sách rỗng khi POI hoạt động nhưng chưa có narration PUBLISHED.
- Đưa response mẫu QR và khuyến nghị giới hạn tần suất vào cùng bullet của endpoint QR để giữ liền mạch danh sách.
- Gộp các mục lịch sử V1.5 bị trùng.

## Lịch sử thay đổi V1.5

- Cập nhật caller, §3.4 và §4.5 sang GET /internal/pois/{poiId}/current theo Gateway.
- Đồng bộ các tham chiếu use case hiện hành với phiên bản v2.6.
- Gộp và loại câu 404 lặp trong §3.2 và §3.3.
- Bổ sung ví dụ response QR và khuyến nghị giới hạn tần suất để chống dò mã.

## Lịch sử thay đổi V1.4

- H2: Bổ sung API Content nội bộ cho Gateway lấy danh sách POI và POI/nội dung đang hoạt động theo poiId.
- H4: Chuẩn hóa JWT RS256, kid và các claim sub, role, iat, exp; Gateway không chấp nhận claim userId thay sub.
- M1: Chốt thứ tự xác thực retryOfJobId, điều kiện bỏ qua ACTIVE_JOB_EXISTS và cấu trúc details.jobIds[].
- M2: Làm rõ hành vi SSE khi token hết hạn, giả định một Gateway, và phản hồi callback nội bộ.
- M3: Quy định cache JWKS theo kid và cách tải lại khi gặp kid chưa biết hoặc Auth Service tạm lỗi.
- M4: Ghi giới hạn Idempotency-Key theo giới hạn đang có trong code Narration Service.
- M5: Bỏ isDeleted khỏi response tra POI bằng QR.
- M6: Làm rõ danh sách POI hoạt động và thời điểm hiển thị danh sách ngôn ngữ.
- L1: Bổ sung ma trận caller → API nội bộ và phân biệt API công khai với API /internal.
- L2: Bổ sung phản hồi 404 cho các tài nguyên không tồn tại/đã xóa mềm và 500 trong HTTP status dùng chung.

---

## 1. Quy ước chung

### 1.1. Base URL và headers

- Mọi API công khai có tiền tố `/api/v1`; mọi API nội bộ có tiền tố `/internal`.
- API cần xác thực nhận `Authorization: Bearer <accessToken>`. Ngoại lệ không cần access token: đăng nhập, làm mới token, JWKS, các API khách tham quan `/api/v1/client/**` (bao gồm lấy card, tải ảnh mã QR `/api/v1/client/pois/{poiId}/qr-image`, gửi heartbeat `/api/v1/client/presence/heartbeat` và lấy số người online `/api/v1/client/presence/online-count`). Riêng SSE `/api/v1/jobs/{jobId}/events` dùng `accessToken` trong query parameter vì `EventSource` của trình duyệt không gửi được `Authorization` header; bắt buộc áp dụng HTTPS và loại query token khỏi access log máy chủ.
- API Gateway tự động sinh `X-Correlation-ID` cho mỗi request đầu vào rồi forward ID đó qua HTTP và message headers. Client không cần tự sinh header; Gateway là nơi xác lập ID truy vết tin cậy phục vụ Distributed Tracing.
- API công khai chỉ nhận request qua API Gateway. API `/internal` nhận request từ service có `X-Service-Token` hợp lệ. Với request đã xác thực, Gateway chuyển `userId` và `role` tới service nội bộ qua header tin cậy `X-User-Id` và `X-User-Role`; service nội bộ không dùng giá trị do client tự gửi để phân quyền hoặc ghi `createdBy`.
- `Idempotency-Key` bắt buộc với `POST /api/v1/jobs` và `POST /api/v1/pois/quick-create-with-job`. Thiếu header trả 400 `IDEMPOTENCY_KEY_REQUIRED`.
- Mọi lời gọi REST nội bộ dùng duy nhất header `X-Service-Token`. Giá trị là secret riêng cho từng service gọi; không dùng một secret chung cho mọi caller và không nhận token từ client. Service nhận kiểm tra secret tương ứng với service gọi.
- Endpoint quản trị cần role `CONTENT_ADMIN`. Các API khách tham quan và JWKS công khai không yêu cầu đăng nhập.

### 1.1.1. Caller của API nội bộ

| Caller | Endpoint nội bộ | Mục đích |
|---|---|---|
| API Gateway → Content Service | `GET /internal/pois`, `GET /internal/pois/{poiId}/current`, `GET /internal/pois/by-qr/{qrCode}`, `GET /internal/pois/nearby`, `GET /internal/pois/{poiId}/card` | Gateway tổng hợp dữ liệu POI, giải mã QR và tìm kiếm lân cận cho Client |
| API Gateway → Narration Service | `GET /internal/narrations`, `GET /internal/narrations/{lang}` | Gateway lấy danh sách ngôn ngữ PUBLISHED, phụ đề và signed URL âm thanh |
| Content Service → Narration Service | `POST /internal/jobs` | Content Service tạo tác vụ thuyết minh đa ngôn ngữ sau khi lưu POI và Content v1 trong luồng Quick Create (§3.3 & BR-22) |
| Narration Service → API Gateway | `POST /internal/callbacks/job-progress` | Narration Service gửi tiến độ job cho Gateway phát tán tới client qua SSE |
| Narration Service → Content Service | `GET /internal/contents/{contentId}/current` | Narration Service kiểm tra nội dung tồn tại, chưa xóa mềm và chốt version trước khi tạo job |
| Translation Service → Content Service | `GET /internal/contents/{contentId}/versions/{version}` | Translation Service lấy nội dung văn bản gốc đúng version để dịch |
| Narration Service → TTS Service | `GET /internal/voices`, `GET /internal/audios/{audioId}/signed-url` | Lấy danh mục giọng đọc hợp lệ và cấp signed URL nghe audio MP3 |
| Narration Service và TTS Service → Translation Service | `GET /internal/translations/{translationId}` | Lấy văn bản bản dịch làm phụ đề và làm đầu vào tổng hợp giọng nói |

*Ghi chú về Presence Tracking*: API Gateway định tuyến trực tiếp các API Presence công khai (`/api/v1/client/presence/**`) và quản trị (`/api/v1/presence/metrics`) tới **Content Service**; Content Service kết nối trực tiếp với **Redis Cache** để xử lý sliding window 120 giây (không qua thêm tầng service nội bộ khác).

### 1.2. Response envelope

Response thành công dùng envelope:

```json
{
  "status": "success",
  "data": {}
}
```

Response lỗi dùng envelope:

```json
{
  "status": "error",
  "errorCode": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "details": {},
  "correlationId": "c4f7..."
}
```

`details` có thể là `null` hoặc chứa cấu trúc đối tượng/danh sách lỗi chi tiết; lỗi validation có thể trả danh sách lỗi theo trường. HTTP status là nguồn xác định loại lỗi; `errorCode` là mã ổn định để client xử lý logic. Envelope này là quy ước nhất quán của hệ thống. Mã lỗi dùng định dạng `UPPER_SNAKE_CASE`.

### 1.3. HTTP status dùng chung

- `200 OK`: đọc hoặc cập nhật thành công.
- `201 Created`: tạo tài nguyên đồng bộ thành công.
- `202 Accepted`: đã nhận yêu cầu xử lý bất đồng bộ (tạo Job).
- `204 No Content`: xóa thành công, không có response body.
- `400 Bad Request`: dữ liệu đầu vào sai cú pháp hoặc vi phạm validation.
- `401 Unauthorized`: thiếu token, token không hợp lệ hoặc đã hết hạn.
- `403 Forbidden`: không đủ quyền hoặc vai trò (role) không hợp lệ.
- `404 Not Found`: không tìm thấy tài nguyên hoặc tài nguyên đã bị xóa mềm.
- `409 Conflict`: xung đột trạng thái, version lạc quan, khóa duy nhất hoặc tái sử dụng sai Idempotency Key.
- `500 Internal Server Error`: lỗi nội bộ không dự kiến hoặc lỗi hệ thống không thể phục hồi.
- `503 Service Unavailable`: service phụ thuộc tạm thời không khả dụng.

Định dạng ngày giờ trên toàn hệ thống dùng chuẩn **ISO 8601 UTC**, ví dụ: `2026-10-23T10:00:00Z`.

---

## 2. Auth Service

### 2.1. Đăng nhập

`POST /api/v1/auth/login` — không cần access token.

Request:
```json
{
  "username": "admin",
  "password": "password123"
}
```

Response 200:
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleS0yMDI2LTAxIn0...",
    "refreshToken": "opaque-refresh-token",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshExpiresIn": 604800,
    "user": {
      "userId": "u-123",
      "username": "admin",
      "name": "Nguyen Van A",
      "role": "CONTENT_ADMIN"
    }
  }
}
```

Access token là JWT ký bằng private key theo thuật toán **RS256**; JWT header có `kid` khớp với một khóa trong JWKS. Claims bắt buộc gồm:
- `sub`: userId của người dùng (ví dụ `"u-123"`).
- `role`: vai trò người dùng (ví dụ `"CONTENT_ADMIN"`).
- `iat`: thời điểm phát hành (Unix timestamp).
- `exp`: thời điểm hết hạn (Unix timestamp).

**Quy tắc Gateway**: API Gateway chỉ chấp nhận các claim định danh này theo hợp đồng; **tuyệt đối không chấp nhận claim `userId` thay cho `sub`**. Gateway xác thực chữ ký JWT hoàn toàn stateless bằng Public Key lấy từ JWKS endpoint của Auth Service. Access token có hiệu lực 15 phút (900s), refresh token có hiệu lực 7 ngày (604800s).

**Quy tắc khóa tài khoản nguyên tử (BR-12)**: Sau 5 lần đăng nhập sai liên tiếp, Auth Service khóa tài khoản trong 15 phút. Việc tăng `failedLoginCount` và khóa ở ngưỡng phải cập nhật nguyên tử (atomic update) trong CSDL. Trong thời gian khóa (`lockedUntil > now`), đăng nhập bị từ chối kể cả khi nhập đúng mật khẩu và không tăng bộ đếm. Bộ đếm `failedLoginCount` được đặt lại về 0, đồng thời xóa `lockedUntil` (`lockedUntil = null`) khi:
1. Đăng nhập thành công với mật khẩu đúng (khi tài khoản không bị khóa).
2. Hết thời gian khóa 15 phút.
3. Quản trị viên mở khóa thủ công qua API.

Endpoint đăng nhập trả `refreshToken` cho ứng dụng theo UC7. Endpoint làm mới nhận `refreshToken` và trả access token mới; UC v2.8 / v3.0 không quy định xoay (rotate) refresh token. Token gốc chỉ được trả trong luồng xác thực, không bao giờ trả giá trị hash đang lưu trong database. API quản lý tài khoản tuyệt đối không bao giờ trả `refreshToken`, `passwordHash` hoặc `refreshTokenHash`.

Mã lỗi:
- `401 INVALID_CREDENTIALS`: thông tin đăng nhập sai hoặc tài khoản đang bị khóa; thông điệp không tiết lộ tài khoản có tồn tại hay không để chống brute-force user enumeration.
- `503 AUTH_SERVICE_UNAVAILABLE`: CSDL Auth Service hoặc dịch vụ ký khóa không phản hồi.

### 2.2. JWKS

`GET /api/v1/auth/.well-known/jwks.json` — không cần access token. Gateway lấy public key từ endpoint này để xác thực chữ ký JWT và chọn khóa theo `kid`.

Response 200:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "key-2026-01",
      "n": "base64url-modulus...",
      "e": "AQAB"
    }
  ]
}
```

`kid` trong JWT header phải khớp với `kid` của khóa dùng để ký. JWKS chỉ chứa Public Key, không bao giờ chứa Private Key. Gateway cache public key theo `kid` (ví dụ 10 phút). Khi gặp `kid` chưa biết trong token, Gateway tải lại JWKS đúng **một lần** rồi tiến hành xác thực lại. Nếu Auth Service tạm thời lỗi không gọi được, Gateway tiếp tục sử dụng các public key đã có trong cache để phục vụ request.

### 2.3. Làm mới access token

`POST /api/v1/auth/refresh` — không cần access token.

Request:
```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Response 200:
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1Ni...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

Refresh token được lưu trong CSDL dưới dạng chuỗi băm (hash). Token không hợp lệ, đã hết hạn hoặc đã bị thu hồi trả lỗi:
- `401 INVALID_REFRESH_TOKEN`: Refresh token không hợp lệ hoặc đã hết hạn.

### 2.4. Đăng xuất

`POST /api/v1/auth/logout` — cần access token.

Request:
```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Response 204 No Content. Auth Service thu hồi refresh token (xóa hoặc đánh dấu revoked trong DB); client xóa toàn bộ token lưu cục bộ. Access JWT đã phát hành vẫn có hiệu lực tối đa đến lúc hết hạn (tối đa 15 phút) do cơ chế xác thực stateless của Gateway.

### 2.5. Tài khoản nội bộ

Các endpoint dưới đây yêu cầu quyền `CONTENT_ADMIN`.

#### GET /api/v1/auth/users
Lấy danh sách tài khoản nội bộ có phân trang và lọc:
- Query: `page=1&pageSize=20&query=admin&locked=true`
- Dữ liệu trả về gồm: `userId`, `username`, `name`, `role`, `failedLoginCount`, `lockedUntil`, `createdAt`, `updatedAt`.
- Tuyệt đối không trả `passwordHash` hoặc `refreshToken` / `refreshTokenHash`.

Response 200:
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "userId": "u-123",
        "username": "admin",
        "name": "Nguyen Van A",
        "role": "CONTENT_ADMIN",
        "failedLoginCount": 0,
        "lockedUntil": null,
        "createdAt": "2026-10-01T08:00:00Z",
        "updatedAt": "2026-10-23T09:00:00Z"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

#### POST /api/v1/auth/users
Tạo tài khoản quản trị nội bộ mới. `username` bắt buộc và phải đúng định dạng hệ thống; `name` bắt buộc; `role` hiện chỉ nhận `CONTENT_ADMIN`; mật khẩu tối thiểu 8 ký tự và được băm bcrypt trước khi lưu.

Request:
```json
{
  "username": "mod_01",
  "name": "Nguyen Van A",
  "password": "SecurePassword1!",
  "role": "CONTENT_ADMIN"
}
```

Response 201 trả thông tin tài khoản không nhạy cảm.
- `409 USERNAME_EXISTS`: Tên đăng nhập đã tồn tại trong hệ thống.

#### PUT /api/v1/auth/users/{userId}/password
Quản trị viên đặt mật khẩu mới hoặc người dùng tự đổi mật khẩu.
- Request Body (Quản trị viên đặt lại cho user khác): `{"newPassword": "NewPassword123!"}`
- Request Body (Người dùng tự đổi mật khẩu của mình): Bắt buộc thêm `currentPassword`:
  ```json
  {
    "currentPassword": "OldPassword123!",
    "newPassword": "NewPassword123!"
  }
  ```
- Cập nhật password hash mới và đồng thời thu hồi tất cả refresh token hiện có của tài khoản đó.
- Response: 204 No Content.
- Mã lỗi:
  - `404 USER_NOT_FOUND`: Người dùng không tồn tại.
  - `400 CURRENT_PASSWORD_INVALID`: Mật khẩu hiện tại không đúng khi tự đổi; giữ nguyên dữ liệu cũ.

#### PUT /api/v1/auth/users/{userId}/unlock
Mở khóa thủ công cho tài khoản đang bị khóa. Đặt `failedLoginCount = 0`, `lockedUntil = null`.

Response 200:
```json
{
  "status": "success",
  "data": {
    "userId": "u-123",
    "locked": false
  }
}
```

Mã lỗi:
- `404 USER_NOT_FOUND`: Người dùng không tồn tại.
- `409 ACCOUNT_NOT_LOCKED`: Tài khoản hiện không ở trạng thái bị khóa.

---

## 3. Content Service

Các endpoint quản trị yêu cầu quyền `CONTENT_ADMIN`.

### 3.1. Ngôn ngữ nguồn

`GET /api/v1/languages/source` — cần `CONTENT_ADMIN`. Trả danh sách mã ngôn ngữ nguồn được hỗ trợ theo quy tắc BR-03.

Response 200:
```json
{
  "status": "success",
  "data": {
    "languages": ["vi", "en", "fr"]
  }
}
```

### 3.2. Điểm tham quan & Bản đồ số

Mô hình dữ liệu bảng `POINTS_OF_INTEREST` trong Content DB được nâng cấp với các trường không gian:
- `poi_id` (VARCHAR(36) / UUID, Primary Key)
- `name` (VARCHAR(255), Not Null)
- `description` (TEXT)
- `latitude` (DECIMAL(10, 8), Not Null) — Vĩ độ từ thao tác click bản đồ MapLibre (-90 đến +90)
- `longitude` (DECIMAL(11, 8), Not Null) — Kinh độ từ thao tác click bản đồ MapLibre (-180 đến +180)
- `trigger_radius` (INT, Default 30) — Bán kính kích hoạt geofence (mét, từ 10m đến 500m)
- `qr_code` (VARCHAR(64), Unique, Not Null) — Chuỗi ngẫu nhiên duy nhất
- `created_at`, `updated_at` (TIMESTAMP)

#### GET /api/v1/pois
Lấy danh sách điểm tham quan hiển thị trên bảng quản trị hoặc các marker trên bản đồ số.
- Query: `page=1&pageSize=50&query=bao-tang`

Response 200:
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "poiId": "p-999",
        "name": "Bảo tàng Chứng tích Chiến tranh",
        "description": "Nơi lưu giữ các hiện vật lịch sử chiến tranh tại Việt Nam",
        "latitude": 10.7794,
        "longitude": 106.6924,
        "triggerRadius": 30,
        "qrCode": "QR-WREM-87214",
        "hasActiveContent": true,
        "createdAt": "2026-10-20T08:00:00Z",
        "updatedAt": "2026-10-23T09:30:00Z"
      }
    ],
    "page": 1,
    "pageSize": 50,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

#### POST /api/v1/pois
Tạo điểm tham quan mới từ thao tác click trực tiếp trên bản đồ quản trị.
- Ràng buộc: `name` bắt buộc, không rỗng, tối đa 255 ký tự; `description` tối đa 2.000 ký tự; `latitude` (-90 đến +90); `longitude` (-180 đến +180); `triggerRadius` mặc định 30m (từ 10m đến 500m).
- Hệ thống tự sinh `poiId` (UUID) và chuỗi `qrCode` ngẫu nhiên khó đoán duy nhất (BR-14). Nếu trùng chuỗi sinh ngẫu nhiên, tự động thử lại tối đa 3 lần; sau đó trả 500 `QR_CODE_GENERATION_FAILED`.

Request:
```json
{
  "name": "Bến Nhà Rồng",
  "description": "Bảo tàng Hồ Chí Minh - Chi nhánh TP.HCM",
  "latitude": 10.7681,
  "longitude": 106.7067,
  "triggerRadius": 35
}
```

Response 201: Trả về bản ghi vừa tạo kèm `qrCode`.

#### GET /api/v1/pois/{poiId}
Lấy chi tiết POI theo ID. POI không tồn tại trả 404 `POI_NOT_FOUND`.

#### PUT /api/v1/pois/{poiId}
Cập nhật thông tin điểm tham quan (tên, mô tả, tọa độ di dời marker hoặc bán kính geofence). Giữ nguyên `poiId` và `qrCode`.

Request:
```json
{
  "name": "Bến Nhà Rồng (Cập nhật)",
  "description": "Di tích lịch sử văn hóa cấp quốc gia",
  "latitude": 10.7682,
  "longitude": 106.7068,
  "triggerRadius": 40
}
```

Response 200: Trả về bản ghi đã cập nhật. POI không tồn tại trả 404 `POI_NOT_FOUND`.

#### DELETE /api/v1/pois/{poiId}
Xóa vật lý điểm tham quan, response 204 No Content. POI không tồn tại trả 404 `POI_NOT_FOUND`.
- Ràng buộc khóa ngoại: Nếu bất kỳ bản ghi `CONTENTS` nào tham chiếu điểm này (kể cả nội dung đã xóa mềm), Content Service chặn thao tác và trả 409 `POI_HAS_CONTENT` (BR-15).

#### GET /api/v1/pois/{poiId}/qr-image
Kết xuất và trả về file ảnh mã QR độ phân giải cao dành cho Quản trị viên phục vụ in ấn hoặc xuất bản biểu mẫu (yêu cầu quyền `CONTENT_ADMIN`).
- Query parameters: `format=png|svg` (default png), `size=300|600|1200` (default 600px).
- Header: `Content-Type: image/png` hoặc `image/svg+xml`.
- POI không tồn tại trả 404 `POI_NOT_FOUND`.

---

### 3.3. Tạo nhanh Điểm tham quan & Tác vụ thuyết minh tích hợp (Quick Create with Job)

Endpoint tổng hợp: `POST /api/v1/pois/quick-create-with-job`
- Yêu cầu quyền: `CONTENT_ADMIN`
- Header bắt buộc: `Idempotency-Key: <uuid-hoặc-chuỗi-duy-nhất>` (thiếu trả 400 `IDEMPOTENCY_KEY_REQUIRED`).

Request:
```json
{
  "poi": {
    "name": "Chợ Bến Thành",
    "description": "Biểu tượng văn hóa và thương mại trung tâm thành phố",
    "latitude": 10.7725,
    "longitude": 106.6980,
    "triggerRadius": 40
  },
  "content": {
    "title": "Thuyết minh Chợ Bến Thành",
    "sourceLang": "vi",
    "textContent": "Chợ Bến Thành được khởi công xây dựng từ năm 1912..."
  },
  "job": {
    "targets": [
      { "lang": "en", "voiceId": "en-US-Journey-F" },
      { "lang": "fr", "voiceId": "fr-FR-Standard-A" },
      { "lang": "ja", "voiceId": "ja-JP-Standard-A" }
    ]
  }
}
```

#### Quy tắc kiến trúc & xử lý giao dịch phân tán không deadlock (BR-22):
1. **Xác thực & Kiểm tra header**: API Gateway xác thực JWT (`CONTENT_ADMIN`) và forward header `Idempotency-Key`, `X-User-Id`, `X-Correlation-ID` sang Content Service.
2. **Local Database Transaction**: Trong Content Service, thực hiện trong **duy nhất 1 local database transaction**:
   - Tạo bản ghi `POINTS_OF_INTEREST` (sinh `poiId`, `qrCode`, lưu `name`, `description`, `latitude`, `longitude`, `triggerRadius`).
   - Tạo bản ghi `CONTENTS` (`version = 1`, `createdBy` lấy từ `X-User-Id`, `title`, `sourceLang`, `textContent`).
   - **COMMIT LOCAL TRANSACTION NGAY LẬP TỨC** và phát sự kiện `content.updated`.
   - *Lưu ý sống còn về mặt kiến trúc*: **TUYỆT ĐỐI KHÔNG GIỮ DATABASE TRANSACTION CỦA CONTENT SERVICE MỞ** trong khi gọi REST sang Narration Service. Lý do: Narration Service khi tạo job sẽ gọi ngược lại `GET /internal/contents/{contentId}/current` sang Content Service; nếu transaction Content DB còn mở, kết nối CSDL và lock bản ghi sẽ gây ra deadlock giao dịch phân tán hoặc làm cạn kiệt connection pool.
3. **Kích hoạt Job qua REST nội bộ**: Sau khi local transaction đã commit thành công, Content Service gọi REST nội bộ `POST /internal/jobs` sang Narration Service (xác thực bằng `X-Service-Token`), truyền kèm `contentId`, `targets[]`, `Idempotency-Key` và `X-Correlation-ID`.
4. **Phản hồi thành công**: Khi Narration Service trả về thông tin Job (status PENDING/PROCESSING), Content Service tổng hợp và trả về response 201 Created.
5. **Xử lý sự cố (Compensating Transaction)**: Nếu Narration Service không phản hồi (timeout) hoặc trả lỗi 5xx (`503 NARRATION_SERVICE_UNAVAILABLE`): Để đảm bảo tính toàn vẹn trọn vẹn (All-or-Nothing) của thao tác tạo nhanh, Content Service thực thi **giao dịch bù trừ (compensating transaction)** xóa vật lý bản ghi `CONTENTS` và `POINTS_OF_INTEREST` vừa tạo, sau đó trả về lỗi HTTP 503 `NARRATION_SERVICE_UNAVAILABLE` kèm thông báo cho client thực hiện retry với cùng `Idempotency-Key`.

Response 201 Created:
```json
{
  "status": "success",
  "data": {
    "poi": {
      "poiId": "p-1001",
      "name": "Chợ Bến Thành",
      "description": "Biểu tượng văn hóa và thương mại trung tâm thành phố",
      "latitude": 10.7725,
      "longitude": 106.6980,
      "triggerRadius": 40,
      "qrCode": "QR-BEN-THANH-901",
      "qrImageUrl": "/api/v1/client/pois/p-1001/qr-image?size=600"
    },
    "content": {
      "contentId": "c-555",
      "version": 1,
      "title": "Thuyết minh Chợ Bến Thành",
      "sourceLang": "vi"
    },
    "job": {
      "jobId": "j-888",
      "status": "PENDING",
      "targets": [
        { "targetId": "t-01", "lang": "en", "voiceId": "en-US-Journey-F", "status": "PENDING" },
        { "targetId": "t-02", "lang": "fr", "voiceId": "fr-FR-Standard-A", "status": "PENDING" },
        { "targetId": "t-03", "lang": "ja", "voiceId": "ja-JP-Standard-A", "status": "PENDING" }
      ]
    }
  }
}
```

Mã lỗi:
- `400 IDEMPOTENCY_KEY_REQUIRED`: Thiếu header Idempotency-Key.
- `400 INVALID_COORDINATES`: Tọa độ ngoài phạm vi [-90, +90] hoặc [-180, +180].
- `400 INVALID_TARGETS`: Targets rỗng, trùng ngôn ngữ hoặc giọng đọc không hợp lệ.
- `503 NARRATION_SERVICE_UNAVAILABLE`: Không kết nối được với Narration Service để tạo job; hệ thống đã kích hoạt bước bù trừ hoàn nguyên dữ liệu.

---

### 3.4. Nội dung thuyết minh

Một nội dung có `contentId` ổn định và các `version` bất biến (immutable versioning). Mỗi điểm tham quan chỉ có tối đa một nội dung chưa xóa mềm.

#### POST /api/v1/pois/{poiId}/contents
Tạo nội dung thuyết minh đầu tiên cho POI.
- Request gồm: `title` (bắt buộc), `sourceLang` (bắt buộc, thuộc danh sách ngôn ngữ nguồn), `textContent` (bắt buộc, không được rỗng, tối đa 10.000 ký tự).
- Content Service tự động lấy `createdBy` từ header tin cậy `X-User-Id` do Gateway chuyển tiếp; không nhận trường này từ client.
- Response 201 trả `contentId`, `version: 1` và dữ liệu đã lưu.
- Mã lỗi:
  - `404 POI_NOT_FOUND`: Điểm tham quan không tồn tại.
  - `409 ACTIVE_CONTENT_EXISTS`: POI này đã có một nội dung đang hoạt động (chưa xóa mềm); mỗi POI chỉ có duy nhất 1 active content.

#### GET /api/v1/contents
Lấy danh sách nội dung có phân trang và tìm kiếm phục vụ UC1:
- Query: `page=1&pageSize=20&query=bao-tang&poiId=p-999`
- Mỗi mục trả thông tin POI, `contentId`, tiêu đề, ngôn ngữ nguồn, version hiện hành và trạng thái xóa mềm.

Response 200:
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "contentId": "c-888",
        "poiId": "p-999",
        "poiName": "Bảo tàng Lịch sử",
        "title": "Giới thiệu bảo tàng",
        "sourceLang": "vi",
        "currentVersion": 3,
        "isDeleted": false,
        "createdAt": "2026-10-01T08:00:00Z",
        "updatedAt": "2026-10-20T10:00:00Z"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

#### GET /api/v1/pois/{poiId}/contents/active
Lấy nội dung đang hoạt động và version hiện hành (version lớn nhất) của một POI. POI không tồn tại hoặc không có nội dung hoạt động trả 404 `POI_NOT_FOUND`.

#### GET /api/v1/contents/{contentId}
Lấy đầy đủ dữ liệu của version hiện hành, gồm `contentId`, `version`, `title`, `sourceLang` và `textContent`; dùng để nạp lại văn bản khi Actor chỉnh sửa nội dung hoặc cần tải lại sau khi gặp lỗi xung đột phiên bản. Nội dung không tồn tại hoặc đã xóa mềm trả 404 `CONTENT_NOT_FOUND`.

#### PUT /api/v1/contents/{contentId}
Tạo version mới theo cơ chế **khóa lạc quan (Optimistic Locking)**, tuyệt đối không ghi đè version cũ.
- Request bắt buộc phải có `baseVersion` cùng các trường nội dung:

```json
{
  "baseVersion": 3,
  "title": "Giới thiệu bảo tàng",
  "sourceLang": "vi",
  "textContent": "Chào mừng quý khách đến với Bảo tàng Lịch sử..."
}
```

- Ràng buộc: `title` bắt buộc; `sourceLang` phải thuộc danh mục ngôn ngữ nguồn hợp lệ; `textContent` không được rỗng và tối đa 10.000 ký tự.
- **Xử lý xung đột**: Chỉ chấp nhận cập nhật nếu `baseVersion` bằng chính xác version hiện hành trong CSDL; version mới được tạo sẽ bằng `baseVersion + 1`. Nếu version hiện hành đã thay đổi (do người khác đã lưu trước), Content Service từ chối cập nhật và trả về:
  - `409 CONTENT_VERSION_CONFLICT`: kèm trường `currentVersion` trong `details` để client biết và tải lại dữ liệu mới nhất. Không tự ý tăng version thay client và không ghi đè phiên bản cũ.
- Nội dung không tồn tại hoặc đã xóa mềm trả 404 `CONTENT_NOT_FOUND`.
- Khi tạo version mới thành công, Content Service phát sự kiện `content.updated` lên RabbitMQ (khuyến nghị qua Transactional Outbox theo BR-17).

#### DELETE /api/v1/contents/{contentId}
Xóa mềm toàn bộ nội dung (đánh dấu `is_deleted = true`), response 204 No Content.
- Nội dung không tồn tại hoặc đã xóa mềm trả 404 `CONTENT_NOT_FOUND`.
- Nội dung bị xóa mềm sẽ không còn hiển thị cho khách và không nhận thêm Job mới; tuy nhiên các Job đang chạy nền vẫn có thể đọc đúng version lịch sử đã chốt.
- Sau khi xóa mềm thành công, phát sự kiện `content.deleted` lên RabbitMQ.

---

### 3.5. API nội bộ đọc nội dung (Internal Content APIs)

Các endpoint dưới đây chỉ dành riêng cho giao tiếp giữa các microservices nội bộ, bắt buộc có header `X-Service-Token`.

#### GET /internal/contents/{contentId}/versions/{version}
Chỉ dành cho Translation Service gọi để lấy văn bản gốc đúng version phục vụ dịch thuật.
- Response trả về: `contentId`, `version`, `title`, `sourceLang`, `textContent` và `isDeleted`.
- API này **vẫn trả nội dung của version được yêu cầu** ngay cả khi nội dung đó đã bị xóa mềm sau khi job đã được tạo. Không dùng endpoint này để tạo job mới.

#### GET /internal/contents/{contentId}/current
Chỉ dành cho Narration Service gọi để kiểm tra tính hợp lệ trước khi tạo job.
- Khi nội dung hiện hành tồn tại và chưa bị xóa mềm, response trả: `contentId`, `version` (version lớn nhất hiện tại), `sourceLang` (không trả `isDeleted`).
- Nội dung không tồn tại hoặc đã xóa mềm trả 404 `CONTENT_NOT_FOUND`.
- Nếu Content Service không phản hồi hoặc lỗi 5xx, Narration Service trả 503 `CONTENT_SERVICE_UNAVAILABLE`. Không tạo job khi bước kiểm tra này thất bại.

#### GET /internal/pois
Chỉ dành cho API Gateway gọi để phục vụ `GET /api/v1/client/pois`.
- Query: `page=1&pageSize=20&query=bao-tang`
- Response trả về: `items[]` gồm `poiId`, `name`, `description`, `latitude`, `longitude`, `triggerRadius` cùng các thông tin phân trang `page`, `pageSize`, `totalItems`, `totalPages`; không trả `qrCode`.
- Chỉ liệt kê các POI có nội dung đang hoạt động; `query` tìm kiếm theo tên điểm tham quan.

#### GET /internal/pois/{poiId}/current
Chỉ dành cho API Gateway gọi để lấy thông tin POI và nội dung đang hoạt động phục vụ các API narration theo poiId.
- Response: `poiId`, `poiName`, `contentId`, `contentTitle`.
- POI hoặc nội dung không tồn tại/đã xóa mềm trả 404 `POI_NOT_FOUND`.

#### GET /internal/pois/by-qr/{qrCode}
Chỉ dành cho API Gateway gọi khi khách quét mã QR để tra cứu điểm tham quan và xác nhận nội dung đang hoạt động.
- Response: `poiId`, `poiName`, `contentId`, `contentTitle` (không trả `isDeleted`).
- Mã QR/POI/nội dung không tồn tại hoặc đã xóa mềm trả 404 `POI_NOT_FOUND`.

#### GET /internal/pois/nearby
Chỉ dành cho API Gateway gọi để tính toán gợi ý các điểm tham quan lân cận dựa trên tọa độ GPS của khách.
- Query: `lat={lat}&lng={lng}&radius={meters}`
- Content Service sử dụng công thức Haversine hoặc Spatial Index để lọc và sắp xếp các POI có khoảng cách `<= radius`.

---

## 4. Theo dõi lượng khách truy cập thời gian thực (Presence Tracking)

Cơ chế: Được đảm nhiệm bởi **Content Service** kết nối trực tiếp với **Redis Cache**. API Gateway định tuyến trực tiếp các endpoint này tới Content Service.
Toàn bộ giải thuật theo dõi sử dụng **Redis Sorted Set (`ZSET`)** với **cửa sổ trượt (sliding window) chuẩn 120 giây** (thay thế triệt để mô hình key đơn lẻ TTL 45 giây):
- Key online: `presence:online_users` (member = `clientId`, score = `unix_timestamp_seconds`).
- Key nghe thuyết minh: `presence:listening_users` (member = `clientId`, score = `unix_timestamp_seconds`).

### 4.1. Gửi Heartbeat từ thiết bị khách

`POST /api/v1/client/presence/heartbeat`
- Không yêu cầu đăng nhập (Public, anonymous).
- Client gửi định kỳ mỗi **30 giây** khi tab trình duyệt đang hoạt động (active via Page Visibility API).

Request:
```json
{
  "clientId": "anon-dev-9f8a1c3e",
  "isListening": true,
  "poiId": "p-1001",
  "lang": "en"
}
```
*Các trường `isListening`, `poiId`, `lang` là tùy chọn; khi thiết bị đang phát âm thanh thuyết minh, `isListening = true`.*

Xử lý tại Content Service:
1. Lấy thời gian hiện tại `now = unix_timestamp_seconds()`.
2. Ghi nhận sự hiện diện vào Redis ZSET:
   - `ZADD presence:online_users <now> <clientId>`
   - Nếu `isListening == true`: `ZADD presence:listening_users <now> <clientId>`
   - Nếu `isListening == false`: `ZREM presence:listening_users <clientId>`
3. Dọn dẹp các thành viên quá hạn ngoài cửa sổ trượt 120 giây:
   - `ZREMRANGEBYSCORE presence:online_users -inf (now - 120)`
   - `ZREMRANGEBYSCORE presence:listening_users -inf (now - 120)`

Response 200:
```json
{
  "status": "success",
  "data": {
    "ack": true,
    "nextHeartbeatInterval": 30
  }
}
```

### 4.2. Lấy số người đang truy cập (Cho Khách tham quan)

`GET /api/v1/client/presence/online-count`
- Không yêu cầu đăng nhập.
- Phục vụ hiển thị nhãn trạng thái trực quan trên giao diện công khai (ví dụ: `🟢 128 người đang tham quan`).
- Tính toán: Thực hiện `ZREMRANGEBYSCORE presence:online_users -inf (now - 120)` và đếm số lượng còn lại bằng `ZCARD presence:online_users`.

Response 200:
```json
{
  "status": "success",
  "data": {
    "activeUsers": 128,
    "updatedAt": "2026-10-23T10:05:00Z"
  }
}
```

### 4.3. Thống kê truy cập cho Quản trị viên

`GET /api/v1/presence/metrics`
- Yêu cầu quyền: `CONTENT_ADMIN`.
- Phục vụ biểu đồ theo dõi tải và số lượng khách trên Admin Dashboard.
- `currentActiveUsers` được tính bằng `ZCARD presence:online_users`.
- `activeListeningUsers` được tính bằng `ZCARD presence:listening_users`.
- `windowSeconds`: cố định 120 giây.

Response 200:
```json
{
  "status": "success",
  "data": {
    "currentActiveUsers": 128,
    "activeListeningUsers": 45,
    "peakToday": 342,
    "totalVisitsToday": 2150,
    "windowSeconds": 120,
    "updatedAt": "2026-10-23T10:05:00Z"
  }
}
```

---

## 5. Dịch vụ Khách tham quan (Client APIs)

Các API `/api/v1/client/**` là API công khai, không yêu cầu xác thực JWT. Tất cả response đều được đóng gói trong envelope chuẩn. API Gateway nhận request từ khách, gọi các service nội bộ (Content Service, Narration Service) và tổng hợp trả về dữ liệu hoàn chỉnh.

### 5.1. Khám phá & Gợi ý điểm tham quan lân cận (Nearby Discovery)

`GET /api/v1/client/pois/nearby?lat={latitude}&lng={longitude}&radius={meters}`
- Dựa vào vị trí GPS của khách để tính khoảng cách không gian (Haversine Formula / Spatial Index).
- `radius`: Bán kính quét (mét), mặc định **1500m** (cho phép từ 100m đến 5000m).
- Kết quả sắp xếp theo khoảng cách tăng dần (từ gần nhất đến xa nhất).

Response 200:
```json
{
  "status": "success",
  "data": {
    "userLocation": { "latitude": 10.7720, "longitude": 106.6975 },
    "radiusMeters": 1500,
    "totalNearby": 2,
    "items": [
      {
        "poiId": "p-1001",
        "poiName": "Chợ Bến Thành",
        "description": "Biểu tượng văn hóa và thương mại trung tâm thành phố",
        "latitude": 10.7725,
        "longitude": 106.6980,
        "distanceMeters": 78.5,
        "isWithinTriggerRadius": false,
        "hasPublishedNarration": true
      },
      {
        "poiId": "p-999",
        "poiName": "Bảo tàng Chứng tích Chiến tranh",
        "description": "Bảo tàng lịch sử chiến tranh tại Việt Nam",
        "latitude": 10.7794,
        "longitude": 106.6924,
        "distanceMeters": 980.2,
        "isWithinTriggerRadius": false,
        "hasPublishedNarration": true
      }
    ]
  }
}
```

### 5.2. Tìm kiếm điểm tham quan trên bản đồ

`GET /api/v1/client/pois?page=1&pageSize=30&query={keyword}`
- Tìm kiếm nhanh theo từ khóa không dấu hoặc có dấu (tên điểm, mô tả), chỉ gồm các POI có nội dung đang hoạt động.
- Trả về tọa độ `latitude`, `longitude` để vẽ marker trên bản đồ số của khách.

Response 200:
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "poiId": "p-1001",
        "poiName": "Chợ Bến Thành",
        "description": "Biểu tượng văn hóa...",
        "latitude": 10.7725,
        "longitude": 106.6980
      }
    ],
    "page": 1,
    "pageSize": 30,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

### 5.3. Xem chi tiết POI & Hiển thị mã QR

#### GET /api/v1/client/pois/{poiId}/card
Khi khách bấm vào một marker trên bản đồ hoặc từ danh sách gợi ý gần để xem thông tin:
- API trả về thông tin chi tiết POI, tọa độ và đường dẫn ảnh mã QR công khai (`qrImageUrl`).

Response 200:
```json
{
  "status": "success",
  "data": {
    "poiId": "p-1001",
    "poiName": "Chợ Bến Thành",
    "description": "Biểu tượng văn hóa và thương mại trung tâm thành phố",
    "latitude": 10.7725,
    "longitude": 106.6980,
    "qrCode": "QR-BEN-THANH-901",
    "qrImageUrl": "/api/v1/client/pois/p-1001/qr-image?size=600",
    "hasNarration": true
  }
}
```

#### GET /api/v1/client/pois/{poiId}/qr-image
**Endpoint công khai (không cần access token)** phục vụ thẻ `<img>` của trình duyệt khách nạp ảnh mã QR độ nét cao mà **không bao giờ bị lỗi 401 Unauthorized**:
- Query parameters: `format=png|svg` (default png), `size=300|600|1200` (default 600px).
- Headers: `Content-Type: image/png` hoặc `image/svg+xml`.
- Response: Luồng nhị phân file ảnh QR. POI không tồn tại trả 404 `POI_NOT_FOUND`.

### 5.4. Quét mã QR (QR Scan Dispatcher)

`GET /api/v1/client/pois/by-qr/{qrCode}` (hỗ trợ cả alias `GET /api/v1/client/qr/{qrCode}`)
- Khách quét mã QR vật lý dán tại di tích hoặc quét mã QR hiển thị từ thiết bị khác (UC14, UC5 A1).
- Gateway gọi `GET /internal/pois/by-qr/{qrCode}` từ Content Service để tra cứu và xác nhận nội dung đang hoạt động.
- QR/POI/nội dung không tồn tại hoặc đã xóa mềm trả 404 `POI_NOT_FOUND`.
- Khuyến nghị áp dụng giới hạn tần suất (rate limiting) cho endpoint này để phòng chống quét dò mã QR.

Response 200:
```json
{
  "status": "success",
  "data": {
    "poiId": "p-1001",
    "poiName": "Chợ Bến Thành"
  }
}
```

### 5.5. Hộp thuyết minh đa ngôn ngữ (Narration Box)

Sau khi quét mã QR thành công hoặc khi nhấn "Mở thuyết minh trực tiếp", client mở **Hộp thuyết minh đa ngôn ngữ** (Bottom Sheet / Drawer) và gọi các endpoint:

#### Lấy danh sách ngôn ngữ khả dụng:
`GET /api/v1/client/pois/{poiId}/narrations`
- Gateway gọi `GET /internal/pois/{poiId}/current` để xác nhận POI/content hoạt động rồi gọi `GET /internal/narrations?contentId={contentId}` sang Narration Service.
- Narration Service chọn version lớn nhất có target `PUBLISHED` theo từng ngôn ngữ (version phục vụ không bắt buộc phải trùng version hiện hành của Content gốc).
- Nếu POI đang hoạt động nhưng chưa có ngôn ngữ nào đạt trạng thái `PUBLISHED`, endpoint trả 200 với mảng rỗng: `"narrations": []`.
- POI hoặc nội dung không tồn tại/đã xóa mềm trả 404 `POI_NOT_FOUND`.

Response 200:
```json
{
  "status": "success",
  "data": {
    "poiId": "p-1001",
    "poiName": "Chợ Bến Thành",
    "contentTitle": "Thuyết minh Chợ Bến Thành",
    "narrations": [
      { "lang": "vi", "version": 1, "languageName": "Tiếng Việt" },
      { "lang": "en", "version": 1, "languageName": "English" },
      { "lang": "fr", "version": 1, "languageName": "Français" },
      { "lang": "ja", "version": 1, "languageName": "日本語" }
    ]
  }
}
```

#### Lấy Text thuyết minh & Signed URL Audio MP3 theo ngôn ngữ:
`GET /api/v1/client/pois/{poiId}/narrations/{lang}`
- Gateway gọi `GET /internal/pois/{poiId}/current` rồi gọi `GET /internal/narrations/{lang}?contentId={contentId}` sang Narration Service.
- Narration Service chọn target `PUBLISHED` có version lớn nhất cho ngôn ngữ đó, lấy phụ đề văn bản dịch thuật và signed URL của file âm thanh MP3.
- Response trả về gồm `subtitle` (phụ đề văn bản đầy đủ) và `audioUrl` (URL có chữ ký hết hạn sau 1 giờ).
- POI hoặc nội dung không tồn tại/đã xóa mềm trả 404 `POI_NOT_FOUND`.
- Nếu ngôn ngữ yêu cầu chưa có thuyết minh `PUBLISHED`, hệ thống trả lỗi:
  - `404 NARRATION_NOT_FOUND`: kèm mảng `details.availableLangs` chứa danh sách các mã ngôn ngữ đang có thuyết minh PUBLISHED để giao diện gợi ý cho khách:

```json
{
  "status": "error",
  "errorCode": "NARRATION_NOT_FOUND",
  "message": "Chưa có thuyết minh PUBLISHED cho ngôn ngữ đã chọn",
  "details": {
    "availableLangs": ["vi", "en"]
  },
  "correlationId": "c4f7..."
}
```

Response 200 khi thành công:
```json
{
  "status": "success",
  "data": {
    "poiId": "p-1001",
    "poiName": "Chợ Bến Thành",
    "contentTitle": "Thuyết minh Chợ Bến Thành",
    "lang": "en",
    "languageName": "English",
    "version": 1,
    "subtitle": "Ben Thanh Market was built in 1912 and has become a historical and cultural symbol of Ho Chi Minh City...",
    "audioUrl": "https://storage.example.com/audio/p1001_en_v1.mp3?signature=abc123xyz&expires=1792688400",
    "audioUrlExpiresAt": "2026-10-23T11:00:00Z",
    "durationSeconds": 185
  }
}
```
*Frontend sử dụng trường `subtitle` để render hộp văn bản phụ đề theo ngôn ngữ, và trường `audioUrl` để nạp vào trình phát MP3 player (Play, Pause, Tua +10s/-10s, chọn tốc độ phát).*

---

## 6. Narration Service, SSE & Pipeline xử lý nền

### 6.1. Danh mục giọng đọc và API phụ thuộc nội bộ

#### GET /internal/voices
API nội bộ. TTS Service cung cấp danh mục ngôn ngữ và giọng đọc (voiceId) hợp lệ:

```json
{
  "status": "success",
  "data": {
    "supportedVoices": [
      { "lang": "en", "voices": ["en-US-Journey-F", "en-GB-Standard-A"] },
      { "lang": "vi", "voices": ["vi-VN-Standard-A", "vi-VN-Neural2-A"] },
      { "lang": "fr", "voices": ["fr-FR-Standard-A"] },
      { "lang": "ja", "voices": ["ja-JP-Standard-A"] }
    ]
  }
}
```

Cặp `lang` – `voiceId` gửi lên khi tạo job bắt buộc phải hợp lệ theo danh mục này. Ngôn ngữ đích được phép trùng ngôn ngữ nguồn; khi đó Translation Service thực hiện cơ chế pass-through (sao chép nguyên văn văn bản gốc làm bản dịch, không gọi dịch vụ dịch bên thứ ba).

#### GET /api/v1/voices
Cần `CONTENT_ADMIN`. Narration Service đọc danh mục từ TTS Service qua `/internal/voices` rồi trả về `supportedVoices` cho giao diện tạo job (UC2, UC8).

#### GET /internal/translations/{translationId}
Translation Service cung cấp cho Narration Service và TTS Service. Response gồm `translationId`, `contentId`, `version`, `lang` và `textContent`; văn bản dịch này được dùng làm phụ đề (subtitle) và đầu vào tổng hợp giọng nói.

#### GET /internal/audios/{audioId}/signed-url
TTS Service cung cấp cho Narration Service. Response gồm `audioUrl` và `expiresAt`; URL có chữ ký hết hạn sau 1 giờ. CSDL chỉ lưu `objectKey`, tuyệt đối không lưu URL có chữ ký.

---

### 6.2. Tạo job

#### POST /api/v1/jobs — cần `CONTENT_ADMIN` và `Idempotency-Key`
#### POST /internal/jobs — API nội bộ dành cho Content Service gọi trong luồng Quick Create

Request:
```json
{
  "contentId": "c-888",
  "retryOfJobId": "j-previous",
  "targets": [
    { "lang": "en", "voiceId": "en-US-Journey-F" },
    { "lang": "fr", "voiceId": "fr-FR-Standard-A" }
  ]
}
```

Quy tắc tạo job:
- Client không gửi version: Narration Service gọi `GET /internal/contents/{contentId}/current` để xác nhận nội dung tồn tại, chưa bị xóa mềm và lấy version hiện hành; job lưu cố định version đó.
- Mỗi `lang` chỉ xuất hiện một lần trong `targets` và có đúng một `voiceId` tương ứng.
- Kiểm tra job trùng và tạo job diễn ra trong cùng một transaction, có khóa theo `(contentId, version)` để hai request đồng thời không cùng vượt qua bước kiểm tra.

Response 202 Accepted:
```json
{
  "status": "success",
  "data": {
    "jobId": "j-777",
    "contentId": "c-888",
    "version": 3,
    "status": "PENDING",
    "createdAt": "2026-10-08T10:00:00Z",
    "targets": [
      { "targetId": "t-111", "lang": "en", "voiceId": "en-US-Journey-F", "status": "PENDING" },
      { "targetId": "t-112", "lang": "fr", "voiceId": "fr-FR-Standard-A", "status": "PENDING" }
    ]
  }
}
```

**Quy tắc Idempotency & Tái sử dụng**:
- Cùng `Idempotency-Key` và cùng request body sẽ trả lại job đã tạo trước đó; cùng key nhưng payload khác trả 409 `IDEMPOTENCY_KEY_REUSED`. Key có phạm vi theo user và operation, được lưu giữ ít nhất 24 giờ sau khi job kết thúc.

**Thứ tự 3 bước kiểm tra `retryOfJobId`**:
Nếu có job đang ở trạng thái PENDING hoặc PROCESSING cho cùng `contentId`, `version` và ngôn ngữ, request nếu không có `retryOfJobId` hợp lệ che đúng target đang xung đột sẽ bị từ chối với 409 `ACTIVE_JOB_EXISTS` (kèm mảng các jobId xung đột trong `details.jobIds[]`). Khi Quản trị viên xác nhận tạo lại (retry), client gửi request mới kèm Idempotency-Key mới và truyền `retryOfJobId` tham chiếu job cũ. Narration Service kiểm tra theo đúng thứ tự 3 bước nghiêm ngặt:
1. **Kiểm tra sự tồn tại & Content ID**: Job được tham chiếu bởi `retryOfJobId` phải tồn tại trong CSDL và phải có cùng `contentId`. Nếu không thỏa mãn, trả ngay 409 `INVALID_RETRY_REFERENCE`.
2. **Kiểm tra tính nhất quán Version**: Version của job được tham chiếu phải bằng chính xác version hiện hành của nội dung. Nếu version hiện hành đã thay đổi, trả ngay 409 `CONTENT_VERSION_CHANGED`.
3. **Kiểm tra Target tương ứng**: Job được tham chiếu phải chứa target có cùng ngôn ngữ tương ứng. Nếu thỏa mãn cả 3 bước, hệ thống cho phép bỏ qua kiểm tra `ACTIVE_JOB_EXISTS` cho target đó. Job cũ không bị hủy mà vẫn tiếp tục chạy độc lập; job mới tận dụng lại bản dịch theo khóa `(contentId, version, lang)` và file âm thanh theo khóa `(translationId, voiceId)` để tối ưu chi phí và thời gian xử lý.

Danh sách mã lỗi tạo job:
- `400 IDEMPOTENCY_KEY_REQUIRED`: Thiếu header Idempotency-Key.
- `400 INVALID_IDEMPOTENCY_KEY`: Idempotency-Key dài hơn 255 ký tự.
- `400 INVALID_TARGETS`: Targets rỗng, trùng ngôn ngữ, hoặc mã ngôn ngữ/giọng đọc không hợp lệ theo danh mục.
- `404 CONTENT_NOT_FOUND`: Nội dung không tồn tại hoặc đã bị xóa mềm trong Content Service.
- `409 ACTIVE_JOB_EXISTS`: Đang có job PENDING/PROCESSING cho cùng contentId, version và lang; `details.jobIds[]` trả về mảng các jobId đang xung đột.
- `409 IDEMPOTENCY_KEY_REUSED`: Trùng Idempotency-Key nhưng payload request khác với lần gọi trước.
- `409 CONTENT_VERSION_CHANGED`: Version hiện hành của nội dung đã thay đổi so với version của job được tham chiếu khi retry.
- `409 INVALID_RETRY_REFERENCE`: Job tham chiếu không tồn tại, khác contentId hoặc không chứa target tương ứng.
- `503 CONTENT_SERVICE_UNAVAILABLE`: Không kết nối được với Content Service để xác thực version nội dung.

---

### 6.3. Xem tiến độ

`GET /api/v1/jobs/{jobId}` — cần `CONTENT_ADMIN`. Job không tồn tại trả 404 `JOB_NOT_FOUND`.

Response 200:
```json
{
  "status": "success",
  "data": {
    "jobId": "j-777",
    "contentId": "c-888",
    "version": 3,
    "status": "PROCESSING",
    "createdAt": "2026-10-08T10:00:00Z",
    "updatedAt": "2026-10-08T10:00:15Z",
    "targets": [
      {
        "targetId": "t-111",
        "lang": "en",
        "voiceId": "en-US-Journey-F",
        "status": "SYNTHESIZING",
        "errorCode": null,
        "updatedAt": "2026-10-08T10:00:15Z"
      },
      {
        "targetId": "t-112",
        "lang": "fr",
        "voiceId": "fr-FR-Standard-A",
        "status": "PUBLISHED",
        "errorCode": null,
        "updatedAt": "2026-10-08T10:00:12Z"
      }
    ]
  }
}
```

**Máy trạng thái Job & Target (BR-04)**:
- **Trạng thái Job**: `PENDING` → `PROCESSING` → `COMPLETED` | `PARTIALLY_COMPLETED` | `FAILED`; nhánh hủy: `PENDING` | `PROCESSING` → `CANCELLED`.
- **Trạng thái Target**: `PENDING` → `TRANSLATING` → `SYNTHESIZING` → `PUBLISHED` | `FAILED` | `CANCELLED`.
- Khi Narration Service phát `narration.requested`, job chuyển sang `PROCESSING` và target chuyển `TRANSLATING`. Khi nhận `translation.completed`, target chuyển sang `SYNTHESIZING`. Khi nhận `tts.completed`, target chuyển thành `PUBLISHED` (kể cả khi sự kiện hoàn thành dịch đến sau do mạng).
- Target đã ở trạng thái cuối (`PUBLISHED`, `FAILED`, `CANCELLED`) không bị thay đổi bởi bất kỳ sự kiện nào đến muộn; riêng thao tác hủy job có thể chuyển target `PUBLISHED` sang `CANCELLED`.
- Quy tắc tổng hợp trạng thái Job:
  - Tất cả target `PUBLISHED` → Job là `COMPLETED`.
  - Có ít nhất một target `PUBLISHED` và ít nhất một target `FAILED` → Job là `PARTIALLY_COMPLETED`.
  - Tất cả target `FAILED` → Job là `FAILED`.
  - Hủy job → Job là `CANCELLED`.
- Mọi cập nhật trạng thái target, tổng hợp job và xử lý timeout bắt buộc phải khóa bản ghi job trước, sau đó mới cập nhật target và job trong cùng một transaction. Target `FAILED` (kể cả do `TIMEOUT`) không bao giờ được hồi sinh bởi sự kiện thành công đến muộn.

---

### 6.4. Hủy job

`POST /api/v1/jobs/{jobId}/cancel` — cần `CONTENT_ADMIN`.

Hủy job là thao tác xử lý tức thời, không có trạng thái trung gian `CANCEL_REQUESTED`.
- Chỉ cho phép hủy khi job đang ở trạng thái `PENDING` hoặc `PROCESSING`.
- Trong một transaction có khóa bản ghi job: chuyển trạng thái job thành `CANCELLED`; mọi target chưa `FAILED` (kể cả target đã `PUBLISHED`) đều chuyển thành `CANCELLED`. Các target đã `FAILED` trước đó được giữ nguyên để lưu vết lỗi.
- Sau khi commit transaction, phát sự kiện `narration.cancelled` lên exchange fanout `narration.cancel`.
- Response 200 trả về thông tin job cùng các target sau khi hủy.
- Job đã kết thúc (`COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`) trả 409 `JOB_ALREADY_FINISHED` và không thay đổi dữ liệu; job không tồn tại trả 404 `JOB_NOT_FOUND`.
- Bản dịch và file âm thanh đã sinh không bị xóa khỏi hệ thống vì có thể được tái sử dụng cho job khác. Tuy nhiên target `CANCELLED` sẽ không được phục vụ cho Khách tham quan.

---

### 6.5. Cập nhật tiến độ theo thời gian thực (SSE & Webhook Callback)

#### Callback nội bộ: POST /internal/callbacks/job-progress
Narration Service gửi callback nội bộ tới API Gateway để thông báo tiến độ xử lý của từng job.
- Request dùng header `X-Service-Token` của Narration Service và `X-Correlation-ID`.
- Payload mẫu:
```json
{
  "event": "job.progress",
  "correlationId": "c4f7...",
  "data": {
    "jobId": "j-777",
    "status": "PROCESSING",
    "updatedAt": "2026-10-08T10:00:10Z",
    "targets": [
      { "targetId": "t-111", "lang": "en", "status": "SYNTHESIZING" }
    ]
  }
}
```

#### SSE: GET /api/v1/jobs/{jobId}/events?accessToken=<token>
Client (trình duyệt của Quản trị viên) kết nối Server-Sent Events (SSE) để nhận cập nhật tiến độ theo thời gian thực mà không cần polling liên tục.
- Do `EventSource` không hỗ trợ gửi custom header `Authorization`, Gateway xác thực JWT truyền qua query parameter `accessToken`. Gateway kiểm tra chữ ký, kid, hạn dùng và role `CONTENT_ADMIN` theo đúng chuẩn bảo mật; yêu cầu kết nối qua HTTPS và loại bỏ query token khỏi access log.
- Response header: `Content-Type: text/event-stream; charset=UTF-8`.
- Gateway đăng ký kết nối theo `jobId`; callback nội bộ chỉ được chuyển tiếp tới các client đang theo dõi đúng `jobId` đó.
- Khi access token hết hạn, kết nối SSE nhận mã lỗi 401; client phải đóng kết nối cũ và tạo `EventSource` mới với `accessToken` vừa được làm mới. Client luôn có thể lấy trạng thái chuẩn cuối cùng bằng `GET /api/v1/jobs/{jobId}`.

---

## 7. RabbitMQ Event Contracts

### 7.1. Cấu trúc Exchange & Routing Keys

- **Exchange sự kiện chính**: `narration.events` (Topic Exchange, Durable). Tên routing key chính là tên của sự kiện.
- **Exchange phát lệnh hủy**: `narration.cancel` (Fanout Exchange, Durable).
- **Quy ước thông điệp**: Body dùng định dạng JSON với các trường camelCase. Metadata kỹ thuật được đặt trong AMQP headers: `eventId` (UUID), `correlationId`, `schemaVersion`, `occurredAt` (UTC timestamp). `targetId` được đặt trong body cho tất cả các sự kiện gắn với từng target.
- **Publisher Confirms & Idempotent Consumer**: Publisher bắt buộc kích hoạt publisher confirms. Sự kiện có thể được gửi lặp lại trong môi trường mạng phân tán (at-least-once delivery); mọi consumer bắt buộc phải xử lý idempotent theo khóa nghiệp vụ (BR-06).
- **Dead Letter Queue (DLQ)**: Queue chính durable, message persistent, chế độ manual ack. Khi message sai định dạng hoặc lỗi logic không thể sửa: thực hiện nack không requeue để chuyển thẳng vào DLQ. Lỗi do gọi nhà cung cấp bên ngoài (Translation/TTS Provider) sau tối đa 3 lần retry thất bại (tổng 4 lần gọi) sẽ phát sự kiện `.failed`, ack thông điệp ban đầu và **tuyệt đối không đưa lỗi nhà cung cấp vào DLQ**.
- **Khuyến nghị Transactional Outbox (BR-17)**: Narration Service và Content Service ghi nhận sự kiện vào bảng outbox trong cùng database transaction với thay đổi dữ liệu; tiến độ relay đẩy lên broker và chỉ đánh dấu hoàn tất sau khi broker confirm.

---

### 7.2. Event Schemas

#### content.updated
Content Service phát mỗi khi tạo một version nội dung mới:
```json
{
  "contentId": "c-888",
  "version": 4
}
```

#### content.deleted
Content Service phát khi xóa mềm một nội dung:
```json
{
  "contentId": "c-888"
}
```

#### narration.requested
Narration Service gửi cho Translation Service (mỗi target một message độc lập):
```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "contentId": "c-888",
  "version": 3,
  "lang": "en",
  "voiceId": "en-US-Journey-F"
}
```

#### translation.completed
Translation Service gửi tới TTS Service và Narration Service qua 2 queue riêng biệt:
```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "translationId": "tr-222",
  "lang": "en",
  "voiceId": "en-US-Journey-F"
}
```

#### translation.failed
Translation Service gửi cho Narration Service khi dịch thuật thất bại:
```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "lang": "en",
  "errorCode": "PROVIDER_ERROR"
}
```

#### tts.completed
TTS Service gửi cho Narration Service khi tổng hợp giọng nói thành công:
```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "translationId": "tr-222",
  "audioId": "au-333",
  "lang": "en"
}
```

#### tts.failed
TTS Service gửi cho Narration Service khi tổng hợp giọng nói thất bại:
```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "lang": "en",
  "errorCode": "STORAGE_ERROR"
}
```

#### narration.cancelled
Narration Service phát tán lên exchange fanout `narration.cancel`:
```json
{
  "jobId": "j-777"
}
```

*Lưu ý về errorCode*: Thuộc enum cố định: `TIMEOUT`, `PROVIDER_ERROR`, `CONTENT_NOT_FOUND`, `STORAGE_ERROR`, `INTERNAL_ERROR`.

---

### 7.3. Queue Bindings

| Queue | Binding (Routing Key / Exchange) | Consumer | Mục đích |
|---|---|---|---|
| `translation.q.requested` | `narration.requested` | Translation Service | Tiếp nhận yêu cầu dịch từng ngôn ngữ |
| `tts.q.translation-completed` | `translation.completed` | TTS Service | Nhận thông báo dịch xong để kích hoạt tổng hợp giọng nói |
| `narration.q.translation-completed` | `translation.completed` | Narration Service | Nhận thông báo dịch xong để cập nhật target sang `SYNTHESIZING` |
| `narration.q.results` | `translation.failed`, `tts.completed`, `tts.failed` | Narration Service | Nhận kết quả hoàn tất audio (`PUBLISHED`) hoặc thất bại (`FAILED`) |
| Queue riêng từng instance Translation Service | Exchange fanout `narration.cancel` | Translation Service (exclusive, auto-delete) | Nhận lệnh hủy job để ngắt các tác vụ dịch đang chạy |
| Queue riêng từng instance TTS Service | Exchange fanout `narration.cancel` | TTS Service (exclusive, auto-delete) | Nhận lệnh hủy job để ngắt các tác vụ tổng hợp giọng nói đang chạy |

*Quy tắc queue*: Sự kiện `translation.completed` bắt buộc phải được nhân bản vào hai queue độc lập của TTS Service và Narration Service; hai service tuyệt đối không cạnh tranh đọc chung một queue. Mỗi queue công việc đều được cấu hình DLQ riêng.
*Khẳng định*: Hệ thống **không có sự kiện `POI_CREATED` qua RabbitMQ**; toàn bộ quy trình tạo điểm tham quan và kích hoạt job trong Quick Create được thực hiện qua các cuộc gọi REST đồng bộ nội bộ có kiểm soát (§3.3 & BR-22).

---

## 8. Quy tắc triển khai liên quan đến hợp đồng (Business Rules Reference)

1. **BR-01 (Phân quyền theo vai trò - RBAC)**: Vai trò `CONTENT_ADMIN` có toàn quyền quản lý điểm tham quan (UC8), quản lý nội dung thuyết minh gốc (UC1), tạo tác vụ thuyết minh đa ngôn ngữ (UC2) và quản lý tài khoản nội bộ (UC9). Khách tham quan không cần đăng nhập khi sử dụng các dịch vụ công khai (UC5, UC10, UC11, UC12, UC13, UC14).
2. **BR-02 (Ràng buộc nội dung thuyết minh gốc)**: Tiêu đề và văn bản thuyết minh gốc là bắt buộc, không được để rỗng. Văn bản tối đa 10.000 ký tự.
3. **BR-03 (Quy tắc ngôn ngữ & Giọng đọc)**: Ngôn ngữ đích phải thuộc danh mục được hệ thống hỗ trợ. Ngôn ngữ đích được phép trùng với ngôn ngữ nguồn; khi đó Translation Service thực hiện cơ chế pass-through (sao chép nguyên văn nội dung gốc, không gọi nhà cung cấp dịch) để vẫn tạo được file âm thanh cho ngôn ngữ nguồn. Danh mục ngôn ngữ đích và giọng đọc hợp lệ (`lang` – `voiceId`) do TTS Service cung cấp. Danh sách ngôn ngữ nguồn là cấu hình chung của hệ thống.
4. **BR-04 (Máy trạng thái Job và Target)**: Mỗi job có một trạng thái tổng thể; đồng thời mỗi ngôn ngữ đích (`target`) trong job có trạng thái xử lý độc lập.
   - Trạng thái Job: `PENDING` → `PROCESSING` → `COMPLETED` | `PARTIALLY_COMPLETED` | `FAILED`; nhánh hủy: `CANCELLED`.
   - Trạng thái Target: `PENDING` (0) → `TRANSLATING` (1) → `SYNTHESIZING` (2) → `PUBLISHED` (3) | `FAILED` | `CANCELLED`. Trạng thái chỉ được chuyển tiến lên bậc cao hơn. Target đã ở trạng thái cuối không bị thay đổi bởi bất kỳ sự kiện nào đến sau (ngoại trừ thao tác hủy job).
   - Tổng hợp Job: Chỉ tổng hợp khi tất cả target đều đã ở trạng thái cuối. Toàn bộ `PUBLISHED` → `COMPLETED`; có cả `PUBLISHED` và `FAILED` → `PARTIALLY_COMPLETED`; toàn bộ `FAILED` → `FAILED`.
   - Hủy Job: Chuyển job sang `CANCELLED` và mọi target chưa `FAILED` sang `CANCELLED`. Chỉ target `PUBLISHED` mới được phục vụ cho Khách tham quan.
5. **BR-05 (Giao tiếp bất đồng bộ & Xử lý lỗi RabbitMQ)**: Lỗi dữ liệu sai định dạng: nack không requeue, chuyển DLQ; Narration Service kết thúc target bằng timeout (BR-09). Lỗi gọi nhà cung cấp bên ngoài: retry tối đa 3 lần sau lần gọi đầu (tổng 4 lần gọi); nếu vẫn lỗi thì phát sự kiện thất bại (`.failed`), ack thông điệp, không đưa vào DLQ. Lỗi nội bộ: retry có giới hạn (tối đa 3 lần, có backoff); nếu vẫn lỗi thì chuyển DLQ.
6. **BR-06 (Xử lý Idempotent)**: Khóa định danh của bản dịch là `(contentId, version, targetLang)`; khóa định danh của file âm thanh là `(translationId, voiceId)`. Trong một job, mỗi ngôn ngữ đích là duy nhất. Khi tạo job mới hoặc retry, hệ thống tận dụng lại bản dịch và file âm thanh đã tồn tại cho cùng khóa; chỉ xử lý lại các phần chưa hoàn thành.
7. **BR-07 (Tránh xung đột Job & Quy tắc Retry)**: Không cho phép tồn tại đồng thời hai job ở trạng thái `PENDING` hoặc `PROCESSING` cho cùng `(contentId, version, targetLang)`. Khi có yêu cầu trùng, hệ thống trả mã lỗi 409 `ACTIVE_JOB_EXISTS` kèm danh sách `details.jobIds[]`. Để tạo lại (retry), Quản trị viên phải gửi yêu cầu mới kèm header `Idempotency-Key` mới và truyền `retryOfJobId` tham chiếu job xung đột. Narration Service xác thực 3 bước nghiêm ngặt: (1) job tham chiếu tồn tại và cùng `contentId`; (2) version của job tham chiếu bằng version hiện hành; (3) job tham chiếu có target tương ứng.
8. **BR-08 (Hạn dùng của URL có chữ ký - Signed URL)**: File âm thanh MP3 lưu trữ trong Object Storage được bảo mật; người dùng chỉ truy cập qua URL có chữ ký (Signed URL) do TTS Service sinh ra. Signed URL có thời hạn hiệu lực chính xác là **1 giờ (3.600 giây)**. Khi URL hết hạn, ứng dụng client tự động gọi lại API để lấy URL mới. Tuyệt đối không lưu URL có chữ ký vào database hoặc cache công khai.
9. **BR-09 (Timeout xử lý theo giai đoạn)**: Narration Service thiết lập thời gian chờ (timeout) độc lập cho từng giai đoạn: tối đa 15 phút cho dịch thuật và tối đa 15 phút cho tổng hợp giọng nói. Nếu quá thời gian chờ mà chưa nhận được sự kiện kết quả, Narration Service chủ động chuyển target đó sang trạng thái `FAILED` với mã lỗi `TIMEOUT` và tiến hành tổng hợp lại trạng thái job.
10. **BR-10 (Bảo toàn trạng thái khi Hủy Job)**: Khi nhận được yêu cầu hủy job hợp lệ, Narration Service đặt trạng thái job thành `CANCELLED`, chuyển mọi target chưa `FAILED` thành `CANCELLED` và phát sự kiện `narration.cancelled`. Các worker Translation và TTS kiểm tra danh sách job đã hủy trước khi xử lý, lưu trữ hoặc phát sự kiện kết quả để tránh lãng phí tài nguyên và không ghi đè kết quả của job đã hủy.
11. **BR-11 (Phiên bản phục vụ Khách tham quan)**: Khi phục vụ nội dung thuyết minh cho khách tham quan (`/api/v1/client/pois/{poiId}/narrations/**`), hệ thống lựa chọn bản ghi có target `PUBLISHED` thuộc phiên bản (**version**) lớn nhất của từng ngôn ngữ. Phiên bản phục vụ khách không bắt buộc phải trùng với phiên bản nội dung gốc hiện hành. Nếu một ngôn ngữ chưa có bản ghi `PUBLISHED`, hệ thống trả mã lỗi 404 `NARRATION_NOT_FOUND` kèm danh sách các ngôn ngữ đang khả dụng (`details.availableLangs`).
12. **BR-12 (Bảo mật tài khoản & Khóa nguyên tử)**: Mật khẩu người dùng được băm bằng thuật toán **bcrypt** (cost factor tối thiểu 10) trước khi lưu vào CSDL. Khi đăng nhập sai 5 lần liên tiếp, tài khoản tự động bị khóa trong **15 phút**. Thao tác tăng số lần sai và khóa tài khoản phải được thực hiện nguyên tử. Sau khi đăng nhập thành công hoặc hết hạn 15 phút hoặc được mở khóa thủ công, bộ đếm số lần sai được đặt lại về 0 và thời điểm khóa được xóa bỏ.
13. **BR-13 (Truy vết phân tán - Distributed Tracing)**: API Gateway tự động sinh `X-Correlation-ID` ở mọi request đầu vào. ID này bắt buộc phải được truyền xuyên suốt qua tất cả các HTTP headers nội bộ và RabbitMQ message headers. Mọi log của các services liên quan đều phải gắn kèm `correlationId` để phục vụ theo dõi và điều tra sự cố.
14. **BR-14 (Cập nhật - Điểm tham quan & Bản đồ số)**: Điểm tham quan bắt buộc có tọa độ địa lý `latitude` (-90 đến +90) và `longitude` (-180 đến +180) được gán từ thao tác nhấn trực tiếp trên bản đồ số. Tên điểm bắt buộc, tối đa 255 ký tự; mô tả tối đa 2.000 ký tự. Mã `qr_code` là chuỗi ngẫu nhiên khó đoán, duy nhất, do hệ thống tự sinh khi tạo mới và **bất biến (không thay đổi sau khi tạo)** để đảm bảo các biển bảng mã QR in tại hiện trường luôn có hiệu lực.
15. **BR-15 (Ràng buộc xóa Điểm tham quan)**: Tuyệt đối không được xóa điểm tham quan đang được bất kỳ bản ghi `CONTENTS` nào tham chiếu (kể cả nội dung đã bị xóa mềm). Khi vi phạm, hệ thống trả mã lỗi 409 `POI_HAS_CONTENT` và giữ nguyên dữ liệu. Điểm tham quan chưa từng có nội dung được xóa vật lý (không dùng xóa mềm).
16. **BR-16 (Quản lý Tài khoản nội bộ)**: `username` là duy nhất trên toàn hệ thống. Mật khẩu tối thiểu 8 ký tự. API tuyệt đối không bao giờ trả `passwordHash` hoặc `refreshToken` / `refreshTokenHash` về client. Khi người dùng đổi mật khẩu thành công, hệ thống thu hồi toàn bộ refresh token hiện có của tài khoản đó. Mở khóa tài khoản đặt `failedLoginCount = 0` và `lockedUntil = null`.
17. **BR-17 (Phát sự kiện tin cậy - Outbox Pattern)**: Khuyến nghị triển khai Transactional Outbox Pattern tại Content Service và Narration Service: thay đổi dữ liệu và bản ghi outbox được ghi trong cùng một database transaction; tiến trình relay đẩy sự kiện lên RabbitMQ và chỉ đánh dấu đã gửi sau khi broker confirm. Với Translation và TTS Service, message đầu vào chỉ được ack sau khi sự kiện kết quả đã được broker xác nhận.
18. **BR-18 (Mới - Tọa độ không gian & Geofence)**: Mỗi điểm tham quan được gán bán kính kích hoạt `trigger_radius` (mặc định 30m, cho phép cấu hình từ 10m đến 500m). Khi tính toán gợi ý điểm lân cận hoặc kích hoạt tự động cho khách tham quan, hệ thống sử dụng công thức không gian **Haversine Formula** hoặc Spatial Index trên Content DB để tính khoảng cách đường chim bay giữa tọa độ GPS của thiết bị khách và tọa độ POI, sắp xếp kết quả theo khoảng cách tăng dần.
19. **BR-19 (Mới - Quản lý Presence & Quyền riêng tư - Privacy-First)**: Số lượng người truy cập được theo dõi ẩn danh qua cơ chế **Redis Sorted Set (`ZSET`)** với **cửa sổ trượt (sliding window) chuẩn 120 giây** (loại bỏ hoàn toàn cơ chế key đơn lẻ TTL 45s). Thiết bị khách gửi heartbeat nhẹ định kỳ mỗi 30 giây (`clientId`, tùy chọn `isListening`, `poiId`, `lang`). Khi nhận heartbeat hoặc tính toán số liệu, hệ thống tự động loại bỏ các thành viên có `score < now - 120` (`ZREMRANGEBYSCORE`) và đếm số phần tử còn lại bằng `ZCARD`. Hệ thống không lưu trữ thông tin định danh cá nhân (PII), không lưu lịch sử vị trí GPS của khách; địa chỉ IP được ẩn danh hoặc băm (hash).
20. **BR-20 (Mới - Hiển thị Mã QR Điểm tham quan)**: Mỗi điểm tham quan cung cấp khả năng kết xuất ảnh mã QR độ phân giải cao qua endpoint công khai `GET /api/v1/client/pois/{poiId}/qr-image` để hiển thị to, rõ nét trên ứng dụng của khách tham quan mà không bị lỗi phân quyền 401; đồng thời cung cấp endpoint quản trị `GET /api/v1/pois/{poiId}/qr-image` cho Quản trị viên tải về in ấn. Mã QR chứa URL dẫn trực tiếp tới điểm tham quan hoặc chuỗi mã định danh `qr_code`.
21. **BR-21 (Mới - Hộp thuyết minh đa ngôn ngữ - Narration Box)**: Khi khách quét mã QR hoặc bấm nghe trực tiếp, Hộp thuyết minh đa ngôn ngữ (Narration Box) hiển thị đồng bộ hai thành phần:
   - Khối văn bản phụ đề (`subtitle`): Lấy từ bản dịch hoàn chỉnh của phiên bản `PUBLISHED` lớn nhất của ngôn ngữ được chọn.
   - Trình phát âm thanh (Audio Player): Tự động nạp file MP3 từ `audioUrl` (Signed URL có thời hạn 1 giờ).
   Khi khách chuyển đổi ngôn ngữ, hệ thống cập nhật tức thời cả nội dung phụ đề văn bản và luồng phát âm thanh tương ứng với ngôn ngữ mới.
22. **BR-22 (Mới - Tạo nhanh tích hợp - Atomic Quick Create)**: Thao tác tạo nhanh trên bản đồ số (`POST /api/v1/pois/quick-create-with-job`) tuân thủ nghiêm ngặt quy tắc giao dịch phân tán không deadlock:
   1. Content Service thực thi trong **1 local database transaction**: tạo POI (lưu `latitude`, `longitude`, `triggerRadius`, sinh `qrCode`) và tạo Content v1 (`title`, `sourceLang`, `textContent`, `createdBy`), sau đó **COMMIT transaction** và phát sự kiện `content.updated`.
   2. Sau khi commit thành công (đã đóng transaction CSDL để chống deadlock và cạn connection pool), Content Service gọi REST nội bộ `POST /internal/jobs` sang Narration Service kèm `Idempotency-Key` và `X-Correlation-ID`.
   3. Nếu Narration Service lỗi hoặc timeout (503), Content Service thực hiện **bước bù trừ (compensating transaction)** xóa POI và Content v1 vừa tạo, trả về lỗi 503 `NARRATION_SERVICE_UNAVAILABLE` để bảo đảm tính toàn vẹn trọn vẹn (all-or-nothing). Toàn bộ luồng thực hiện qua REST đồng bộ, **hoàn toàn không phát sự kiện `POI_CREATED` qua RabbitMQ**.
