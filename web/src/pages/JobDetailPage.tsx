import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { contentApi } from '../api/contentApi';
import { jobsApi } from '../api/jobsApi';
import { ApiError, ContentItem, JobDetail, JobStatus, JobTarget } from '../api/types';
import { AudioPreviewModal } from '../components/AudioPreviewModal';
import { ErrorBanner } from '../components/ErrorBanner';
import { Spinner } from '../components/Spinner';
import { StatusBadge } from '../components/StatusBadge';
import { Button } from '../components/Button';
import { PageHeader } from '../components/PageHeader';
import { usePolling } from '../hooks/usePolling';
import { getLanguageByCode, getVoiceByVoiceId } from '../config/languageRegistry';

export const JobDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const jobId = id || '';
  const navigate = useNavigate();

  const [cancelling, setCancelling] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [retryingTargetLang, setRetryingTargetLang] = useState<string | null>(null);
  const [showRetryConfirm, setShowRetryConfirm] = useState(false);
  const [actionError, setActionError] = useState<unknown>(null);
  const [notice, setNotice] = useState('');
  const [content, setContent] = useState<ContentItem | null>(null);
  const [previewLang, setPreviewLang] = useState<string | undefined>(undefined);
  const [showPreview, setShowPreview] = useState(false);
  const [copied, setCopied] = useState(false);
  const [isMockMode, setIsMockMode] = useState(false);

  useEffect(() => {
    jobsApi.getVoices().then((res) => {
      if (res && res.isMock) {
        setIsMockMode(true);
      }
    }).catch(() => {});
  }, []);

  const isTerminal = (data: JobDetail): boolean => {
    const terminalStatuses: JobStatus[] = [
      'COMPLETED',
      'PARTIALLY_COMPLETED',
      'FAILED',
      'CANCELLED',
    ];
    return terminalStatuses.includes(data.status);
  };

  const {
    data: job,
    loading,
    error: pollError,
    isTimeout,
    refresh,
  } = usePolling<JobDetail>({
    fn: () => jobsApi.getJob(jobId),
    isTerminal,
    intervalMs: 5000,
    maxDurationMs: 600000, // 10 minutes maximum polling
    enabled: !!jobId,
  });

  // Load Content to get POI name and title for header display and preview
  useEffect(() => {
    if (job?.contentId && !content) {
      contentApi
        .getContent(job.contentId)
        .then((c) => {
          if (c) setContent(c);
        })
        .catch(() => {});
    }
  }, [job?.contentId, content]);

  const canCancel = job && (job.status === 'PENDING' || job.status === 'PROCESSING');

  const failedTargets = (job?.targets || []).filter((t) => {
    if (t.status !== 'FAILED') return false;
    const lDef = getLanguageByCode(t.lang);
    return lDef ? lDef.enabled !== false : true;
  });
  const canRetry = job && (job.status === 'FAILED' || job.status === 'PARTIALLY_COMPLETED') && failedTargets.length > 0;
  const isContentOutdated = !!(content && job && content.version > job.version);

  const isSlowProcessing = !!(
    job &&
    (job.status === 'PENDING' || job.status === 'PROCESSING') &&
    job.createdAt &&
    Date.now() - new Date(job.createdAt).getTime() > 300000
  );

  const handleCopyId = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const handleCancel = async () => {
    if (!jobId || !canCancel) return;
    const confirmed = window.confirm(`Bạn có chắc chắn muốn hủy tác vụ này không?`);
    if (!confirmed) return;

    setCancelling(true);
    setActionError(null);
    try {
      await jobsApi.cancelJob(jobId);
      setNotice(`Tác vụ đã được hủy thành công.`);
      await refresh();
    } catch (err) {
      setActionError(err);
    } finally {
      setCancelling(false);
    }
  };

  const handleExecuteRetry = async () => {
    if (!job || !failedTargets.length) return;
    setRetrying(true);
    setActionError(null);
    try {
      const retryPayload = {
        contentId: job.contentId,
        retryOfJobId: job.jobId,
        expectedVersion: job.version,
        targets: failedTargets.map((t) => ({
          lang: t.lang,
          voiceId: t.voiceId,
        })),
      };

      const newJob = await jobsApi.createJob(retryPayload, crypto.randomUUID());
      jobsApi.addRecentJobId(newJob.jobId);
      setShowRetryConfirm(false);
      navigate(`/jobs/${newJob.jobId}`);
    } catch (err) {
      setActionError(err);
      setShowRetryConfirm(false);
    } finally {
      setRetrying(false);
    }
  };

  const handleRetrySingleTarget = async (target: JobTarget) => {
    if (!job) return;
    setRetryingTargetLang(target.lang);
    setActionError(null);
    try {
      const retryPayload = {
        contentId: job.contentId,
        retryOfJobId: job.jobId,
        expectedVersion: job.version,
        targets: [{
          lang: target.lang,
          voiceId: target.voiceId,
        }],
      };
      const newJob = await jobsApi.createJob(retryPayload, crypto.randomUUID());
      jobsApi.addRecentJobId(newJob.jobId);
      navigate(`/jobs/${newJob.jobId}`);
    } catch (err) {
      setActionError(err);
    } finally {
      setRetryingTargetLang(null);
    }
  };

  const formatDate = (iso?: string) => {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleString('vi-VN');
    } catch {
      return iso;
    }
  };

  const getStepAndElapsed = (target: JobTarget, jobCreatedAt?: string) => {
    const norm = (target.status || 'PENDING').toUpperCase();
    if (norm === 'PUBLISHED' || norm === 'FAILED' || norm === 'CANCELLED') return '';

    let stepName = 'Đang chờ';
    if (norm === 'TRANSLATING') stepName = 'Đang dịch thuật';
    else if (norm === 'SYNTHESIZING') stepName = 'Đang tạo giọng đọc';
    else if (norm === 'PROCESSING') stepName = 'Đang xử lý';

    const baseTime = target.updatedAt || jobCreatedAt;
    if (!baseTime) return stepName;
    const elapsedSec = Math.max(0, Math.floor((Date.now() - new Date(baseTime).getTime()) / 1000));
    const elapsedMin = Math.floor(elapsedSec / 60);

    if (elapsedMin < 1) return `${stepName} · Vừa xong`;
    return `${stepName} · ${elapsedMin} phút`;
  };

  const getFriendlyErrorMessage = (code?: string | null, msg?: string | null) => {
    if (msg && !msg.startsWith('[')) return msg;
    switch (code) {
      case 'TIMEOUT':
        return 'Quá thời gian xử lý';
      case 'AUDIO_GENERATION_FAILED':
        return 'Không thể tạo tệp âm thanh';
      case 'CONTENT_NOT_FOUND':
        return 'Không tìm thấy nội dung';
      case 'INTERNAL_ERROR':
        return 'Lỗi xử lý nội bộ';
      default:
        return code || 'Thất bại';
    }
  };

  const isNotFound =
    (!loading && !job) ||
    (pollError instanceof ApiError && pollError.status === 404);

  if (isNotFound) {
    return (
      <div className="admin-page" style={{ padding: '0 4px' }}>
        <div
          style={{
            background: '#ffffff',
            border: '1px solid var(--border-light)',
            borderRadius: '8px',
            padding: '40px 24px',
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: '32px', marginBottom: '12px' }}>🔍</div>
          <h2 style={{ fontSize: '18px', color: 'var(--text-main)', margin: '0 0 8px' }}>
            Không tìm thấy tác vụ
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px', margin: '0 0 20px' }}>
            Tác vụ với mã <code>{jobId}</code> không tồn tại hoặc bạn không có quyền xem thông tin này.
          </p>
          <Button variant="primary" onClick={() => navigate('/jobs')}>
            ← Quay lại danh sách tác vụ
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page" style={{ padding: '0 4px', maxWidth: '880px', margin: '0 auto' }}>
      <PageHeader
        eyebrow="TIẾN TRÌNH THUYẾT MINH"
        title="Chi tiết tác vụ"
        description="Theo dõi quá trình tự động dịch thuật và chuyển văn bản thành giọng nói."
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button
              variant="secondary"
              onClick={refresh}
              disabled={loading}
              icon={
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.3" />
                </svg>
              }
            >
              Làm mới
            </Button>
            <Button variant="secondary" onClick={() => navigate('/jobs')}>
              ← Danh sách tác vụ
            </Button>
          </div>
        }
      />

      <ErrorBanner error={actionError} onDismiss={() => setActionError(null)} />

      {isMockMode && (
        <div
          style={{
            background: '#f0f9ff',
            border: '1px solid #bae6fd',
            borderRadius: '8px',
            padding: '12px 16px',
            margin: '14px 0',
            color: '#0369a1',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <span>ℹ</span>
          <span><strong>Chế độ chạy thử (giọng giả lập):</strong> Tác vụ đang được xử lý trong môi trường kiểm thử với giọng đọc giả lập.</span>
        </div>
      )}

      {notice && (
        <div
          style={{
            background: '#e8f2ea',
            border: '1px solid #b7d6be',
            borderRadius: '8px',
            padding: '12px 16px',
            margin: '14px 0',
            color: '#285e3a',
            fontSize: '13px',
          }}
        >
          {notice}
        </div>
      )}

      {isSlowProcessing && (
        <div
          style={{
            background: '#fff8e6',
            border: '1px solid #ffd885',
            borderRadius: '8px',
            padding: '14px 18px',
            margin: '14px 0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '10px',
          }}
        >
          <div style={{ color: '#7a5100', fontSize: '13px', flex: 1 }}>
            <strong>⚠️ Đang xử lý lâu hơn bình thường (đã hơn 5 phút).</strong> Hệ thống đang nỗ lực hoàn tất quy trình chuyển đổi giọng nói. Bạn có thể kiên nhẫn đợi thêm hoặc hủy tác vụ nếu cần.
          </div>
          {canCancel && (
            <Button variant="danger" size="sm" onClick={handleCancel} loading={cancelling}>
              ✕ Hủy ngay
            </Button>
          )}
        </div>
      )}

      {isTimeout && (
        <div
          style={{
            background: '#fff8e6',
            border: '1px solid #ffd885',
            borderRadius: '8px',
            padding: '12px 16px',
            margin: '14px 0',
            color: '#7a5100',
            fontSize: '13px',
          }}
        >
          <strong>Đã dừng tự động cập nhật sau 10 phút.</strong> Bạn có thể bấm "Làm mới" bất kỳ lúc nào để kiểm tra trạng thái mới nhất.
        </div>
      )}

      {loading && !job ? (
        <div style={{ padding: '60px 0', textAlign: 'center' }}>
          <Spinner />
          <p style={{ marginTop: '12px', color: '#64748b' }}>Đang nạp thông tin tác vụ...</p>
        </div>
      ) : job && (
        <div
          className="job-panel panel"
          style={{
            background: '#ffffff',
            border: '1px solid var(--border-light)',
            borderRadius: '8px',
            padding: '24px',
            marginTop: '20px',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '16px',
              borderBottom: '1px solid var(--border-light)',
              paddingBottom: '14px',
              flexWrap: 'wrap',
              gap: '12px',
            }}
          >
            <div>
              <h2 style={{ fontSize: '18px', color: 'var(--text-main)', margin: '0 0 4px' }}>
                {content?.title || 'Đang tải tiêu đề...'}
              </h2>
              <div style={{ fontSize: '13px', color: '#475569' }}>
                🏛 {content?.poiName || 'Điểm tham quan'} · <strong>Bản {job.version}</strong>
              </div>
            </div>

            <StatusBadge status={job.status} />
          </div>

          {/* Metadata Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: '14px',
              fontSize: '13px',
              background: '#f8fafc',
              padding: '14px 18px',
              borderRadius: '6px',
              border: '1px solid #e2e8f0',
            }}
          >
            <div>
              <span style={{ color: '#64748b', display: 'block', marginBottom: '2px' }}>Mã tác vụ:</span>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontFamily: 'monospace', fontSize: '12px', wordBreak: 'break-all' }}>{job.jobId}</span>
                <button
                  type="button"
                  onClick={() => handleCopyId(job.jobId)}
                  title="Sao chép mã tác vụ"
                  style={{
                    border: 'none',
                    background: 'transparent',
                    cursor: 'pointer',
                    padding: '2px 4px',
                    fontSize: '12px',
                    color: copied ? '#16a34a' : '#64748b',
                  }}
                >
                  {copied ? '✓' : '📋'}
                </button>
              </div>
            </div>
            <div>
              <span style={{ color: '#64748b', display: 'block' }}>Khởi tạo lúc:</span>
              <span>{formatDate(job.createdAt)}</span>
            </div>
            <div>
              <span style={{ color: '#64748b', display: 'block' }}>Cập nhật gần nhất:</span>
              <span>{formatDate(job.updatedAt)}</span>
            </div>
          </div>

          {/* Targets List */}
          <div style={{ marginTop: '24px' }}>
            <h3 style={{ fontSize: '15px', color: '#334155', marginBottom: '12px' }}>
              Danh sách ngôn ngữ đích ({job.targets.length})
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {job.targets.map((t) => {
                const langDef = getLanguageByCode(t.lang);
                const voiceDef = getVoiceByVoiceId(t.voiceId);
                const stepAndElapsed = getStepAndElapsed(t, job.createdAt);
                const friendlyError = getFriendlyErrorMessage(t.errorCode, (t as any).errorMessage);

                const isTargetRetrying = retryingTargetLang === t.lang;

                return (
                  <div
                    key={t.targetId || t.lang}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '12px 16px',
                      background: '#f8fafc',
                      border: '1px solid #e2e8f0',
                      borderRadius: '6px',
                      flexWrap: 'wrap',
                      gap: '12px',
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 'bold', fontSize: '15px', color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                        <span>{langDef ? (langDef.nameVi === langDef.nativeName ? langDef.nameVi : `${langDef.nameVi} (${langDef.nativeName})`) : t.lang.toUpperCase()}</span>
                        {(!langDef || langDef.enabled === false) && (
                          <span style={{ fontSize: '11px', color: '#991b1b', background: '#fee2e2', padding: '1px 6px', borderRadius: '4px', fontWeight: 600 }}>
                            Ngôn ngữ đã tạm dừng
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                        Giọng đọc: <strong title={t.voiceId}>{voiceDef ? voiceDef.label : t.voiceId}</strong>
                        {stepAndElapsed ? (
                          <span style={{ marginLeft: '10px', color: '#0f4c81', fontWeight: 500 }}>
                            {stepAndElapsed}
                          </span>
                        ) : null}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <StatusBadge status={t.status} />

                      {t.status === 'PUBLISHED' && content?.poiId && (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => {
                            setPreviewLang(t.lang);
                            setShowPreview(true);
                          }}
                          icon={<span>▶</span>}
                        >
                          Nghe thử
                        </Button>
                      )}

                      {t.status === 'FAILED' && (
                        <>
                          <span
                            title={t.errorCode || ''}
                            style={{
                              fontSize: '12px',
                              color: '#b91c1c',
                              background: '#fef2f2',
                              border: '1px solid #fecaca',
                              padding: '2px 8px',
                              borderRadius: '4px',
                            }}
                          >
                            {friendlyError}
                          </span>

                          {langDef && langDef.enabled !== false ? (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleRetrySingleTarget(t)}
                              disabled={isTargetRetrying || retrying}
                              loading={isTargetRetrying}
                            >
                              ↻ Thử lại
                            </Button>
                          ) : (
                            <span style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic' }}>
                              (Ngôn ngữ đã tạm dừng)
                            </span>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Action Row */}
          <div
            className="actions-row"
            style={{
              marginTop: '28px',
              paddingTop: '18px',
              borderTop: '1px solid var(--border-light)',
              display: 'flex',
              gap: '12px',
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
          >
            {canCancel && (
              <Button
                variant="danger"
                size="md"
                onClick={handleCancel}
                loading={cancelling}
              >
                ✕ Hủy tác vụ này
              </Button>
            )}

            {canRetry && (
              <Button
                variant="primary"
                size="md"
                onClick={() => setShowRetryConfirm(true)}
              >
                ↻ Thử lại tất cả ngôn ngữ lỗi ({failedTargets.length})
              </Button>
            )}

            {job.status === 'COMPLETED' && (
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  color: '#15803d',
                  background: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  padding: '8px 14px',
                  borderRadius: '4px',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                ✓ Tác vụ đã hoàn tất thành công cho mọi ngôn ngữ
              </span>
            )}
          </div>
        </div>
      )}

      {/* Retry Confirmation Modal */}
      {showRetryConfirm && job && (
        <div className="modal-backdrop">
          <div className="modal-dialog" style={{ maxWidth: '520px' }}>
            <div className="modal-header">
              <h2 className="modal-title">Xác nhận thử lại các ngôn ngữ lỗi</h2>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowRetryConfirm(false)}
                disabled={retrying}
              >
                &times;
              </button>
            </div>

            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {isContentOutdated ? (
                <div
                  style={{
                    background: '#fffbeb',
                    border: '1px solid #fde68a',
                    padding: '12px 16px',
                    borderRadius: '6px',
                    color: '#92400e',
                    fontSize: '13px',
                  }}
                >
                  <p style={{ margin: '0 0 8px 0', fontWeight: 'bold' }}>
                    ⚠️ Nội dung gốc đã có phiên bản mới hơn!
                  </p>
                  <p style={{ margin: 0 }}>
                    Tác vụ này thuộc <strong>Bản {job.version}</strong>, trong khi nội dung hiện tại đã là <strong>Bản {content?.version}</strong>.
                    Hệ thống khuyến nghị bạn nên tạo tác vụ mới cho phiên bản mới nhất để nội dung thuyết minh luôn đồng bộ.
                  </p>
                </div>
              ) : (
                <p style={{ margin: 0, fontSize: '14px', color: '#334155' }}>
                  Hệ thống sẽ tạo một tác vụ mới để xử lý lại các ngôn ngữ bị lỗi từ tác vụ này:
                </p>
              )}

              <div
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '6px',
                  padding: '12px 14px',
                }}
              >
                <div style={{ fontSize: '13px', fontWeight: 600, color: '#475569', marginBottom: '8px' }}>
                  Danh sách ngôn ngữ sẽ thử lại:
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {failedTargets.map((t) => {
                    const lDef = getLanguageByCode(t.lang);
                    const vDef = getVoiceByVoiceId(t.voiceId);
                    return (
                      <div
                        key={t.lang}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          fontSize: '13px',
                          color: '#1e293b',
                          background: '#ffffff',
                          padding: '6px 10px',
                          borderRadius: '4px',
                          border: '1px solid #e2e8f0',
                        }}
                      >
                        <strong>{lDef ? (lDef.nameVi === lDef.nativeName ? lDef.nameVi : `${lDef.nameVi} (${lDef.nativeName})`) : t.lang}</strong>
                        <span style={{ color: '#64748b' }} title={t.voiceId}>{vDef ? vDef.label : t.voiceId}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <Button
                variant="secondary"
                onClick={() => setShowRetryConfirm(false)}
                disabled={retrying}
              >
                Hủy
              </Button>

              {isContentOutdated ? (
                <Button
                  variant="primary"
                  onClick={() => {
                    setShowRetryConfirm(false);
                    navigate(`/jobs/new?contentId=${encodeURIComponent(job.contentId)}`);
                  }}
                >
                  Tạo tác vụ cho phiên bản mới ({content?.version}) →
                </Button>
              ) : (
                <Button
                  variant="primary"
                  onClick={handleExecuteRetry}
                  loading={retrying}
                >
                  {retrying ? 'Đang khởi tạo...' : 'Xác nhận thử lại'}
                </Button>
              )}
            </div>
          </div>
        </div>
      )}

      {showPreview && content?.poiId && (
        <AudioPreviewModal
          poiId={content.poiId}
          initialLang={previewLang}
          onClose={() => {
            setShowPreview(false);
            setPreviewLang(undefined);
          }}
        />
      )}
    </div>
  );
};
