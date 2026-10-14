# Truy vết UC1 và API & Event Contracts V1.7

Nguồn đã đối chiếu: UC v2.8 trong `../docs/source/UC.docx` (UC1, UC7, B.1, BR-01, BR-07, BR-13) và contract trong `../docs/source/api-contracts.md` (§1, §2.1–2.4, §3.1–3.3). Trong checkout này contract nằm tại `docs/source/api-contracts.md`, không phải `docs/api-contracts.md` như đường dẫn trong yêu cầu.

`✅` nghĩa là phần trách nhiệm thuộc Gateway đã được triển khai và test. `⚠️` nghĩa là UC có hành vi ở service khác, không thuộc phạm vi source `api-gateway/`; Gateway chỉ xác minh việc định tuyến/chuyển tiếp.

## UC1

| UC1 / contract | Chủ sở hữu nghiệp vụ | Việc Gateway làm | Trạng thái và test |
|---|---|---|---|
| Tiền điều kiện `CONTENT_ADMIN`; BR-01, BR-07 | Auth/Gateway | JWT RS256, `kid`, claims bắt buộc; sai token 401, sai role 403; truyền danh tính qua header tin cậy | ✅ `malformedExpiredAndWrongAlgorithmTokensAlwaysReturnUnauthorized`, `wrongRoleIsForbiddenAndUnknownKidRefreshesJwksOnlyOnce` |
| Basic 1 — include UC7 đăng nhập | Auth Service | Proxy login; không yêu cầu token; chuyển nguyên 401 `INVALID_CREDENTIALS`; lỗi Auth tạo 503 | ✅ `authRoutesArePublicExceptLogoutAndForwardAuthErrors` |
| Basic 2 — hiện trang chủ | Frontend | Không có xử lý ở Gateway | ✅ Không có endpoint Gateway cần thêm |
| Basic 3 — Actor mở quản lý nội dung | Frontend | Nhận request tiếp theo tại route whitelist | ✅ kiểm thử route ở Basic 4 |
| Basic 4 — xem danh sách POI; E2 nếu Content lỗi | Gateway + Content | Xác thực, tạo correlation ID, proxy `GET /api/v1/pois`; lỗi kết nối/timeout hoặc 502–504 thành 503 | ✅ `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `downstreamGatewayStatusesAndConnectionResetBecomeContentUnavailable`, `downstreamResponseTimeoutBecomesContentUnavailable` |
| Basic 5 — Actor chọn/thay nội dung và nhập dữ liệu | Frontend | Không có xử lý nghiệp vụ | ✅ Không có endpoint Gateway cần thêm |
| Basic 6 — Actor chọn lưu | Frontend | Nhận POST/PUT tại route whitelist | ✅ `everyContentRouteForwardsMethodPathAndTrustedIdentity` |
| Basic 7, Extension Point, E1 — validation/400 theo field | Content Service | Không kiểm tra title, sourceLang hoặc text; chuyển nguyên status/body/header lỗi | ✅ `searchQueryAndRequestBodyAreForwardedWithoutGatewayBusinessValidation`, `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails` |
| Basic 8 — tạo version mới, optimistic locking, createdBy, `content.updated`; E3/E4 | Content Service | Chuyển POST/PUT nguyên vẹn; Content sở hữu version, DB, event và 409 | ✅ Proxy được test; ⚠️ lưu DB, version và phát event không thể xác minh vì Content Service không nằm trong source `api-gateway/` |
| A1 — tìm kiếm/lọc | Content Service | Chuyển nguyên query string, không diễn giải tham số | ✅ `searchQueryAndRequestBodyAreForwardedWithoutGatewayBusinessValidation` |
| A2 — xóa mềm, `content.deleted` | Content Service | Chuyển DELETE; giữ response 204 không body | ✅ `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails`; ⚠️ xóa mềm/event thuộc Content |
| A3 — Actor hủy thay đổi | Frontend | Không có xử lý ở Gateway | ✅ Không có endpoint Gateway cần thêm |
| E1 — lỗi validation Content | Content Service | Giữ nguyên 400 và `details` | ✅ `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails` |
| E2 — Content không phản hồi | Gateway | Trả envelope 503 `CONTENT_SERVICE_UNAVAILABLE` có correlation ID | ✅ `downstreamGatewayStatusesAndConnectionResetBecomeContentUnavailable`, `downstreamResponseTimeoutBecomesContentUnavailable` |
| E3 — đã có nội dung hoạt động | Content Service | Giữ nguyên 409 `ACTIVE_CONTENT_EXISTS` | ✅ `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails` |
| E4 — baseVersion xung đột; tải nội dung hiện hành | Content Service + Gateway | Chuyển nguyên 409/currentVersion; route GET và PUT content cho tải lại/gửi lưu lại | ✅ `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails`; ⚠️ nội dung hiện hành do Content trả |
| Post-condition — lưu DB hoặc xóa mềm và phát event | Content Service | Gateway chỉ chuyển tiếp response; không có DB/broker | ⚠️ Cần Content Service để xác minh hiệu ứng cuối |

## UC7 được include và `/health`

| Luồng | Route/quy tắc | Trạng thái và test |
|---|---|---|
| Login; UC7 E3 | `POST /api/v1/auth/login` công khai; lỗi Auth không phản hồi → `503 AUTH_SERVICE_UNAVAILABLE` | ✅ `authRoutesArePublicExceptLogoutAndForwardAuthErrors` |
| Refresh; UC7 A1 | `POST /api/v1/auth/refresh` công khai | ✅ `authRoutesArePublicExceptLogoutAndForwardAuthErrors` |
| JWKS | `GET /api/v1/auth/.well-known/jwks.json` công khai; xác thực protected request gọi thẳng Auth, không gọi vòng qua Gateway | ✅ `authRoutesArePublicExceptLogoutAndForwardAuthErrors`, `JwksKeyServiceTest` |
| Logout; UC7 A2 | `POST /api/v1/auth/logout` cần access token hợp lệ | ✅ `authRoutesArePublicExceptLogoutAndForwardAuthErrors` |
| UC6 smoke-test | `GET /health` công khai, trả `{"status":"UP"}` và không gọi service phụ thuộc | ✅ `healthStartsWithoutCallingAuthOrContent`, `HealthWithoutDependenciesTest#applicationStartsAndHealthWorksWhenAuthAndContentAreOffline` |

