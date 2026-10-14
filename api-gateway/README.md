# API Gateway — UC1 Quản lý nội dung thuyết minh

Gateway là biên HTTP của UC1: kiểm tra access token bằng public key, áp dụng role `CONTENT_ADMIN`, tạo `X-Correlation-ID` và định tuyến request tới Auth hoặc Content Service. Gateway không lưu dữ liệu và không kiểm tra nội dung nghiệp vụ; Content Service chịu trách nhiệm validation, version, xóa mềm và phát event.

## Công nghệ

- Java 17 bytecode; dùng Maven Wrapper 3.3.4 với Maven 3.9.11.
- Spring Boot 3.5.16, Spring Cloud 2025.0.3 và Spring Cloud Gateway WebFlux.
- Nimbus JOSE + JWT 10.0.2 để xác thực RS256/JWKS.
- Spring Boot 3.5.16 là bản OSS cuối của dòng 3.5; Spring Cloud 2025.0.3 là bản OSS cuối của train tương thích và hỗ trợ OSS kết thúc ngày 30-06-2026. Các phiên bản được chọn theo yêu cầu Boot 3.x, nhưng cần lập kế hoạch nâng cấp khi dự án cho phép.

## Chạy và kiểm tra sức khỏe

Chỉ cần JDK 17 trở lên. `/health` không gọi Auth/Content, vì vậy chạy được khi các service đó chưa khởi động.

```bash
./mvnw clean verify
./mvnw spring-boot:run
curl -i http://localhost:8080/health
```

Trên Windows PowerShell có thể chạy `./mvnw.cmd clean verify` và `./mvnw.cmd spring-boot:run`. Mặc định Gateway lắng nghe cổng 8080; Auth Service ở `http://localhost:8081`, Content Service ở `http://localhost:8082`.

Để chạy cùng hai service thật, đặt URL trước khi khởi động:

```bash
AUTH_SERVICE_URL=http://localhost:8081 \
CONTENT_SERVICE_URL=http://localhost:8082 \
./mvnw spring-boot:run
```

Trong PowerShell:

```powershell
$env:AUTH_SERVICE_URL = 'http://localhost:8081'
$env:CONTENT_SERVICE_URL = 'http://localhost:8082'
./mvnw.cmd spring-boot:run
```

`api-gateway/` không chứa Auth Service hay Content Service. Các địa chỉ mặc định chỉ là giá trị local; nếu không có service ở đó, route tương ứng trả 503 theo envelope của Gateway.

## Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `8080` | Cổng Gateway |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Base URL Auth, dùng cả proxy và tải JWKS trực tiếp |
| `CONTENT_SERVICE_URL` | `http://localhost:8082` | Base URL Content |
| `JWKS_TTL` | `10m` | Thời gian cache public key |
| `JWKS_UNKNOWN_KID_TTL` | `30s` | Negative-cache cho `kid` lạ; quyết định ngoài contract |
| `JWT_CLOCK_SKEW` | `30s` | Lệch đồng hồ tối đa khi kiểm tra JWT |
| `GATEWAY_CONNECT_TIMEOUT_MS` | `3000` | Connect timeout cho proxy, mili-giây |
| `GATEWAY_CONNECT_TIMEOUT` | `3s` | Connect timeout cho WebClient lấy JWKS |
| `GATEWAY_RESPONSE_TIMEOUT` | `30s` | Response timeout cho proxy và JWKS |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Danh sách origin, phân tách bằng dấu phẩy |

Các route, method, đích và chính sách xác thực nằm cùng nhau tại `src/main/resources/application.yml`. Gateway không cần database, RabbitMQ, Redis, service discovery hay secret ký JWT.

## Route whitelist

