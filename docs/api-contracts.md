# API và Event Contracts — V1.6

Khớp với UC v2.7 (docs/UC.docx). Tài liệu định nghĩa hợp đồng REST và RabbitMQ cho hệ thống thuyết minh đa ngôn ngữ. API và event dùng camelCase; tên cột database dùng snake_case.

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
- L2: Bổ sung phản hồi 404 cho các tài nguyên không tồn tại/đã xóa mềm và 500 trong HTTP status chung.
- L3: Chốt NARRATION_NOT_FOUND cùng details.availableLangs tại Narration Service; Gateway chỉ chuyển tiếp.
- L5: Phân biệt tên POI nội bộ (`name`) với tên trong API khách (`poiName`).
- L6: Nêu ngoại lệ SSE dùng accessToken trong query thay cho Authorization header.

## 1. Quy ước chung

### 1.1. Base URL và headers

- Mọi API công khai có tiền tố /api/v1; mọi API nội bộ có tiền tố /internal.
- API cần xác thực nhận Authorization: Bearer <accessToken>. Ngoại lệ không cần access token: đăng nhập, làm mới token, JWKS và các API khách tham quan /api/v1/client/**. Riêng SSE /api/v1/jobs/{jobId}/events dùng accessToken trong query vì EventSource không gửi Authorization header; áp dụng HTTPS và loại query token khỏi access log.
- API Gateway sinh X-Correlation-ID cho mỗi request đầu vào rồi forward ID đó qua HTTP và message headers. Client không cần tự sinh header; Gateway là nơi xác lập ID truy vết tin cậy.
- API công khai chỉ nhận request qua API Gateway. API /internal nhận request từ service có X-Service-Token hợp lệ. Với request đã xác thực, Gateway chuyển userId và role tới service nội bộ qua header tin cậy X-User-Id và X-User-Role; service không dùng giá trị do client tự gửi để phân quyền hoặc ghi createdBy.
- Idempotency-Key bắt buộc với POST /api/v1/jobs. Thiếu header trả 400 IDEMPOTENCY_KEY_REQUIRED.
- Mọi lời gọi REST nội bộ dùng duy nhất header X-Service-Token. Giá trị là secret riêng cho từng service gọi; không dùng một secret chung cho mọi caller và không nhận token từ client. Service nhận kiểm tra secret tương ứng với service gọi.
- Endpoint quản trị cần role CONTENT_ADMIN. Các API khách tham quan và JWKS công khai không yêu cầu đăng nhập.

### 1.1.1. Caller của API nội bộ

| Caller | Endpoint nội bộ |
|---|---|
| API Gateway → Content Service | GET /internal/pois, GET /internal/pois/{poiId}/current, GET /internal/pois/by-qr/{qrCode} |
| API Gateway → Narration Service | GET /internal/narrations, GET /internal/narrations/{lang} |
| Narration Service → API Gateway | POST /internal/callbacks/job-progress |
| Narration Service → Content Service | GET /internal/contents/{contentId}/current |
| Translation Service → Content Service | GET /internal/contents/{contentId}/versions/{version} |
| Narration Service → TTS Service | GET /internal/voices, GET /internal/audios/{audioId}/signed-url |
| Narration Service và TTS Service → Translation Service | GET /internal/translations/{translationId} |

Mỗi request REST nội bộ xác thực caller bằng X-Service-Token riêng của service đó. Callback tiến độ là ngoại lệ về chiều gọi: Narration Service gọi API Gateway.

### 1.2. Response envelope

Response thành công dùng envelope:

~~~json
{
  "status": "success",
  "data": {}
}
~~~

Response lỗi dùng envelope:

~~~json
{
  "status": "error",
  "errorCode": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "details": {},
  "correlationId": "c4f7..."
}
~~~

details có thể là null; lỗi validation có thể trả danh sách lỗi theo trường. HTTP status là nguồn xác định loại lỗi; errorCode là mã ổn định để client xử lý. Envelope này là quy ước của hệ thống, không gọi là JSend chuẩn. Mã lỗi dùng UPPER_SNAKE_CASE.

### 1.3. HTTP status dùng chung

- 200 OK: đọc hoặc cập nhật thành công.
- 201 Created: tạo tài nguyên đồng bộ.
- 202 Accepted: đã nhận yêu cầu xử lý bất đồng bộ.
- 204 No Content: xóa thành công, không có response body.
- 400 Bad Request: dữ liệu đầu vào sai.
- 401 Unauthorized: thiếu hoặc token không hợp lệ/hết hạn.
- 403 Forbidden: không đủ vai trò.
- 404 Not Found: không tìm thấy tài nguyên.
- 409 Conflict: xung đột trạng thái, version, khóa duy nhất hoặc idempotency key.
- 500 Internal Server Error: lỗi nội bộ không dự kiến hoặc lỗi hệ thống không thể phục hồi.
- 503 Service Unavailable: service phụ thuộc tạm thời không khả dụng.

Ngày giờ dùng ISO 8601 UTC, ví dụ 2026-10-08T10:00:00Z.

## 2. Auth Service

### 2.1. Đăng nhập

POST /api/v1/auth/login — không cần access token.

Request:

~~~json
{
  "username": "admin",
  "password": "password123"
}
~~~

Response 200:

~~~json
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
~~~

Access token là JWT ký bằng private key theo thuật toán RS256; JWT header có kid khớp với một khóa trong JWKS. Claims bắt buộc gồm sub (= userId), role, iat và exp. Gateway chỉ chấp nhận các claim định danh này theo hợp đồng; không chấp nhận claim userId thay cho sub. Gateway xác thực bằng public key. Access token có hiệu lực 15 phút, refresh token 7 ngày. Sau 5 lần đăng nhập sai liên tiếp, Auth Service khóa tài khoản trong 15 phút. Việc tăng failedLoginCount và khóa ở ngưỡng phải cập nhật nguyên tử. Trong thời gian khóa, đăng nhập bị từ chối kể cả khi mật khẩu đúng và không tăng bộ đếm. Bộ đếm được đặt lại về 0, đồng thời xóa lockedUntil, khi đăng nhập thành công, khi hết thời gian khóa hoặc khi được mở khóa thủ công.

Endpoint đăng nhập trả refresh token cho ứng dụng theo UC7. Endpoint làm mới nhận refresh token và trả access token mới; UC v2.7 không quy định xoay refresh token. Token gốc chỉ được trả trong luồng xác thực, không bao giờ trả giá trị hash đang lưu trong database. API quản lý tài khoản tuyệt đối không trả refreshToken, passwordHash hoặc refreshTokenHash.

Lỗi:
- 401 INVALID_CREDENTIALS: thông tin đăng nhập sai hoặc tài khoản đang bị khóa; message không tiết lộ tài khoản có tồn tại hay không.
- 503 AUTH_SERVICE_UNAVAILABLE.

### 2.2. JWKS

GET /api/v1/auth/.well-known/jwks.json — không cần access token. Gateway lấy public key từ endpoint này để xác thực chữ ký JWT và chọn khóa theo kid.

Response 200:

~~~json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "key-2026-01",
      "n": "base64url-modulus",
      "e": "AQAB"
    }
  ]
}
~~~

kid trong JWT header phải khớp với kid của khóa dùng để ký. JWKS không chứa private key. Gateway cache public key theo kid, ví dụ 10 phút. Khi gặp kid chưa biết, Gateway tải lại JWKS đúng một lần rồi xác thực lại. Nếu Auth Service tạm thời lỗi, Gateway tiếp tục dùng key đã cache.

### 2.3. Làm mới access token

POST /api/v1/auth/refresh — không cần access token.

Request:

~~~json
{
  "refreshToken": "opaque-refresh-token"
}
~~~

Response 200 trả accessToken, tokenType và expiresIn. Refresh token được lưu dạng hash. Token không hợp lệ, hết hạn hoặc đã thu hồi trả 401 INVALID_REFRESH_TOKEN.

### 2.4. Đăng xuất

POST /api/v1/auth/logout — cần access token.

Request:

~~~json
{
  "refreshToken": "opaque-refresh-token"
}
~~~

Response 204. Auth Service thu hồi refresh token; client xóa token cục bộ. Access JWT đã phát hành vẫn có hiệu lực tối đa đến lúc hết hạn (15 phút).

### 2.5. Tài khoản nội bộ

Các endpoint dưới đây cần CONTENT_ADMIN.

- GET /api/v1/auth/users?page=1&pageSize=20&query=admin&locked=true: danh sách có phân trang; trường trả về gồm userId, username, name, role, failedLoginCount, lockedUntil, createdAt, updatedAt. Không trả passwordHash hoặc refresh token.
- POST /api/v1/auth/users: tạo tài khoản. username bắt buộc và phải đúng định dạng hệ thống; name bắt buộc; role hiện chỉ nhận CONTENT_ADMIN; mật khẩu tối thiểu 8 ký tự.

Request:

~~~json
{
  "username": "mod_01",
  "name": "Nguyen Van A",
  "password": "SecurePassword1!",
  "role": "CONTENT_ADMIN"
}
~~~

Response 201 trả thông tin tài khoản không nhạy cảm. Username trùng trả 409 USERNAME_EXISTS.

- PUT /api/v1/auth/users/{userId}/password: quản trị viên đặt mật khẩu mới. Body: {"newPassword":"..."}. Nếu người dùng tự đổi mật khẩu của mình, bắt buộc thêm currentPassword. Cập nhật password hash và thu hồi refresh token của tài khoản. Response 204.
  - Người dùng không tồn tại: 404 USER_NOT_FOUND.
  - Mật khẩu hiện tại không đúng khi tự đổi: 400 CURRENT_PASSWORD_INVALID; không thay đổi dữ liệu.
- PUT /api/v1/auth/users/{userId}/unlock: chỉ hợp lệ nếu tài khoản đang bị khóa. Đặt failedLoginCount = 0, lockedUntil = null. Response 200 với {"status":"success","data":{"userId":"u-123","locked":false}}. Không bị khóa trả 409 ACCOUNT_NOT_LOCKED; không tồn tại trả 404 USER_NOT_FOUND.

## 3. Content Service

Các endpoint quản trị cần CONTENT_ADMIN.

### 3.1. Ngôn ngữ nguồn

GET /api/v1/languages/source — cần CONTENT_ADMIN. Trả danh sách mã ngôn ngữ nguồn được hỗ trợ theo BR-03.

Response 200:

~~~json
{
  "status": "success",
  "data": {
    "languages": ["vi", "en", "fr"]
  }
}
~~~

### 3.2. Điểm tham quan

- GET /api/v1/pois?page=1&pageSize=20&query=bao-tang: danh sách phân trang.
- POST /api/v1/pois tạo điểm tham quan; request gồm name (bắt buộc, tối đa 255 ký tự) và description (tối đa 2.000 ký tự). Hệ thống tự sinh poiId (UUID) và qrCode duy nhất. Response 201 trả bản ghi vừa tạo.
- GET /api/v1/pois/{poiId} lấy chi tiết. POI không tồn tại trả 404 POI_NOT_FOUND.
- PUT /api/v1/pois/{poiId} cập nhật name và description; giữ nguyên poiId và qrCode. POI không tồn tại trả 404 POI_NOT_FOUND.
- DELETE /api/v1/pois/{poiId} xóa vật lý, response 204. POI không tồn tại trả 404 POI_NOT_FOUND. Nếu bất kỳ CONTENTS nào tham chiếu điểm này, kể cả nội dung đã xóa mềm, trả 409 POI_HAS_CONTENT.

qrCode là chuỗi ngẫu nhiên khó đoán, duy nhất, dùng trong mã QR và được giữ nguyên khi cập nhật. Nếu sinh mã bị trùng, Content Service thử lại tối đa 3 lần; sau đó trả 500 QR_CODE_GENERATION_FAILED.

### 3.3. Nội dung thuyết minh

Một nội dung có contentId ổn định và các version bất biến. Mỗi điểm tham quan chỉ có tối đa một nội dung chưa xóa mềm.

- POST /api/v1/pois/{poiId}/contents tạo nội dung đầu tiên. Request gồm title, sourceLang, textContent. Văn bản không được rỗng và tối đa 10.000 ký tự. Response 201 trả contentId, version: 1 và dữ liệu đã lưu. POI không tồn tại trả 404 POI_NOT_FOUND. Nếu đã có nội dung hoạt động cho POI này, trả 409 ACTIVE_CONTENT_EXISTS.
- GET /api/v1/contents?page=1&pageSize=20&query=bao-tang: danh sách nội dung có phân trang và tìm kiếm để phục vụ UC1; có thể lọc theo POI. Mỗi mục trả thông tin POI, contentId, tiêu đề, ngôn ngữ nguồn, version hiện hành và trạng thái xóa mềm.
- GET /api/v1/pois/{poiId}/contents/active lấy nội dung hoạt động và version hiện hành (version lớn nhất).
- GET /api/v1/contents/{contentId} — cần CONTENT_ADMIN. Trả đầy đủ dữ liệu của version hiện hành, gồm contentId, version, title, sourceLang và textContent; dùng để nạp lại văn bản khi Actor sửa nội dung hoặc cần tải lại sau 409 CONTENT_VERSION_CONFLICT.
- PUT /api/v1/contents/{contentId} tạo version mới, không ghi đè version cũ. Request phải có baseVersion cùng các trường nội dung cần cập nhật:

~~~json
{
  "baseVersion": 3,
  "title": "Giới thiệu bảo tàng",
  "sourceLang": "vi",
  "textContent": "Chào mừng quý khách..."
}
~~~

title là bắt buộc; sourceLang phải thuộc danh sách ngôn ngữ nguồn được cấu hình; textContent không được rỗng và tối đa 10.000 ký tự. Khi tạo nội dung, Content Service lấy createdBy từ danh tính nội bộ do Gateway chuyển tiếp, không nhận trường này từ client. Chỉ chấp nhận cập nhật nếu baseVersion bằng version hiện hành; version mới bằng baseVersion + 1. Nếu đã có người lưu trước hoặc hai lần lưu đồng thời tranh cùng version, trả 409 CONTENT_VERSION_CONFLICT kèm currentVersion; không tự tăng version thay client và không ghi đè phiên bản cũ.

GET, PUT và DELETE /api/v1/contents/{contentId} trả 404 CONTENT_NOT_FOUND nếu nội dung không tồn tại hoặc đã xóa mềm. DELETE xóa mềm toàn bộ nội dung, response 204. Nội dung bị xóa mềm không hiển thị cho khách và không nhận job mới; job đang chạy vẫn có thể đọc đúng version đã chốt.

Content Service phát content.updated khi tạo version và content.deleted khi xóa mềm. Theo khuyến nghị BR-17, nếu triển khai Transactional Outbox thì thay đổi dữ liệu và bản ghi outbox được ghi trong cùng transaction; relay chỉ đánh dấu sự kiện đã gửi sau publisher confirm. Outbox là biện pháp khuyến nghị trong UC v2.7; timeout ở Narration Service vẫn phải xử lý trường hợp sự kiện không đến.

### 3.4. API nội bộ đọc nội dung

GET /internal/contents/{contentId}/versions/{version} — chỉ service nội bộ được gọi. Trả contentId, version, title, sourceLang, textContent và isDeleted. API này vẫn trả nội dung của version được yêu cầu nếu nội dung bị xóa mềm sau khi job được tạo. Không dùng endpoint này để tạo job mới.

GET /internal/contents/{contentId}/current — chỉ Narration Service gọi để kiểm tra trước khi tạo job. Khi nội dung hiện hành tồn tại và chưa bị xóa mềm, response trả contentId, version và sourceLang; không trả isDeleted. Nội dung không tồn tại hoặc đã xóa mềm trả 404 CONTENT_NOT_FOUND. Nếu Content Service không phản hồi hoặc lỗi 5xx, Narration Service trả 503 CONTENT_SERVICE_UNAVAILABLE. Không tạo job khi kiểm tra thất bại.

GET /internal/pois?page=1&pageSize=20&query=bao-tang — chỉ API Gateway gọi để phục vụ GET /api/v1/client/pois. Trả items[] gồm poiId, name và description cùng page, pageSize, totalItems, totalPages; không trả qrCode. Chỉ liệt kê POI có nội dung đang hoạt động; query tìm theo name.

GET /internal/pois/{poiId}/current — chỉ API Gateway gọi để lấy POI và nội dung đang hoạt động phục vụ các API narration theo poiId. Response gồm poiId, poiName, contentId và contentTitle. POI hoặc nội dung không tồn tại/đã xóa mềm trả 404 POI_NOT_FOUND.

GET /internal/pois/by-qr/{qrCode} — chỉ API Gateway gọi để tra điểm tham quan theo mã QR và xác nhận nội dung đang hoạt động; response gồm poiId, poiName, contentId và contentTitle, không có isDeleted. POI hoặc nội dung không tồn tại/đã xóa mềm trả 404 POI_NOT_FOUND.

## 4. Narration Service và cổng khách

### 4.1. Danh mục giọng đọc và API phụ thuộc nội bộ

GET /internal/voices — nội bộ. TTS Service cung cấp danh mục ngôn ngữ và voice hợp lệ.

~~~json
{
  "status": "success",
  "data": {
    "supportedVoices": [
      { "lang": "en", "voices": ["en-US-Journey-F", "en-GB-Standard-A"] }
    ]
  }
}
~~~

Cặp lang–voiceId phải hợp lệ theo danh mục này. Ngôn ngữ đích được phép trùng ngôn ngữ nguồn; khi đó Translation Service thực hiện pass-through.

GET /api/v1/voices — cần CONTENT_ADMIN; Narration Service đọc danh mục từ TTS Service rồi trả supportedVoices cho giao diện UC2.

Translation Service cung cấp GET /internal/translations/{translationId} cho Narration Service và TTS Service. Response gồm translationId, contentId, version, lang và textContent; nội dung này được dùng làm phụ đề và đầu vào tổng hợp giọng nói.

TTS Service cung cấp GET /internal/audios/{audioId}/signed-url cho Narration Service. Response gồm audioUrl và expiresAt; URL hết hạn sau một giờ. Database chỉ lưu objectKey, không lưu URL có chữ ký.

### 4.2. Tạo job

POST /api/v1/jobs — cần CONTENT_ADMIN và Idempotency-Key.

~~~json
{
  "contentId": "c-888",
  "retryOfJobId": "j-previous",
  "targets": [
    { "lang": "en", "voiceId": "en-US-Journey-F" },
    { "lang": "fr", "voiceId": "fr-FR-Standard-A" }
  ]
}
~~~

Client không gửi version: Narration Service gọi GET /internal/contents/{contentId}/current để xác nhận nội dung tồn tại, chưa bị xóa mềm và lấy version hiện hành; job lưu cố định version đó. Mỗi lang chỉ xuất hiện một lần trong targets và có đúng một voiceId tương ứng. Kiểm tra job trùng và tạo job diễn ra trong cùng transaction, có khóa theo (contentId, version), để hai request đồng thời không cùng vượt qua bước kiểm tra.

Response 202:

~~~json
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
~~~

Job có thể chuyển sang PROCESSING ngay sau response. Cùng Idempotency-Key và cùng request trả lại job đã tạo; cùng key nhưng payload khác trả 409 IDEMPOTENCY_KEY_REUSED. Key có phạm vi theo user và operation, được giữ ít nhất đến 24 giờ sau khi job kết thúc.

Nếu có job PENDING/PROCESSING cho cùng contentId, version và ngôn ngữ, request không có retryOfJobId hợp lệ che đúng target đang xung đột trả 409 ACTIVE_JOB_EXISTS; details.jobIds[] chứa các jobId xung đột. Nếu một target còn xung đột với job khác không được retryOfJobId che, request vẫn trả 409 ACTIVE_JOB_EXISTS. Khi Actor xác nhận tạo lại, client gửi request mới với Idempotency-Key mới và retryOfJobId tham chiếu job xung đột. Kiểm tra retryOfJobId theo thứ tự: (1) job tham chiếu phải tồn tại và cùng contentId, nếu không trả 409 INVALID_RETRY_REFERENCE; (2) version của job tham chiếu phải bằng version hiện hành, nếu không trả 409 CONTENT_VERSION_CHANGED; (3) job tham chiếu phải có target tương ứng. Request hợp lệ bỏ qua ACTIVE_JOB_EXISTS cho target tương ứng. Job cũ không bị hủy và tiếp tục chạy. Job mới dùng lại bản dịch theo khóa (contentId, version, lang) và audio theo khóa (translationId, voiceId); chỉ thành phần chưa có hoặc lần xử lý trước thất bại mới cần xử lý lại.

Lỗi tạo job:
- 400 IDEMPOTENCY_KEY_REQUIRED: thiếu Idempotency-Key.
- 400 INVALID_IDEMPOTENCY_KEY: Idempotency-Key dài hơn 255 ký tự (giới hạn hiện có trong Narration Service).
- 400 INVALID_TARGETS: target rỗng/trùng, ngôn ngữ hoặc voice không hợp lệ.
- 404 CONTENT_NOT_FOUND: nội dung không tồn tại hoặc đã xóa mềm.
- 409 ACTIVE_JOB_EXISTS: job đang hoạt động trùng content/version/lang; details.jobIds[] là mảng các jobId xung đột.
- 409 IDEMPOTENCY_KEY_REUSED: cùng key nhưng payload khác.
- 409 CONTENT_VERSION_CHANGED: version hiện hành đã đổi so với job được tham chiếu khi tạo lại.
- 409 INVALID_RETRY_REFERENCE: job tham chiếu không tồn tại, khác contentId hoặc không có target tương ứng.
- 503 CONTENT_SERVICE_UNAVAILABLE.

### 4.3. Xem tiến độ

GET /api/v1/jobs/{jobId} — cần CONTENT_ADMIN. Job không tồn tại trả 404 JOB_NOT_FOUND.

Response 200 gồm jobId, contentId, version, status, createdAt, updatedAt, targets[]; mỗi target gồm targetId, lang, voiceId, status, errorCode (nếu có), updatedAt. Job status: PENDING, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED. Target status: PENDING, TRANSLATING, SYNTHESIZING, PUBLISHED, FAILED, CANCELLED. Khi Narration Service phát narration.requested, job chuyển PROCESSING và target tương ứng chuyển TRANSLATING; translation.completed chuyển target tiến lên SYNTHESIZING, còn tts.completed chuyển target thành PUBLISHED, kể cả khi sự kiện dịch đến sau.

Target cuối (PUBLISHED, FAILED, CANCELLED) không đổi do event đến muộn; riêng thao tác hủy job có thể chuyển target PUBLISHED sang CANCELLED. Job tổng hợp khi tất cả target ở trạng thái cuối: tất cả PUBLISHED → COMPLETED; có cả PUBLISHED và FAILED → PARTIALLY_COMPLETED; tất cả FAILED → FAILED. Hủy job tạo trạng thái tổng CANCELLED.

Khi có target chưa ở trạng thái cuối, job giữ PROCESSING. Mọi cập nhật target, tổng hợp job và xử lý timeout phải khóa job trước rồi cập nhật target và job trong cùng transaction. Target FAILED, kể cả do TIMEOUT, không được hồi sinh bởi event thành công đến muộn.

### 4.4. Hủy job

POST /api/v1/jobs/{jobId}/cancel — cần CONTENT_ADMIN.

Hủy job là thao tác tức thời, không có trạng thái CANCEL_REQUESTED. Chỉ hủy khi job đang PENDING hoặc PROCESSING. Trong một transaction có khóa job, đặt job thành CANCELLED; mọi target chưa FAILED, kể cả PUBLISHED, thành CANCELLED. Target đã FAILED giữ nguyên. Sau commit, phát narration.cancelled; Transactional Outbox được khuyến nghị theo BR-17.

Response 200 trả job cùng các target sau khi hủy. Job đã kết thúc trả 409 JOB_ALREADY_FINISHED và không đổi dữ liệu; không tồn tại trả 404 JOB_NOT_FOUND. Bản dịch/audio dùng chung không bị xóa. Target CANCELLED không được phục vụ cho khách.

### 4.5. Khách tham quan

Các API /api/v1/client không yêu cầu đăng nhập. Response được bọc trong success envelope. API Gateway lấy POI/content đang hoạt động qua Content Service, gọi Narration Service và gộp metadata với danh sách ngôn ngữ hoặc artifact. Danh sách ngôn ngữ chỉ xuất hiện sau khi khách chọn một POI.

- GET /api/v1/client/pois?page=1&pageSize=20&query=bao-tang: danh sách để khách duyệt, phân trang và tìm theo tên, chỉ gồm POI có nội dung hoạt động. data gồm items[], page, pageSize, totalItems và totalPages; mỗi mục có poiId, poiName và description; không trả qrCode. Gateway gọi GET /internal/pois và ánh xạ name thành poiName.
- GET /api/v1/client/qr/{qrCode}: dùng khi quét QR (UC5 A1), gọi GET /internal/pois/by-qr/{qrCode} và trả poiId để tiếp tục luồng lấy narration. QR/POI/nội dung không hợp lệ hoặc không có nội dung hoạt động trả 404 POI_NOT_FOUND. Ví dụ response thành công: `{"status":"success","data":{"poiId":"p-999"}}`. Nên giới hạn tần suất gọi endpoint này để giảm nguy cơ dò mã QR.
- GET /api/v1/client/pois/{poiId}/narrations: Gateway gọi GET /internal/pois/{poiId}/current để xác nhận POI/content hoạt động rồi gọi GET /internal/narrations?contentId={contentId}. Response gồm poiId, poiName, contentTitle và danh sách narrations có target PUBLISHED. Narration Service chọn version lớn nhất có target PUBLISHED theo từng ngôn ngữ; version phục vụ không bắt buộc trùng version hiện hành. Nếu POI đang hoạt động nhưng chưa có narration PUBLISHED, endpoint trả 200 với `"narrations": []`.
- GET /api/v1/client/pois/{poiId}/narrations/{lang}: Gateway gọi GET /internal/pois/{poiId}/current rồi GET /internal/narrations/{lang}?contentId={contentId}. Narration Service chọn target PUBLISHED có version lớn nhất cho ngôn ngữ đó, lấy phụ đề và signed URL. Gateway gộp metadata POI/nội dung với artifact để trả cho client.

Trong API quản trị Content, tên POI dùng trường name; API khách dùng poiName do Gateway gộp dữ liệu từ Content và Narration Service.

Response 200 của danh sách narration:

~~~json
{
  "status": "success",
  "data": {
    "poiId": "p-999",
    "poiName": "Bảo tàng Lịch sử",
    "contentTitle": "Giới thiệu bảo tàng",
    "narrations": [
      { "lang": "en", "version": 3 },
      { "lang": "vi", "version": 2 }
    ]
  }
}
~~~

Response 200 của narration theo ngôn ngữ:

~~~json
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
~~~

Signed URL hết hạn sau 1 giờ; sinh URL mới bằng cách gọi lại endpoint theo ngôn ngữ. Response chứa signed URL không được lưu trong database hoặc cache công khai. POI/nội dung không tồn tại hoặc đã xóa mềm trả 404 POI_NOT_FOUND. Ngôn ngữ không có narration PUBLISHED trả 404 NARRATION_NOT_FOUND với details.availableLangs là danh sách lang hiện có ở trạng thái PUBLISHED của nội dung; danh sách rỗng nếu chưa có ngôn ngữ nào.

~~~json
{
  "status": "error",
  "errorCode": "NARRATION_NOT_FOUND",
  "message": "Chưa có thuyết minh PUBLISHED cho ngôn ngữ đã chọn",
  "details": {
    "availableLangs": ["en", "vi"]
  },
  "correlationId": "c4f7..."
}
~~~

API nội bộ của Narration Service:
- GET /internal/narrations?contentId={contentId}: trả narrations[] gồm lang và version.
- GET /internal/narrations/{lang}?contentId={contentId}: trả lang, version, subtitle, audioUrl và audioUrlExpiresAt.

GET /internal/narrations/{lang} do Narration Service trả 404 NARRATION_NOT_FOUND kèm details.availableLangs nếu không có target PUBLISHED cho ngôn ngữ đó; API Gateway chỉ chuyển tiếp lỗi này. Các API nội bộ chỉ service được xác thực gọi bằng X-Service-Token.

### 4.6. Cập nhật tiến độ theo thời gian thực

Narration Service gửi callback nội bộ tới API Gateway bằng POST /internal/callbacks/job-progress. Request dùng X-Service-Token của Narration Service và X-Correlation-ID. Callback chỉ chứa tiến độ của một job:

~~~json
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
~~~

Client nhận SSE qua GET /api/v1/jobs/{jobId}/events?accessToken=<accessToken>. EventSource không gửi được Authorization header; Gateway kiểm tra access token JWT trong accessToken theo cùng quy tắc chữ ký, kid, hạn dùng và role CONTENT_ADMIN của API có xác thực. Query token chỉ dùng qua HTTPS và phải được loại khỏi access log. Response dùng Content-Type: text/event-stream. Khi mở stream, Gateway đăng ký kết nối theo jobId; callback chỉ được gửi tới các stream đã đăng ký đúng jobId, không broadcast tiến độ sang job khác.

Gateway không giữ callback để phát lại cho client mất kết nối; SSE là best-effort. Giả định triển khai một instance API Gateway; nếu chạy nhiều instance cần sticky routing hoặc pub/sub để callback tới đúng instance giữ stream. EventSource tự kết nối lại bằng URL cũ, nên khi access token hết hạn stream nhận 401; sau khi refresh token, client phải đóng và tạo EventSource mới với accessToken mới. Client lấy trạng thái chuẩn bằng GET /api/v1/jobs/{jobId}. Callback nội bộ trả 200 khi nhận; nếu không có stream đăng ký cho jobId thì vẫn trả 200 và bỏ qua callback; X-Service-Token sai trả 401.

## 5. RabbitMQ event contract

### 5.1. Quy ước chung

- Exchange sự kiện: narration.events (topic, durable); routing key bằng tên event.
- Exchange hủy: narration.cancel (fanout).
- Event body dùng camelCase. Metadata đặt trong AMQP headers: eventId (UUID), correlationId, schemaVersion, occurredAt (UTC). targetId đặt trong body cho các event theo target.
- Publisher bật confirms; sự kiện có thể phát lặp (at-least-once). Consumer phải idempotent.
- Queue chính durable, message persistent, manual ack và prefetch cấu hình được. Message malformed: nack không requeue, chuyển DLQ. Provider failure sau tối đa 3 lần retry (tổng tối đa 4 lần gọi tính cả lần đầu) phát event .failed, không đưa DLQ. DLQ được giám sát, giới hạn TTL tối đa 24 giờ và chỉ replay thủ công khi job chưa kết thúc, target chưa ở trạng thái cuối.
- Service chỉ ack message đầu vào sau khi kết quả đã được lưu bền vững và event kết quả đã được broker xác nhận. Outbox tại Narration và Content Service được khuyến nghị theo BR-17; Translation/TTS phải bảo đảm không mất event kết quả khi ack. Nếu không triển khai outbox, timeout ở Narration Service phải kết thúc target bị thiếu event thành FAILED với TIMEOUT.
- Consumer cancellation phải ghi nhận jobId đã hủy ở nơi lưu bền vững dùng chung cho các replica và kiểm tra trạng thái này trước khi gọi provider, lưu kết quả hoặc phát event kết quả. Danh sách job hủy phải tồn tại lâu hơn thời gian giữ DLQ (ví dụ 48 giờ khi DLQ tối đa 24 giờ). Không dựa riêng vào event fanout trong bộ nhớ; event hủy có thể đến trước/sau event công việc và replica có thể khởi động lại.
- Nội dung và metadata của REST nội bộ dùng X-Service-Token theo §1.1; cơ chế này không thay thế metadata headers của RabbitMQ.

### 5.2. Event schemas

Payload tối thiểu theo từng routing key:

content.updated — Content Service phát mỗi khi tạo version mới:

~~~json
{
  "contentId": "c-888",
  "version": 4
}
~~~

content.deleted — Content Service phát khi xóa mềm nội dung:

~~~json
{
  "contentId": "c-888"
}
~~~

narration.requested — Narration Service gửi Translation Service một message cho mỗi target:

~~~json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "contentId": "c-888",
  "version": 3,
  "lang": "en",
  "voiceId": "en-US-Journey-F"
}
~~~

translation.completed — Translation Service gửi tới TTS Service và Narration Service qua hai queue riêng:

~~~json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "translationId": "tr-222",
  "lang": "en",
  "voiceId": "en-US-Journey-F"
}
~~~

translation.failed — Translation Service gửi Narration Service:

~~~json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "lang": "en",
  "errorCode": "PROVIDER_ERROR"
}
~~~

tts.completed — TTS Service gửi Narration Service:

~~~json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "translationId": "tr-222",
  "audioId": "au-333",
  "lang": "en"
}
~~~

tts.failed — TTS Service gửi Narration Service:

~~~json
{
  "jobId": "j-777",
  "targetId": "t-111",
  "lang": "en",
  "errorCode": "STORAGE_ERROR"
}
~~~

narration.cancelled — Narration Service gửi lên exchange fanout narration.cancel:

~~~json
{
  "jobId": "j-777"
}
~~~

correlationId của job nằm trong metadata header, không lặp trong payload. errorCode thuộc enum: TIMEOUT, PROVIDER_ERROR, CONTENT_NOT_FOUND, STORAGE_ERROR, INTERNAL_ERROR. Lỗi do provider hết lượt retry dùng PROVIDER_ERROR; timeout do Narration Service hết thời gian chờ dùng TIMEOUT.

### 5.3. Queue bindings

| Queue | Binding | Consumer |
|---|---|---|
| translation.q.requested | narration.requested | Translation Service |
| tts.q.translation-completed | translation.completed | TTS Service |
| narration.q.translation-completed | translation.completed | Narration Service |
| narration.q.results | translation.failed, tts.completed, tts.failed | Narration Service |
| Queue từng instance Translation Service | exchange narration.cancel | Translation Service (exclusive, auto-delete) |
| Queue từng instance TTS Service | exchange narration.cancel | TTS Service (exclusive, auto-delete) |

translation.completed phải được nhân bản vào queue riêng của TTS và Narration Service, không để hai service cạnh tranh đọc chung một queue. Có DLQ riêng cho từng queue công việc.

content.updated và content.deleted được phát lên narration.events nhưng hiện chưa có queue consumer bắt buộc.

## 6. Quy tắc triển khai liên quan tới hợp đồng

- Mọi cập nhật trạng thái target, tổng hợp job, timeout và hủy job khóa bản ghi job trước, sau đó mới cập nhật target trong cùng transaction.
- Timeout được cấu hình riêng cho giai đoạn dịch/TTS; giá trị khuyến nghị trong UC v2.7 là 15 phút mỗi giai đoạn. Khi timeout, target thành FAILED với errorCode = TIMEOUT.
- Lỗi từ API/Provider bên ngoài được retry tối đa 3 lần sau lần gọi đầu, tổng tối đa 4 lần gọi. Sau khi hết retry, phát event .failed; không đưa lỗi provider vào DLQ. Lỗi nội bộ có giới hạn retry riêng, không requeue vô hạn.
- TTS lưu objectKey, không lưu signed URL. Signed URL chỉ được tạo theo yêu cầu nghe.
