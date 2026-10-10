# Đối chiếu activity diagram và kiểm thử

Đối chiếu này dùng [UC v2.8](UC.docx), [API V1.7](api-contracts.md) và các sơ đồ được nhúng trong UC.docx. Nhánh Gateway hiện tại chưa chứa mã nguồn Narration Service; workflow riêng của Narration được chuẩn bị nhưng chưa thể chạy build/phát hành khi thiếu POM và source.

## Phạm vi mã nguồn trong nhánh Gateway

| Use case | Phần có trong nhánh này | Giới hạn |
|---|---|---|
| UC2 — Tạo, theo dõi và hủy job narration | Gateway có route chuyển tiếp các request job và SSE theo hợp đồng. | Vòng đời job, retry, idempotency, dispatch RabbitMQ và `details.jobIds[]` thuộc Narration Service, chưa có mã nguồn trong nhánh này. |
| UC5 — Khách lấy narration theo POI/QR | Gateway có các route client và adapter gọi upstream theo hợp đồng. | Trả narration đã PUBLISHED, chọn version/ngôn ngữ và xác thực POI phụ thuộc Narration/Content; chưa thể xác minh end-to-end từ mã nguồn nhánh này. |
| UC6 — CI/CD | Workflow Gateway chạy Maven Wrapper CI theo paths; sau merge main và CI xanh, tự động build JAR, chạy trực tiếp trên runner, kiểm tra `/health`, rồi phát hành JAR + SHA256 lên GitHub Releases. | Workflow Narration đã có nhưng Maven verify và release được bỏ qua khi thiếu `narration-service/pom.xml`; cần đối chiếu lại khi source được bàn giao. |
| UC7 — Xác thực ở API Gateway | Gateway xác thực JWT, chỉ dùng `sub`, chuẩn hóa role, loại bỏ identity header giả mạo và kiểm tra token nội bộ cho callback. | Auth Service (login/refresh/logout/JWKS issuer) chưa có mã nguồn trong checkout. |

## Use case cần service chưa có trong checkout

Narration, Auth, Content, Translation và TTS chưa có implementation trong nhánh Gateway-only. Vì vậy không thể chạy đầy đủ UC2, UC5 hoặc luồng end-to-end qua các service. UC1, UC3/UC4 và phần đầy đủ của UC7 cũng cần các service chưa được bàn giao. Mocks trong Gateway chỉ kiểm tra phần Gateway, không thay thế việc kiểm thử các upstream thật.

## Đối chiếu hợp đồng với code trong nhánh này

| Hạng mục | Tình trạng | Đối chiếu hiện tại | Việc của dev |
|---|---|---|---|
| CI/CD — API Gateway | Workflow đã cập nhật theo UC v2.8 | Push/PR chạy `./mvnw clean verify` theo paths; sau merge main, CI chạy lại rồi job release build JAR, chạy `java -jar` trực tiếp trên runner, gọi GET `/health` tối đa khoảng 60 giây và phát hành GitHub Release kèm SHA256. Không dùng máy chủ staging hay SSH. | Bật/kiểm tra branch protection để PR chỉ merge khi CI xanh. Xác nhận check cần thiết cho từng service phù hợp với paths filter. |
| CI/CD — Narration | Chưa thể kiểm chứng runtime | Workflow hiện báo rõ và bỏ qua Maven verify khi thiếu POM; job release chỉ chạy khi POM có mặt. Không có source để kiểm tra health hoặc unit test. | Bàn giao source/POM/tests; xác minh GET `/health` trả 200 không cần SQL Server/RabbitMQ. Khi có test, kiểm tra có Testcontainers hay không; nếu có, ghi cách xử lý riêng và không chạy container trong quy trình này. |
| API client theo poiId | Có route Gateway | Hành vi dữ liệu narration và xử lý POI hoạt động nhưng chưa có narration PUBLISHED cần được xác minh khi Narration/Content được đưa lên. | Xác minh trên Narration/Content khi source được bàn giao. |
| QR `/api/v1/client/qr/{qrCode}` | Có route Gateway | Gateway chuyển tiếp tra cứu POI tới upstream; implementation Content chưa có trong checkout. | Xác minh Content trả đúng `poiId` và mã lỗi theo API V1.7. |
| SSE `/api/v1/jobs/{jobId}/events` | Có endpoint Gateway | Callback do Narration gửi và mã lỗi callback cần đối chiếu khi service Narration được đưa lên. | Đối chiếu status code callback với API V1.7 khi có Narration. |
| JWKS | Gateway hỗ trợ cấu hình JWKS URI | Gateway xác thực JWT; Auth Service/JWKS issuer chưa có mã nguồn trong checkout. | Xác minh JWKS issuer và cache khóa theo `kid` khi Auth Service được bàn giao. |
| Narration lấy `/internal/contents/{contentId}/current` | Chưa có implementation trong nhánh | Cần xác minh endpoint nội bộ khi Narration/Content được đưa lên. | Đối chiếu caller, token nội bộ và response theo API V1.7. |
| `retryOfJobId`, xung đột job và `details.jobIds[]` | Chưa có implementation trong nhánh | Chưa thể kiểm tra thứ tự xác thực retry, lọc job xung đột hoặc số lượt retry trong Narration. | Triển khai/đối chiếu theo API V1.7 và UC2 v2.8 khi có source. |
| Claim `sub` | Đã có ở Gateway | Gateway dùng `sub` làm định danh và không dùng claim `userId` thay thế. | — |
| Giới hạn Idempotency-Key | Chưa có implementation trong nhánh | Logic thuộc Narration Service, chưa có source trong checkout. | Xác minh với API V1.7 khi có Narration. |
| GET `/health` | Khớp ở API Gateway | Controller trả `status: UP`; endpoint được cho phép không cần JWT. Controller không truy cập SQL Server hoặc RabbitMQ. Chưa thể kiểm tra Narration vì thiếu source/POM. | Khi có Narration, bảo đảm health liveness trả HTTP 200 mà không yêu cầu SQL Server/RabbitMQ. |

Activity Diagram UC2, UC5, UC6 và sơ đồ B.4 đã được vẽ lại, thay đúng ảnh nhúng trong UC.docx theo UC v2.8.

## Kết quả kiểm tra

- API Gateway `clean verify`: BUILD SUCCESS; 23 test, 0 lỗi, 0 bỏ qua.
- Narration `clean verify`: chưa chạy được vì checkout không có `narration-service/pom.xml` hoặc source; không có số test để báo cáo.
- Kiểm tra/kiểm thử cần dựng SQL Server hoặc RabbitMQ bằng container: chưa kiểm tra được do không dùng Docker. Không có container nào được cài hoặc chạy.
- Hai workflow: YAML được parse thành công bằng PyYAML; không có bước Docker/Podman, container, SSH hoặc staging.
- UC.docx: python-docx mở lại được, có đúng 21 ảnh; bốn ảnh đích đã được xem riêng. Không thể render toàn bộ tài liệu/cập nhật trường mục lục tự động vì LibreOffice không có sẵn.