| # | Method + path | Quyền |
|---|---|---|
| 1 | `GET /api/v1/languages/source` | `CONTENT_ADMIN` |
| 2 | `GET /api/v1/pois` | `CONTENT_ADMIN` |
| 3 | `POST /api/v1/pois/{poiId}/contents` | `CONTENT_ADMIN` |
| 4 | `GET /api/v1/pois/{poiId}/contents/active` | `CONTENT_ADMIN` |
| 5 | `GET /api/v1/contents` | `CONTENT_ADMIN` |
| 6 | `GET /api/v1/contents/{contentId}` | `CONTENT_ADMIN` |
| 7 | `PUT /api/v1/contents/{contentId}` | `CONTENT_ADMIN` |
| 8 | `DELETE /api/v1/contents/{contentId}` | `CONTENT_ADMIN` |
| 9 | `POST /api/v1/auth/login` | Công khai |
| 10 | `POST /api/v1/auth/refresh` | Công khai |
| 11 | `GET /api/v1/auth/.well-known/jwks.json` | Công khai |
| 12 | `POST /api/v1/auth/logout` | JWT hợp lệ |
| 13 | `GET /health` | Công khai, Gateway tự trả |

Method/path khác, gồm mọi `/internal/**`, trả 404 `ROUTE_NOT_FOUND`. CORS preflight được trả 204 khi `OPTIONS` trỏ tới path whitelist và `Access-Control-Request-Method` là method có route thật; preflight không đi qua JWT.

## Ví dụ curl theo UC1

Ví dụ dưới đây cần Auth và Content Service triển khai đúng contract V1.7. Mật khẩu là dữ liệu phát triển trong ví dụ; không đặt secret hoặc private key trong repo.

```bash
# 1. Đăng nhập qua Auth Service
LOGIN_JSON=$(curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password123"}')
TOKEN=$(printf '%s' "$LOGIN_JSON" | jq -r '.data.accessToken')

# 2. UC1 bước 4: danh sách POI
curl -i 'http://localhost:8080/api/v1/pois?page=1&pageSize=20&query=bao-tang' \
  -H "Authorization: Bearer $TOKEN"

# Lấy poiId từ response trước khi tạo nội dung
POI_ID='<poiId từ danh sách POI>'
CONTENT_ID='<contentId trả về sau khi tạo>'

# 3. Tạo nội dung đầu tiên
curl -i -X POST "http://localhost:8080/api/v1/pois/$POI_ID/contents" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Giới thiệu bảo tàng","sourceLang":"vi","textContent":"Chào mừng quý khách..."}'

# 4. Sửa nội dung: baseVersion lấy từ bản ghi hiện hành
curl -i -X PUT "http://localhost:8080/api/v1/contents/$CONTENT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"baseVersion":1,"title":"Giới thiệu bảo tàng","sourceLang":"vi","textContent":"Nội dung cập nhật..."}'

# 5. Gửi lại baseVersion cũ: Content Service trả 409 CONTENT_VERSION_CONFLICT
curl -i -X PUT "http://localhost:8080/api/v1/contents/$CONTENT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"baseVersion":1,"title":"Bản sửa cũ","sourceLang":"vi","textContent":"Bản nháp của Actor..."}'

# 6. Xóa mềm; response thành công là 204 không body
curl -i -X DELETE "http://localhost:8080/api/v1/contents/$CONTENT_ID" \
  -H "Authorization: Bearer $TOKEN"

# 7. Khi Content Service tắt hoặc hết response timeout: 503 CONTENT_SERVICE_UNAVAILABLE
curl -i 'http://localhost:8080/api/v1/pois' -H "Authorization: Bearer $TOKEN"
```

Gateway giữ nguyên body/status/header của lỗi nghiệp vụ Content (400, 404, 409) và response thành công (200, 201, 204). Với lỗi kết nối/timeout hoặc response 502–504, Gateway tạo 503 envelope; Gateway không retry POST/PUT/DELETE.

## Tài liệu trong thư mục này

- [`docs/traceability-uc1.md`](docs/traceability-uc1.md): ánh xạ UC1, UC7 và từng route tới trách nhiệm Gateway và test.
- [`docs/files-explained.md`](docs/files-explained.md): lý do từng file tồn tại, luồng filter và các bẫy khi bảo trì.

