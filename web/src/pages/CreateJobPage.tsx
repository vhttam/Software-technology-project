import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { contentApi } from '../api/contentApi';
import { jobsApi } from '../api/jobsApi';
import { ApiError, ContentItem, JobTargetRequest, PublishedNarrationSummary } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { Spinner } from '../components/Spinner';
import { Button } from '../components/Button';
import { PageHeader } from '../components/PageHeader';
import {
  getVoiceOptionsForLanguage,
  getDefaultVoiceForLanguage,
  getAllLanguages,
  fetchLanguageConfig,
  LanguageDef,
} from '../config/languageRegistry';

type LanguageStatusType = 'NONE' | 'CURRENT' | 'OUTDATED' | 'PROCESSING';

interface LanguageStatusInfo {
  type: LanguageStatusType;
  label: string;
  version?: number;
}

export const CreateJobPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const preselectedContentId = searchParams.get('contentId') || '';
  const preselectedRetryOfJobId = searchParams.get('retryOfJobId') || '';

  const navigate = useNavigate();

  const [languages, setLanguages] = useState<LanguageDef[]>(getAllLanguages());
  const [contents, setContents] = useState<ContentItem[]>([]);
  const [selectedContentId, setSelectedContentId] = useState(preselectedContentId);
  const [retryOfJobId] = useState(preselectedRetryOfJobId);

  // Map of lang -> selected voiceId (or '' if not checked)
  const [selectedTargets, setSelectedTargets] = useState<Record<string, string>>({});
  const [langStatuses, setLangStatuses] = useState<Record<string, LanguageStatusInfo>>({});

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [supportsVoiceSelection, setSupportsVoiceSelection] = useState<boolean>(true);

  // Idempotency-Key ref: preserved in background, hidden from UI
  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

  useEffect(() => {
    const init = async () => {
      setLoading(true);
      setError(null);
      try {
        fetchLanguageConfig().then((list) => {
          if (list && list.length) setLanguages(list.filter((l) => l.enabled !== false));
        }).catch(() => {});

        jobsApi.getVoices().then((res) => {
          if (res && res.supportsVoiceSelection !== undefined) {
            setSupportsVoiceSelection(res.supportsVoiceSelection);
          }
        }).catch(() => {});

        const contentsRes = await contentApi.listContents(1, 100);

        const activeList = (contentsRes.items || []).filter((c) => !c.isDeleted);
        setContents(activeList);

        if (preselectedContentId && activeList.some((c) => c.contentId === preselectedContentId)) {
          setSelectedContentId(preselectedContentId);
        } else if (activeList.length > 0 && !selectedContentId) {
          setSelectedContentId(activeList[0].contentId);
        }
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    };

    init();
  }, [preselectedContentId]);

  const selectedContent = contents.find((c) => c.contentId === selectedContentId);

  // Whenever selectedContent changes, fetch its narrations & active jobs to determine language statuses
  useEffect(() => {
    if (!selectedContent) {
      setLangStatuses({});
      return;
    }

    let active = true;
    const fetchStatus = async () => {
      try {
        const [narrationsRes, processingJobsRes] = await Promise.all([
          contentApi.listPoiNarrations(selectedContent.poiId).catch(() => ({ narrations: [] as PublishedNarrationSummary[] })),
          jobsApi.listJobs({ poiId: selectedContent.poiId, status: 'PROCESSING' }).catch(() => ({ items: [] })),
        ]);

        if (!active) return;

        const narrations = narrationsRes.narrations || [];
        const processingTargets = new Set<string>();
        for (const job of processingJobsRes.items || []) {
          if (job.contentId === selectedContent.contentId) {
            for (const t of job.targets || []) {
              if (t.status === 'PENDING' || t.status === 'TRANSLATING' || t.status === 'SYNTHESIZING') {
                processingTargets.add(t.lang);
              }
            }
          }
        }

        const newStatuses: Record<string, LanguageStatusInfo> = {};
        const newSelectedTargets: Record<string, string> = {};

        for (const langDef of languages) {
          if (langDef.enabled === false) continue;
          const l = langDef.code;
          const defaultVoice = getDefaultVoiceForLanguage(l)?.voiceId || '';
          const isAvailable = langDef.available !== false && !langDef.disabledReason;

          if (!isAvailable) {
            newStatuses[l] = { type: 'NONE', label: 'Tạm thời chưa dùng được. Hãy thử lại sau.' };
            newSelectedTargets[l] = '';
          } else if (processingTargets.has(l)) {
            newStatuses[l] = { type: 'PROCESSING', label: 'Đang xử lý' };
            newSelectedTargets[l] = '';
          } else {
            const pub = narrations.find((n) => n.lang === l);
            if (pub) {
              if (pub.version === selectedContent.version) {
                newStatuses[l] = {
                  type: 'CURRENT',
                  label: `Đã có (Bản ${pub.version})`,
                  version: pub.version,
                };
                newSelectedTargets[l] = ''; // Do NOT pre-check
              } else {
                newStatuses[l] = {
                  type: 'OUTDATED',
                  label: `Đã cũ (Bản ${pub.version})`,
                  version: pub.version,
                };
                newSelectedTargets[l] = defaultVoice; // PRE-CHECK outdated
              }
            } else {
              newStatuses[l] = { type: 'NONE', label: 'Chưa có' };
              newSelectedTargets[l] = defaultVoice; // PRE-CHECK new
            }
          }
        }

        setLangStatuses(newStatuses);
        setSelectedTargets(newSelectedTargets);
      } catch (e) {
        console.error('Failed to inspect content language status:', e);
      }
    };

    fetchStatus();
    return () => {
      active = false;
    };
  }, [selectedContent]);

  const handleToggleLang = (lang: string, defaultVoice: string) => {
    setSelectedTargets((prev) => ({
      ...prev,
      [lang]: prev[lang] ? '' : defaultVoice,
    }));
  };

  const handleChangeVoice = (lang: string, voiceId: string) => {
    setSelectedTargets((prev) => ({
      ...prev,
      [lang]: voiceId,
    }));
  };

  const selectedTargetsList = Object.entries(selectedTargets).filter(([, voiceId]) => !!voiceId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!selectedContent) {
      setError(new Error('Vui lòng chọn một nội dung thuyết minh đang hoạt động.'));
      return;
    }

    const targets: JobTargetRequest[] = selectedTargetsList.map(([lang, voiceId]) => ({
      lang,
      voiceId,
    }));

    if (targets.length === 0) {
      setError(new Error('Vui lòng chọn ít nhất một ngôn ngữ đích để tạo thuyết minh.'));
      return;
    }

    setSubmitting(true);
    const keyToUse = idempotencyKeyRef.current;

    try {
      const job = await jobsApi.createJob(
        {
          contentId: selectedContent.contentId,
          expectedVersion: selectedContent.version,
          retryOfJobId: retryOfJobId ? retryOfJobId.trim() : undefined,
          targets,
        },
        keyToUse
      );

      jobsApi.addRecentJobId(job.jobId);
      idempotencyKeyRef.current = crypto.randomUUID();
      navigate(`/jobs/${job.jobId}`);
    } catch (err) {
      setError(err);
      if (err instanceof ApiError && err.status !== 0) {
        idempotencyKeyRef.current = crypto.randomUUID();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="admin-page" style={{ maxWidth: '800px', margin: '0 auto', padding: '0 16px' }}>
      <PageHeader
        eyebrow="TÁC VỤ THUYẾT MINH"
        title="Tạo tác vụ thuyết minh"
        description="Dịch và tạo giọng đọc cho điểm tham quan chỉ với vài bước."
      />

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {loading ? (
        <div style={{ padding: '60px 0', textAlign: 'center' }}>
          <Spinner />
          <p style={{ marginTop: '12px', color: '#64748b' }}>Đang nạp danh mục nội dung và giọng đọc...</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {/* Bước 1 */}
          <div
            className="panel"
            style={{
              background: '#ffffff',
              border: '1px solid var(--border-light)',
              borderRadius: '8px',
              padding: '20px 24px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
              <span
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '28px',
                  height: '28px',
                  borderRadius: '50%',
                  background: 'var(--primary, #0f4c81)',
                  color: '#ffffff',
                  fontWeight: 'bold',
                  fontSize: '14px',
                }}
              >
                1
              </span>
              <h2 style={{ margin: 0, fontSize: '17px', color: 'var(--text-main)' }}>
                Chọn nội dung cần thuyết minh
              </h2>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label htmlFor="content-select" style={{ fontSize: '14px', fontWeight: 500, color: '#334155' }}>
                Nội dung thuyết minh gốc *
              </label>
              <select
                id="content-select"
                required
                value={selectedContentId}
                onChange={(e) => {
                  setSelectedContentId(e.target.value);
                  idempotencyKeyRef.current = crypto.randomUUID();
                }}
                disabled={submitting || contents.length === 0}
                style={{
                  height: '42px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  backgroundColor: '#ffffff',
                  outline: 'none',
                }}
              >
                <option value="">-- Chọn nội dung thuyết minh --</option>
                {contents.map((c) => {
                  const versionSuffix = c.version > 1 ? ` · Bản ${c.version}` : '';
                  return (
                    <option key={c.contentId} value={c.contentId}>
                      {c.title} — {c.poiName || c.poiId}{versionSuffix}
                    </option>
                  );
                })}
              </select>

              {contents.length === 0 && (
                <div style={{ color: '#b91c1c', fontSize: '13px', marginTop: '6px' }}>
                  Chưa có nội dung nào đang hoạt động. Vui lòng vào mục "Nội dung thuyết minh" để tạo trước khi tạo tác vụ.
                </div>
              )}
            </div>
          </div>

          {/* Bước 2 */}
          <div
            className="panel"
            style={{
              background: '#ffffff',
              border: '1px solid var(--border-light)',
              borderRadius: '8px',
              padding: '20px 24px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
              <span
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '28px',
                  height: '28px',
                  borderRadius: '50%',
                  background: 'var(--primary, #0f4c81)',
                  color: '#ffffff',
                  fontWeight: 'bold',
                  fontSize: '14px',
                }}
              >
                2
              </span>
              <h2 style={{ margin: 0, fontSize: '17px', color: 'var(--text-main)' }}>
                Chọn ngôn ngữ đích và giọng đọc
              </h2>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {languages.filter((l) => l.enabled !== false).map((langDef) => {
                const lang = langDef.code;
                const isAvailable = langDef.available !== false && !langDef.disabledReason;
                const isChecked = !!selectedTargets[lang];
                const availableVoices = getVoiceOptionsForLanguage(lang);
                const defaultVoice = getDefaultVoiceForLanguage(lang)?.voiceId || '';
                const selectedVoice = selectedTargets[lang] || defaultVoice;
                const isSource = selectedContent?.sourceLang === lang;
                const status = langStatuses[lang];

                let statusBadge = null;
                if (!isAvailable) {
                  statusBadge = (
                    <span
                      style={{
                        fontSize: '12px',
                        fontWeight: 500,
                        padding: '2px 8px',
                        borderRadius: '4px',
                        background: '#fef2f2',
                        color: '#991b1b',
                        border: '1px solid #fecaca',
                      }}
                    >
                      Tạm thời chưa dùng được. Hãy thử lại sau.
                    </span>
                  );
                } else if (status) {
                  switch (status.type) {
                    case 'CURRENT':
                      statusBadge = (
                        <span
                          style={{
                            fontSize: '12px',
                            fontWeight: 600,
                            padding: '2px 8px',
                            borderRadius: '4px',
                            background: '#dcfce7',
                            color: '#15803d',
                            border: '1px solid #bbf7d0',
                          }}
                        >
                          ✓ {status.label}
                        </span>
                      );
                      break;
                    case 'OUTDATED':
                      statusBadge = (
                        <span
                          style={{
                            fontSize: '12px',
                            fontWeight: 600,
                            padding: '2px 8px',
                            borderRadius: '4px',
                            background: '#fef3c7',
                            color: '#b45309',
                            border: '1px solid #fde68a',
                          }}
                        >
                          ⚠️ {status.label}
                        </span>
                      );
                      break;
                    case 'PROCESSING':
                      statusBadge = (
                        <span
                          style={{
                            fontSize: '12px',
                            fontWeight: 600,
                            padding: '2px 8px',
                            borderRadius: '4px',
                            background: '#e0f2fe',
                            color: '#0369a1',
                            border: '1px solid #bae6fd',
                          }}
                        >
                          ⏳ {status.label}
                        </span>
                      );
                      break;
                    case 'NONE':
                    default:
                      statusBadge = (
                        <span
                          style={{
                            fontSize: '12px',
                            fontWeight: 500,
                            padding: '2px 8px',
                            borderRadius: '4px',
                            background: '#f1f5f9',
                            color: '#64748b',
                            border: '1px solid #e2e8f0',
                          }}
                        >
                          {status.label}
                        </span>
                      );
                      break;
                  }
                }

                return (
                  <div
                    key={lang}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      padding: '14px 16px',
                      border: isChecked ? '1px solid var(--primary, #0f4c81)' : '1px solid #e2e8f0',
                      borderRadius: '6px',
                      backgroundColor: isChecked ? '#f8fafc' : '#ffffff',
                      transition: 'all 0.15s ease-in-out',
                      gap: '10px',
                      opacity: isAvailable ? 1 : 0.6,
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        flexWrap: 'wrap',
                        gap: '10px',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <input
                          type="checkbox"
                          id={`target-${lang}`}
                          checked={isChecked}
                          onChange={() => isAvailable && handleToggleLang(lang, defaultVoice)}
                          disabled={submitting || status?.type === 'PROCESSING' || !isAvailable}
                          style={{ width: '18px', height: '18px', cursor: isAvailable ? 'pointer' : 'not-allowed' }}
                        />
                        <label
                          htmlFor={`target-${lang}`}
                          style={{
                            margin: 0,
                            cursor: 'pointer',
                            fontSize: '15px',
                            fontWeight: 600,
                            color: 'var(--text-main)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            flexWrap: 'wrap',
                          }}
                        >
                          <span>{langDef.nameVi} ({langDef.nativeName})</span>
                          {isSource && (
                            <span
                              style={{
                                fontSize: '11px',
                                fontWeight: 500,
                                color: '#0369a1',
                                background: '#e0f2fe',
                                padding: '1px 6px',
                                borderRadius: '4px',
                              }}
                            >
                              Ngôn ngữ gốc (không cần dịch)
                            </span>
                          )}
                        </label>
                      </div>

                      <div>{statusBadge}</div>
                    </div>

                    {isChecked && status?.type === 'CURRENT' && (
                      <div style={{ fontSize: '12px', color: '#b45309', background: '#fffbeb', padding: '6px 10px', borderRadius: '4px' }}>
                        ℹ Ngôn ngữ này đã có bản thuyết minh cho phiên bản hiện tại. Bạn có thể tiếp tục nếu muốn tạo lại giọng đọc khác.
                      </div>
                    )}

                    {isChecked && (
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '12px',
                          marginTop: '4px',
                          paddingLeft: '28px',
                          flexWrap: 'wrap',
                        }}
                      >
                        <span style={{ fontSize: '13px', color: '#475569', minWidth: '80px', fontWeight: 500 }}>
                          Giọng đọc:
                        </span>
                        {!supportsVoiceSelection ? (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                            <span
                              style={{
                                fontSize: '13px',
                                color: '#1e293b',
                                background: '#f1f5f9',
                                padding: '4px 10px',
                                borderRadius: '4px',
                                border: '1px solid #e2e8f0',
                                fontWeight: 500,
                              }}
                            >
                              Giọng mặc định
                            </span>
                            <span style={{ fontSize: '12px', color: '#64748b' }}>
                              (Hệ thống tự động sử dụng giọng đọc tự nhiên chuẩn cho ngôn ngữ này)
                            </span>
                          </div>
                        ) : (
                          <>
                            <select
                              disabled={submitting}
                              value={selectedVoice}
                              onChange={(e) => handleChangeVoice(lang, e.target.value)}
                              style={{
                                height: '36px',
                                padding: '0 10px',
                                borderRadius: '4px',
                                border: '1px solid #cbd5e0',
                                backgroundColor: '#ffffff',
                                fontSize: '13px',
                                outline: 'none',
                                maxWidth: '320px',
                                flex: 1,
                              }}
                            >
                              {availableVoices.map((v) => (
                                <option key={v.voiceId} value={v.voiceId}>
                                  {v.label}
                                </option>
                              ))}
                            </select>
                            <span style={{ fontSize: '11px', color: '#94a3b8' }}>
                              Mã giọng: {selectedVoice}
                            </span>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Bước 3 */}
          <div
            className="panel"
            style={{
              background: '#ffffff',
              border: '1px solid var(--border-light)',
              borderRadius: '8px',
              padding: '20px 24px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
              <span
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '28px',
                  height: '28px',
                  borderRadius: '50%',
                  background: 'var(--primary, #0f4c81)',
                  color: '#ffffff',
                  fontWeight: 'bold',
                  fontSize: '14px',
                }}
              >
                3
              </span>
              <h2 style={{ margin: 0, fontSize: '17px', color: 'var(--text-main)' }}>
                Xác nhận và bắt đầu
              </h2>
            </div>

            {/* Summary card */}
            <div
              style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '6px',
                padding: '14px 18px',
                marginBottom: '20px',
                fontSize: '14px',
                lineHeight: 1.6,
              }}
            >
              <div>
                <strong>Nội dung:</strong> {selectedContent ? selectedContent.title : '(Chưa chọn)'}
              </div>
              <div>
                <strong>Điểm tham quan:</strong> {selectedContent?.poiName || selectedContent?.poiId || '(Chưa chọn)'}
              </div>
              <div>
                <strong>Phiên bản:</strong> Phiên bản {selectedContent?.version || 1}
              </div>
              <div style={{ marginTop: '4px' }}>
                <strong>Ngôn ngữ đã chọn:</strong>{' '}
                {selectedTargetsList.length > 0 ? (
                  <span style={{ color: 'var(--primary, #0f4c81)', fontWeight: 600 }}>
                    {selectedTargetsList.length} ngôn ngữ ({selectedTargetsList.map(([l]) => l.toUpperCase()).join(', ')})
                  </span>
                ) : (
                  <span style={{ color: '#b91c1c' }}>Chưa chọn ngôn ngữ nào</span>
                )}
              </div>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              loading={submitting}
              disabled={submitting || contents.length === 0 || selectedTargetsList.length === 0}
              style={{ width: '100%', height: '44px', fontSize: '15px' }}
            >
              {submitting ? 'Đang khởi tạo tác vụ...' : '▶ Bắt đầu tạo thuyết minh'}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
};
