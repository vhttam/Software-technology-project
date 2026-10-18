import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { jobsApi } from '../api/jobsApi';
import { contentApi } from '../api/contentApi';
import { ContentItem, JobListItem, PoiItem } from '../api/types';
import { SearchInput } from '../components/SearchInput';
import { Button } from '../components/Button';
import { StatusBadge } from '../components/StatusBadge';
import { Spinner } from '../components/Spinner';
import { ErrorBanner } from '../components/ErrorBanner';
import { PageHeader } from '../components/PageHeader';
import { getLanguageByCode, getVoiceByVoiceId } from '../config/languageRegistry';

function formatRelativeTime(dateStr?: string): string {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const now = new Date();
  const diffSec = Math.floor((now.getTime() - date.getTime()) / 1000);
  if (diffSec < 60) return 'Vừa xong';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin} phút trước`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour} giờ trước`;
  const diffDays = Math.floor(diffHour / 24);
  if (diffDays === 1) {
    return `Hôm qua lúc ${date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`;
  }
  if (diffDays < 7) return `${diffDays} ngày trước`;
  return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export const JobHistoryPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [jobs, setJobs] = useState<JobListItem[]>([]);
  const [pois, setPois] = useState<PoiItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [retryingJob, setRetryingJob] = useState<JobListItem | null>(null);
  const [retryingContent, setRetryingContent] = useState<ContentItem | null>(null);
  const [showRetryConfirm, setShowRetryConfirm] = useState(false);
  const [retryError, setRetryError] = useState<unknown>(null);
  const [retrying, setRetrying] = useState(false);

  // Filters
  const [query, setQuery] = useState('');
  const [selectedPoiId, setSelectedPoiId] = useState(searchParams.get('poiId') || '');
  const [selectedStatus, setSelectedStatus] = useState('');
  const [selectedDateRange, setSelectedDateRange] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalItems, setTotalItems] = useState(0);

  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Load POIs for dropdown
  useEffect(() => {
    contentApi
      .listPois(1, 100)
      .then((res) => setPois(res.items || []))
      .catch(() => {});
  }, []);

  // Fetch jobs
  const fetchJobs = useCallback(
    async (isBackground = false) => {
      if (!isBackground) setLoading(true);
      setError(null);
      try {
        const res = await jobsApi.listJobs({
          page: currentPage,
          pageSize: 15,
          poiId: selectedPoiId || undefined,
          status: selectedStatus || undefined,
          dateRange: selectedDateRange || undefined,
          query: query || undefined,
        });
        setJobs(res.items || []);
        setTotalPages(res.totalPages || 1);
        setTotalItems(res.totalItems || 0);
      } catch (err) {
        if (!isBackground) setError(err);
      } finally {
        if (!isBackground) setLoading(false);
      }
    },
    [currentPage, selectedPoiId, selectedStatus, selectedDateRange, query]
  );

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  // Polling when any job is PROCESSING or PENDING
  const pollingRef = useRef<NodeJS.Timeout | null>(null);
  useEffect(() => {
    const hasActiveJob = jobs.some(
      (j) => j.status === 'PENDING' || j.status === 'PROCESSING'
    );
    if (hasActiveJob) {
      pollingRef.current = setInterval(() => {
        fetchJobs(true);
      }, 10000);
    } else if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [jobs, fetchJobs]);

  const handleCopyId = (id: string) => {
    navigator.clipboard.writeText(id).then(() => {
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    });
  };


  const handleClearFilters = () => {
    setQuery('');
    setSelectedPoiId('');
    setSelectedStatus('');
    setSelectedDateRange('');
    setCurrentPage(1);
    setSearchParams({});
  };

  const handleInitiateRetry = async (job: JobListItem) => {
    setRetryingJob(job);
    setRetryError(null);
    setShowRetryConfirm(true);
    try {
      const c = await contentApi.getContent(job.contentId);
      setRetryingContent(c);
    } catch {
      setRetryingContent(null);
    }
  };

  const handleExecuteRetry = async () => {
    if (!retryingJob) return;
    const failedTargets = (retryingJob.targets || []).filter((t) => t.status === 'FAILED');
    if (!failedTargets.length) return;

    setRetrying(true);
    setRetryError(null);
    try {
      const retryPayload = {
        contentId: retryingJob.contentId,
        retryOfJobId: retryingJob.jobId,
        expectedVersion: retryingJob.version,
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
      setRetryError(err);
    } finally {
      setRetrying(false);
    }
  };

  const hasFilterActive = Boolean(
    query || selectedPoiId || selectedStatus || selectedDateRange
  );

  const getTargetPillIcon = (status: string) => {
    if (status === 'PUBLISHED') return '✓';
    if (status === 'FAILED') return '⚠';
    if (status === 'CANCELLED') return '✕';
    return '↻';
  };

  const getTargetPillColor = (status: string) => {
    if (status === 'PUBLISHED') return { bg: '#def7ec', text: '#03543f', border: '#bcf0da' };
    if (status === 'FAILED') return { bg: '#fde8e8', text: '#9b1c1c', border: '#fbd5d5' };
    if (status === 'CANCELLED') return { bg: '#f3f4f6', text: '#4b5563', border: '#e5e7eb' };
    return { bg: '#fef08a', text: '#713f12', border: '#fde047' };
  };

  return (
    <div className="admin-page" style={{ padding: '0 4px' }}>
      <PageHeader
        eyebrow="TIẾN TRÌNH THUYẾT MINH"
        title="Lịch sử tác vụ"
        description="Theo dõi trạng thái dịch thuật và tổng hợp giọng đọc đa ngôn ngữ của các điểm tham quan."
        action={
          <Button
            variant="primary"
            onClick={() => navigate('/jobs/new')}
            icon={<span>+</span>}
          >
            Tạo tác vụ mới
          </Button>
        }
      />

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {/* Toolbar */}
      <div
        className="table-toolbar"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          flexWrap: 'wrap',
          background: '#ffffff',
          padding: '14px 18px',
          border: '1px solid var(--border-light)',
          borderRadius: '6px',
          marginBottom: '20px',
        }}
      >
        <SearchInput
          value={query}
          onChange={(val) => {
            setQuery(val);
            setCurrentPage(1);
          }}
          placeholder="Tìm theo tên điểm hoặc tiêu đề..."
          debounceMs={300}
          style={{ maxWidth: '320px', flex: '1 1 240px' }}
        />

        <select
          value={selectedPoiId}
          onChange={(e) => {
            setSelectedPoiId(e.target.value);
            setCurrentPage(1);
          }}
          style={{
            height: '40px',
            padding: '0 12px',
            borderRadius: '4px',
            border: '1px solid #cbd5e0',
            backgroundColor: '#ffffff',
            fontSize: '14px',
            outline: 'none',
            flex: '1 1 200px',
            maxWidth: '260px',
          }}
        >
          <option value="">Tất cả điểm tham quan</option>
          {pois.map((poi) => (
            <option key={poi.poiId} value={poi.poiId}>
              {poi.name}
            </option>
          ))}
        </select>

        <select
          value={selectedStatus}
          onChange={(e) => {
            setSelectedStatus(e.target.value);
            setCurrentPage(1);
          }}
          style={{
            height: '40px',
            padding: '0 12px',
            borderRadius: '4px',
            border: '1px solid #cbd5e0',
            backgroundColor: '#ffffff',
            fontSize: '14px',
            outline: 'none',
          }}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="PROCESSING">Đang xử lý</option>
          <option value="COMPLETED">Hoàn thành</option>
          <option value="PARTIALLY_COMPLETED">Hoàn thành một phần</option>
          <option value="FAILED">Thất bại</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>

        <select
          value={selectedDateRange}
          onChange={(e) => {
            setSelectedDateRange(e.target.value);
            setCurrentPage(1);
          }}
          style={{
            height: '40px',
            padding: '0 12px',
            borderRadius: '4px',
            border: '1px solid #cbd5e0',
            backgroundColor: '#ffffff',
            fontSize: '14px',
            outline: 'none',
          }}
        >
          <option value="">Tất cả thời gian</option>
          <option value="today">Hôm nay</option>
          <option value="7days">7 ngày qua</option>
        </select>

        <div style={{ display: 'flex', gap: '8px', marginLeft: 'auto' }}>
          {hasFilterActive && (
            <Button variant="ghost" size="md" onClick={handleClearFilters}>
              Xóa bộ lọc
            </Button>
          )}

          <Button
            variant="secondary"
            size="md"
            onClick={() => fetchJobs()}
            icon={
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.3" />
              </svg>
            }
          >
            Làm mới
          </Button>
        </div>
      </div>

      {/* Main List */}
      <div
        className="jobs-table-container"
        style={{
          background: '#ffffff',
          border: '1px solid var(--border-light)',
          borderRadius: '6px',
          overflow: 'hidden',
        }}
      >
        {loading ? (
          <div style={{ padding: '60px', textAlign: 'center' }}>
            <Spinner />
            <p style={{ marginTop: '12px', color: '#64748b' }}>Đang nạp danh sách tác vụ...</p>
          </div>
        ) : jobs.length === 0 ? (
          <div style={{ padding: '60px 20px', textAlign: 'center' }}>
            <div style={{ fontSize: '32px', marginBottom: '12px' }}>📋</div>
            <h3 style={{ margin: '0 0 6px', fontSize: '18px', color: 'var(--text-main)' }}>
              {hasFilterActive ? 'Không tìm thấy tác vụ phù hợp với bộ lọc' : 'Chưa có tác vụ nào trong hệ thống'}
            </h3>
            <p style={{ margin: '0 0 16px', color: 'var(--text-muted)', fontSize: '14px' }}>
              {hasFilterActive
                ? 'Hãy thử thay đổi từ khóa tìm kiếm hoặc bỏ các tiêu chí lọc.'
                : 'Bạn có thể khởi tạo tiến trình dịch và sinh âm thanh thuyết minh cho bất kỳ nội dung nào.'}
            </p>
            {hasFilterActive ? (
              <Button variant="secondary" onClick={handleClearFilters}>
                Xóa bộ lọc
              </Button>
            ) : (
              <Button variant="primary" onClick={() => navigate('/jobs/new')}>
                + Tạo tác vụ mới
              </Button>
            )}
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid var(--border-light)' }}>
                  <th style={{ padding: '12px 16px', fontSize: '13px', color: '#475569' }}>Nội dung & Điểm tham quan</th>
                  <th style={{ padding: '12px 16px', fontSize: '13px', color: '#475569' }}>Ngôn ngữ đích</th>
                  <th style={{ padding: '12px 16px', fontSize: '13px', color: '#475569' }}>Thời gian tạo</th>
                  <th style={{ padding: '12px 16px', fontSize: '13px', color: '#475569' }}>Trạng thái</th>
                  <th style={{ padding: '12px 16px', fontSize: '13px', color: '#475569', textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr
                    key={job.jobId}
                    style={{
                      borderBottom: '1px solid var(--border-light)',
                      transition: 'background-color 0.15s',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#fafbfc')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <td style={{ padding: '14px 16px', verticalAlign: 'top' }}>
                      <div style={{ fontWeight: 'bold', fontSize: '15px', color: 'var(--text-main)', marginBottom: '4px' }}>
                        {job.contentTitle || 'Nội dung không tên'}
                      </div>
                      <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '6px' }}>
                        🏛 {job.poiName || 'Điểm tham quan'} (Phiên bản {job.version})
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px', color: '#94a3b8' }}>
                        <span>Mã: {job.jobId.slice(0, 8)}...</span>
                        <button
                          type="button"
                          onClick={() => handleCopyId(job.jobId)}
                          style={{
                            background: 'none',
                            border: 'none',
                            padding: '0 4px',
                            cursor: 'pointer',
                            color: '#2563eb',
                            fontSize: '11px',
                          }}
                          title="Sao chép toàn bộ mã tác vụ"
                        >
                          {copiedId === job.jobId ? '✓ Đã chép' : 'Sao chép'}
                        </button>
                      </div>
                    </td>

                    <td style={{ padding: '14px 16px', verticalAlign: 'top' }}>
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        {job.targets && job.targets.length > 0 ? (
                          job.targets.map((target) => {
                            const colors = getTargetPillColor(target.status);
                            const icon = getTargetPillIcon(target.status);
                            const langDef = getLanguageByCode(target.lang);
                            const isPaused = !langDef || langDef.enabled === false;
                            return (
                              <span
                                key={target.targetId || target.lang}
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: '4px',
                                  padding: '2px 8px',
                                  borderRadius: '12px',
                                  fontSize: '11px',
                                  fontWeight: 600,
                                  backgroundColor: colors.bg,
                                  color: colors.text,
                                  border: `1px solid ${colors.border}`,
                                }}
                                title={`${target.lang.toUpperCase()}: ${target.status}${isPaused ? ' (Ngôn ngữ đã tạm dừng)' : ''}`}
                              >
                                <span>{icon}</span>
                                <span>{target.lang.toUpperCase()}</span>
                                {isPaused && (
                                  <span style={{ fontSize: '9px', opacity: 0.85, background: 'rgba(0,0,0,0.1)', padding: '1px 3px', borderRadius: '3px' }}>
                                    Đã dừng
                                  </span>
                                )}
                              </span>
                            );
                          })
                        ) : (
                          <span style={{ color: '#94a3b8', fontSize: '12px' }}>Chưa có</span>
                        )}
                      </div>
                    </td>

                    <td style={{ padding: '14px 16px', verticalAlign: 'top' }}>
                      <span
                        title={job.createdAt ? new Date(job.createdAt).toLocaleString('vi-VN') : ''}
                        style={{ fontSize: '13px', color: 'var(--text-main)', cursor: 'default' }}
                      >
                        {formatRelativeTime(job.createdAt)}
                      </span>
                    </td>

                    <td style={{ padding: '14px 16px', verticalAlign: 'top' }}>
                      <StatusBadge status={job.status} />
                    </td>

                    <td style={{ padding: '14px 16px', verticalAlign: 'top', textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', alignItems: 'center' }}>
                        {(() => {
                          const failedTargets = (job.targets || []).filter((t) => t.status === 'FAILED');
                          const canRetry = (job.status === 'FAILED' || job.status === 'PARTIALLY_COMPLETED') && failedTargets.length > 0;
                          if (!canRetry) return null;
                          return (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleInitiateRetry(job)}
                              style={{ color: '#b91c1c', borderColor: '#fca5a5' }}
                              title="Thử lại các ngôn ngữ bị lỗi trong tác vụ này"
                            >
                              ↻ Thử lại lỗi
                            </Button>
                          );
                        })()}
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => navigate(`/jobs/${job.jobId}`)}
                        >
                          Xem chi tiết →
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px 18px',
              borderTop: '1px solid var(--border-light)',
              background: '#f8fafc',
              fontSize: '13px',
              color: 'var(--text-muted)',
            }}
          >
            <div>
              Hiển thị trang <strong>{currentPage}</strong> / {totalPages} (Tổng số {totalItems} tác vụ)
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <Button
                variant="secondary"
                size="sm"
                disabled={currentPage <= 1}
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              >
                ← Trang trước
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={currentPage >= totalPages}
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              >
                Trang sau →
              </Button>
            </div>
          </div>
        )}
      </div>

      {showRetryConfirm && retryingJob && (
        <div
          className="modal-overlay"
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '16px',
          }}
          onClick={() => {
            if (!retrying) setShowRetryConfirm(false);
          }}
        >
          <div
            className="modal-card"
            style={{
              background: '#ffffff',
              borderRadius: '8px',
              maxWidth: '520px',
              width: '100%',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              className="modal-header"
              style={{
                padding: '16px 20px',
                borderBottom: '1px solid #e2e8f0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 600, color: '#1e293b' }}>
                Thử lại các ngôn ngữ bị lỗi
              </h3>
              <button
                type="button"
                onClick={() => setShowRetryConfirm(false)}
                disabled={retrying}
                style={{
                  background: 'transparent',
                  border: 'none',
                  fontSize: '20px',
                  cursor: 'pointer',
                  color: '#94a3b8',
                }}
              >
                ✕
              </button>
            </div>

            <div className="modal-body" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <ErrorBanner error={retryError} onDismiss={() => setRetryError(null)} />

              {retryingContent && retryingContent.version > retryingJob.version ? (
                <div
                  style={{
                    background: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: '6px',
                    padding: '12px 14px',
                    color: '#92400e',
                    fontSize: '13px',
                    lineHeight: 1.5,
                  }}
                >
                  <strong>⚠️ Nội dung đã được cập nhật phiên bản mới:</strong>
                  <div style={{ marginTop: '4px' }}>
                    Nội dung hiện tại là <strong>phiên bản {retryingContent.version}</strong>, trong khi tác vụ gốc dựa trên <strong>phiên bản {retryingJob.version}</strong>. Nếu thử lại, bản dịch sẽ dựa trên nội dung cũ.
                  </div>
                  <div style={{ marginTop: '6px', fontWeight: 600 }}>
                    Khuyến nghị: Bạn nên tạo tác vụ mới từ nội dung hiện tại để có bản dịch cập nhật nhất.
                  </div>
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
                  {(retryingJob.targets || [])
                    .filter((t) => t.status === 'FAILED')
                    .map((t) => {
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
                          <strong>
                            {lDef ? `${lDef.nameVi} (${lDef.nativeName})` : t.lang}
                            {(!lDef || lDef.enabled === false) && (
                              <span style={{ fontSize: '11px', color: '#991b1b', background: '#fee2e2', padding: '1px 6px', borderRadius: '4px', marginLeft: '6px' }}>
                                Ngôn ngữ đã tạm dừng
                              </span>
                            )}
                          </strong>
                          <span style={{ color: '#64748b' }}>{vDef ? vDef.label : t.voiceId}</span>
                        </div>
                      );
                    })}
                </div>
              </div>
            </div>

            <div className="modal-footer" style={{ padding: '14px 20px', borderTop: '1px solid #e2e8f0', display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <Button
                variant="secondary"
                onClick={() => setShowRetryConfirm(false)}
                disabled={retrying}
              >
                Hủy
              </Button>

              {retryingContent && retryingContent.version > retryingJob.version ? (
                <div style={{ display: 'flex', gap: '8px' }}>
                  <Button
                    variant="secondary"
                    onClick={() => handleExecuteRetry()}
                    loading={retrying}
                    style={{ fontSize: '12px' }}
                  >
                    Vẫn thử lại tác vụ cũ
                  </Button>
                  <Button
                    variant="primary"
                    onClick={() => {
                      setShowRetryConfirm(false);
                      navigate(`/jobs/new?contentId=${encodeURIComponent(retryingJob.contentId)}`);
                    }}
                  >
                    Tạo tác vụ mới (v{retryingContent.version}) →
                  </Button>
                </div>
              ) : (
                <Button
                  variant="primary"
                  onClick={() => handleExecuteRetry()}
                  loading={retrying}
                >
                  {retrying ? 'Đang khởi tạo...' : 'Xác nhận thử lại'}
                </Button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
