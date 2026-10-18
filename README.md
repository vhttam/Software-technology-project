# Hệ thống Thuyết minh Tự động Đa ngôn ngữ

Hệ thống thuyết minh tự động đa ngôn ngữ cho điểm tham quan, bảo tàng và di tích lịch sử. Hệ thống được kiến trúc theo mô hình Microservices hướng sự kiện (Event-Driven Architecture) với Spring Boot (Java 17), React (TypeScript/Vite), RabbitMQ, và cơ sở dữ liệu độc lập cho từng dịch vụ.

---

## 1. Khởi động nhanh bằng một lệnh (Single Command Startup)

Chỉ cần cài đặt **Docker Desktop** (hỗ trợ Windows, macOS và Linux). Toàn bộ hệ thống có thể khởi động ngay lập tức bằng một lệnh duy nhất mà không cần cài đặt thêm Java, Node.js hay bất kỳ công cụ phát triển nào trên máy chủ.

### Trên Windows (Khuyên dùng: file .bat hoặc PowerShell)
- **Cách 1 (Nhanh nhất - Không lo lỗi ExecutionPolicy)**: Chạy lệnh `.\start.bat` trong PowerShell/CMD, hoặc nhấp đúp (double-click) trực tiếp vào file `start.bat`.
  ```cmd
  .\start.bat
  ```
