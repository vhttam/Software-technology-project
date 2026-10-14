# Giải thích file trong API Gateway

Các test dưới đây chạy bằng `./mvnw clean verify`, dùng RSA sinh trong test và MockWebServer cho Auth/Content. Không cần service ngoài.

## Luồng một request

```text
Client
  → RequestObservabilityWebFilter (đầu tiên): tạo UUID mới, ghi response header và bắt đầu thời gian
  → TrustedHeadersWebFilter: bỏ X-User-Id, X-User-Role, X-Service-Token, X-Correlation-ID do client gửi
  → RoutePolicyWebFilter: chọn route/auth policy; preflight hợp lệ trả 204; path/method không có route trả 404
  → RouteConfiguration: Spring Cloud Gateway định tuyến đúng Auth hoặc Content
  → JwtAuthenticationGlobalFilter (-100): route công khai bỏ Authorization; route cần token kiểm tra JWT;
      lỗi token trả 401, sai role trả 403, JWKS chưa thể lấy trả 503 AUTH_SERVICE_UNAVAILABLE
  → downstream: token không được chuyển tiếp; danh tính hợp lệ và correlation ID được đặt vào header tin cậy
  → DownstreamStatusMappingGlobalFilter: response 502/503/504 của đích đổi thành 503 envelope;
      các status/header/body khác được giữ nguyên
  → Client: response header X-Correlation-ID do Gateway xác lập; một dòng log request có status/thời gian/userId

Ngoại lệ khi đích không trả HTTP hợp lệ: GatewayExceptionHandler đổi lỗi kết nối/timeout thành 503 theo đích;
lỗi Gateway không dự kiến thành 500 INTERNAL_ERROR. /health do HealthController tự trả 200, không gọi service.
```

## Vì sao Gateway tồn tại

Client chỉ cần gọi một địa chỉ API thay vì biết địa chỉ từng service.
Gateway xác minh chữ ký JWT bằng public key mà không gọi Auth cho mỗi request.
Gateway kiểm tra role coarse-grained trước khi request tới Content.
Gateway loại header định danh giả mạo và gửi danh tính đã xác minh cho service.
Correlation ID giúp nối log request qua nhiều service.
Gateway áp dụng whitelist và xử lý lỗi kết nối thống nhất.
Nội dung, version, database và event vẫn thuộc Content Service.

## Bẫy thường gặp khi sửa Gateway

- Đổi thứ tự filter có thể làm lỗi 401/403/404 mất correlation ID hoặc làm log thiếu routeId.
- Không được tin `X-User-Id`, `X-User-Role`, `X-Service-Token` hay `X-Correlation-ID` từ Client.
- Không chuyển `Authorization` tới service sau khi Gateway xác thực; Auth logout nhận danh tính từ header tin cậy.
- Đừng parse/chỉnh response JSON của Content; lỗi field, `currentVersion`, 204 và 409 phải còn nguyên.
- Retry POST/PUT/DELETE có thể tạo version hoặc thao tác trùng; HttpClient của Gateway tắt retry tự động.
- JWKS lấy trực tiếp từ `AUTH_SERVICE_URL`; gọi qua Gateway sẽ tạo vòng lặp.
- Key cache đã quá TTL vẫn dùng được nếu Auth lỗi khi refresh; `kid` lạ chỉ refresh một lần và có negative-cache ngắn.
- `/health` nằm ngoài `/api/v1`; không thêm phụ thuộc Auth/Content vào health check.
- Mọi route mới phải thêm vào danh sách `gateway.routes` trong `application.yml`, kèm method/path/target/authentication.
- Không đưa body, password, bearer token hoặc query `accessToken` vào log.

## Quyết định ngoài contract

1. Dùng mã lỗi Gateway `UNAUTHORIZED`, `FORBIDDEN`, `ROUTE_NOT_FOUND`, `INTERNAL_ERROR`, `CONTENT_SERVICE_UNAVAILABLE` và `AUTH_SERVICE_UNAVAILABLE`; contract quy định envelope/status nhưng không định nghĩa đầy đủ các mã Gateway này.
2. Map lỗi kết nối/DNS/reset/timeout và response downstream 502/503/504 sang HTTP 503; body 503 do Gateway tạo có `details: null`.
3. Không forward `Authorization` xuống Auth/Content. Logout vẫn phải qua xác thực JWT tại Gateway, sau đó Auth nhận `X-User-Id` và `X-User-Role` tin cậy.
4. CORS cho phép origin cấu hình được, GET/POST/PUT/DELETE/OPTIONS, header Authorization/Content-Type/Idempotency-Key và expose X-Correlation-ID. Mặc định local origin là `http://localhost:3000`.
5. Preflight OPTIONS chỉ được trả thành công nếu URL trỏ tới route whitelist và requested method khớp route thật; các method/path khác vẫn 404.
6. JWKS negative-cache cho `kid` lạ tồn tại 30 giây để giảm tải khi bị gửi nhiều kid ngẫu nhiên.
7. Leeway JWT mặc định 30 giây; JWKS TTL mặc định 10 phút; connect timeout mặc định 3 giây và response timeout 30 giây.
8. Correlation ID trong envelope lỗi Gateway luôn là ID do Gateway sinh. Khi proxy body lỗi từ service, body được giữ nguyên theo yêu cầu minh bạch; correlation ID đáng tin vẫn nằm trong response header và request header gửi xuống service.

