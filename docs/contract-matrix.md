# Ma trận hợp đồng API và sự kiện

Ma trận được lập từ docs/source/api-contracts.md V1.7 và docs/source/UC.docx v2.8. Tài liệu nguồn giữ nguyên. File ERD không có trong working tree hiện tại; cột bảng/cột được suy ra từ UC và hợp đồng, xem “Quyết định ngoài contract” cuối file.

Trạng thái chỉ chuyển thành ✅ khi test đã chạy xanh trong lượt kiểm chứng tương ứng. Link trong cột test trỏ tới assertion thực tế; “Chưa có test tự động” là coverage gap và vẫn để ⬜. Các luồng phụ thuộc RabbitMQ còn cần kiểm thử consumer/publisher với broker thật.

| ID | Loại / hợp đồng | Service sở hữu | Phase | Test chứng minh | Trạng thái |
|---|---|---|---|---|---|
| C-001 | §1.1 public prefix /api/v1, internal prefix /internal | Tất cả | 0 | Chưa có test tự động | ⬜ |
| C-002 | §1.1 Bearer auth, ngoại lệ login/refresh/JWKS/client và SSE query token | Gateway | 6 | Chưa có test tự động | ⬜ |
| C-003 | §1.1 Gateway sinh/forward X-Correlation-ID, bỏ giá trị client | Gateway | 6 | [GatewayIntegrationTest.healthStartsWithoutCallingAuthOrContentAndCreatesCorrelationIds](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L150); [GatewayIntegrationTest.visitorListUsesInternalContentAndMapsNameToPoiName](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L476) | ✅ |
| C-004 | §1.1 internal caller dùng X-Service-Token riêng; forwarded X-User-Id, X-User-Role chỉ tin từ Gateway | Tất cả | 1–6 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44); [TranslationContractTest.internalTranslationReturnsPersistedTextAndRequiresCallerToken](../translation-service/src/test/java/vn/edu/cnpm/translation/TranslationContractTest.java#L16); [TtsContractTest.voiceCatalogAndSignedAudioLinkFollowContract](../tts-service/src/test/java/vn/edu/cnpm/tts/TtsContractTest.java#L18) | ✅ |
| C-005 | §1.1 Idempotency-Key bắt buộc khi tạo job | Narration | 5 | [NarrationContractTest.requiredKeyTargetValidationAndCancelAreContractual](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L58) | ✅ |
| C-006 | §1.1 role quản trị là CONTENT_ADMIN | Gateway | 6 | [GatewayIntegrationTest.wrongRoleIsForbiddenAndUnknownKidRefreshesJwksOnlyOnce](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L382) | ✅ |
| C-007 | §1.2 success envelope {status,data} | Tất cả | 1–6 | Chưa có test tự động | ⬜ |
| C-008 | §1.2 error envelope và correlationId | Tất cả | 1–6 | Chưa có test tự động | ⬜ |
| C-009 | §1.3 HTTP status semantics 200/201/202/204/400/401/403/404/409/500/503 | Tất cả | 1–6 | Chưa có test tự động | ⬜ |
| C-010 | ISO-8601 UTC, camelCase JSON/event, snake_case DB columns | Tất cả | 1–6 | Chưa có test tự động | ⬜ |
| A-001 | POST /api/v1/auth/login | Auth | 1 | [AuthContractTest.loginJwksRefreshAndLogoutFollowContract](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L24) | ✅ |
| A-002 | GET /api/v1/auth/.well-known/jwks.json | Auth | 1 | [AuthContractTest.loginJwksRefreshAndLogoutFollowContract](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L24) | ✅ |
| A-003 | POST /api/v1/auth/refresh | Auth | 1 | [AuthContractTest.loginJwksRefreshAndLogoutFollowContract](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L24) | ✅ |
| A-004 | POST /api/v1/auth/logout | Auth | 1 | [AuthContractTest.loginJwksRefreshAndLogoutFollowContract](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L24) | ✅ |
| A-005 | GET /api/v1/auth/users | Auth | 1 | Chưa có test tự động | ⬜ |
| A-006 | POST /api/v1/auth/users | Auth | 1 | [AuthContractTest.internalAccountApiHidesSecretsAndReturnsStableErrors](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L59) | ✅ |
| A-007 | PUT /api/v1/auth/users/{userId}/password | Auth | 1 | Chưa có test tự động | ⬜ |
| A-008 | PUT /api/v1/auth/users/{userId}/unlock | Auth | 1 | Chưa có test tự động | ⬜ |
| A-009 | GET /api/v1/languages/source | Content | 2 | Chưa có test tự động | ⬜ |
| A-010 | GET /api/v1/pois | Content | 2 | Chưa có test tự động | ⬜ |
| A-011 | POST /api/v1/pois | Content | 2 | Chưa có test tự động | ⬜ |
| A-012 | GET /api/v1/pois/{poiId} | Content | 2 | Chưa có test tự động | ⬜ |
| A-013 | PUT /api/v1/pois/{poiId} | Content | 2 | [ContentContractTest.poiCrudKeepsQrAndBlocksDeleteWhenAnyContentRowExists](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L20) | ✅ |
| A-014 | DELETE /api/v1/pois/{poiId} | Content | 2 | Chưa có test tự động | ⬜ |
| A-015 | POST /api/v1/pois/{poiId}/contents | Content | 2 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44) | ✅ |
| A-016 | GET /api/v1/contents | Content | 2 | Chưa có test tự động | ⬜ |
| A-017 | GET /api/v1/pois/{poiId}/contents/active | Content | 2 | Chưa có test tự động | ⬜ |
| A-018 | GET /api/v1/contents/{contentId} | Content | 2 | Chưa có test tự động | ⬜ |
| A-019 | PUT /api/v1/contents/{contentId} | Content | 2 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44) | ✅ |
| A-020 | DELETE /api/v1/contents/{contentId} | Content | 2 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44) | ✅ |
| A-021 | GET /internal/contents/{contentId}/versions/{version} | Content | 2 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44) | ✅ |
| A-022 | GET /internal/contents/{contentId}/current | Content | 2 | Chưa có test tự động | ⬜ |
| A-023 | GET /internal/pois | Content | 2 | Chưa có test tự động | ⬜ |
| A-024 | GET /internal/pois/{poiId}/current | Content | 2 | Chưa có test tự động | ⬜ |
| A-025 | GET /internal/pois/by-qr/{qrCode} | Content | 2 | Chưa có test tự động | ⬜ |
| A-026 | GET /internal/voices | TTS | 4 | [TtsContractTest.voiceCatalogAndSignedAudioLinkFollowContract](../tts-service/src/test/java/vn/edu/cnpm/tts/TtsContractTest.java#L18) | ✅ |
| A-027 | GET /api/v1/voices | Narration | 5 | Chưa có test tự động | ⬜ |
| A-028 | GET /internal/translations/{translationId} | Translation | 3 | [TranslationContractTest.internalTranslationReturnsPersistedTextAndRequiresCallerToken](../translation-service/src/test/java/vn/edu/cnpm/translation/TranslationContractTest.java#L16) | ✅ |
| A-029 | GET /internal/audios/{audioId}/signed-url | TTS | 4 | [TtsContractTest.voiceCatalogAndSignedAudioLinkFollowContract](../tts-service/src/test/java/vn/edu/cnpm/tts/TtsContractTest.java#L18) | ✅ |
| A-030 | POST /api/v1/jobs | Narration | 5 | [NarrationContractTest.createJobUsesCurrentContentVersionAndIdempotencyKey](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L37) | ✅ |
| A-031 | GET /api/v1/jobs/{jobId} | Narration | 5 | Chưa có test tự động | ⬜ |
| A-032 | POST /api/v1/jobs/{jobId}/cancel | Narration | 5 | [NarrationContractTest.requiredKeyTargetValidationAndCancelAreContractual](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L58) | ✅ |
| A-033 | GET /internal/narrations?contentId= | Narration | 5 | Chưa có test tự động | ⬜ |
| A-034 | GET /internal/narrations/{lang}?contentId= | Narration | 5 | Chưa có test tự động | ⬜ |
| A-035 | POST /internal/callbacks/job-progress | Gateway | 6 | [GatewayIntegrationTest.narrationProgressCallbackChecksCallerAndAcceptsUnsubscribedJob](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L566) | ✅ |
| A-036 | GET /api/v1/jobs/{jobId}/events?accessToken= | Gateway | 6 | Chưa có test tự động | ⬜ |
| A-037 | GET /api/v1/client/pois | Gateway | 6 | [GatewayIntegrationTest.visitorListUsesInternalContentAndMapsNameToPoiName](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L476) | ✅ |
| A-038 | GET /api/v1/client/qr/{qrCode} | Gateway | 6 | [GatewayIntegrationTest.clientQrLookupOnlyReturnsPoiId](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L542) | ✅ |
| A-039 | GET /api/v1/client/pois/{poiId}/narrations | Gateway | 6 | [GatewayIntegrationTest.visitorNarrationMergesActivePoiAndPublishedLanguages](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L493) | ✅ |
| A-040 | GET /api/v1/client/pois/{poiId}/narrations/{lang} | Gateway | 6 | Chưa có test tự động | ⬜ |
| A-041 | GET /health liveness-only, no DB/RabbitMQ | Mỗi ứng dụng | 1–7 | [Auth health](../auth-service/src/test/java/vn/edu/cnpm/auth/HealthWithoutDependenciesTest.java#L15); [Content health](../content-service/src/test/java/vn/edu/cnpm/content/HealthWithoutDependenciesTest.java#L15); [Narration health](../narration-service/src/test/java/vn/edu/cnpm/narration/HealthWithoutDependenciesTest.java#L15); [Translation health](../translation-service/src/test/java/vn/edu/cnpm/translation/HealthWithoutDependenciesTest.java#L15); [TTS health](../tts-service/src/test/java/vn/edu/cnpm/tts/HealthWithoutDependenciesTest.java#L15); [Gateway health](../api-gateway/src/test/java/vn/edu/cnpm/gateway/HealthWithoutDependenciesTest.java#L27) | ✅ |
| E-001 | VALIDATION_ERROR với field details | Tất cả | 1–6 | Chưa có test tự động | ⬜ |
| E-002 | INVALID_CREDENTIALS giống nhau cho user sai, password sai, tài khoản khóa | Auth | 1 | [AuthContractTest.internalAccountApiHidesSecretsAndReturnsStableErrors](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L59) | ✅ |
| E-003 | AUTH_SERVICE_UNAVAILABLE | Gateway | 6 | [GatewayIntegrationTest.authFailureDuringUnknownKidRefreshIs503ButCachedKeysStillWork](../api-gateway/src/test/java/vn/edu/cnpm/gateway/GatewayIntegrationTest.java#L411) | ✅ |
| E-004 | INVALID_REFRESH_TOKEN | Auth | 1 | [AuthContractTest.loginJwksRefreshAndLogoutFollowContract](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L24) | ✅ |
| E-005 | USERNAME_EXISTS | Auth | 1 | [AuthContractTest.internalAccountApiHidesSecretsAndReturnsStableErrors](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthContractTest.java#L59) | ✅ |
| E-006 | USER_NOT_FOUND | Auth | 1 | Chưa có test tự động | ⬜ |
| E-007 | CURRENT_PASSWORD_INVALID | Auth | 1 | Chưa có test tự động | ⬜ |
| E-008 | ACCOUNT_NOT_LOCKED | Auth | 1 | Chưa có test tự động | ⬜ |
| E-009 | POI_NOT_FOUND | Content | 2 | [ContentContractTest.poiCrudKeepsQrAndBlocksDeleteWhenAnyContentRowExists](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L20) | ✅ |
| E-010 | POI_HAS_CONTENT | Content | 2 | [ContentContractTest.poiDeleteConflictsEvenWhenContentWasSoftDeleted](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L87) | ✅ |
| E-011 | QR_CODE_GENERATION_FAILED | Content | 2 | Chưa có test tự động | ⬜ |
| E-012 | ACTIVE_CONTENT_EXISTS | Content | 2 | Chưa có test tự động | ⬜ |
| E-013 | CONTENT_NOT_FOUND | Content | 2 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44) | ✅ |
| E-014 | CONTENT_VERSION_CONFLICT with currentVersion | Content | 2 | [ContentContractTest.contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally](../content-service/src/test/java/vn/edu/cnpm/content/ContentContractTest.java#L44) | ✅ |
| E-015 | CONTENT_SERVICE_UNAVAILABLE | Narration | 5 | Chưa có test tự động | ⬜ |
| E-016 | IDEMPOTENCY_KEY_REQUIRED | Narration | 5 | [NarrationContractTest.requiredKeyTargetValidationAndCancelAreContractual](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L58) | ✅ |
| E-017 | INVALID_IDEMPOTENCY_KEY | Narration | 5 | Chưa có test tự động | ⬜ |
| E-018 | INVALID_TARGETS | Narration | 5 | Chưa có test tự động | ⬜ |
| E-019 | ACTIVE_JOB_EXISTS with details.jobIds[] | Narration | 5 | Chưa có test tự động | ⬜ |
| E-020 | IDEMPOTENCY_KEY_REUSED | Narration | 5 | [NarrationContractTest.createJobUsesCurrentContentVersionAndIdempotencyKey](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L37) | ✅ |
| E-021 | CONTENT_VERSION_CHANGED | Narration | 5 | Chưa có test tự động | ⬜ |
| E-022 | INVALID_RETRY_REFERENCE | Narration | 5 | Chưa có test tự động | ⬜ |
| E-023 | JOB_NOT_FOUND | Narration | 5 | Chưa có test tự động | ⬜ |
| E-024 | JOB_ALREADY_FINISHED | Narration | 5 | [NarrationContractTest.requiredKeyTargetValidationAndCancelAreContractual](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L58) | ✅ |
| E-025 | NARRATION_NOT_FOUND with details.availableLangs | Narration | 5 | [NarrationContractTest.timeoutFailsStalledTargetAndPublishedLookupReturnsNotFoundDetails](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L100) | ✅ |
| E-026 | Event error enum TIMEOUT/PROVIDER_ERROR/CONTENT_NOT_FOUND/STORAGE_ERROR/INTERNAL_ERROR | Translation/TTS/Narration | 3–5 | Chưa có test tự động | ⬜ |
| M-001 | §5.1 durable topic narration.events; routing key equals event name | Event publishers | 2–5 | Chưa có test tự động | ⬜ |
| M-002 | §5.1 durable fanout narration.cancel | Narration | 5 | Chưa có test tự động | ⬜ |
| M-003 | §5.1 event headers eventId/correlationId/schemaVersion/occurredAt; targetId in body | Event publishers | 2–5 | Chưa có test tự động | ⬜ |
| M-004 | §5.1 publisher confirms, persistent messages, manual ack, configurable prefetch | Event publishers/consumers | 2–5 | Chưa có test tự động | ⬜ |
| M-005 | §5.1 malformed message nack/no requeue to queue-specific DLQ | Translation/TTS/Narration | 3–5 | Chưa có test tự động | ⬜ |
| M-006 | §5.1 provider retry max 4 calls, then .failed, ack, no DLQ | Translation/TTS | 3–4 | Chưa có test tự động | ⬜ |
| M-007 | §5.1 persisted cancellation checked before provider/result | Translation/TTS | 3–4 | Chưa có test tự động | ⬜ |
| M-008 | §5.2 content.updated payload {contentId,version} | Content | 2 | Chưa có test tự động | ⬜ |
| M-009 | §5.2 content.deleted payload {contentId} | Content | 2 | Chưa có test tự động | ⬜ |
| M-010 | §5.2 narration.requested per target | Narration | 5 | Chưa có test tự động | ⬜ |
| M-011 | §5.2 translation.completed and separate TTS/Narration queues | Translation | 3 | Chưa có test tự động | ⬜ |
| M-012 | §5.2 translation.failed | Translation | 3 | Chưa có test tự động | ⬜ |
| M-013 | §5.2 tts.completed | TTS | 4 | Chưa có test tự động | ⬜ |
| M-014 | §5.2 tts.failed | TTS | 4 | Chưa có test tự động | ⬜ |
| M-015 | §5.2 narration.cancelled {jobId} | Narration | 5 | Chưa có test tự động | ⬜ |
| M-016 | §5.3 queue bindings, separate result queues and service-specific DLQs | All | 3–5 | Chưa có test tự động | ⬜ |
| M-017 | §6 lock job then targets in one transaction; final state ordering and aggregation | Narration | 5 | Chưa có test tự động | ⬜ |
| M-018 | §6 15-minute phase timeout and TIMEOUT failure | Narration | 5 | [NarrationContractTest.timeoutFailsStalledTargetAndPublishedLookupReturnsNotFoundDetails](../narration-service/src/test/java/vn/edu/cnpm/narration/NarrationContractTest.java#L100) | ✅ |
| M-019 | §6 bounded provider/internal retries | Translation/TTS | 3–4 | Chưa có test tự động | ⬜ |
| M-020 | §6 audio metadata stores objectKey, never signed URL | TTS | 4 | [TtsContractTest.voiceCatalogAndSignedAudioLinkFollowContract](../tts-service/src/test/java/vn/edu/cnpm/tts/TtsContractTest.java#L18) | ✅ |
| B-001 | BR-01 RBAC, BR-02 text length, BR-03 configured languages/voice pairs | Gateway/Content/Narration/TTS | 1–6 | Chưa có test tự động | ⬜ |
| B-002 | BR-04 job-local cancellation and BR-05 malformed/provider retry distinction | Narration/Translation/TTS | 3–5 | Chưa có test tự động | ⬜ |
| B-003 | BR-06 translation/audio reuse keys and idempotent consumers | Translation/TTS/Narration | 3–5 | Chưa có test tự động | ⬜ |
| B-004 | BR-07 access/refresh lifetime and role semantics | Auth/Gateway | 1/6 | Chưa có test tự động | ⬜ |
| B-005 | BR-08 MP3/object metadata, BR-09 timeout, BR-10 state transitions/lock order | TTS/Narration | 4–5 | Chưa có test tự động | ⬜ |
| B-006 | BR-11 immutable content versions, soft delete, per-language latest published version | Content/Narration | 2/5 | Chưa có test tự động | ⬜ |
| B-007 | BR-12 atomic failed-login count/lock and reset | Auth | 1 | [AuthConcurrencyContractTest.concurrentWrongPasswordsAtomicallyLockAtFiveAttempts](../auth-service/src/test/java/vn/edu/cnpm/auth/AuthConcurrencyContractTest.java#L19) | ✅ |
| B-008 | BR-13 correlation propagation | Gateway/Narration/event consumers | 1–6 | Chưa có test tự động | ⬜ |
| B-009 | BR-14 POI UUID/unique QR/name/description rules | Content | 2 | Chưa có test tự động | ⬜ |
| B-010 | BR-15 physical POI delete blocked by any content FK | Content | 2 | Chưa có test tự động | ⬜ |
| B-011 | BR-16 bcrypt, no password/refresh secret response, revoke refresh on password change | Auth | 1 | Chưa có test tự động | ⬜ |
| B-012 | BR-17 Content/Narration outbox transaction and publisher confirm | Content/Narration | 2/5 | Chưa có test tự động | ⬜ |
| O-001 | H2 MODE=MSSQLServer for offline verification; SQL Server configured through DB_URL for local runtime | All | 0 | Chưa có test tự động | ⬜ |
| O-002 | Local object storage uses HMAC-SHA256 URL expiring in one hour; S3-compatible storage replaces adapter | TTS | 4 | [TtsContractTest.voiceCatalogAndSignedAudioLinkFollowContract](../tts-service/src/test/java/vn/edu/cnpm/tts/TtsContractTest.java#L18) | ✅ |
| O-003 | Deleted ERD files are not restored; schema follows names explicitly stated in UC/contract | All | 0 | Chưa có test tự động | ⬜ |