- **Cách 2 (Qua PowerShell)**: Nếu máy chưa bật quyền chạy script PowerShell (`ExecutionPolicy`), dùng cờ `-ExecutionPolicy Bypass`:
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\start.ps1
  ```
  *(Nếu máy đã cho phép chạy script: gõ `.\start.ps1`)*

### Trên macOS / Linux (Bash)
```bash
./start.sh
```

> **Cơ chế tự động của script:**
> 1. Kiểm tra trạng thái Docker Daemon.
> 2. Kiểm tra và cảnh báo xung đột cổng mạng (3000, 8080, 5672, 15672, 1433).
> 3. Tự động khởi tạo file `.env` từ `.env.example` với các khóa bí mật ngẫu nhiên bảo mật cao nếu chưa có.
> 4. Khởi động toàn bộ container qua Docker Compose.
> 5. Giám sát liveness/readiness của API Gateway cho đến khi toàn hệ thống sẵn sàng.

---

## 2. Các chế độ chạy (Profiles) & Giới hạn tài nguyên

Hệ thống hỗ trợ 2 chế độ triển khai qua Docker Compose:

| Đặc tính | Chế độ Nhẹ (Lite - Mặc định) | Chế độ Đầy đủ (Full) |
| :--- | :--- | :--- |
| **Lệnh chạy** | `.\start.bat` (hoặc `.\start.ps1`, `./start.sh`) | `.\start.ps1 -Full` (hoặc `./start.sh --full`) |
| **Cơ sở dữ liệu** | H2 Database trong bộ nhớ độc lập cho từng service | Microsoft SQL Server 2022 (tự động khởi tạo 5 CSDL) |
| **Message Broker** | RabbitMQ 3.13 Management | RabbitMQ 3.13 Management |
| **Mức chiếm dụng RAM** | Ước lượng ~3.5 GB theo JVM limit (đo lường thực tế chính xác tự động bởi workflow CI `docker-smoke-test.yml` trên Linux amd64) | ~ 6.0 - 8.0 GB |
| **Kiến trúc kiểm chứng** | Đã kiểm chứng trên x86_64 / amd64 (Linux & Windows) | Đã kiểm chứng trên x86_64 / amd64 |
| **Lưu trữ dữ liệu** | Xóa khi dừng container | Lưu trữ lâu dài qua Docker Volume (`mssql_data`, `audio_data`) |
| **Mục đích** | Đánh giá, chấm điểm đồ án, demo nhanh | Triển khai thực nghiệm, kiểm thử tải và tính bền vững |

### Giới hạn tài nguyên bộ nhớ JVM
Mỗi container Spring Boot được cấu hình biến môi trường `-XX:MaxRAMPercentage=75.0` kèm giới hạn bộ nhớ cứng (memory limit: 384MB - 768MB) trong file `docker-compose.yml`, đảm bảo các service hoạt động mượt mà và không gây quá tải tài nguyên hệ thống.

### Lưu ý quan trọng về môi trường không có Docker
Sau khi hoàn tất tái cấu trúc đưa provider thật vào đúng ranh giới dịch vụ và loại bỏ `LocalJobWorker` (vốn ghi dữ liệu trực tiếp xuyên các CSDL), toàn bộ luồng tạo tác vụ thuyết minh hiện tại hoạt động theo mô hình hướng sự kiện (Event-Driven) bắt buộc cần Message Broker (RabbitMQ). Do đó:
- **Để chạy và trải nghiệm đầy đủ tính năng hệ thống**: Bắt buộc cần cài đặt **Docker Desktop** (khởi động bằng `start.bat` / `start.ps1` / `start.sh`).
- **Nếu máy phát triển không có Docker**: Bạn vẫn có thể chạy kiểm thử độc lập từng microservice bằng Maven (`mvnw test`). Trường hợp bắt buộc muốn chạy thử nghiệm luồng cũ bằng script polling CSDL không cần Docker, vui lòng checkout nhánh git cũ:
  ```bash
  git checkout backup/pre-api-message-update-2026-10-10
  ```

---

## 3. Quản lý hệ thống (Scripts)

Tất cả các script đều được chuẩn hóa chạy tương thích trên Windows (`.ps1`) và macOS/Linux (`.sh`):

* **Theo dõi logs toàn hệ thống:**
  ```cmd
  .\logs.bat             # Windows (hoặc .\logs.ps1)
  ./logs.sh              # macOS/Linux
  ```
  *(Để xem riêng một service: `.\logs.bat tts-service`)*

* **Dừng hệ thống (giữ nguyên dữ liệu):**
  ```cmd
  .\stop.bat             # Windows (hoặc .\stop.ps1)
  ./stop.sh              # macOS/Linux
  ```

* **Đặt lại dữ liệu (Reset hệ thống về ban đầu):**
  Script sẽ yêu cầu xác nhận trước khi xóa các container và volume dữ liệu:
  ```cmd
  .\reset.bat            # Windows (hoặc .\reset.ps1)
  ./reset.sh             # macOS/Linux
  ```

* **Đóng gói dự án thành file ZIP sạch bàn giao:**
  Tự động loại bỏ `.git`, `node_modules`, `target`, `logs`, `.env`, và các file nhạy cảm:
  ```powershell
  .\scripts\pack-zip.ps1 # Windows -> tạo dist/he-thong-thuyet-minh.zip
  ./scripts/pack-zip.sh  # macOS/Linux
  ```

---

## 4. Địa chỉ truy cập & Cổng mạng

Sau khi hệ thống khởi động thành công:

| Thành phần | Địa chỉ URL | Ghi chú |
| :--- | :--- | :--- |
| **Giao diện Quản trị Web** | [http://localhost:3000](http://localhost:3000) | Giao diện React SPA (Nginx) |
| **Cổng API Gateway** | [http://localhost:8080](http://localhost:8080) | Cổng truy cập API duy nhất |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | Quản lý hàng đợi message (User mặc định: `cnpm`) |
| **Kiểm tra sức khỏe hệ thống** | [http://localhost:8080/health](http://localhost:8080/health) | Trạng thái tổng thể toàn bộ hệ thống |

> **Tài khoản khởi tạo mặc định:**
> - Tên đăng nhập: `admin`
> - Mật khẩu khởi tạo: Được định nghĩa trong biến `BOOTSTRAP_ADMIN_PASSWORD` tại file `.env` (tự động sinh ngẫu nhiên khi chạy script lần đầu, hoặc do người vận hành chỉ định).

---

## 5. Kiến trúc dịch vụ & Xử lý bất đồng bộ (Event-Driven)

Hệ thống gồm 7 khối container phối hợp chặt chẽ:

```
[Khách tham quan / Quản trị viên]
             │
             ▼
      [API Gateway :8080] ───(JWT / RBAC Policy)───┐
             │                                     │
      ┌──────┼──────────────┬──────────────┐       │
      ▼      ▼              ▼              ▼       ▼
   [Auth] [Content]    [Narration]       [Web Frontend :3000]
                            │
               (narration.requested)
                            ▼
                     [RabbitMQ Broker]
                            │
               (narration.requested)
                            ▼
                  [Translation Service]
                            │
               (translation.completed)
                            ▼
                     [RabbitMQ Broker]
                            │
               (translation.completed)
                            ▼
                     [TTS Service] ───(FFmpeg Quality Check)
                            │
                 (tts.completed / failed)
                            ▼
                     [RabbitMQ Broker]
                            │
                 (tts.completed / failed)
                            ▼
                     [Narration Service] ──► PUBLISHED / FAILED
