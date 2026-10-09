# Đối chiếu activity diagram và kiểm thử

Đối chiếu này dùng tài liệu [UC v2.5](source/UC.docx) và các sơ đồ thiết kế trong [docs/source](source/README.md). Kiểm thử backend được chạy bằng Maven Wrapper, không cần Docker.

## Use case đã có mã nguồn

| Use case | Nhánh đã kiểm tra | Cách kiểm tra và giới hạn |
|---|---|---|
| UC2 — Tạo, theo dõi và hủy job narration | Tạo job hợp lệ; validate content, ngôn ngữ/voice; Idempotency-Key thiếu, quá dài, lặp cùng payload hoặc dùng lại với payload khác; job đang chạy trùng content/version/lang; retry job hợp lệ và version cũ; khóa tạo job; dispatch một event mỗi target; dispatch lặp; translation/TTS thành công theo cả hai thứ tự; translation/TTS thất bại; hoàn tất một phần/toàn bộ; event sai hoặc đến trước dispatch; event lặp; event đến sau trạng thái cuối; timeout ở hai giai đoạn; hủy job PENDING/PROCESSING, giữ FAILED, chuyển các target khác sang CANCELLED; job không tồn tại/đã kết thúc. | Unit tests cho service, trạng thái domain và listener. Listener xác nhận event malformed hoặc lỗi sau retry bị reject vào DLQ, không requeue vô hạn. Outbox/callback được kiểm tra bằng mock. Không chạy against SQL Server/RabbitMQ thật trong lượt kiểm tra này. |
| UC5 — Khách lấy narration theo POI/QR | Chỉ trả target PUBLISHED; chọn version cao nhất theo ngôn ngữ; gộp POI/nội dung với danh sách hoặc artifact; lấy subtitle và signed URL; QR/POI không tồn tại, service downstream lỗi, envelope lỗi, ngôn ngữ không có, artifact thiếu hoặc translation lệch content/version/lang. | Unit tests tại Narration Service và API Gateway. UI playback, pause/seek, đổi ngôn ngữ và xin lại URL hết hạn không có code client trong checkout, vì vậy không được kiểm thử như hành vi backend. |
| UC6 — CI/CD | Kiểm tra các workflow chạy Maven Wrapper khi push/PR; deploy chỉ chạy thủ công trên `main`, chuyển JAR qua SSH, health-check và rollback; không dùng Docker theo yêu cầu trước đó. | Hai lệnh `clean verify` được chạy cục bộ; GitHub Actions sẽ xác nhận lại khi branch được push. Không thực hiện deploy staging vì đó không phải nhánh kiểm thử và cần cấu hình máy/secrets bên ngoài repository. |
| UC7 — Xác thực ở API Gateway | Đọc claim `userId`/`sub`, chuẩn hóa role, loại bỏ identity header do client giả mạo; các route API yêu cầu role, callback tiến độ cần token nội bộ, route client/health công khai. | Unit tests cho JWT claim mapping, trusted headers, correlation ID và callback. Auth Service (login/refresh/logout/khóa tài khoản) chưa có mã nguồn, nên các bước đó chưa thể chạy end-to-end. |

## Use case chưa có mã nguồn để chạy

Trong checkout hiện tại, `auth-service`, `content-service`, `translation-service` và `tts-service` chỉ có README, chưa có `pom.xml` hoặc mã Java. Vì vậy không thể thực thi UC1 (quản lý content), UC3/UC4 (dịch và tổng hợp giọng nói), hay phần đầy đủ của UC7; các actor/use case UC8 và UC9 cũng chưa có service tương ứng. Các luồng này chưa được tính là đã kiểm thử end-to-end. Mocks của Narration chỉ kiểm tra cách A trao đổi với dependency giả lập, không thay thế việc kiểm thử implementation của các service vắng mặt.

## Kết quả chạy

Các lệnh kiểm tra tại thư mục repository:

```powershell
.\mvnw.cmd -B -f api-gateway/pom.xml clean verify
.\mvnw.cmd -B -f narration-service/pom.xml clean verify
```

Kết quả sau khi bổ sung kiểm tra event trước dispatch: API Gateway **16 test, 0 lỗi**; Narration Service **44 test, 0 lỗi**. Đây là kiểm thử tự động cấp module; chưa bao gồm kiểm thử kết nối SQL Server/RabbitMQ hay deployment thật.
