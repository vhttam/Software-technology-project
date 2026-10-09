# API và Event Contracts — V1.2

Tài liệu này định nghĩa hợp đồng REST và RabbitMQ cho hệ thống thuyết minh tự động đa ngôn ngữ. Các quy tắc nghiệp vụ và máy trạng thái phải khớp với v2.5. API và event dùng camelCase; tên cột database dùng snake_case.

## 1. Quy ước chung

### 1.1. Base URL và headers

- Base URL: `/api/v1`.
- API cần xác thực nhận `Authorization: Bearer <accessToken>`.
- API Gateway sinh `X-Correlation-ID` cho mỗi request đầu vào rồi forward ID đó qua HTTP và message headers. Client không cần tự sinh header; Gateway là nơi xác lập ID truy vết tin cậy.
- Với request đã xác thực, Gateway chuyển `userId` và `role` tới service nội bộ qua header tin cậy (ví dụ `X-User-Id`, `X-User-Role`). Service phía sau chỉ nhận request từ Gateway; không dùng giá trị do client tự gửi để phân quyền hoặc ghi `createdBy`.
- Client có thể gửi `Idempotency-Key` khi tạo job. Header này bắt buộc với `POST /jobs`.
- API nội bộ chỉ cho service được xác thực gọi; không công khai trực tiếp ra Internet. Triển khai này dùng `X-Service-Token` cho lời gọi nội bộ tới Content, Translation và TTS; Gateway dùng `X-Gateway-Service-Token` khi gọi Narration Service và callback tiến độ dùng `X-Internal-Token`. Các token lấy từ secret cấu hình, không nhận từ client.
- Endpoint đăng nhập và các endpoint `/client` không yêu cầu access token.

### 1.2. Response envelope

Response thành công dùng envelope:

```json
{
  "status": "success",
  "data": {}
}
```

Response lỗi dùng envelope tùy biến thống nhất:

```json
{
  "status": "error",
  "errorCode": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "details": {},
  "correlationId": "c4f7..."
}
```

`details` có thể là `null`; lỗi validation có thể trả danh sách lỗi theo trường. HTTP status là nguồn xác định loại lỗi; `errorCode` là mã ổn định để client xử lý. Envelope này là quy ước của hệ thống, không gọi là JSend chuẩn.

### 1.3. HTTP status dùng chung

- `200 OK`: đọc hoặc cập nhật thành công.
- `201 Created`: tạo tài nguyên đồng bộ.
- `202 Accepted`: đã nhận yêu cầu xử lý bất đồng bộ.
- `204 No Content`: xóa thành công, không có response body.
- `400 Bad Request`: dữ liệu đầu vào sai.
- `401 Unauthorized`: thiếu hoặc token không hợp lệ/hết hạn.
- `403 Forbidden`: không đủ vai trò.
- `404 Not Found`: không tìm thấy tài nguyên.
- `409 Conflict`: xung đột trạng thái, version, khóa duy nhất hoặc idempotency key.
- `503 Service Unavailable`: service phụ thuộc tạm thời không khả dụng.

Ngày giờ dùng ISO 8601 UTC, ví dụ `2026-10-08T10:00:00Z`.

## 2. Auth Service

### 2.1. Đăng nhập

`POST /auth/login` — không cần access token.

Request:

```json
{
  "username": "admin",
  "password": "password123"
}
```

Response `200`:

