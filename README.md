# Software Technology Project

Repository cho hệ thống thuyết minh đa ngôn ngữ. Hệ thống dự kiến gồm API Gateway, Auth, Content, Narration, Translation và TTS; các service trao đổi qua REST và RabbitMQ. Narration lưu trạng thái job, target và transactional outbox trong SQL Server. Quy ước API/event được mô tả tại [docs/api-contracts.md](docs/api-contracts.md).

Tài liệu UC v2.7, ERD và sequence diagram nguồn được lưu trong [docs/source](docs/source/README.md). Ma trận đối chiếu activity diagram với code và test nằm tại [docs/uc-activity-coverage.md](docs/uc-activity-coverage.md).

## Trạng thái mã nguồn trong checkout này

| Thành phần | Port | Tình trạng |
|---|---:|---|
| API Gateway | 8080 | Có mã Java; route Auth, Content và Narration |
| Auth Service | 8081 | Thư mục chưa có mã nguồn hoặc `pom.xml` |
| Narration Service | 8082 | Có mã Java; lưu job bằng SQL Server, nhận/phát event qua RabbitMQ |
| Content Service | 8083 | Thư mục chưa có mã nguồn hoặc `pom.xml` |
| Translation Service | 8084 | Thư mục chưa có mã nguồn hoặc `pom.xml` |
| TTS Service | 8085 | Thư mục chưa có mã nguồn hoặc `pom.xml` |
| SQL Server | 1433 | Cài và chạy riêng trên máy hoặc server |
| RabbitMQ | 5672 | Cài và chạy riêng trên máy hoặc server |

Vì bốn service Auth, Content, Translation và TTS chưa được đưa vào checkout này, hiện chưa thể khởi chạy toàn bộ luồng production end-to-end từ repository. Có thể chạy hai service đã có mã nguồn cùng mock để kiểm tra phần A. Mục **Chạy đủ sáu service** bên dưới ghi trình tự và lệnh khởi chạy thủ công cần dùng sau khi các module còn thiếu được đưa vào.

## Yêu cầu và chuẩn bị thủ công

Cài JDK 17 trở lên, OpenSSL, Node.js, SQL Server và RabbitMQ. Java 25 dùng được; hai module hiện biên dịch tương thích Java 17 để CI và máy triển khai vẫn có thể dùng JDK 17. Maven Wrapper trong repository tự tải Maven ở lần chạy đầu, nên không cần cài Maven toàn máy; máy cần Internet để tải Maven và thư viện.

1. Bật TCP/IP cho SQL Server, đặt port `1433` và bật SQL Server Authentication. Tạo database `narration_db` bằng SQL Server Management Studio: mở và chạy [`infrastructure/sql/create-narration-db.sql`](infrastructure/sql/create-narration-db.sql). Narration Service sẽ tự chạy Flyway migration khi khởi động.
2. Khởi động RabbitMQ. Tạo user/vhost quyền đọc-ghi cho Narration. Với cài đặt local mới, có thể dùng RabbitMQ Command Prompt:

   ```powershell
   rabbitmqctl add_user admin admin_dev_password
   rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"
   ```

   Nếu user đã tồn tại, dùng `rabbitmqctl change_password admin <mat-khau-local>` và đặt `RABBITMQ_PASSWORD` tương ứng trước khi chạy Narration.
3. Tạo cặp khóa dùng riêng cho local. Chỉ Gateway cần public key; giữ private key ngoài Git:

   ```powershell
   New-Item -ItemType Directory -Force infrastructure/keys | Out-Null
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out infrastructure/keys/jwt-private.pem
   openssl pkey -in infrastructure/keys/jwt-private.pem -pubout -out infrastructure/keys/jwt-public.pem
   ```

## Chạy local phần hiện có

Mở hai cửa sổ PowerShell tại thư mục gốc repository. Nếu mật khẩu SQL Server hoặc RabbitMQ khác giá trị local mặc định, đặt biến môi trường trong cửa sổ sẽ chạy Narration.

**Cửa sổ 1 — Narration Service:**

```powershell
$env:DB_PASSWORD = 'mat-khau-sa-cua-ban'
$env:RABBITMQ_PASSWORD = 'mat-khau-rabbit-cua-ban'
.\scripts\run-narration-service.ps1
```

Script bật mock cho Content/TTS/Translation, chạy migration, mở HTTP tại `http://localhost:8082` và giữ log trong cửa sổ này. Nếu dùng đúng mật khẩu mẫu trong `application.yml`, có thể bỏ hai lệnh đặt biến môi trường.

**Cửa sổ 2 — API Gateway:**

