# Software Technology Project

Dự án Java 17/Spring Boot cung cấp API Gateway cho hệ thống thuyết minh đa ngôn ngữ. Hợp đồng API và use case nằm tại [API contracts V1.6](docs/api-contracts.md) và [UC v2.7](docs/UC.docx).

## API Gateway

- Xác thực access token JWT bằng khóa RSA hoặc JWKS; lấy định danh từ claim `sub` và chuẩn hóa role.
- Chuyển tiếp các API xác thực, POI, narration, voice và job theo hợp đồng.
- Cung cấp API client tra cứu POI, QR và narration theo ngôn ngữ.
- Phát tiến độ job qua Server-Sent Events tại `GET /api/v1/jobs/{jobId}/events` và nhận callback nội bộ có `X-Service-Token`.
- Cung cấp `GET /health` để kiểm tra trạng thái Gateway.
- Hỗ trợ mock mode khi chạy local.

## Chạy local

Cần JDK 17 trở lên, OpenSSL và Node.js. Maven Wrapper trong repository tải Maven và các thư viện cần thiết.

Tạo cặp khóa RSA cho môi trường local:

```powershell
New-Item -ItemType Directory -Force infrastructure/keys | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out infrastructure/keys/jwt-private.pem
openssl pkey -in infrastructure/keys/jwt-private.pem -pubout -out infrastructure/keys/jwt-public.pem
```

Khởi động Gateway:

```powershell
.\scripts\run-api-gateway.ps1
```

Gateway chạy tại `http://localhost:8080`. Kiểm tra health:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Chạy build và kiểm tra module:

```powershell
.\mvnw.cmd -B -f api-gateway/pom.xml clean verify
```

## CI/CD Staging

Workflow [`.github/workflows/api-gateway.yml`](.github/workflows/api-gateway.yml) chạy Maven Wrapper cho thay đổi liên quan API Gateway. Pull Request cần các check Gateway thành công trước khi merge. Sau khi merge vào `main`, workflow chạy lại, tạo JAR và deploy qua SSH lên Staging; quy trình chuyển symlink `current`, restart systemd, kiểm tra `GET /health` và rollback release trước nếu health check thất bại. `workflow_dispatch` trên `main` hỗ trợ chạy lại hoặc rollback.

Workflow dùng GitHub Environment `staging` với các secrets `STAGING_HOST`, `STAGING_USER`, `STAGING_SSH_KEY` và `STAGING_KNOWN_HOSTS`. Máy Staging cần Linux, Java 17, systemd, `curl` và unit [`infrastructure/systemd/sgu-api-gateway.service`](infrastructure/systemd/sgu-api-gateway.service). Mẫu biến môi trường nằm tại [`infrastructure/systemd/api-gateway.env.example`](infrastructure/systemd/api-gateway.env.example).

## Tài liệu

- [API contracts V1.6](docs/api-contracts.md)
- [UC v2.7](docs/UC.docx)
- [Sơ đồ Mermaid](docs/diagrams/)
- [Đối chiếu activity diagram và code](docs/uc-activity-coverage.md)
