# GIAO KÈO DỮ LIỆU (API & EVENT CONTRACTS) - V1.0

## Quy ước chung (Global Rules)
- **Base URL:** `/api/v1/`
- **Headers bắt buộc (cho các API cần xác thực):**
  - `Authorization: Bearer <access_token>`
  - `X-Correlation-ID: <uuid>` (Gateway tự sinh, các service phải forward tiếp)
- **Format trả về mặc định (chuẩn JSend):**
  - Thành công: `{ "status": "success", "data": { ... } }`
  - Thất bại: `{ "status": "error", "error_code": "...", "message": "..." }`

---

## 1. REST API: Auth Service (UC7, UC9)

### 1.1. Đăng nhập
- **POST** `/auth/login`
- **Request:**
```json
{
  "username": "admin",
  "password": "password123"
}
```
- **Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiI...",
    "refresh_token": "d7a8f9b2c...",
    "expires_in": 900
  }
}
```

### 1.2. Quản lý tài khoản (Admin)
- **GET** `/auth/users` (Danh sách tài khoản)
- **Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "users": [
      {
        "user_id": "u-123",
        "username": "admin",
        "role": "CONTENT_ADMIN",
        "failed_login_count": 0,
        "locked_until": null
      }
    ]
  }
}
```

- **POST** `/auth/users` (Tạo tài khoản mới)
- **Request:**
```json
{
  "username": "mod_01",
  "name": "Nguyen Van A",
  "password": "SecurePassword1!",
  "role": "CONTENT_ADMIN"
}
```

- **PUT** `/auth/users/{user_id}/unlock` (Mở khóa tài khoản)
- **Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "message": "Tài khoản u-123 đã được mở khóa."
  }
}
```

---

## 2. REST API: Content Service (UC1, UC8)

### 2.1. Điểm tham quan (POI)
- **GET** `/pois` (Kèm phân trang, filter)
- **POST** `/pois` (Tạo mới, DB tự sinh poi_id và qr_code)
- **Request:**
```json
{
  "name": "Bảo tàng Lịch sử",
  "description": "Khu trưng bày cổ vật triều Nguyễn..."
}
```
- **Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "poi_id": "p-999",
    "qr_code": "QR-ABC123XYZ",
    "name": "Bảo tàng Lịch sử"
  }
}
```
- **PUT** `/pois/{poi_id}`
- **DELETE** `/pois/{poi_id}`

### 2.2. Nội dung thuyết minh
- **POST** `/pois/{poi_id}/contents` (Tạo version mới)
- **Request:**
```json
{
  "title": "Giới thiệu bảo tàng",
  "source_lang": "vi",
  "text_content": "Chào mừng quý khách..."
}
```
- **Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "content_id": "c-888",
    "version": 1
  }
}
```
- **GET** `/pois/{poi_id}/contents/active` (Lấy version mới nhất chưa bị xóa mềm)
- **DELETE** `/contents/{content_id}` (Xóa mềm is_deleted = true)

---

## 3. REST API: Narration Service & Cổng Khách (UC2, UC5)

### 3.1. Quản lý Job (Dành cho Admin)
- **POST** `/jobs` 
  - **Headers:** `Idempotency-Key: <unique-string>`
  - **Request:** 
```json
{
  "content_id": "c-888",
  "version": 1,
  "targets": [
    { "lang": "en", "voice_id": "en-US-Journey-F" },
    { "lang": "fr", "voice_id": "fr-FR-Standard-A" }
  ]
}
```
  - **Response (202 Accepted):**
```json
{
  "status": "success",
  "data": {
    "job_id": "j-777",
    "status": "PENDING"
  }
}
```
- **GET** `/jobs/{job_id}` (Xem tiến độ tổng hợp)
- **POST** `/jobs/{job_id}/cancel` (Hủy Job - Cập nhật CANCEL_REQUESTED)

### 3.2. Cổng nghe cho Khách tham quan
- **GET** `/client/pois/{qr_code}/narrations`
  - **Response (200 OK):** 
```json
{
  "status": "success",
  "data": {
    "poi_name": "Bảo tàng Lịch sử",
    "content_title": "Giới thiệu bảo tàng",
    "narrations": [
      {
        "lang": "en",
        "audio_url": "https://minio.local/bucket/audio.mp3?X-Amz-Signature=...",
        "subtitle": "Welcome..."
      }
    ]
  }
}
```

---

## 4. REST API: Tích hợp nội bộ (Internal)

### 4.1. Lấy danh mục giọng đọc (Từ TTS Service)
- **GET** `/internal/voices`
  - **Response (200 OK):** 
```json
{
  "status": "success",
  "data": {
    "supported_voices": [
      {
        "lang": "en",
        "voices": ["en-US-Journey-F", "en-GB-Standard-A"]
      }
    ]
  }
}
```

---

## 5. MESSAGE BROKER (RABBITMQ) - BẤT ĐỒNG BỘ
**Exchange Chung:** `narration.events` (Type: Topic, Durable: true)

### 5.1. narration.requested (Tạo Target)
- **Routing Key:** `narration.requested`
- **Consumer:** Translation Service
- **Payload:** 
```json
{
  "job_id": "j-777",
  "target_id": "t-111",
  "content_id": "c-888",
  "version": 1,
  "lang": "en",
  "voice_id": "en-US-Journey-F",
  "occurred_at": "2026-10-07T10:00:00Z"
}
```

### 5.2. translation.completed (Dịch xong)
- **Routing Key:** `translation.completed`
- **Consumer:** TTS Service VÀ Narration Service (Fan-out qua 2 Queue)
- **Payload:** 
```json
{
  "job_id": "j-777",
  "target_id": "t-111",
  "translation_id": "tr-222",
  "lang": "en",
  "voice_id": "en-US-Journey-F",
  "occurred_at": "2026-10-07T10:00:05Z"
}
```

### 5.3. tts.completed (Audio xong)
- **Routing Key:** `tts.completed`
- **Consumer:** Narration Service
- **Payload:** 
```json
{
  "job_id": "j-777",
  "target_id": "t-111",
  "translation_id": "tr-222",
  "audio_id": "au-333",
  "lang": "en",
  "occurred_at": "2026-10-07T10:00:15Z"
}
```

### 5.4. Các sự kiện Lỗi
- **Routing Key:** `translation.failed` hoặc `tts.failed`
- **Consumer:** Narration Service
- **Payload:** 
```json
{
  "job_id": "j-777",
  "target_id": "t-111",
  "error_code": "PROVIDER_TIMEOUT",
  "occurred_at": "2026-10-07T10:01:00Z"
}
```

### 5.5. narration.cancelled (Lệnh Hủy khẩn cấp)
- **Exchange riêng:** `narration.cancel.fanout` (Type: Fanout)
- **Consumer:** Translation Service, TTS Service (Mọi replica)
- **Payload:** 
```json
{
  "job_id": "j-777",
  "correlation_id": "req-uuid-1234",
  "occurred_at": "2026-10-07T10:05:00Z"
}
```