```

1. **`api-gateway` (Cổng 8080)**: Reverse proxy, định tuyến, xác thực JWT tập trung qua JWKS endpoint, chặn header giả mạo (`X-User-*`), bảo vệ RBAC.
2. **`auth-service` (Cổng 8081)**: Quản lý người dùng, phân quyền, cấp phát token JWT ký bằng khóa RSA bất đối xứng.
3. **`content-service` (Cổng 8082)**: Quản lý nội dung thuyết minh nguồn, điểm tham quan (POIs), cấu hình mã QR.
4. **`narration-service` (Cổng 8083)**: Quản lý vòng đời tác vụ thuyết minh (`NarrationJob`), điều phối luồng xử lý và cập nhật tiến độ theo thời gian thực (SSE).
5. **`translation-service` (Cổng 8084)**: Tiêu thụ sự kiện `narration.requested`, dịch văn bản sang 5 ngôn ngữ mục tiêu (Anh, Pháp, Nhật, Trung, Hàn) bảo toàn ranh giới ngắt câu CJK/UTF-8.
6. **`tts-service` (Cổng 8085)**: Tiêu thụ sự kiện `translation.completed`, tạo file âm thanh MP3, kiểm tra chất lượng âm thanh bằng FFmpeg, lưu trữ file nguyên tử và phát hành sự kiện `tts.completed`.
7. **`web` (Cổng 3000)**: Ứng dụng giao diện người dùng React đóng gói trong Nginx alpine siêu nhẹ.

---

## 6. Phân quyền người dùng (Role-Based Access Control - RBAC)

Hệ thống thực thi ma trận phân quyền nghiêm ngặt tại cả tầng API Gateway và các dịch vụ nội bộ:

| Phân hệ / Chức năng | SUPER_ADMIN | CONTENT_MANAGER | STAFF | Khách vãng lai (Public) |
| :--- | :---: | :---: | :---: | :---: |
| Quản lý tài khoản người dùng (Xem, Tạo, Khóa/Mở) | ✅ | ❌ | ❌ | ❌ |
| Đổi mật khẩu cá nhân | ✅ | ✅ | ✅ | ❌ |
| Quản lý nội dung thuyết minh & Điểm tham quan | ✅ | ✅ | ❌ | ❌ |
| Tạo tác vụ thuyết minh tự động đa ngôn ngữ | ✅ | ✅ | ❌ | ❌ |
| Xem danh sách & Chi tiết tiến độ tác vụ | ✅ | ✅ | ✅ | ❌ |
| Nghe thử bản âm thanh (Preview Audio) | ✅ | ✅ | ✅ | ❌ |
| Xem lịch sử tác vụ & Dịch bổ sung | ✅ | ✅ | ❌ | ❌ |
| Nghe thuyết minh qua mã QR (/listen/:code) | ✅ | ✅ | ✅ | ✅ |

---

## 7. Nhà cung cấp Dịch thuật & TTS (Translation & TTS Providers)

Hệ thống được thiết kế theo mẫu Adapter Pattern, cho phép chuyển đổi linh hoạt giữa chế độ Giả lập (Mock) và Nhà cung cấp thật (Real) thông qua biến môi trường `PROVIDER_MODE` (`mock` hoặc `real`).

### Provider Tự động Tích hợp
* **Dịch thuật (Translation)**: Google Translate Client API (`gtx`) với cơ chế dự phòng tự động chuyển tiếp sang MyMemory API khi gặp sự cố mạng hoặc giới hạn ký tự.
* **Tổng hợp tiếng nói (TTS)**: Google Translate TTS Client API (`tw-ob`), tạo âm thanh chuẩn định dạng MP3 (MPEG Layer 3, 24kHz).
* **Kiểm định chất lượng âm thanh**: `tts-service` tự động chạy bộ lọc `ffmpeg` / `ffprobe` (`silencedetect`) để phát hiện và loại bỏ các file âm thanh bị lỗi khoảng lặng kéo dài (>1.5s) hoặc thời lượng quá ngắn (<0.5s).

### Chế độ thật (Real Mode) & Cơ chế Throttle điều tiết tốc độ
Khi cấu hình `PROVIDER_MODE=real`, hệ thống tự động kích hoạt các provider thực tế:
- **Dịch thuật (Translation)**: Sử dụng endpoint client công khai của Google Translate (`https://translate.googleapis.com/translate_a/single?client=gtx`) kết hợp cơ chế dự phòng tự động sang MyMemory Translation API.
- **Tổng hợp tiếng nói (TTS)**: Sử dụng endpoint client công khai Google TTS (`https://translate.google.com/translate_tts?client=tw-ob`), hỗ trợ đầy đủ 6 ngôn ngữ (tiếng Việt, Anh, Pháp, Nhật, Trung, Hàn) với định dạng xuất MP3 tiêu chuẩn.
- **Cơ chế Throttle (Rate Limiting)**: Vì các endpoint công khai áp dụng giới hạn tần suất gọi (rate limit), hệ thống cài đặt sẵn bộ điều tiết tốc độ nội bộ:
  - `GoogleTranslationProvider`: Tự động giãn cách tối thiểu **400ms** giữa các lượt request dịch.
  - `GoogleTtsProvider`: Tự động giãn cách tối thiểu **500ms** giữa các phân đoạn (chunk) tổng hợp giọng nói.
  Cơ chế này ngăn ngừa triệt để lỗi HTTP 429 (Too Many Requests) khi xử lý đồng thời nhiều ngôn ngữ mục tiêu.