```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1Ni...",
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

Access token là JWT ký bằng private key; Gateway xác thực bằng public key. Access token có hiệu lực 15 phút, refresh token 7 ngày. Sau 5 lần đăng nhập sai liên tiếp, Auth Service khóa tài khoản trong 15 phút. Việc tăng `failedLoginCount` và khóa ở ngưỡng phải cập nhật nguyên tử. Trong thời gian khóa, đăng nhập bị từ chối kể cả khi mật khẩu đúng và không tăng bộ đếm. Bộ đếm được đặt lại về 0, đồng thời xóa `lockedUntil`, khi đăng nhập thành công, khi hết thời gian khóa hoặc khi được mở khóa thủ công.

Endpoint đăng nhập trả refresh token cho ứng dụng theo UC7. Endpoint làm mới nhận refresh token và trả access token mới; v2.5 không quy định xoay refresh token. Token gốc chỉ được trả trong luồng xác thực, không bao giờ trả giá trị hash đang lưu trong database. API quản lý tài khoản tuyệt đối không trả `refreshToken`, `passwordHash` hoặc `refreshTokenHash`.

Lỗi:
- `401 INVALID_CREDENTIALS`: thông tin đăng nhập sai hoặc tài khoản đang bị khóa; message không tiết lộ tài khoản có tồn tại hay không.
- `503 AUTH_SERVICE_UNAVAILABLE`.

### 2.2. Làm mới access token

`POST /auth/refresh` — không cần access token.

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Response `200`: trả `accessToken`, `tokenType`, `expiresIn`. Refresh token được lưu dạng hash. Token không hợp lệ, hết hạn hoặc đã thu hồi trả `401 INVALID_REFRESH_TOKEN`.

### 2.3. Đăng xuất

`POST /auth/logout` — cần access token.

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Response `204`. Auth Service thu hồi refresh token; client xóa token cục bộ. Access JWT đã phát hành vẫn có hiệu lực tối đa đến lúc hết hạn (15 phút).

### 2.4. Tài khoản nội bộ

Các endpoint dưới đây cần `CONTENT_ADMIN`.

- `GET /auth/users?page=1&pageSize=20&query=admin&locked=true`: danh sách có phân trang; trường trả về gồm `userId`, `username`, `name`, `role`, `failedLoginCount`, `lockedUntil`, `createdAt`, `updatedAt`. Không trả `passwordHash` hoặc refresh token.
- `POST /auth/users`: tạo tài khoản. `username` bắt buộc và phải đúng định dạng hệ thống; `name` bắt buộc; `role` hiện chỉ nhận `CONTENT_ADMIN`; mật khẩu tối thiểu 8 ký tự.

Request:

```json
{
  "username": "mod_01",
  "name": "Nguyen Van A",
  "password": "SecurePassword1!",
  "role": "CONTENT_ADMIN"
}
```

Response `201` trả thông tin tài khoản không nhạy cảm. Username trùng trả `409 USERNAME_EXISTS`.

- `PUT /auth/users/{userId}/password`: quản trị viên đặt mật khẩu mới. Body: `{"newPassword":"..."}`. Nếu người dùng tự đổi mật khẩu của mình, bắt buộc thêm `currentPassword`. Cập nhật password hash và thu hồi refresh token của tài khoản. Response `204`.
- `PUT /auth/users/{userId}/unlock`: chỉ hợp lệ nếu tài khoản đang bị khóa. Đặt `failedLoginCount = 0`, `lockedUntil = null`. Response `200` với `{ "status":"success", "data":{"userId":"u-123","locked":false} }`. Không bị khóa trả `409 ACCOUNT_NOT_LOCKED`; không tồn tại trả `404 USER_NOT_FOUND`.

## 3. Content Service

Các endpoint quản trị cần `CONTENT_ADMIN`.

### 3.1. Điểm tham quan

- `GET /pois?page=1&pageSize=20&query=bao-tang`: danh sách phân trang.
- `POST /pois` tạo điểm tham quan; request gồm `name` (bắt buộc, tối đa 255 ký tự) và `description` (tối đa 2.000 ký tự). Hệ thống tự sinh `poiId` (UUID) và `qrCode` duy nhất. Response `201` trả bản ghi vừa tạo.
- `GET /pois/{poiId}` lấy chi tiết.
- `PUT /pois/{poiId}` cập nhật `name` và `description`; giữ nguyên `poiId` và `qrCode`.
- `DELETE /pois/{poiId}` xóa vật lý, response `204`. Nếu bất kỳ CONTENTS nào tham chiếu điểm này, kể cả nội dung đã xóa mềm, trả `409 POI_HAS_CONTENT`.

`qrCode` là chuỗi ngẫu nhiên khó đoán, duy nhất, dùng trong mã QR và được giữ nguyên khi cập nhật. Nếu sinh mã bị trùng, Content Service thử lại tối đa 3 lần; sau đó trả `500 QR_CODE_GENERATION_FAILED`.

### 3.2. Nội dung thuyết minh

Một nội dung có `contentId` ổn định và các version bất biến. Mỗi điểm tham quan chỉ có tối đa một nội dung chưa xóa mềm.

- `POST /pois/{poiId}/contents` tạo nội dung đầu tiên. Request gồm `title`, `sourceLang`, `textContent`. Văn bản không được rỗng và tối đa 10.000 ký tự. Response `201` trả `contentId`, `version: 1` và dữ liệu đã lưu. Nếu đã có nội dung hoạt động cho POI này, trả `409 ACTIVE_CONTENT_EXISTS`.
- `GET /contents?page=1&pageSize=20&query=bao-tang`: danh sách nội dung có phân trang và tìm kiếm để phục vụ UC1; có thể lọc theo POI. Mỗi mục trả thông tin POI, `contentId`, tiêu đề, ngôn ngữ nguồn, version hiện hành và trạng thái xóa mềm.
- `GET /pois/{poiId}/contents/active` lấy nội dung hoạt động và version hiện hành (version lớn nhất).
- `PUT /contents/{contentId}` tạo version mới, không ghi đè version cũ. Request phải có `baseVersion` cùng các trường nội dung cần cập nhật:

```json
{
  "baseVersion": 3,
  "title": "Giới thiệu bảo tàng",
  "sourceLang": "vi",
  "textContent": "Chào mừng quý khách..."
}
```

`title` là bắt buộc; `sourceLang` phải thuộc danh sách ngôn ngữ nguồn được cấu hình; `textContent` không được rỗng và tối đa 10.000 ký tự. Khi tạo nội dung, Content Service lấy `createdBy` từ danh tính nội bộ do Gateway chuyển tiếp, không nhận trường này từ client. Chỉ chấp nhận cập nhật nếu `baseVersion` bằng version hiện hành; version mới bằng `baseVersion + 1`. Nếu đã có người lưu trước hoặc hai lần lưu đồng thời tranh cùng version, trả `409 CONTENT_VERSION_CONFLICT` kèm `currentVersion`; không tự tăng version thay client và không ghi đè phiên bản cũ.

- `DELETE /contents/{contentId}` xóa mềm toàn bộ nội dung, response `204`. Nội dung bị xóa mềm không hiển thị cho khách và không nhận job mới. Job đang chạy vẫn có thể đọc đúng version đã chốt.

Content Service phát `content.updated` khi tạo version và `content.deleted` khi xóa mềm. Theo khuyến nghị BR-17, nếu triển khai Transactional Outbox thì thay đổi dữ liệu và bản ghi outbox được ghi trong cùng transaction; relay chỉ đánh dấu sự kiện đã gửi sau publisher confirm. Outbox là biện pháp khuyến nghị trong v2.5; timeout ở Narration Service vẫn phải xử lý trường hợp sự kiện không đến.

### 3.3. API nội bộ đọc version

`GET /internal/contents/{contentId}/versions/{version}` — chỉ service nội bộ được gọi. Trả `contentId`, `version`, `title`, `sourceLang`, `textContent`, `isDeleted`. API này vẫn trả nội dung của version được yêu cầu nếu nội dung bị xóa mềm sau khi job được tạo. Không dùng endpoint này để tạo job mới.

`POST /internal/contents/for-job` — chỉ Narration Service gọi để kiểm tra trước khi tạo job. Request gồm `contentId`; response trả version hiện hành và cờ `isDeleted`. Nội dung không tồn tại hoặc đã xóa mềm không đủ điều kiện tạo job. Đây là kiểm tra riêng với API đọc version bất biến ở trên.

`GET /internal/pois/by-qr/{qrCode}` — chỉ service nội bộ được gọi. Dùng cho UC5 để tra điểm tham quan theo mã QR và xác nhận nội dung đang hoạt động; response gồm `poiId`, `poiName`, `contentId`, `contentTitle`, `isDeleted`. POI hoặc nội dung không tồn tại/đã xóa mềm trả `404`.

## 4. Narration Service và cổng khách

### 4.1. Danh mục giọng đọc

`GET /internal/voices` — nội bộ. TTS Service cung cấp danh mục ngôn ngữ và voice hợp lệ.

```json
{
  "status": "success",
  "data": {
    "supportedVoices": [
      { "lang": "en", "voices": ["en-US-Journey-F", "en-GB-Standard-A"] }
    ]
  }
}
```

Cặp `lang`–`voiceId` phải hợp lệ theo danh mục này. Ngôn ngữ đích được phép trùng ngôn ngữ nguồn; khi đó Translation Service thực hiện pass-through.

`GET /api/v1/voices` — API quản trị cần `CONTENT_ADMIN`; Narration Service đọc danh mục từ TTS Service rồi trả `supportedVoices` cho giao diện UC2.

Translation Service cung cấp `GET /internal/translations/{translationId}` cho Narration Service và TTS Service. Response gồm `translationId`, `contentId`, `version`, `lang` và `textContent`; nội dung này được dùng làm phụ đề và đầu vào tổng hợp giọng nói.

TTS Service cung cấp `GET /internal/audios/{audioId}/signed-url` cho Narration Service. Response gồm `audioUrl` và `expiresAt`; URL hết hạn sau một giờ. Database chỉ lưu `objectKey`, không lưu URL có chữ ký.

### 4.2. Tạo job

`POST /jobs` — cần `CONTENT_ADMIN` và `Idempotency-Key`.

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

Client không gửi version: Narration Service xác nhận nội dung chưa bị xóa mềm và lấy version hiện hành từ Content Service; job lưu cố định version đó. Mỗi `lang` chỉ xuất hiện một lần trong `targets` và phải có đúng một `voiceId` tương ứng. Kiểm tra job trùng và tạo job diễn ra trong cùng transaction, có khóa theo `(contentId, version)`, để hai request đồng thời không cùng vượt qua bước kiểm tra.

Response `202`:

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

Job có thể chuyển sang `PROCESSING` ngay sau response. Cùng Idempotency-Key và cùng request trả lại job đã tạo; cùng key nhưng payload khác trả `409 IDEMPOTENCY_KEY_REUSED`. Khóa có phạm vi theo user và operation, được giữ ít nhất đến 24 giờ sau khi job kết thúc.

`retryOfJobId` là trường tùy chọn, chỉ dùng cho luồng “Tạo lại”. Nếu có job PENDING/PROCESSING cho cùng nội dung, version và ngôn ngữ, mặc định trả `409 ACTIVE_JOB_EXISTS` kèm `jobId`. Sau khi Actor xác nhận, client gửi request mới với Idempotency-Key mới và `retryOfJobId` trỏ tới job xung đột. Narration Service chỉ chấp nhận nếu job tham chiếu cùng nội dung/version; các bản dịch và audio đã có được dùng lại theo khóa `(contentId, version, lang)` và `(translationId, voiceId)`. Nếu version hiện hành đã đổi, trả `409 CONTENT_VERSION_CHANGED`; Actor phải tạo job cho version hiện hành.

Lỗi tạo job:
- `400 INVALID_TARGETS`: target rỗng/trùng, ngôn ngữ hoặc voice không hợp lệ.
- `404 CONTENT_NOT_FOUND`: nội dung không tồn tại hoặc đã xóa mềm.
- `503 CONTENT_SERVICE_UNAVAILABLE`.

### 4.3. Xem tiến độ

`GET /jobs/{jobId}` — cần `CONTENT_ADMIN`.

Response `200` gồm `jobId`, `contentId`, `version`, `status`, `createdAt`, `updatedAt`, `targets[]`; mỗi target gồm `targetId`, `lang`, `voiceId`, `status`, `errorCode` (nếu có), `updatedAt`. Job status: `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`. Target status: `PENDING`, `TRANSLATING`, `SYNTHESIZING`, `PUBLISHED`, `FAILED`, `CANCELLED`. Khi Narration Service phát `narration.requested`, job chuyển `PROCESSING` và target tương ứng chuyển `TRANSLATING`; `translation.completed` chuyển target tiến lên `SYNTHESIZING`, còn `tts.completed` chuyển target thành `PUBLISHED`, kể cả khi sự kiện dịch đến sau.

Target cuối (`PUBLISHED`, `FAILED`, `CANCELLED`) không đổi do event đến muộn; riêng thao tác hủy job có thể chuyển target `PUBLISHED` sang `CANCELLED`. Job tổng hợp khi tất cả target ở trạng thái cuối: tất cả PUBLISHED → `COMPLETED`; có cả PUBLISHED và FAILED → `PARTIALLY_COMPLETED`; tất cả FAILED → `FAILED`. Hủy job tạo trạng thái tổng `CANCELLED`.

Khi có target chưa ở trạng thái cuối, job giữ `PROCESSING`. Mọi cập nhật target, tổng hợp job và xử lý timeout phải khóa job trước rồi cập nhật target và job trong cùng transaction. Target `FAILED`, kể cả do `TIMEOUT`, không được hồi sinh bởi event thành công đến muộn.

### 4.4. Hủy job

`POST /jobs/{jobId}/cancel` — cần `CONTENT_ADMIN`.

Hủy job là thao tác tức thời, không có trạng thái `CANCEL_REQUESTED`. Chỉ hủy khi job đang `PENDING` hoặc `PROCESSING`. Trong một transaction có khóa job, đặt job thành `CANCELLED`; mọi target chưa `FAILED`, kể cả `PUBLISHED`, thành `CANCELLED`. Target đã `FAILED` giữ nguyên. Sau commit, phát `narration.cancelled`; Transactional Outbox được khuyến nghị theo BR-17.

Response `200` trả job cùng các target sau khi hủy. Job đã kết thúc trả `409 JOB_ALREADY_FINISHED` và không đổi dữ liệu; không tồn tại trả `404 JOB_NOT_FOUND`. Bản dịch/audio dùng chung không bị xóa. Target CANCELLED không được phục vụ cho khách.

### 4.5. Khách tham quan

Các endpoint `/api/v1/client` của API Gateway không yêu cầu đăng nhập. Gateway tra POI và nội dung đang hoạt động qua Content Service theo `qrCode`, sau đó gọi Narration Service và gộp kết quả với `poiId`, `poiName`, `contentTitle` để trả cho client. Gateway trả `404 POI_NOT_FOUND` nếu POI/nội dung không tồn tại hoặc đã xóa mềm.

- `GET /api/v1/client/pois/{qrCode}/narrations` — Gateway gọi `GET /internal/pois/by-qr/{qrCode}` của Content Service, rồi gọi `GET /internal/narrations?contentId={contentId}` của Narration Service. Response gồm `poiId`, `poiName`, `contentTitle` và danh sách ngôn ngữ có target `PUBLISHED`. Narration Service chọn version lớn nhất có target PUBLISHED theo từng ngôn ngữ; version phục vụ không bắt buộc trùng version hiện hành.
- `GET /api/v1/client/pois/{qrCode}/narrations/{lang}` — Gateway tra POI/content như trên rồi gọi `GET /internal/narrations/{lang}?contentId={contentId}`. Narration Service chọn target PUBLISHED có version lớn nhất cho ngôn ngữ đó, lấy phụ đề từ Translation Service và yêu cầu TTS Service sinh signed URL từ object key. Gateway gộp metadata POI/nội dung với artifact để trả cho client.

Hai endpoint `/internal/narrations` chỉ service nội bộ được gọi và yêu cầu service credential. Endpoint danh sách trả `narrations[]` gồm `lang` và `version`; endpoint theo ngôn ngữ trả `lang`, `version`, `subtitle`, `audioUrl`, `audioUrlExpiresAt`.

Response `200` của endpoint ngôn ngữ:

```json
{
  "status": "success",
  "data": {
    "poiId": "p-999",
    "poiName": "Bảo tàng Lịch sử",
    "contentTitle": "Giới thiệu bảo tàng",
    "lang": "en",
    "version": 3,
    "subtitle": "Welcome...",
    "audioUrl": "https://storage.example/audio.mp3?signature=...",
    "audioUrlExpiresAt": "2026-10-08T11:00:00Z"
  }
}
```

Signed URL hết hạn sau 1 giờ; sinh URL mới bằng cách gọi lại endpoint. Response chứa signed URL không được lưu trong database hoặc cache công khai. POI/nội dung không tồn tại hoặc đã xóa mềm trả `404 POI_NOT_FOUND`; ngôn ngữ chưa có target PUBLISHED trả `404 NARRATION_NOT_FOUND`.

### 4.6. Cập nhật tiến độ theo thời gian thực

Khi tiến độ hoặc kết quả job thay đổi, Narration Service gửi callback nội bộ tới API Gateway. Gateway chuyển thông báo tới Actor qua WebSocket hoặc SSE. Thông báo gồm `jobId`, trạng thái tổng job, các target vừa thay đổi và thời điểm cập nhật; có thể mang `correlationId` để truy vết. Kênh này là best-effort; khi mất kết nối, client lấy trạng thái chuẩn bằng `GET /jobs/{jobId}`. Callback chỉ service nội bộ được gọi.

Thông điệp tiến độ tối thiểu có dạng:

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

## 5. RabbitMQ event contract

### 5.1. Quy ước chung

- Exchange sự kiện: `narration.events` (`topic`, durable); routing key bằng tên event.
- Exchange hủy: `narration.cancel` (`fanout`).
- Event body dùng camelCase. Metadata đặt trong AMQP headers: `eventId` (UUID), `correlationId`, `schemaVersion`, `occurredAt` (UTC). `targetId` đặt trong body cho các event theo target.
- Publisher bật confirms; sự kiện có thể phát lặp (at-least-once). Consumer phải idempotent.
- Queue chính durable, message persistent, manual ack và prefetch cấu hình được. Message malformed: nack không requeue, chuyển DLQ. Provider failure sau retry phát event `.failed`, không đưa DLQ. DLQ được giám sát, giới hạn TTL tối đa 24 giờ và chỉ replay thủ công khi job chưa kết thúc, target chưa ở trạng thái cuối.
- Service chỉ ack message đầu vào sau khi kết quả đã được lưu bền vững và event kết quả đã được broker xác nhận. Outbox tại Narration và Content Service được khuyến nghị theo BR-17; Translation/TTS phải bảo đảm không mất event kết quả khi ack. Nếu không triển khai outbox, timeout ở Narration Service phải kết thúc target bị thiếu event thành `FAILED` với `TIMEOUT`.
- Consumer cancellation phải ghi nhận `jobId` đã hủy ở nơi lưu bền vững dùng chung cho các replica và kiểm tra trạng thái này trước khi gọi provider, lưu kết quả hoặc phát event kết quả. Danh sách job hủy phải tồn tại lâu hơn thời gian giữ DLQ (ví dụ 48 giờ khi DLQ tối đa 24 giờ). Không dựa riêng vào event fanout trong bộ nhớ; event hủy có thể đến trước/sau event công việc và replica có thể khởi động lại.

### 5.2. Event schemas

Payload tối thiểu theo từng routing key:

`content.updated` — Content Service → chưa có consumer bắt buộc, phát mỗi khi tạo version mới:

```json
{
  "contentId": "c-888",
  "version": 4
}
```

`content.deleted` — Content Service → chưa có consumer bắt buộc, phát khi xóa mềm nội dung:

```json
{
  "contentId": "c-888"
}
```

`narration.requested` — Narration Service → Translation Service, một message cho mỗi target:

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

`translation.completed` — Translation Service → TTS Service và Narration Service (mỗi service một queue riêng):

```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "translationId": "tr-222",
  "lang": "en",
  "voiceId": "en-US-Journey-F"
}
```

`translation.failed` — Translation Service → Narration Service:

```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "lang": "en",
  "errorCode": "PROVIDER_ERROR"
}
```

`tts.completed` — TTS Service → Narration Service:

```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "translationId": "tr-222",
  "audioId": "au-333",
  "lang": "en"
}
```

`tts.failed` — TTS Service → Narration Service:

```json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "lang": "en",
  "errorCode": "STORAGE_ERROR"
}
```

`narration.cancelled` — Narration Service → exchange fanout `narration.cancel`:

```json
{
  "jobId": "j-777"
}
```

`correlationId` của job nằm trong header metadata, không lặp trong body. `errorCode` thuộc enum: `TIMEOUT`, `PROVIDER_ERROR`, `CONTENT_NOT_FOUND`, `STORAGE_ERROR`, `INTERNAL_ERROR`. Lỗi do provider hết retry dùng `PROVIDER_ERROR`; timeout do Narration Service hết thời gian chờ dùng `TIMEOUT`.

### 5.3. Queue bindings

| Queue | Binding | Consumer |
|---|---|---|
| `translation.q.requested` | `narration.requested` | Translation Service |
| `tts.q.translation-completed` | `translation.completed` | TTS Service |
| `narration.q.translation-completed` | `translation.completed` | Narration Service |
| `narration.q.results` | `translation.failed`, `tts.completed`, `tts.failed` | Narration Service |
| Queue từng instance Translation Service | fanout exchange `narration.cancel` | Translation Service (exclusive, auto-delete) |
| Queue từng instance TTS Service | fanout exchange `narration.cancel` | TTS Service (exclusive, auto-delete) |

`translation.completed` phải được nhân bản vào queue riêng của TTS và Narration Service, không để hai service cạnh tranh đọc chung một queue. Có DLQ riêng cho từng queue công việc.

`content.updated` và `content.deleted` được phát lên `narration.events` nhưng hiện chưa có queue consumer bắt buộc.

## 6. Các quyết định triển khai cần giữ đúng hợp đồng

- Mọi cập nhật trạng thái target, tổng hợp job, timeout và hủy job khóa bản ghi job trước, sau đó mới cập nhật target trong cùng transaction.
- Timeout được cấu hình riêng cho giai đoạn dịch/TTS; giá trị khuyến nghị trong v2.5 là 15 phút mỗi giai đoạn. Khi timeout, target thành `FAILED` với `errorCode = TIMEOUT`.
- Lỗi provider hoặc dependency tạm thời được retry với backoff tối đa 3 lần sau lần gọi đầu (tối đa 4 lần gọi tổng cộng). Sau khi hết retry, phát event `.failed`; không đưa lỗi provider vào DLQ. Lỗi nội bộ có giới hạn retry riêng, không requeue vô hạn.
- TTS lưu object key, không lưu signed URL. Signed URL chỉ được tạo theo yêu cầu nghe.
- CI dùng Maven Wrapper (`./mvnw clean verify`) khi có thay đổi ở service tương ứng trên branch/PR; thành viên không cần cài Maven toàn máy. CD không tự chạy sau push: người vận hành khởi chạy workflow thủ công từ `main` sau khi CI thành công. Pipeline chép JAR qua SSH, chuyển symlink release và khởi động lại unit `systemd`; nếu health check `GET /actuator/health` thất bại thì khôi phục symlink về JAR trước đó. Quy trình không yêu cầu image hoặc Docker runtime.