```powershell
.\scripts\run-api-gateway.ps1
```

Gateway mở tại `http://localhost:8080`. Hai script có hai secret theo service gọi: đặt cùng `GATEWAY_SERVICE_TOKEN` trong cả hai cửa sổ để Gateway gọi Narration, và cùng `NARRATION_SERVICE_TOKEN` trong cả hai cửa sổ để Narration gọi callback về Gateway. Hai secret này phải khác nhau.

Kiểm tra health:

```powershell
Invoke-RestMethod http://localhost:8082/health
Invoke-RestMethod http://localhost:8080/health
```

Tạo access token phát triển và thử tạo job qua Gateway:

```powershell
$token = (node scripts/create-dev-jwt.mjs).Trim()
$body = '{"contentId":"content-demo","targets":[{"lang":"en","voiceId":"en-US-Journey-F"}]}'
$job = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/jobs `
  -Headers @{ Authorization = "Bearer $token"; 'Idempotency-Key' = [guid]::NewGuid().ToString() } `
  -ContentType 'application/json' -Body $body
$job.data
```

Đọc trạng thái job:

```powershell
$jobId = $job.data.jobId
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/jobs/$jobId" `
  -Headers @{ Authorization = "Bearer $token" }
```

Trong checkout này, mock giả lập lời gọi REST tới Content/TTS/Translation; các event hoàn tất vẫn cần được phát mẫu. Dùng `jobId` và `targetId` vừa nhận để gửi event tới Narration Service:

```powershell
$target = $job.data.targets[0]
$internalHeaders = @{ 'X-Service-Token' = 'local_gateway_token_change_me' }
$translationEvent = @{
  jobId = $jobId
  targetId = $target.targetId
  translationId = 'tr-local-1'
  lang = $target.lang
  voiceId = $target.voiceId
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8082/dev/mock/events/translation-completed `
  -Headers $internalHeaders -ContentType 'application/json' -Body $translationEvent

$ttsEvent = @{
  jobId = $jobId
  targetId = $target.targetId
  translationId = 'tr-local-1'
  audioId = 'audio-local-1'
  lang = $target.lang
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8082/dev/mock/events/tts-completed `
  -Headers $internalHeaders -ContentType 'application/json' -Body $ttsEvent
```

Đọc lại job để xem trạng thái kết thúc. Dừng từng service bằng `Ctrl+C` trong cửa sổ tương ứng.

## Chạy đủ sáu service

Khi checkout đã có đủ source, chạy SQL Server và RabbitMQ trước, sau đó mở một cửa sổ PowerShell cho mỗi service theo thứ tự dưới đây. Các lệnh Maven cho Auth, Content, Translation và TTS áp dụng nếu những module này được bàn giao theo cấu trúc Maven/Spring Boot như hai module hiện có; hiện các `pom.xml` đó chưa có nên chưa thể chạy hoặc xác minh.

| Thứ tự | Service | Port | Lệnh trong cửa sổ PowerShell riêng |
|---:|---|---:|---|
| 1 | Auth | 8081 | `$env:SERVER_PORT='8081'; .\mvnw.cmd -f auth-service/pom.xml spring-boot:run` |
| 2 | Content | 8083 | `$env:SERVER_PORT='8083'; .\mvnw.cmd -f content-service/pom.xml spring-boot:run` |
| 3 | Translation | 8084 | `$env:SERVER_PORT='8084'; .\mvnw.cmd -f translation-service/pom.xml spring-boot:run` |
| 4 | TTS | 8085 | `$env:SERVER_PORT='8085'; .\mvnw.cmd -f tts-service/pom.xml spring-boot:run` |
| 5 | Narration | 8082 | Đặt `APP_MOCKS_ENABLED=false`, cấu hình SQL Server/RabbitMQ, rồi chạy `scripts/run-narration-service.ps1` |
| 6 | API Gateway | 8080 | Đặt `APP_MOCKS_ENABLED=false`, tạo JWT key như phần trên, rồi chạy `scripts/run-api-gateway.ps1` |

Đặt biến môi trường trong đúng cửa sổ sẽ chạy service. Trước khi tắt mock, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, thông tin RabbitMQ và các URL downstream trong environment của Narration; Gateway cần `JWT_PUBLIC_KEY_PATH`, `GATEWAY_SERVICE_TOKEN`, `NARRATION_SERVICE_TOKEN` và URL Auth/Content. Nếu Gateway lấy khóa từ JWKS, đặt thêm `JWT_JWK_SET_URI` tới `/api/v1/auth/.well-known/jwks.json`. Các giá trị mặc định trong hai script trỏ tới `localhost` và các port ở bảng. Hai service hiện có cung cấp `/health` cho smoke test; các service khác cần tuân theo cùng đường dẫn khi được bổ sung.

Với source hiện có, chỉ có thể thực hiện đầy đủ hai bước Narration và Gateway theo mục trước; các bước Auth, Content, Translation và TTS sẽ hoạt động sau khi code tương ứng được thêm vào checkout. CI/CD bên dưới cũng chỉ kiểm tra và deploy hai module đã có `pom.xml`.

## Chạy kiểm tra

Từ thư mục gốc, chạy test/build độc lập cho từng service bằng Maven Wrapper:

```powershell
.\mvnw.cmd -B -f api-gateway/pom.xml clean verify
.\mvnw.cmd -B -f narration-service/pom.xml clean verify
```

## CI/CD và deploy Staging

Hai workflow trong `.github/workflows` lọc theo paths để chỉ chạy Maven Wrapper cho service có thay đổi. Với Pull Request, workflow luôn báo check phát hiện paths; check `Verify api-gateway` chỉ chạy khi PR chạm Gateway. API Gateway đã chuyển sang phát hành JAR: sau khi Pull Request xanh được merge vào `main`, workflow chạy CI lại; nếu xanh, CD tự động chép JAR bằng SSH vào release mới, đổi symlink `current`, restart systemd và kiểm tra `GET /health`. Health check lỗi sẽ khôi phục release trước. Có thể chạy lại hoặc rollback riêng một service bằng `workflow_dispatch` từ `main`; thao tác này không phải điều kiện phát hành. Branch protection trên GitHub phải yêu cầu cả `Detect api-gateway changes` và `Verify api-gateway` trước khi merge.

Narration Service vẫn dùng workflow Docker hiện tại và sẽ được chuyển riêng ở đợt tiếp theo. Hai module này là các service duy nhất có mã nguồn trong checkout; Auth, Content, Translation và TTS chưa có implementation tương ứng.

### Chuẩn bị máy Staging

Gateway cần Linux, Java 17, systemd, `curl`, các service downstream, SQL Server và RabbitMQ có thể truy cập từ host. Tài khoản SSH phải ghi được vào `/opt/sgu/api-gateway/releases` và được cấp quyền `sudo` không tương tác, giới hạn để restart `sgu-api-gateway.service`. Cài unit systemd và file cấu hình trước khi bật workflow:

```bash
sudo install -o root -g root -m 0644 infrastructure/systemd/sgu-api-gateway.service /etc/systemd/system/
sudo install -d -o sgu -g sgu -m 0750 /opt/sgu/api-gateway/releases /etc/sgu /opt/sgu/keys
sudo install -o sgu -g sgu -m 0640 infrastructure/systemd/api-gateway.env.example /etc/sgu/api-gateway.env
sudo install -o sgu -g sgu -m 0640 /duong-dan-an-toan/jwt-public.pem /opt/sgu/keys/jwt-public.pem
sudo systemctl daemon-reload
sudo systemctl enable sgu-api-gateway.service
```

Tài khoản chạy unit là `sgu`; bảo đảm tài khoản này tồn tại và có quyền đọc cấu hình/JWT public key. Trước lần phát hành JAR đầu tiên, dừng deployment Docker cũ để giải phóng cổng 8080 và kiểm tra unit `/health`. Đặt mật khẩu thật cho SQL Server/RabbitMQ và hai service token. `GATEWAY_SERVICE_TOKEN` phải giống nhau trong hai file vì Narration xác thực lời gọi từ Gateway; `NARRATION_SERVICE_TOKEN` cũng phải giống nhau trong hai file vì Gateway xác thực callback từ Narration. Hai token phải khác nhau. Không commit file môi trường hoặc private key.

### GitHub environment `staging`

Tạo environment `staging` trong **Settings → Environments** và đặt các secrets sau trong environment đó:

- `STAGING_HOST`: hostname/IP của Staging.
- `STAGING_USER`: tài khoản SSH triển khai release và restart systemd.
- `STAGING_SSH_KEY`: private SSH key của tài khoản trên.
- `STAGING_KNOWN_HOSTS`: host key Staging đã xác minh, định dạng OpenSSH `host key-type public-key`.

Narration workflow cũ vẫn cần `GHCR_USERNAME` và `GHCR_READ_TOKEN` để kéo image cho đến khi workflow đó được chuyển sang JAR. Log Gateway xem bằng `journalctl -u sgu-api-gateway`; log Narration hiện xem bằng `docker logs sgu-narration-service`.