> ### ⚠️ Tuyên bố miễn trừ trách nhiệm về API Thử nghiệm (Educational Disclaimer)
> Các endpoint dịch thuật và TTS trên sử dụng giao thức HTTP client công khai nhằm phục vụ **mục đích nghiên cứu học tập, thử nghiệm đồ án công nghệ phần mềm**.
> - Không đảm bảo cam kết chất lượng dịch vụ (SLA) và có thể thay đổi chính sách từ nhà cung cấp bên thứ ba.
> - **Khi đưa vào sản xuất thương mại chính thức (Production)**: Cần chuyển đổi cấu hình sang sử dụng các dịch vụ có bản quyền và SLA chính thức:
>   - *Dịch thuật*: Google Cloud Translation API hoặc Microsoft Azure Translator.
>   - *Tổng hợp tiếng nói*: Google Cloud Text-to-Speech API, Microsoft Azure Speech Services hoặc ElevenLabs.
>   Hệ thống đã có sẵn interface chuẩn `TranslationProvider` và `TtsProvider`, việc thay thế adapter chính thức chỉ cần bổ sung file cấu hình API Key và cấu hình endpoint tương ứng mà không cần thay đổi bất kỳ luồng xử lý nào khác.

---

## 8. Kiểm thử & Đảm bảo chất lượng (Verification)

Mỗi dịch vụ có thể được kiểm thử độc lập mà không cần phụ thuộc vào hạ tầng bên ngoài nhờ cơ chế H2 In-Memory và RabbitMQ Testcontainers / Mock:

```powershell
# Kiểm thử toàn diện từng backend microservice
cd auth-service; .\mvnw.cmd test
cd ..\content-service; .\mvnw.cmd test
cd ..\translation-service; .\mvnw.cmd test
cd ..\tts-service; .\mvnw.cmd test
cd ..\narration-service; .\mvnw.cmd test
cd ..\api-gateway; .\mvnw.cmd test

# Kiểm thử giao diện Web frontend
cd ..\web; pnpm install; pnpm test; pnpm build
```

---

## 9. Tài liệu tham chiếu

* [Đặc tả Yêu cầu Nghiệp vụ (Use Case Specification v2.8)](docs/source/UC.docx)
* [Đặc tả Hợp đồng API & Hướng sự kiện (API & Event Contracts v1.7)](docs/source/api-contracts.md)
* [Ma trận Hợp đồng và Trạng thái Kiểm chứng](docs/contract-matrix.md)
* [Hướng dẫn Mở rộng & Thêm Ngôn ngữ Mới](docs/them-ngon-ngu.md)

