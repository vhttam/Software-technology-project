import React, { useEffect, useState, useRef } from 'react';
import { jobsApi } from '../api/jobsApi';
import { BatchTranslateResponseData } from '../api/types';
import {
  getEnabledLanguages,
  getVoiceOptionsForLanguage,
  getDefaultVoiceForLanguage,
  LanguageDef,
  fetchLanguageConfig,
} from '../config/languageRegistry';
import { Button } from './Button';

interface BatchTranslateModalProps {
  onClose: () => void;
  onSuccess?: () => void;
}

export const BatchTranslateModal: React.FC<BatchTranslateModalProps> = ({ onClose, onSuccess }) => {
  const [languages, setLanguages] = useState<LanguageDef[]>(getEnabledLanguages());
  const [selectedLang, setSelectedLang] = useState<string>('en');
  const [selectedVoice, setSelectedVoice] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [activeBatch, setActiveBatch] = useState<BatchTranslateResponseData | null>(null);
  const pollTimerRef = useRef<number | null>(null);

  useEffect(() => {
    fetchLanguageConfig()
      .then((langs) => {
        if (langs && langs.length > 0) {
          const enabledLangs = langs.filter((l) => l.enabled !== false);
          setLanguages(enabledLangs);
          if (enabledLangs.length > 0) {
            const first = enabledLangs.find((l) => l.code !== 'vi') || enabledLangs[0];
            setSelectedLang(first.code);
            const defVoice = getDefaultVoiceForLanguage(first.code)?.voiceId || '';
            setSelectedVoice(defVoice);
          }
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const defVoice = getDefaultVoiceForLanguage(selectedLang)?.voiceId || '';
    setSelectedVoice(defVoice);
  }, [selectedLang]);

  // Polling server batch progress
  useEffect(() => {
    if (!activeBatch || activeBatch.status !== 'PROCESSING') {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      return;
    }

    const poll = async () => {
      try {
        const updated = await jobsApi.getBatchTranslate(activeBatch.batchId);
        setActiveBatch(updated);
        if (updated.status === 'COMPLETED' && onSuccess) {
          onSuccess();
        }
      } catch (err) {
        console.error('Lỗi kiểm tra tiến độ batch:', err);
      }
    };

    pollTimerRef.current = window.setInterval(poll, 1200);
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [activeBatch?.batchId, activeBatch?.status]);

  const handleStart = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLang) return;
    setSubmitting(true);
    setError(null);

    try {
      const res = await jobsApi.startBatchTranslate({
        targetLang: selectedLang,
        voiceId: selectedVoice || undefined,
      });
      setActiveBatch(res);
      if (res.status === 'COMPLETED' && onSuccess) {
        onSuccess();
      }
    } catch (err: any) {
      setError(err?.message || 'Không thể khởi động tiến trình dịch bổ sung hàng loạt');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!activeBatch?.batchId) return;
    setCancelling(true);
    try {
      const res = await jobsApi.cancelBatchTranslate(activeBatch.batchId);
      setActiveBatch(res);
    } catch (err: any) {
      setError(err?.message || 'Không thể hủy tiến trình dịch hàng loạt');
    } finally {
      setCancelling(false);
    }
  };

  const voiceOptions = getVoiceOptionsForLanguage(selectedLang);
  const selectedLangDef = languages.find((l) => l.code === selectedLang);

  const total = activeBatch?.queuedContents || 0;
  const processed = activeBatch?.processedContents || 0;
  const percent = total > 0 ? Math.min(100, Math.round((processed / total) * 100)) : 100;

  return (
    <div
      className="modal-backdrop"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1050,
        padding: '20px',
      }}
    >
      <div
        className="modal-card"
        style={{
          background: '#ffffff',
          borderRadius: '10px',
          maxWidth: '560px',
          width: '100%',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div
          style={{
            padding: '18px 24px',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', color: '#0f172a' }}>
              Dịch bổ sung hàng loạt
            </h3>
            <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#64748b' }}>
              Tự động tạo tác vụ dịch cho toàn bộ nội dung chưa có ngôn ngữ đích
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              border: 'none',
              background: 'transparent',
              fontSize: '20px',
              cursor: 'pointer',
              color: '#94a3b8',
            }}
          >
            ✕
          </button>
        </div>

        <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {error && <div className="alert alert-danger">{error}</div>}

          {!activeBatch && (
            <form onSubmit={handleStart} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#334155' }}>
                  Ngôn ngữ đích cần dịch bổ sung:
                </label>
                <select
                  value={selectedLang}
                  onChange={(e) => setSelectedLang(e.target.value)}
                  style={{
                    height: '40px',
                    padding: '0 12px',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e0',
                    fontSize: '14px',
                    backgroundColor: '#ffffff',
                    outline: 'none',
                  }}
                >
                  {languages.filter((l) => l.enabled !== false).map((l) => {
                    const isAvailable = l.available !== false && !l.disabledReason;
                    return (
                      <option key={l.code} value={l.code} disabled={!isAvailable}>
                        {l.nameVi} ({l.nativeName}) {!isAvailable ? '— [Tạm thời chưa dùng được]' : ''}
                      </option>
                    );
                  })}
                </select>
                {selectedLangDef && (selectedLangDef.available === false || selectedLangDef.disabledReason) && (
                  <span style={{ fontSize: '12px', color: '#b91c1c' }}>
                    ⚠️ Tạm thời chưa dùng được. Hãy thử lại sau.
                  </span>
                )}
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{ fontSize: '14px', fontWeight: 600, color: '#334155' }}>
                  Giọng đọc tổng hợp:
                </label>
                <select
                  value={selectedVoice}
                  onChange={(e) => setSelectedVoice(e.target.value)}
                  style={{
                    height: '40px',
                    padding: '0 12px',
                    borderRadius: '6px',
                    border: '1px solid #cbd5e0',
                    fontSize: '14px',
                    backgroundColor: '#ffffff',
                    outline: 'none',
                  }}
                >
                  {voiceOptions.map((v) => (
                    <option key={v.voiceId} value={v.voiceId}>
                      {v.label} ({v.type})
                    </option>
                  ))}
                </select>
              </div>

              <div
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '6px',
                  padding: '14px',
                  fontSize: '13px',
                  color: '#475569',
                  lineHeight: '1.5',
                }}
              >
                <div style={{ fontWeight: 600, color: '#1e293b', marginBottom: '4px' }}>
                  ⚡ Cơ chế chống trùng lặp và vận hành tự động:
                </div>
                <ul style={{ margin: 0, paddingLeft: '18px' }}>
                  <li>Chỉ quét và dịch những nội dung <strong>chưa có</strong> ngôn ngữ đích ở phiên bản hiện tại.</li>
                  <li>Tự động bỏ qua nội dung đã có bản dịch hợp lệ hoặc đang có tác vụ xử lý dở dang.</li>
                  <li>Tiến trình chạy <strong>phía server</strong>: không phụ thuộc vào tab trình duyệt.</li>
                </ul>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
                <Button variant="secondary" type="button" onClick={onClose} disabled={submitting}>
                  Đóng
                </Button>
                <Button variant="primary" type="submit" disabled={submitting || selectedLangDef?.enabled === false}>
                  {submitting ? (
                    <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span
                        style={{
                          width: '14px',
                          height: '14px',
                          border: '2px solid #ffffff',
                          borderTopColor: 'transparent',
                          borderRadius: '50%',
                          display: 'inline-block',
                          animation: 'spin 0.8s linear infinite',
                        }}
                      />{' '}
                      Đang chuẩn bị...
                    </span>
                  ) : (
                    'Bắt đầu dịch hàng loạt'
                  )}
                </Button>
              </div>
            </form>
          )}

          {activeBatch && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: '#f8fafc',
                  padding: '12px 16px',
                  borderRadius: '6px',
                  border: '1px solid #e2e8f0',
                }}
              >
                <div>
                  <div style={{ fontSize: '12px', color: '#64748b', fontWeight: 600 }}>TRẠNG THÁI TIẾN TRÌNH</div>
                  <div style={{ fontSize: '15px', fontWeight: 700, color: '#0f172a', marginTop: '2px' }}>
                    {activeBatch.status === 'PROCESSING' && '⏳ Đang tạo và xử lý tác vụ...'}
                    {activeBatch.status === 'COMPLETED' && '✓ Hoàn thành dịch hàng loạt'}
                    {activeBatch.status === 'CANCELLED' && '⏹ Đã hủy tiến trình'}
                  </div>
                </div>

                <div>
                  <span
                    style={{
                      fontSize: '12px',
                      fontWeight: 700,
                      padding: '4px 10px',
                      borderRadius: '20px',
                      background: activeBatch.status === 'COMPLETED' ? '#dcfce7' : activeBatch.status === 'CANCELLED' ? '#fee2e2' : '#e0f2fe',
                      color: activeBatch.status === 'COMPLETED' ? '#15803d' : activeBatch.status === 'CANCELLED' ? '#b91c1c' : '#0369a1',
                    }}
                  >
                    {activeBatch.status === 'COMPLETED' ? 'HOÀN TẤT' : activeBatch.status === 'CANCELLED' ? 'ĐÃ HỦY' : 'ĐANG CHẠY'}
                  </span>
                </div>
              </div>

              {/* Progress bar */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', color: '#475569' }}>
                  <span>Tiến độ tạo tác vụ:</span>
                  <span style={{ fontWeight: 600 }}>{processed} / {total} nội dung ({percent}%)</span>
                </div>
                <div style={{ height: '10px', background: '#e2e8f0', borderRadius: '5px', overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${percent}%`,
                      background: activeBatch.status === 'COMPLETED' ? '#22c55e' : activeBatch.status === 'CANCELLED' ? '#ef4444' : '#0ea5e9',
                      transition: 'width 0.3s ease',
                    }}
                  />
                </div>
              </div>

              {/* Stats summary */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '10px' }}>
                <div style={{ background: '#f1f5f9', padding: '10px', borderRadius: '6px', textAlign: 'center' }}>
                  <div style={{ fontSize: '11px', color: '#64748b' }}>Tổng nội dung</div>
                  <div style={{ fontSize: '16px', fontWeight: 700, color: '#1e293b' }}>{activeBatch.totalContents}</div>
                </div>
                <div style={{ background: '#f1f5f9', padding: '10px', borderRadius: '6px', textAlign: 'center' }}>
                  <div style={{ fontSize: '11px', color: '#64748b' }}>Đã có (Bỏ qua)</div>
                  <div style={{ fontSize: '16px', fontWeight: 700, color: '#15803d' }}>{activeBatch.skippedContents}</div>
                </div>
                <div style={{ background: '#f1f5f9', padding: '10px', borderRadius: '6px', textAlign: 'center' }}>
                  <div style={{ fontSize: '11px', color: '#64748b' }}>Cần dịch bổ sung</div>
                  <div style={{ fontSize: '16px', fontWeight: 700, color: '#0369a1' }}>{activeBatch.queuedContents}</div>
                </div>
              </div>

              <div style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic', background: '#f8fafc', padding: '8px 12px', borderRadius: '4px' }}>
                💡 Lưu ý: Tác vụ dịch và tạo giọng đọc được thực hiện độc lập dưới nền máy chủ. Bạn có thể đóng cửa sổ này bất cứ lúc nào và kiểm tra kết quả tại trang Quản lý tác vụ.
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                {activeBatch.status === 'PROCESSING' && (
                  <Button variant="danger" type="button" onClick={handleCancel} disabled={cancelling}>
                    {cancelling ? 'Đang hủy...' : 'Hủy tiến trình'}
                  </Button>
                )}
                <Button variant="secondary" type="button" onClick={onClose}>
                  {activeBatch.status === 'PROCESSING' ? 'Đóng cửa sổ (chạy ngầm)' : 'Đóng'}
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