## Bảng giải thích file

| Đường dẫn | Vì sao phải có (xóa đi thì hỏng gì) | Công dụng trong luồng UC1/contract | Lưu ý | Test nào bảo vệ |
|---|---|---|---|---|
| `.gitignore` | Tránh commit output build/IDE | Chỉ ảnh hưởng working tree | Giữ `/target/` khỏi Git | `clean verify` sinh target |
| `pom.xml` | Khai báo Java/Spring, Gateway, Nimbus và test runtime | Tạo ứng dụng WebFlux; xác thực §2; định tuyến §3 | Boot 3.5.16, Cloud 2025.0.3; không thêm DB/broker | Toàn bộ suite |
| `mvnw` | Cho Linux/CI build mà không cần cài Maven | UC6 dùng Maven Wrapper | Giữ executable bit khi commit; Maven 3.9.11 được pin trong properties | `./mvnw clean verify` |
| `mvnw.cmd` | Wrapper tương ứng cho Windows | Chạy cùng build trên máy phát triển | Không thay bằng Maven cài ngoài | `./mvnw.cmd clean verify` |
| `.mvn/wrapper/maven-wrapper.properties` | Pin wrapper/Maven; thiếu thì wrapper không biết phiên bản tải | Reproducible build UC6 | Chỉ tải Apache Maven distribution, không chứa secret | Wrapper chạy trong mỗi verify |
| `.mvn/wrapper/maven-wrapper.jar` | Khởi động Maven Wrapper | Bootstrap lệnh build | Binary do Apache Maven Wrapper phát hành; không sửa tay | Wrapper chạy trong mỗi verify |
| `src/main/resources/application.yml` | Nơi cấu hình cổng, URL, timeout, TTL, CORS và route whitelist | Routes 1–13; §1–§3; UC6 `/health` | Một nguồn route/auth policy; URL local là placeholder | Mọi kiểm thử route, CORS, health |
| `src/main/java/vn/edu/cnpm/gateway/ApiGatewayApplication.java` | Điểm khởi động Spring; thiếu thì JAR không chạy | Khởi tạo Gateway UC1 | Không gọi service hoặc tải JWKS khi startup | `HealthWithoutDependenciesTest` |
| `.../config/GatewayProperties.java` | Bind cấu hình có kiểu dữ liệu; tránh URL/policy hard-code trong filter | Route target, role, TTL, timeout, CORS | Thêm route qua YAML, không nhân đôi policy trong code | `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `HealthWithoutDependenciesTest` |
| `.../config/RouteConfiguration.java` | Tạo proxy routes cho Auth/Content từ whitelist | Routes 1–12; §2.1–2.4, §3.1–3.3 | Giữ path/query; health là controller local | `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `authRoutesArePublicExceptLogoutAndForwardAuthErrors` |
| `.../config/JwksWebClientConfiguration.java` | Cần HTTP client riêng lấy JWKS trực tiếp từ Auth | §2.2, BR-07 | Lazy; timeout riêng; không gọi vòng qua Gateway | `JwksKeyServiceTest`, JWT integration tests |
| `.../config/GatewayHttpClientCustomizer.java` | Tắt retry tự động của HTTP client proxy | Proxy nguyên vẹn, không lặp POST/PUT/DELETE | Không bật retry ở route hoặc client khác | `downstreamGatewayStatusesAndConnectionResetBecomeContentUnavailable` |
| `.../filter/GatewayAttributes.java` | Tên chung cho dữ liệu trao đổi giữa WebFilter và GlobalFilter | Correlation, route, userId/role | Không lấy giá trị tương ứng từ header client | Mọi integration test |
| `.../filter/RequestObservabilityWebFilter.java` | Sinh correlation ID trước các filter và log request | BR-13, §1.1; mọi route/error | Log path đã lọc `accessToken`; tuyệt đối không log body/token | `healthStartsWithoutCallingAuthOrContentAndCreatesCorrelationIds`, `accessTokenPasswordAndBodyAreNeverWrittenToRequestLogs` |
| `.../filter/TrustedHeadersWebFilter.java` | Loại header định danh do client giả mạo | §1.1, BR-07 | Chạy trước whitelist để áp dụng cho mọi request | `everyContentRouteForwardsMethodPathAndTrustedIdentity`, `authRoutesArePublicExceptLogoutAndForwardAuthErrors` |
| `.../filter/RoutePolicyWebFilter.java` | Từ chối method/path ngoài whitelist và xử lý CORS preflight | ROUTE_NOT_FOUND; G8; routes 1–13 | Chỉ preflight của route thật mới 204 | `unsupportedPathsAndMethodsAreJson404AndOnlyRealRoutePreflightPasses` |
| `.../filter/JwtAuthenticationGlobalFilter.java` | Thực thi auth policy cho route proxy | §1.1, §2.1–2.4, BR-01/07 | `Authorization` không forward; token hợp lệ mới tạo X-User-* | JWT, role, Auth route tests |
| `.../filter/DownstreamStatusMappingGlobalFilter.java` | Thay response 502–504 thành envelope 503 | E2, UC7 E3; G6 | Mọi response khác giữ nguyên; không parse JSON | `downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails`, `downstreamGatewayStatusesAndConnectionResetBecomeContentUnavailable` |
| `.../security/AuthenticatedUser.java` | Biểu diễn `sub` và `role` đã xác minh | §1.1 trusted headers | Không dùng claim `userId` thay `sub` | JWT/role tests |
| `.../security/JwksKeyService.java` | Tải, cache, refresh và single-flight public key | §2.2, BR-07 | Cache theo kid; stale key khi Auth lỗi; negative-cache 30s | `JwksKeyServiceTest`, rotation/concurrency integration tests |
| `.../security/JwtValidator.java` | Kiểm tra RS256, chữ ký, kid, sub/role/iat/exp và thời gian | §2.1–2.2 | Lệch giờ 30s mặc định; mọi lỗi token thành 401 | `malformedExpiredAndWrongAlgorithmTokensAlwaysReturnUnauthorized`, role test |
| `.../error/GatewayErrorEnvelope.java` | Chuẩn hóa shape JSON lỗi Gateway | §1.2 envelope | `details` là null với lỗi Gateway | 401/403/404/503 assertions |
| `.../error/GatewayErrorWriter.java` | Ghi JSON lỗi có correlation ID và status đúng | §1.2, G7 | Không gửi exception hoặc stack trace ra client | JWT, unknown route, outage tests |
| `.../error/InvalidTokenException.java` | Phân biệt token lỗi với lỗi Gateway bất ngờ | §2.1–2.2 | Filter chuyển thành 401 | JWT rejection tests |
| `.../error/UnknownKeyException.java` | Biểu diễn kid không tồn tại sau refresh JWKS | §2.2 | Kết thúc 401, negative-cache giới hạn tần suất refresh | `unknownKidReloadsOnceThenUsesShortNegativeCache`, rotation test |
| `.../error/AuthServiceUnavailableException.java` | Phân biệt Auth/JWKS outage với token không hợp lệ | UC7 E3, §2.2 | Không trả 401 khi không thể tải key mới và chưa có key cache | `authFailureWithoutCachedKeyBecomesServiceUnavailable`, integration test |
| `.../error/GatewayExceptionHandler.java` | Thay error handler mặc định, che lỗi nội bộ và map lỗi mạng | 404/500/503 envelope | Log request lỗi kèm correlationId; response không lộ stack | reset/timeout/health/unknown route tests |
| `.../health/HealthController.java` | Trả liveness local cho UC6 smoke test | `GET /health` | Không gọi Auth, Content, DB hoặc broker | `healthStartsWithoutCallingAuthOrContent`, `HealthWithoutDependenciesTest` |
| `src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java` | Kiểm tra luồng HTTP thật của Gateway qua MockWebServer | T1–T12, UC1/UC7, header, proxy và lỗi | RSA test key sinh trong test; không chứa private key thật | 13 integration tests trong class |
| `src/test/java/vn/edu/cnpm/gateway/security/JwksKeyServiceTest.java` | Kiểm tra TTL/cache/negative-cache/stale fallback/single-flight | §2.2, G3 | Mutable clock loại bỏ sleep dài cho TTL | 5 JWKS unit tests |
| `src/test/java/vn/edu/cnpm/gateway/HealthWithoutDependenciesTest.java` | Chứng minh app start khi URL Auth/Content không chạy | UC6 smoke test | Chỉ gọi `/health`; startup không gọi dependency | `applicationStartsAndHealthWorksWhenAuthAndContentAreOffline` |
| `README.md` | Hướng dẫn chạy, cấu hình và gọi thử theo UC1 | Route/API và local developer flow | Curl thao tác nghiệp vụ yêu cầu Auth/Content thật theo contract | `clean verify`, health test |
| `docs/traceability-uc1.md` | Chỉ ra từng mục UC1/UC7/route và test tương ứng | UC v2.8 và contract V1.7 | Phân biệt Gateway ✅ với Content Service ⚠️ | Test được liệt kê trên từng dòng |
| `docs/files-explained.md` | Giải thích lý do, thứ tự filter, quyết định và bẫy sửa | BR-01/07/13, §1–§3 | Cập nhật cùng code/test khi thêm filter/route | Tất cả test đã dẫn theo thành phần |

