# Đối chiếu activity diagram và kiểm thử

Đối chiếu này dùng tài liệu [UC v2.7](UC.docx), hợp đồng [API V1.6](api-contracts.md) và các sơ đồ thiết kế trong [docs/source](source/README.md).

## Use case đã có mã nguồn

| Use case | Nhánh đã kiểm tra | Cách kiểm tra và giới hạn |
|---|---|---|
| UC2 — Tạo, theo dõi và hủy job narration | Tạo job hợp lệ; validate content, ngôn ngữ/voice; Idempotency-Key thiếu, quá dài, lặp cùng payload hoặc dùng lại với payload khác; job đang chạy trùng content/version/lang; `ACTIVE_JOB_EXISTS` trả `details.jobIds[]` và retry không che các job xung đột khác; retry job hợp lệ và version cũ; khóa tạo job; dispatch một event mỗi target; dispatch lặp; translation/TTS thành công theo cả hai thứ tự; translation/TTS thất bại; hoàn tất một phần/toàn bộ; event sai hoặc đến trước dispatch; event lặp; event đến sau trạng thái cuối; timeout ở hai giai đoạn; hủy job PENDING/PROCESSING, giữ FAILED, chuyển các target khác sang CANCELLED; job không tồn tại/đã kết thúc. | Unit tests cho service, trạng thái domain và listener. Listener xác nhận event malformed hoặc lỗi sau retry bị reject vào DLQ, không requeue vô hạn. Outbox/callback được kiểm tra bằng mock. Không chạy against SQL Server/RabbitMQ thật trong lượt kiểm tra này. |
| UC5 — Khách lấy narration theo POI/QR | Chỉ trả target PUBLISHED; POI hoạt động nhưng không có narration PUBLISHED trả 200 với `narrations: []`; chọn version cao nhất theo ngôn ngữ; gộp POI/nội dung với danh sách hoặc artifact; lấy subtitle và signed URL; QR/POI không tồn tại, service downstream lỗi, envelope lỗi, ngôn ngữ không có, artifact thiếu hoặc translation lệch content/version/lang. | Unit tests tại Narration Service và API Gateway. UI playback, pause/seek, đổi ngôn ngữ và xin lại URL hết hạn không có code client trong checkout, vì vậy không được kiểm thử như hành vi backend. |
| UC6 — CI/CD | UC6 v2.7: CI Maven Wrapper; CD tự động khi merge vào main (JAR qua SSH, symlink, systemd, /health, rollback); hai service có workflow JAR riêng, lọc paths và concurrency theo service. | Hai lệnh `clean verify` được chạy cục bộ; GitHub Actions xác nhận lại khi branch được push. Không thực hiện deploy staging; cần cấu hình branch protection, máy và secrets bên ngoài repository. |
| UC7 — Xác thực ở API Gateway | Chỉ dùng claim `sub`, chuẩn hóa role, loại bỏ identity header do client giả mạo; các route API yêu cầu role, callback tiến độ cần token nội bộ, route client/health công khai. | Unit tests cho JWT claim mapping, trusted headers, correlation ID và callback. Auth Service (login/refresh/logout/khóa tài khoản) chưa có mã nguồn, nên các bước đó chưa thể chạy end-to-end. |

## Use case chưa có mã nguồn để chạy

Trong checkout hiện tại, `auth-service`, `content-service`, `translation-service` và `tts-service` chỉ có README, chưa có `pom.xml` hoặc mã Java. Vì vậy không thể thực thi UC1 (quản lý content), UC3/UC4 (dịch và tổng hợp giọng nói), hay phần đầy đủ của UC7; các actor/use case UC8 và UC9 cũng chưa có service tương ứng. Các luồng này chưa được tính là đã kiểm thử end-to-end. Mocks của Narration chỉ kiểm tra cách A trao đổi với dependency giả lập, không thay thế việc kiểm thử implementation của các service vắng mặt.

## Đối chiếu hợp đồng với code

| Hạng mục | Tình trạng trong code | Đối chiếu | Việc của dev |
|---|---|---|---|
| CI/CD staging | khớp về code | Cả Gateway và Narration có workflow riêng chạy Maven Wrapper theo paths, CI trên PR và main, JAR qua SSH, release mới, symlink, systemd, GET /health và rollback. | Cấu hình branch protection cho bốn checks; khai báo `staging` secrets và chuẩn bị máy/unit systemd. |
| API khách theo poiId (`/api/v1/client/pois/{poiId}/narrations...`) | đã có | Gateway có route danh sách narration và route theo lang; khi tra Content gọi `/internal/pois/{poiId}/current`, khớp API V1.6. Cả Gateway và Narration đều có GET `/health`. | — |
| QR `/api/v1/client/qr/{qrCode}` | đã có | Gateway gọi `/internal/pois/by-qr/{qrCode}` và trả poiId. | — |
| SSE `/api/v1/jobs/{jobId}/events` | đã có | Gateway mở stream theo jobId. Callback trả 200 khi nhận và 401 khi token sai, khớp V1.6. | — |
| JWKS | khớp phần Gateway | Gateway có cấu hình JWKS URI tùy chọn; Nimbus Remote JWK Set cache bộ khóa theo `kid`; kiểm thử xác nhận chỉ tải lại một lần khi gặp `kid` mới. Auth Service/JWKS issuer không có mã nguồn trong checkout. | — |
| Narration gọi `/internal/contents/{contentId}/current` | đã có | Narration Service gọi endpoint này khi xác thực nội dung trước khi tạo job. | — |
| `retryOfJobId` | khớp | Narration kiểm tra job tham chiếu, contentId, version và target; lỗi xung đột trả `details.jobIds[]` và vẫn xét mọi job xung đột khác ngoài target được retry. | — |
| Claim `sub` | khớp | Gateway chỉ dùng claim `sub` làm định danh, không chấp nhận claim `userId` thay thế. | — |
| Giới hạn Idempotency-Key | đã có | Code cho phép tối đa 255 ký tự và trả INVALID_IDEMPOTENCY_KEY khi vượt quá; V1.6 ghi theo giới hạn này. | — |

Activity Diagram UC2, UC5, UC6 và sơ đồ B.4 đã được cập nhật theo các luồng trong UC v2.7.

## Kết quả chạy

Các lệnh kiểm tra tại thư mục repository:

```powershell
.\mvnw.cmd -B -f api-gateway/pom.xml clean verify
.\mvnw.cmd -B -f narration-service/pom.xml clean verify
```

Kết quả kiểm thử module: API Gateway **23 test, 0 lỗi**; Narration Service **47 test, 0 lỗi**. Đây là kiểm thử tự động cấp module; chưa bao gồm kiểm thử kết nối SQL Server/RabbitMQ hay deployment thật.
