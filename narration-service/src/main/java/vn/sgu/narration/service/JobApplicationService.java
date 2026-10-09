package vn.sgu.narration.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.config.RabbitConfiguration;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.integration.NarrationDependencies;
import vn.sgu.narration.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class JobApplicationService {
    private static final String OPERATION = "POST:/jobs";
    private static final List<JobStatus> ACTIVE_STATUSES = List.of(JobStatus.PENDING, JobStatus.PROCESSING);

    private final NarrationJobRepository jobRepository;
    private final JobTargetRepository targetRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final CancelledJobRepository cancelledJobRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NarrationDependencies dependencies;
    private final JobViewMapper mapper;
    private final OutboxWriter outboxWriter;
    private final ProgressNotifications progressNotifications;
    private final ObjectMapper objectMapper;
    private final Duration cancellationRetention;
    private final TransactionTemplate transactionTemplate;

    public JobApplicationService(NarrationJobRepository jobRepository,
                                 JobTargetRepository targetRepository,
                                 IdempotencyRepository idempotencyRepository,
                                 CancelledJobRepository cancelledJobRepository,
                                 JdbcTemplate jdbcTemplate,
                                 NarrationDependencies dependencies,
                                 JobViewMapper mapper,
                                 OutboxWriter outboxWriter,
                                 ProgressNotifications progressNotifications,
                                 ObjectMapper objectMapper,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${app.jobs.cancellation-retention:PT48H}") Duration cancellationRetention) {
        this.jobRepository = jobRepository;
        this.targetRepository = targetRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.cancelledJobRepository = cancelledJobRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dependencies = dependencies;
        this.mapper = mapper;
        this.outboxWriter = outboxWriter;
        this.progressNotifications = progressNotifications;
        this.objectMapper = objectMapper;
        this.cancellationRetention = cancellationRetention;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Contracts.JobView create(String userId, String correlationId, String idempotencyKey,
                                    Contracts.CreateJobRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Thiếu Idempotency-Key");
        }
        if (idempotencyKey.length() > 255) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key vượt quá 255 ký tự");
        }
        if (userId == null || userId.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED_USER", "Không xác định được người dùng");
        }
        validateTargets(request);
        String requestHash = requestHash(request);

        Optional<IdempotencyEntity> existing = idempotencyRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, OPERATION, idempotencyKey);
        if (existing.isPresent() && isIdempotencyReusable(existing.get(), Instant.now())) {
            return resolveIdempotency(existing.get(), requestHash);
        }

        Contracts.ContentCheck content = dependencies.validateContentForJob(request.contentId(), correlationId);
        if (content == null || content.version() == null || !Boolean.FALSE.equals(content.isDeleted())) {
            throw new AppException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Nội dung không tồn tại hoặc đã bị xóa mềm");
        }
        validateVoices(request.targets(), correlationId);
        return Objects.requireNonNull(transactionTemplate.execute(status -> createInTransaction(
                userId, correlationId, idempotencyKey, request, requestHash, content.version())));
    }

    private void validateTargets(Contracts.CreateJobRequest request) {
        if (request.targets() == null || request.targets().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS", "Phải chọn ít nhất một ngôn ngữ đích");
        }
        Set<String> languages = new HashSet<>();
        for (Contracts.TargetInput target : request.targets()) {
            if (target == null || target.lang() == null || target.lang().isBlank()
                    || target.voiceId() == null || target.voiceId().isBlank()
                    || !languages.add(target.lang().trim().toLowerCase(Locale.ROOT))) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS", "Target rỗng, trùng ngôn ngữ hoặc thiếu voiceId");
            }
        }
    }

    private void validateVoices(List<Contracts.TargetInput> targets, String correlationId) {
        Contracts.VoiceCatalog catalog = dependencies.voices(correlationId);
        if (catalog == null || catalog.supportedVoices() == null
                || catalog.supportedVoices().stream().anyMatch(group -> group == null
                || group.lang() == null || group.lang().isBlank() || group.voices() == null)) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_SERVICE_UNAVAILABLE", "Không lấy được danh mục giọng đọc");
        }
        Map<String, Set<String>> supported = new HashMap<>();
        for (Contracts.VoiceGroup group : catalog.supportedVoices()) {
            supported.put(group.lang().toLowerCase(Locale.ROOT), new HashSet<>(group.voices()));
        }
        for (Contracts.TargetInput target : targets) {
            Set<String> voices = supported.get(target.lang().toLowerCase(Locale.ROOT));
            if (voices == null || !voices.contains(target.voiceId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS",
                        "Ngôn ngữ hoặc voiceId không thuộc danh mục được hỗ trợ",
                        Map.of("lang", target.lang(), "voiceId", target.voiceId()));
            }
        }
    }

    protected Contracts.JobView createInTransaction(String userId, String correlationId,
                                                    String idempotencyKey,
                                                    Contracts.CreateJobRequest request,
                                                    String requestHash, int version) {
        acquireIdempotencyLock(userId, idempotencyKey);
        acquireContentVersionLock(request.contentId(), version);
        Instant now = Instant.now();
        Optional<IdempotencyEntity> repeated = idempotencyRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, OPERATION, idempotencyKey);
        if (repeated.isPresent() && isIdempotencyReusable(repeated.get(), now)) {
            return resolveIdempotency(repeated.get(), requestHash);
        }
        repeated.ifPresent(record -> idempotencyRepository.delete(record));

        List<NarrationJobEntity> activeJobs = jobRepository.findByContentVersionAndStatuses(
                request.contentId(), version, ACTIVE_STATUSES);
        String permittedRetryJob = validateRetryReference(request, activeJobs, version);
        for (NarrationJobEntity active : activeJobs) {
            if (active.getJobId().equals(permittedRetryJob)) continue;
            for (Contracts.TargetInput input : request.targets()) {
                if (active.getTargets().stream().anyMatch(t -> t.getLang().equalsIgnoreCase(input.lang()))) {
                    throw new AppException(HttpStatus.CONFLICT, "ACTIVE_JOB_EXISTS",
                            "Đã có job đang xử lý cho nội dung, phiên bản và ngôn ngữ này",
                            Map.of("jobId", active.getJobId()));
                }
            }
        }

        String jobId = "j-" + UUID.randomUUID().toString().replace("-", "");
        NarrationJobEntity job = new NarrationJobEntity(jobId, request.contentId(), version,
                userId, correlationId, request.retryOfJobId(), now);
        request.targets().stream().sorted(Comparator.comparing(Contracts.TargetInput::lang))
                .forEach(input -> job.addTarget(new JobTargetEntity(
                        "t-" + UUID.randomUUID().toString().replace("-", ""),
                        input.lang().trim(), input.voiceId().trim(), now)));
        jobRepository.saveAndFlush(job);
        idempotencyRepository.save(new IdempotencyEntity(userId, OPERATION, idempotencyKey,
                requestHash, jobId, now.plus(Duration.ofHours(24)), now));
        return mapper.toView(job);
    }

    private String validateRetryReference(Contracts.CreateJobRequest request,
                                          List<NarrationJobEntity> activeJobs, int version) {
        if (request.retryOfJobId() == null || request.retryOfJobId().isBlank()) return null;
        NarrationJobEntity referenced = activeJobs.stream()
                .filter(j -> j.getJobId().equals(request.retryOfJobId())).findFirst()
                .orElseGet(() -> jobRepository.findById(request.retryOfJobId())
                        .filter(job -> ACTIVE_STATUSES.contains(job.getStatus()))
                        .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, "INVALID_RETRY_REFERENCE",
                                "Job được tham chiếu không còn hoạt động hoặc không tồn tại")));
        if (!referenced.getContentId().equals(request.contentId())) {
            throw new AppException(HttpStatus.CONFLICT, "INVALID_RETRY_REFERENCE",
                    "Job được tham chiếu thuộc nội dung khác");
        }
        if (referenced.getContentVersion() != version) {
            throw new AppException(HttpStatus.CONFLICT, "CONTENT_VERSION_CHANGED",
                    "Version hiện hành đã thay đổi; hãy tạo job cho version mới nhất");
        }
        boolean matches = request.targets().stream().allMatch(input -> referenced.getTargets().stream()
                .anyMatch(target -> target.getLang().equalsIgnoreCase(input.lang())));
        if (!matches) {
            throw new AppException(HttpStatus.CONFLICT, "INVALID_RETRY_REFERENCE",
                    "Job được tham chiếu không có đầy đủ ngôn ngữ cần tạo lại");
        }
        return referenced.getJobId();
    }

    private void acquireIdempotencyLock(String userId, String idempotencyKey) {
        String scope = userId + "|" + OPERATION + "|" + idempotencyKey;
        String hashedScope;
        try {
            hashedScope = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(scope.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
        acquireApplicationLock("idempotency:" + hashedScope, "IDEMPOTENCY_LOCK_UNAVAILABLE");
    }

    private void acquireContentVersionLock(String contentId, int version) {
        acquireApplicationLock("job:create:" + contentId + ":" + version, "JOB_LOCK_UNAVAILABLE");
    }

    private void acquireApplicationLock(String resource, String errorCode) {
        Integer result = jdbcTemplate.queryForObject(
                "DECLARE @result int; EXEC @result = sp_getapplock @Resource = ?, @LockMode = 'Exclusive', " +
                        "@LockOwner = 'Transaction', @LockTimeout = 10000; SELECT @result",
                Integer.class, resource);
        if (result == null || result < 0) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, errorCode,
                    "Không thể khóa phạm vi tạo job; vui lòng thử lại");
        }
    }

    @Transactional(readOnly = true)
    public Contracts.JobView get(String jobId) {
        NarrationJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Không tìm thấy job"));
        return mapper.toView(job);
    }

    @Transactional
    public Contracts.JobView cancel(String jobId) {
        NarrationJobEntity job = jobRepository.findForUpdate(jobId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Không tìm thấy job"));
        if (job.getStatus().isTerminal()) {
            throw new AppException(HttpStatus.CONFLICT, "JOB_ALREADY_FINISHED", "Job đã kết thúc");
        }
        Instant now = Instant.now();
        List<JobTargetEntity> targets = targetRepository.findAllForUpdate(jobId);
        for (JobTargetEntity target : targets) {
            if (target.getStatus() != TargetStatus.FAILED) target.moveTo(TargetStatus.CANCELLED, null, now);
        }
        job.setStatus(JobStatus.CANCELLED, now);
        cancelledJobRepository.save(new CancelledJobEntity(jobId, now, now.plus(cancellationRetention)));
        idempotencyRepository.findByJobId(jobId)
                .forEach(record -> record.setExpiresAt(now.plus(Duration.ofHours(24))));
        outboxWriter.append(RabbitConfiguration.CANCEL_EXCHANGE, "", "narration.cancelled",
                job.getCorrelationId(), new Contracts.NarrationCancelled(jobId), now);
        progressNotifications.publish(job, targets);
        return mapper.toView(job);
    }

    @Transactional
    public void setIdempotencyExpiry(String jobId, Instant expiresAt) {
        idempotencyRepository.findByJobId(jobId).forEach(record -> record.setExpiresAt(expiresAt));
    }

    private Contracts.JobView resolveIdempotency(IdempotencyEntity record, String requestHash) {
        if (!MessageDigest.isEqual(record.getRequestHash().getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new AppException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key đã được dùng với request khác");
        }
        return get(record.getJobId());
    }

    private boolean isIdempotencyReusable(IdempotencyEntity record, Instant now) {
        if (record.getExpiresAt().isAfter(now)) return true;
        return jobRepository.findById(record.getJobId())
                .map(job -> !job.getStatus().isTerminal())
                .orElse(false);
    }

    private String requestHash(Contracts.CreateJobRequest request) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("contentId", request.contentId());
        canonical.put("retryOfJobId", request.retryOfJobId());
        canonical.put("targets", request.targets().stream()
                .sorted(Comparator.comparing(Contracts.TargetInput::lang))
                .map(t -> List.of(t.lang().trim(), t.voiceId().trim())).toList());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JacksonException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không thể tạo request hash", ex);
        }
    }
}
