# Software Technology Project

Repository cho hệ thống thuyết minh đa ngôn ngữ. Hợp đồng API và use case mô tả toàn hệ thống tại [docs/api-contracts.md](docs/api-contracts.md) và [docs/UC.docx](docs/UC.docx).

## Phạm vi nhánh hiện tại

Nhánh này đưa API Gateway lên trước. Mã nguồn Narration Service, workflow CI/CD, cấu hình systemd và migration database của Narration được giữ lại cho đợt cập nhật riêng sau. Gateway vẫn gọi các upstream được cấu hình bằng `NARRATION_SERVICE_URL`, `AUTH_SERVICE_URL` và `CONTENT_SERVICE_URL`; các endpoint phụ thuộc upstream chỉ hoạt động khi những service đó sẵn sàng.

| Thành phần | Port | Tình trạng trong nhánh này |
|---|---:|---|
| API Gateway | 8080 | Có mã Java, kiểm tra JWT, chuyển tiếp request và callback tiến độ |
| Narration Service | 8082 | Chưa có mã nguồn trong nhánh này; sẽ được đưa lên riêng |
| Auth, Content, Translation, TTS | 8081, 8083–8085 | Chưa có mã nguồn trong checkout |

## Chạy API Gateway local

Cần JDK 17 trở lên, OpenSSL và Node.js. Maven Wrapper trong repository tự tải Maven và thư viện cần thiết.

Tạo cặp khóa RSA dùng cho local; không commit private key:

```powershell
New-Item -ItemType Directory -Force infrastructure/keys | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out infrastructure/keys/jwt-private.pem
openssl pkey -in infrastructure/keys/jwt-private.pem -pubout -out infrastructure/keys/jwt-public.pem
```

Khởi động Gateway:

```powershell
.\scripts\run-api-gateway.ps1
```

Script bật mock local và mở `http://localhost:8080`. Mặc định các upstream trỏ tới `localhost`; đặt `NARRATION_SERVICE_URL`, `AUTH_SERVICE_URL` và `CONTENT_SERVICE_URL` nếu chạy chúng ở địa chỉ khác. Kiểm tra health:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Chạy kiểm tra module:

```powershell
.\mvnw.cmd -B -f api-gateway/pom.xml clean verify
```

## CI/CD Gateway lên Staging

Workflow [`.github/workflows/api-gateway.yml`](.github/workflows/api-gateway.yml) chỉ lọc thay đổi liên quan Gateway và Maven Wrapper. Pull Request phải xanh trước khi merge; sau merge, CI chạy lại trên `main` và tự động deploy JAR qua SSH nếu thành công. Workflow tạo release mới, chuyển symlink `current`, restart systemd, kiểm tra `GET /health` và khôi phục release trước nếu health check lỗi. `workflow_dispatch` từ `main` chỉ dùng để chạy lại hoặc rollback Gateway.

Branch protection cần yêu cầu hai check của workflow: `Detect api-gateway changes` và `Verify api-gateway`. Workflow dùng GitHub Environment `staging` với các secrets `STAGING_HOST`, `STAGING_USER`, `STAGING_SSH_KEY` và `STAGING_KNOWN_HOSTS`.

Máy Staging cần Linux, Java 17, systemd, `curl`, unit [`infrastructure/systemd/sgu-api-gateway.service`](infrastructure/systemd/sgu-api-gateway.service) và cấu hình [`infrastructure/systemd/api-gateway.env.example`](infrastructure/systemd/api-gateway.env.example). Unit phục vụ JAR tại `/opt/sgu/api-gateway/current/app.jar`. Cấu hình URL upstream và các secret Gateway trên máy; không commit private key hoặc file môi trường chứa secret.

## Tài liệu thiết kế

- [API contracts V1.6](docs/api-contracts.md)
- [UC v2.7](docs/UC.docx)
- [Sơ đồ Mermaid](docs/diagrams/)
- [Đối chiếu activity diagram và code](docs/uc-activity-coverage.md)
