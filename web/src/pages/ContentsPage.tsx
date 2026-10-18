import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { contentApi } from '../api/contentApi';
import { ApiError, ContentItem, PoiItem, PublishedNarrationSummary } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { Spinner } from '../components/Spinner';
import { SearchInput } from '../components/SearchInput';
import { Button } from '../components/Button';
import { PageHeader } from '../components/PageHeader';
import { BatchTranslateModal } from '../components/BatchTranslateModal';
import { getLanguageByCode } from '../config/languageRegistry';

export const ContentsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const poiIdParam = searchParams.get('poiId') || '';
  const navigate = useNavigate();

  const titleInputRef = useRef<HTMLInputElement>(null);

  const [contents, setContents] = useState<ContentItem[]>([]);
  const [pois, setPois] = useState<PoiItem[]>([]);
  const [sourceLanguages, setSourceLanguages] = useState<string[]>(['vi', 'en', 'fr', 'ja', 'zh', 'ko']);
  const [poiNarrationsMap, setPoiNarrationsMap] = useState<Record<string, PublishedNarrationSummary[]>>({});
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [query, setQuery] = useState('');
  const [selectedPoiFilter, setSelectedPoiFilter] = useState(poiIdParam);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [actionNotice, setActionNotice] = useState('');
  const [showBatchModal, setShowBatchModal] = useState(false);

  // Form states
  const [editingContent, setEditingContent] = useState<ContentItem | null>(null);
  const [targetPoiId, setTargetPoiId] = useState(poiIdParam);
  const [title, setTitle] = useState('');
  const [sourceLang, setSourceLang] = useState('vi');
  const [textContent, setTextContent] = useState('');
  const [baseVersion, setBaseVersion] = useState(1);
  const [saving, setSaving] = useState(false);

  const loadData = async (targetPage = 1, searchQuery = query, poiFilter = selectedPoiFilter) => {
    setLoading(true);
    setError(null);
    try {
      const [contentsData, poisData, langData] = await Promise.all([
        contentApi.listContents(targetPage, 10, searchQuery, poiFilter || undefined),
        contentApi.listPois(1, 100),
        contentApi.getSourceLanguages().catch(() => ({ languages: ['vi', 'en', 'fr', 'ja', 'zh', 'ko'] })),
      ]);

      const items = contentsData.items || [];
      setContents(items);
      setPage(contentsData.page);
      setTotalPages(contentsData.totalPages || 1);
      setPois(poisData.items || []);
      if (langData.languages?.length) {
        setSourceLanguages(langData.languages);
      }

      // Fetch narrations for each POI to detect outdated translations
      const poiIds = Array.from(new Set(items.map((c) => c.poiId).filter(Boolean)));
      if (poiIds.length > 0) {
        const narrationResults = await Promise.allSettled(
          poiIds.map((id) => contentApi.listPoiNarrations(id))
        );
        const map: Record<string, PublishedNarrationSummary[]> = {};
        narrationResults.forEach((res, idx) => {
          if (res.status === 'fulfilled' && res.value?.narrations) {
            map[poiIds[idx]] = res.value.narrations;
          }
        });
        setPoiNarrationsMap(map);
      }
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData(1, query, poiIdParam);
  }, [poiIdParam]);

  const handleFilterChange = (poiId: string) => {
    setSelectedPoiFilter(poiId);
    if (poiId) {
      setSearchParams({ poiId });
    } else {
      setSearchParams({});
    }
    loadData(1, query, poiId);
  };

  const handleSearchChange = (val: string) => {
    setQuery(val);
    loadData(1, val, selectedPoiFilter);
  };

  const handleStartCreate = (defaultPoiId?: string) => {
    setEditingContent(null);
    setTitle('');
    setTextContent('');
    setSourceLang(sourceLanguages[0] || 'vi');
    setTargetPoiId(defaultPoiId || selectedPoiFilter || pois[0]?.poiId || '');
    setTimeout(() => {
      titleInputRef.current?.focus();
      titleInputRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 100);
  };

  const handleStartEdit = async (item: ContentItem) => {
    setError(null);
    try {
      const full = await contentApi.getContent(item.contentId);
      setEditingContent(full);
      setTitle(full.title);
      setSourceLang(full.sourceLang);
      setTextContent(full.textContent || '');
      setBaseVersion(full.version);
      setTimeout(() => {
        titleInputRef.current?.focus();
        titleInputRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 100);
    } catch (err) {
      setError(err);
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaving(true);

    try {
      if (editingContent) {
        const updated = await contentApi.updateContent(editingContent.contentId, {
          baseVersion,
          title: title.trim(),
          sourceLang,
          textContent: textContent.trim(),
        });
        setActionNotice(`Đã lưu phiên bản mới (Phiên bản ${updated.version}) cho nội dung "${updated.title}".`);
      } else {
        if (!targetPoiId) {
          throw new Error('Vui lòng chọn Điểm tham quan.');
        }
        const created = await contentApi.createContent(targetPoiId, {
          title: title.trim(),
          sourceLang,
          textContent: textContent.trim(),
        });
        setActionNotice(`Đã tạo nội dung mới (Phiên bản ${created.version}) cho điểm tham quan.`);
      }
      handleStartCreate();
      await loadData(page, query, selectedPoiFilter);
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'ACTIVE_CONTENT_EXISTS') {
        setError(
          new ApiError(
            err.status,
            err.errorCode,
            'Điểm này đã có nội dung đang hoạt động. Hãy sửa hoặc xóa nội dung hiện tại trước khi tạo mới.',
            err.details,
            err.correlationId
          )
        );
      } else {
        setError(err);
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item: ContentItem) => {
    const confirmed = window.confirm(
      `Xác nhận xóa nội dung "${item.title}"? Nội dung sẽ không còn hiển thị cho khách và không thể nhận tác vụ mới.`
    );
    if (!confirmed) return;

    setError(null);
    try {
      await contentApi.deleteContent(item.contentId);
      setActionNotice(`Đã xóa nội dung "${item.title}".`);
      await loadData(page, query, selectedPoiFilter);
    } catch (err) {
      setError(err);
    }
  };

  const filteredPoiObj = pois.find((p) => p.poiId === selectedPoiFilter);

  return (
    <div className="admin-page" style={{ padding: '0 4px' }}>
      <PageHeader
        eyebrow="QUẢN LÝ NỘI DUNG"
        title="Nội dung thuyết minh"
        description="Quản lý văn bản thuyết minh gốc của từng điểm tham quan."
        action={
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Button
              variant="primary"
              size="md"
              onClick={() => setShowBatchModal(true)}
              icon={
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M5 8l6 6M4 14l6-6 2-3M2 5h12M7 2h1M22 22l-5-10-5 10M14 18h6" />
                </svg>
              }
            >
              Dịch bổ sung hàng loạt
            </Button>
            <Button
              variant="secondary"
              size="md"
              onClick={() => loadData(page)}
              icon={
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.3" />
                </svg>
              }
            >
              Tải lại
            </Button>
          </div>
        }
      />

      {showBatchModal && (
        <BatchTranslateModal
          onClose={() => setShowBatchModal(false)}
          onSuccess={() => {
            loadData(page);
            setActionNotice('Tiến trình dịch hàng loạt đã hoàn tất!');
          }}
        />
      )}

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {actionNotice && (
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
          {actionNotice}
        </div>
      )}

      <div className="manager-grid" style={{ marginTop: '20px' }}>
        {/* Form Panel */}
        <div
          className="panel"
          style={{
            background: '#ffffff',
            border: '1px solid var(--border-light)',
            borderRadius: '8px',
            padding: '20px 24px',
          }}
        >
          <div
            className="panel-heading"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '18px',
              borderBottom: '1px solid var(--border-light)',
              paddingBottom: '12px',
            }}
          >
            <div>
              <div className="eyebrow" style={{ fontSize: '11px', fontWeight: 'bold', color: '#64748b' }}>
                {editingContent ? `CẬP NHẬT PHIÊN BẢN (BASE: v${baseVersion})` : 'TẠO NỘI DUNG MỚI'}
              </div>
              <h2 style={{ margin: '4px 0 0', fontSize: '17px', color: 'var(--text-main)' }}>
                {editingContent ? `Sửa: ${editingContent.title}` : 'Nhập nội dung mới'}
              </h2>
            </div>
            {editingContent && (
              <Button variant="ghost" size="sm" onClick={() => handleStartCreate()}>
                + Tạo nội dung mới
              </Button>
            )}
          </div>

          <form className="stack-form" onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {!editingContent && (
              <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
                <span>Điểm tham quan liên kết *</span>
                <select
                  required
                  value={targetPoiId}
                  onChange={(e) => setTargetPoiId(e.target.value)}
                  disabled={saving || pois.length === 0}
                  style={{
                    height: '40px',
                    padding: '0 12px',
                    borderRadius: '4px',
                    border: '1px solid #cbd5e0',
                    fontSize: '14px',
                    backgroundColor: '#ffffff',
                    outline: 'none',
                  }}
                >
                  <option value="">-- Chọn điểm tham quan --</option>
                  {pois.map((p) => (
                    <option key={p.poiId} value={p.poiId}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>
            )}

            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Tiêu đề nội dung (tối đa 255 ký tự) *</span>
              <input
                ref={titleInputRef}
                required
                maxLength={255}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Ví dụ: Giới thiệu sảnh chính và hiện vật cổ"
                disabled={saving}
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  outline: 'none',
                }}
              />
            </label>

            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Ngôn ngữ nguồn *</span>
              <select
                required
                value={sourceLang}
                onChange={(e) => setSourceLang(e.target.value)}
                disabled={saving}
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  backgroundColor: '#ffffff',
                  outline: 'none',
                }}
              >
                {sourceLanguages
                  .filter((code) => {
                    const lDef = getLanguageByCode(code);
                    if (editingContent && editingContent.sourceLang === code) return true;
                    return lDef ? lDef.enabled !== false : true;
                  })
                  .map((code) => {
                    const lDef = getLanguageByCode(code);
                    const isPaused = lDef ? lDef.enabled === false : false;
                    return (
                      <option key={code} value={code}>
                        {lDef ? `${lDef.nameVi} (${lDef.nativeName})` : code} {isPaused ? '— [Ngôn ngữ đã tạm dừng]' : ''}
                      </option>
                    );
                  })}
              </select>
            </label>

            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Văn bản thuyết minh gốc (tối đa 10.000 ký tự) *</span>
              <textarea
                required
                maxLength={10000}
                rows={6}
                value={textContent}
                onChange={(e) => setTextContent(e.target.value)}
                placeholder="Nhập nội dung thuyết minh gốc dùng để dịch và đọc âm thanh..."
                disabled={saving}
                style={{
                  padding: '10px 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  fontFamily: 'inherit',
                  outline: 'none',
                }}
              />
            </label>

            <div className="button-row" style={{ display: 'flex', gap: '10px', marginTop: '6px' }}>
              <Button type="submit" variant="primary" size="md" loading={saving} disabled={saving}>
                {saving
                  ? 'Đang lưu…'
                  : editingContent
                  ? `Lưu phiên bản ${baseVersion + 1}`
                  : '+ Tạo nội dung'}
              </Button>
              {editingContent && (
                <Button type="button" variant="secondary" size="md" onClick={() => handleStartCreate()} disabled={saving}>
                  Hủy sửa
                </Button>
              )}
            </div>
          </form>
        </div>

        {/* List Panel */}
        <div
          className="panel"
          style={{
            background: '#ffffff',
            border: '1px solid var(--border-light)',
            borderRadius: '8px',
            padding: '20px 24px',
          }}
        >
          <div
            className="panel-heading"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '18px',
              borderBottom: '1px solid var(--border-light)',
              paddingBottom: '12px',
              flexWrap: 'wrap',
              gap: '12px',
            }}
          >
            <div>
              <div className="eyebrow" style={{ fontSize: '11px', fontWeight: 'bold', color: '#64748b' }}>
                DANH SÁCH
              </div>
              <h2 style={{ margin: '4px 0 0', fontSize: '17px', color: 'var(--text-main)' }}>
                Nội dung thuyết minh
              </h2>
            </div>

            {/* Filter and SearchInput aligned at exactly 40px height and same baseline */}
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
              <select
                value={selectedPoiFilter}
                onChange={(e) => handleFilterChange(e.target.value)}
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  backgroundColor: '#ffffff',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              >
                <option value="">Tất cả điểm</option>
                {pois.map((p) => (
                  <option key={p.poiId} value={p.poiId}>
                    {p.name}
                  </option>
                ))}
              </select>

              <SearchInput
                value={query}
                onChange={handleSearchChange}
                placeholder="Tìm nội dung theo tiêu đề hoặc tên điểm..."
                debounceMs={300}
                style={{ width: '260px' }}
              />
            </div>
          </div>

          {loading ? (
            <div style={{ padding: '60px 0', textAlign: 'center' }}>
              <Spinner />
              <p style={{ marginTop: '12px', color: '#64748b' }}>Đang nạp danh sách nội dung…</p>
            </div>
          ) : contents.length === 0 ? (
            selectedPoiFilter && filteredPoiObj ? (
              <div style={{ padding: '40px 16px', textAlign: 'center' }}>
                <div style={{ fontSize: '32px', marginBottom: '10px' }}>🏛</div>
                <h3 style={{ fontSize: '16px', color: 'var(--text-main)', margin: '0 0 6px' }}>
                  Điểm "{filteredPoiObj.name}" chưa có nội dung thuyết minh.
                </h3>
                <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: '0 0 16px' }}>
                  Bạn có thể tạo văn bản thuyết minh đầu tiên cho điểm tham quan này.
                </p>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => handleStartCreate(filteredPoiObj.poiId)}
                >
                  + Tạo nội dung cho điểm này
                </Button>
              </div>
            ) : (
              <div style={{ padding: '40px 16px', textAlign: 'center', color: '#64748b' }}>
                Chưa có nội dung thuyết minh nào phù hợp.
              </div>
            )
          ) : (
            <div className="simple-list" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {contents.map((item) => {
                const narrations = poiNarrationsMap[item.poiId] || [];
                const hasOutdatedTranslation =
                  !item.isDeleted && narrations.some((n) => n.version < item.version || n.isOutdated);

                return (
                  <div
                    className="list-row"
                    key={item.contentId}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      justifyContent: 'space-between',
                      padding: '14px 16px',
                      border: '1px solid var(--border-light)',
                      borderRadius: '6px',
                      opacity: item.isDeleted ? 0.6 : 1,
                      backgroundColor: item.isDeleted ? '#fafafa' : '#ffffff',
                      gap: '12px',
                      flexWrap: 'wrap',
                    }}
                  >
                    <div style={{ flex: 1, minWidth: '220px' }}>
                      <strong style={{ fontSize: '15px', color: 'var(--text-main)' }}>{item.title}</strong>
                      <div style={{ fontSize: '13px', color: '#475569', margin: '4px 0', display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                        <span>Điểm: <b>{item.poiName || item.poiId}</b> · Ngôn ngữ: <b>{getLanguageByCode(item.sourceLang)?.nameVi || item.sourceLang}</b></span>
                        {(!getLanguageByCode(item.sourceLang) || getLanguageByCode(item.sourceLang)?.enabled === false) && (
                          <span style={{ fontSize: '11px', color: '#991b1b', background: '#fee2e2', padding: '1px 6px', borderRadius: '4px', fontWeight: 600 }}>
                            Ngôn ngữ đã tạm dừng
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: '12px', color: '#64748b', display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                        <span
                          style={{
                            background: '#f1f5f9',
                            padding: '2px 8px',
                            borderRadius: '4px',
                            fontWeight: 600,
                            color: '#334155',
                          }}
                        >
                          Phiên bản {item.version}
                        </span>
                        {hasOutdatedTranslation && (
                          <span
                            style={{
                              backgroundColor: '#fffbeb',
                              color: '#b45309',
                              border: '1px solid #fde68a',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontWeight: 600,
                              fontSize: '12px',
                            }}
                          >
                            ⚠️ Có bản dịch đã cũ
                          </span>
                        )}
                        {item.isDeleted ? (
                          <span style={{ color: '#b91c1c', fontWeight: 600 }}>[ĐÃ XÓA]</span>
                        ) : (
                          <span style={{ color: '#15803d', fontWeight: 600 }}>[ĐANG HOẠT ĐỘNG]</span>
                        )}
                      </div>
                    </div>

                    <div className="actions-row" style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      {!item.isDeleted && (
                        <>
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => navigate(`/jobs/new?contentId=${encodeURIComponent(item.contentId)}`)}
                            style={hasOutdatedTranslation ? { backgroundColor: '#d97706', borderColor: '#b45309' } : undefined}
                          >
                            {hasOutdatedTranslation ? '+ Cập nhật bản dịch' : 'Tạo tác vụ ↗'}
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => handleStartEdit(item)}
                          >
                            Sửa
                          </Button>
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => handleDelete(item)}
                          >
                            Xóa
                          </Button>
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="pagination" style={{ display: 'flex', justifyContent: 'center', gap: '12px', marginTop: '20px' }}>
              <Button
                variant="secondary"
                size="sm"
                disabled={page <= 1 || loading}
                onClick={() => loadData(page - 1, query, selectedPoiFilter)}
              >
                ← Trang trước
              </Button>
              <span style={{ fontSize: '13px', alignSelf: 'center', color: '#64748b' }}>
                Trang {page} / {totalPages}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= totalPages || loading}
                onClick={() => loadData(page + 1, query, selectedPoiFilter)}
              >
                Trang sau →
              </Button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