## Route whitelist 1–13

| # | Method + path | Auth | Test bảo vệ | Trạng thái |
|---|---|---|---|---|
| 1 | `GET /api/v1/languages/source` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity` | ✅ |
| 2 | `GET /api/v1/pois` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity` | ✅ |
| 3 | `POST /api/v1/pois/{poiId}/contents` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `searchQueryAndRequestBodyAreForwardedWithoutGatewayBusinessValidation` | ✅ |
| 4 | `GET /api/v1/pois/{poiId}/contents/active` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity` | ✅ |
| 5 | `GET /api/v1/contents` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `searchQueryAndRequestBodyAreForwardedWithoutGatewayBusinessValidation` | ✅ |
| 6 | `GET /api/v1/contents/{contentId}` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity` | ✅ |
| 7 | `PUT /api/v1/contents/{contentId}` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity` | ✅ |
| 8 | `DELETE /api/v1/contents/{contentId}` | `CONTENT_ADMIN` | `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails` | ✅ |
| 9 | `POST /api/v1/auth/login` | Công khai | `authRoutesArePublicExceptLogoutAndForwardAuthErrors` | ✅ |
| 10 | `POST /api/v1/auth/refresh` | Công khai | `authRoutesArePublicExceptLogoutAndForwardAuthErrors` | ✅ |
| 11 | `GET /api/v1/auth/.well-known/jwks.json` | Công khai | `authRoutesArePublicExceptLogoutAndForwardAuthErrors` | ✅ |
| 12 | `POST /api/v1/auth/logout` | Token hợp lệ | `authRoutesArePublicExceptLogoutAndForwardAuthErrors` | ✅ |
| 13 | `GET /health` | Công khai/local | `healthStartsWithoutCallingAuthOrContent`, `HealthWithoutDependenciesTest` | ✅ |

Mọi route được khai báo trong `application.yml`; cùng cấu hình đó được dùng để tạo route của Spring Cloud Gateway và kiểm tra whitelist/auth policy. Những path khác, method sai và `/internal/**` trả 404 `ROUTE_NOT_FOUND`; `unsupportedPathsAndMethodsAreJson404AndOnlyRealRoutePreflightPasses` bảo vệ hành vi này.

