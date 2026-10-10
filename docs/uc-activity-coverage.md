# Đối chiếu activity diagram và kiểm thử

Đối chiếu này dùng [UC v2.7](UC.docx), [API V1.6](api-contracts.md) và các sơ đồ tại [docs/source](source/README.md). Nhánh Gateway hiện tại chưa chứa mã nguồn Narration Service; các thay đổi Narration được giữ cho đợt riêng.

## Phạm vi mã nguồn trong nhánh Gateway

| Use case | Phần có trong nhánh này | Giới hạn |
|---|---|---|
| UC2 — Tạo, theo dõi và hủy job narration | Gateway có route chuyển tiếp các request job và SSE theo hợp đồng. | Vòng đời job, retry, idempotency, dispatch RabbitMQ và `details.jobIds[]` thuộc Narration Service, chưa có mã nguồn trong nhánh này. |
| UC5 — Khách lấy narration theo POI/QR | Gateway có các route client và adapter gọi upstream theo hợp đồng. | Trả narration đã PUBLISHED, chọn version/ngôn ngữ và xác thực POI phụ thuộc Narration/Content; chưa thể xác minh end-to-end từ mã nguồn nhánh này. |
| UC6 — CI/CD | Gateway có workflow Maven Wrapper và phát hành JAR tự động sau merge vào `main`. | Workflow Narration được giữ cho đợt riêng. Chưa thực hiện deploy Staging thật. |
| UC7 — Xác thực ở API Gateway | Gateway xác thực JWT, chỉ dùng `sub`, chuẩn hóa role, loại bỏ identity header giả mạo và kiểm tra token nội bộ cho callback. | Auth Service (login/refresh/logout/JWKS issuer) chưa có mã nguồn trong checkout. |

## Use case cần service chưa có trong checkout

Narration, Auth, Content, Translation và TTS chưa có implementation trong nhánh Gateway-only. Vì vậy không thể chạy đầy đủ UC2, UC5 hoặc luồng end-to-end qua các service. UC1, UC3/UC4 và phần đầy đủ của UC7 cũng cần các service chưa được bàn giao. Mocks trong Gateway chỉ kiểm tra phần Gateway, không thay thế việc kiểm thử các upstream thật.

## Đối chiếu hợp đồng với code trong nhánh này

| Hạng mục | Tình trạng | Đối chiếu / việc còn lại |
|---|---|---|
| CI/CD Staging | Có workflow Gateway | Maven Wrapper, lọc paths, JAR qua SSH, release mới, symlink, systemd, `/health` và rollback. Cần cấu hình branch protection với hai check Gateway, environment `staging` và máy deploy. |
| API client theo poiId | Có route Gateway | Hành vi dữ liệu narration và xử lý POI hoạt động nhưng chưa có narration PUBLISHED cần được xác minh khi Narration/Content được đưa lên. |
| QR `/api/v1/client/qr/{qrCode}` | Có route Gateway | Gateway chuyển tiếp tra cứu POI tới upstream; implementation Content chưa có trong checkout. |
| SSE `/api/v1/jobs/{jobId}/events` | Có endpoint Gateway | Callback do Narration gửi và mã lỗi callback cần đối chiếu khi service Narration được đưa lên. |
| JWKS | Gateway hỗ trợ cấu hình JWKS URI | Gateway cache khóa theo `kid`; Auth Service/JWKS issuer chưa có mã nguồn trong checkout. |
| Narration lấy `/internal/contents/{contentId}/current` | Chưa có implementation trong nhánh | Xác minh ở đợt Narration/Content. |
| `retryOfJobId`, xung đột job và `details.jobIds[]` | Chưa có implementation trong nhánh | Xác minh ở đợt Narration; giữ nguyên các yêu cầu trong hợp đồng API V1.6. |
| Claim `sub` | Đã có ở Gateway | Gateway dùng `sub` làm định danh và không dùng claim `userId` thay thế. |
| Giới hạn Idempotency-Key | Chưa có implementation trong nhánh | Xác minh trong Narration khi service được đưa lên. |

Activity Diagram UC2, UC5, UC6 và sơ đồ B.4 đã được cập nhật theo các luồng trong UC v2.7.

## Kết quả kiểm tra

Lần chạy verify gần nhất trước khi tách nhánh: API Gateway **23 test, 0 lỗi**; Narration **47 test, 0 lỗi**. Phần tách hiện tại không sửa mã Gateway; source và test Narration được giữ riêng, không nằm trong nội dung PR Gateway.
