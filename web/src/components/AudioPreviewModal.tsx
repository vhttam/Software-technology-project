import React, { useEffect, useState } from 'react';
import { contentApi } from '../api/contentApi';
import { jobsApi } from '../api/jobsApi';
import { ApiError, NarrationDetailResponseData, PublishedNarrationSummary } from '../api/types';
import { getLanguageByCode, SUPPORTED_LANGUAGES } from '../config/languageRegistry';

interface AudioPreviewModalProps {
  poiId: string;
  initialLang?: string;
  onClose: () => void;
}

const LANG_ORDER = SUPPORTED_LANGUAGES.map((l) => l.code);

function getLanguageLabel(lang: string): string {
  const def = getLanguageByCode(lang);
  if (!def) return lang.toUpperCase();
  if (def.nameVi === def.nativeName) return def.nameVi;
  return `${def.nameVi} (${def.nativeName})`;
}

export const AudioPreviewModal: React.FC<AudioPreviewModalProps> = ({
  poiId,
  initialLang,
  onClose,
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [poiName, setPoiName] = useState<string>('');
  const [contentTitle, setContentTitle] = useState<string>('');
  const [narrations, setNarrations] = useState<PublishedNarrationSummary[]>([]);
  const [selectedLang, setSelectedLang] = useState<string | null>(initialLang || null);

  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<NarrationDetailResponseData | null>(null);
  const [audioError, setAudioError] = useState<string | null>(null);
  const [audioSrc, setAudioSrc] = useState<string | null>(null);
  const [isMockMode, setIsMockMode] = useState<boolean>(false);
  const audioRef = React.useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    jobsApi.getVoices().then((res) => {
      if (res && res.isMock) {
        setIsMockMode(true);
      }
    }).catch(() => {});
  }, []);

  const reloadDetail = (autoPlay = false) => {
    if (!selectedLang) return;
    setDetailLoading(true);
    setAudioError(null);
    contentApi
      .getNarrationDetail(poiId, selectedLang)
      .then((data) => {
        setDetail(data);
        if (autoPlay) {
          setTimeout(() => {
            if (audioRef.current) {
              audioRef.current.play().catch(() => {});
            }
          }, 300);
        }
      })
      .catch((err: unknown) => {
        if (err instanceof ApiError) {
          setAudioError(err.message || 'Không thể tải chi tiết bản thuyết minh');
        } else {
          setAudioError('Lỗi kết nối khi tải liên kết âm thanh');
        }
      })
      .finally(() => {
        setDetailLoading(false);
      });
  };

  const handleAudioPlayAttempt = () => {
    if (detail?.audioUrlExpiresAt && Date.now() >= new Date(detail.audioUrlExpiresAt).getTime()) {
      if (audioRef.current) {
        audioRef.current.pause();
      }
      setAudioError('Chữ ký bảo mật đã hết hạn. Đang tự động lấy liên kết mới...');
      reloadDetail(true);
    }
  };

  const handleClose = () => {
    if (audioRef.current) {
      audioRef.current.pause();
    }
    onClose();
  };

  // Dừng phát audio khi đổi tab ngôn ngữ
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
    }
  }, [selectedLang]);

  // 1. Tải danh sách ngôn ngữ thuyết minh đã xuất bản của POI
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    contentApi
      .listPoiNarrations(poiId)
      .then((data) => {
        if (!active) return;
        setPoiName(data.poiName);
        setContentTitle(data.contentTitle || '');
        const sorted = [...(data.narrations || [])].sort((a, b) => {
          const ia = LANG_ORDER.indexOf(a.lang);
          const ib = LANG_ORDER.indexOf(b.lang);
          return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
        });
        setNarrations(sorted);

        if (sorted.length > 0) {
          const matchInitial = initialLang
            ? sorted.find((n) => n.lang === initialLang)
            : null;
          const target = matchInitial ? matchInitial.lang : sorted[0].lang;
          setSelectedLang(target);
        } else {
          setSelectedLang(null);
        }
      })
      .catch((err: unknown) => {
        if (!active) return;
        if (err instanceof ApiError) {
          setError(err.message || 'Không thể tải danh sách thuyết minh của điểm tham quan');
        } else {
          setError('Lỗi kết nối khi tải danh sách thuyết minh');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [poiId, initialLang]);

  // 2. Khi chọn một ngôn ngữ, tải chi tiết thuyết minh (phụ đề + link âm thanh MP3)
  useEffect(() => {
    if (!selectedLang) {
      setDetail(null);
      return;
    }

    let active = true;
    setDetailLoading(true);
    setAudioError(null);

    contentApi
      .getNarrationDetail(poiId, selectedLang)
      .then((data) => {
        if (!active) return;
        setDetail(data);
      })
      .catch((err: unknown) => {
        if (!active) return;
        if (err instanceof ApiError) {
          setAudioError(err.message || 'Không thể tải chi tiết bản thuyết minh');
        } else {
          setAudioError('Lỗi kết nối khi tải liên kết âm thanh');
        }
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });

    return () => {
      active = false;
    };
  }, [poiId, selectedLang]);

  // 3. Tự động tương thích định dạng âm thanh (WAV / MP3) để trình duyệt luôn phát được
  useEffect(() => {
    if (!detail?.audioUrl) {
      setAudioSrc(null);
      return;
    }

    let active = true;
    let createdUrl: string | null = null;

    fetch(detail.audioUrl)
      .then(async (res) => {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const buffer = await res.arrayBuffer();
        if (!active) return;
        const isWav =
          buffer.byteLength >= 4 &&
          String.fromCharCode(...new Uint8Array(buffer.slice(0, 4))) === 'RIFF';
        const mime = isWav ? 'audio/wav' : 'audio/mpeg';
        const blob = new Blob([buffer], { type: mime });
        createdUrl = URL.createObjectURL(blob);
        setAudioSrc(createdUrl);
      })
      .catch(() => {
        if (active) setAudioSrc(detail.audioUrl);
      });

    return () => {
      active = false;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [detail?.audioUrl]);

  const activeVersion = detail?.version || (narrations.length > 0 ? narrations[0].version : 1);

  return (
    <div className="modal-backdrop" onClick={handleClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Nghe thử thuyết minh {poiName ? `— ${poiName}` : ''}</h3>
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleClose}>
            ✕
          </button>
        </div>

        <div className="modal-body">
          {contentTitle && (
            <div style={{ fontSize: '14px', color: 'var(--text-muted)', marginBottom: '14px' }}>
              Nội dung: <strong>{contentTitle}</strong> · <strong>Bản {activeVersion}</strong>
            </div>
          )}

          {loading && (
            <div className="loading-indicator">Đang kiểm tra các bản thuyết minh...</div>
          )}

          {error && <div className="alert alert-danger">{error}</div>}

          {!loading && !error && narrations.length === 0 && (
            <div className="card" style={{ padding: '24px', textAlign: 'center' }}>
              <p style={{ margin: '0 0 8px 0', fontSize: '16px', fontWeight: 'bold' }}>
                Chưa có bản thuyết minh nào được xuất bản (PUBLISHED)
              </p>
              <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '14px' }}>
                Điểm tham quan này hiện chưa có ngôn ngữ nào hoàn tất quy trình tổng hợp giọng nói.
                Vui lòng tạo tác vụ mới tại mục <strong>Tạo tác vụ mới</strong> và đợi tác vụ
                chuyển sang trạng thái hoàn tất.
              </p>
            </div>
          )}

          {!loading && narrations.length > 0 && (
            <>
              <div className="form-group">
                <label className="form-label">Chọn ngôn ngữ thuyết minh:</label>
                <div className="lang-tabs" style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  {narrations.map((n) => {
                    const isOutdated = !!(n.isOutdated || (n.currentVersion && n.version < n.currentVersion));
                    const langDef = getLanguageByCode(n.lang);
                    const isPaused = !langDef || langDef.enabled === false;
                    return (
                      <button
                        key={n.lang}
                        type="button"
                        className={`lang-tab-btn ${selectedLang === n.lang ? 'active' : ''}`}
                        onClick={() => setSelectedLang(n.lang)}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                        }}
                      >
                        {getLanguageLabel(n.lang)}
                        {isPaused && (
                          <span
                            style={{
                              background: '#fee2e2',
                              color: '#991b1b',
                              padding: '1px 5px',
                              borderRadius: '4px',
                              fontSize: '10px',
                              fontWeight: 700,
                              marginLeft: '4px',
                            }}
                          >
                            Ngôn ngữ đã tạm dừng
                          </span>
                        )}
                        {isOutdated && (
                          <span
                            style={{
                              background: '#fef3c7',
                              color: '#92400e',
                              padding: '1px 5px',
                              borderRadius: '4px',
                              fontSize: '10px',
                              fontWeight: 700,
                              marginLeft: '4px',
                            }}
                          >
                            Bản cũ
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>

              {detailLoading && (
                <div className="loading-indicator">Đang tải phụ đề và tệp âm thanh MP3...</div>
              )}

              {audioError && <div className="alert alert-danger">{audioError}</div>}

              {!detailLoading && detail && (
                <>
                  <div className="form-group">
                    <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <span>Phụ đề ({getLanguageLabel(detail.lang)}):</span>
                      {(!getLanguageByCode(detail.lang) || getLanguageByCode(detail.lang)?.enabled === false) && (
                        <span
                          style={{
                            background: '#fee2e2',
                            color: '#991b1b',
                            border: '1px solid #fecaca',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            fontSize: '11px',
                            fontWeight: 700,
                          }}
                        >
                          Ngôn ngữ đã tạm dừng
                        </span>
                      )}
                      {(detail.isOutdated || (detail.currentVersion && detail.version < detail.currentVersion)) && (
                        <span
                          style={{
                            background: '#fef3c7',
                            color: '#92400e',
                            border: '1px solid #fde68a',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            fontSize: '11px',
                            fontWeight: 700,
                          }}
                        >
                          ⚠️ Bản cũ (Nội dung gốc hiện là Bản {detail.currentVersion || detail.version})
                        </span>
                      )}
                    </label>
                    {(() => {
                      const langDef = getLanguageByCode(detail.lang);
                      return (
                        <div
                          className="audio-subtitle-box"
                          lang={detail.lang}
                          dir={langDef?.direction || 'ltr'}
                          style={{
                            fontFamily: langDef?.fontStack || "'Times New Roman', Times, serif",
                            fontStyle: 'normal',
                            fontSize: '15px',
                            lineHeight: '1.6',
                            textAlign: langDef?.direction === 'rtl' ? 'right' : 'left',
                          }}
                        >
                          {detail.subtitle || '(Bản dịch không có văn bản phụ đề)'}
                        </div>
                      );
                    })()}
                  </div>

                  <div className="audio-preview-card">
                    {isMockMode && (
                      <div
                        style={{
                          background: '#f0f9ff',
                          border: '1px solid #bae6fd',
                          borderRadius: '6px',
                          padding: '8px 12px',
                          marginBottom: '12px',
                          fontSize: '12px',
                          color: '#0369a1',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                        }}
                      >
                        <span>ℹ</span>
                        <span><strong>Chế độ chạy thử (giọng giả lập):</strong> Hệ thống đang phát mẫu âm thanh chuẩn trong môi trường phát triển.</span>
                      </div>
                    )}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
                      <label className="form-label" style={{ margin: 0, fontWeight: 'bold' }}>
                        Âm thanh ({getLanguageLabel(detail.lang)}):
                      </label>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => reloadDetail(false)}
                        title="Làm mới liên kết âm thanh và chữ ký bảo mật"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 4, cursor: 'pointer' }}
                      >
                        🔄 Làm mới
                      </button>
                    </div>

                    <audio
                      ref={audioRef}
                      key={audioSrc || detail.audioUrl}
                      controls
                      src={audioSrc || detail.audioUrl}
                      style={{ width: '100%', outline: 'none', marginBottom: 8 }}
                      onPlay={handleAudioPlayAttempt}
                      onError={() => {
                        setAudioError(
                          'Không thể phát tệp âm thanh (có thể chữ ký bảo mật đã hết hạn). Vui lòng bấm "Làm mới" để tải lại liên kết.'
                        );
                      }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: 'var(--text-muted)', flexWrap: 'wrap', gap: 6 }}>
                      <span>Định dạng: MP3 (audio/mpeg)</span>
                      <span>
                        Chữ ký bảo mật hết hạn lúc:{' '}
                        <strong>
                          {new Date(detail.audioUrlExpiresAt).toLocaleTimeString('vi-VN')}{' '}
                          {new Date(detail.audioUrlExpiresAt).toLocaleDateString('vi-VN')}
                        </strong>
                        {Date.now() >= new Date(detail.audioUrlExpiresAt).getTime() && (
                          <span style={{ color: 'var(--color-danger, #d9534f)', marginLeft: 6, fontWeight: 'bold' }}>
                            (Đã hết hạn — Bấm "Làm mới")
                          </span>
                        )}
                      </span>
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={handleClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};
