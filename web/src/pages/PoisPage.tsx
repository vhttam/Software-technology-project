import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { contentApi } from '../api/contentApi';
import { ApiError, PoiItem } from '../api/types';
import { AudioPreviewModal } from '../components/AudioPreviewModal';
import { ErrorBanner } from '../components/ErrorBanner';
import { QrCodeModal } from '../components/QrCodeModal';
import { QrThumbnail } from '../components/QrThumbnail';
import { Spinner } from '../components/Spinner';
import { SearchInput } from '../components/SearchInput';
import { Button } from '../components/Button';
import { PageHeader } from '../components/PageHeader';

export const PoisPage: React.FC = () => {
  const navigate = useNavigate();

  const [pois, setPois] = useState<PoiItem[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [previewPoiId, setPreviewPoiId] = useState<string | null>(null);

  // Form states
  const [editingPoi, setEditingPoi] = useState<PoiItem | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [actionNotice, setActionNotice] = useState('');

  // QR Modal state
  const [qrModalPoi, setQrModalPoi] = useState<PoiItem | null>(null);

  // Delete states
  const [deletingPoi, setDeletingPoi] = useState<PoiItem | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteConflict, setDeleteConflict] = useState<{
    poiId: string;
    poiName: string;
    message: string;
  } | null>(null);

  const loadPois = async (targetPage = 1, query = search) => {
    setLoading(true);
    setError(null);
    try {
      const data = await contentApi.listPois(targetPage, 10, query);
      setPois(data.items || []);
      setPage(data.page);
      setTotalPages(data.totalPages || 1);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPois(1);
  }, []);

  const handleSearchChange = (val: string) => {
    setSearch(val);
    loadPois(1, val);
  };

  const handleStartCreate = () => {
    setEditingPoi(null);
    setName('');
    setDescription('');
  };

  const handleStartEdit = (poi: PoiItem) => {
    setEditingPoi(poi);
    setName(poi.name);
    setDescription(poi.description || '');
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    setSaving(true);
    setError(null);
    try {
      if (editingPoi) {
        await contentApi.updatePoi(editingPoi.poiId, { name: name.trim(), description: description.trim() });
        setActionNotice(`Đã cập nhật điểm tham quan "${name.trim()}".`);
      } else {
        await contentApi.createPoi({ name: name.trim(), description: description.trim() });
        setActionNotice(`Đã tạo điểm tham quan mới "${name.trim()}".`);
      }
      handleStartCreate();
      await loadPois(page);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingPoi) return;
    setDeleting(true);
    setError(null);
    setDeleteConflict(null);

    try {
      await contentApi.deletePoi(deletingPoi.poiId);
      setActionNotice(`Đã xóa điểm tham quan "${deletingPoi.name}".`);
      setDeletingPoi(null);
      await loadPois(page);
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'POI_HAS_CONTENT') {
        setDeleteConflict({
          poiId: deletingPoi.poiId,
          poiName: deletingPoi.name,
          message: err.message || 'Điểm còn nội dung đang hoạt động, hãy xóa nội dung trước.',
        });
        setDeletingPoi(null);
      } else {
        setError(err);
      }
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="admin-page" style={{ padding: '0 4px' }}>
      <PageHeader
        eyebrow="QUẢN LÝ ĐIỂM THAM QUAN"
        title="Điểm tham quan & Mã QR"
        description="Tạo, chỉnh sửa, xóa điểm tham quan và quản lý mã QR liên kết."
        action={
          <Button
            variant="secondary"
            size="md"
            onClick={() => loadPois(page)}
            icon={
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.3" />
              </svg>
            }
          >
            Tải lại
          </Button>
        }
      />

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {deleteConflict && (
        <div
          style={{
            background: '#fffbeb',
            border: '1px solid #fef3c7',
            borderRadius: '8px',
            padding: '12px 16px',
            margin: '14px 0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ color: '#92400e', fontSize: '13px' }}>
            {deleteConflict.message} (Điểm: <strong>{deleteConflict.poiName}</strong>)
          </div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate(`/contents?poiId=${encodeURIComponent(deleteConflict.poiId)}`)}
            >
              Đi tới Nội dung →
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setDeleteConflict(null)}
            >
              Đóng
            </Button>
          </div>
        </div>
      )}

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
          ✓ {actionNotice}
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
                THAO TÁC
              </div>
              <h2 style={{ margin: '4px 0 0', fontSize: '17px', color: 'var(--text-main)' }}>
                {editingPoi ? 'Cập nhật điểm tham quan' : 'Tạo điểm tham quan mới'}
              </h2>
            </div>
            {editingPoi && (
              <Button variant="ghost" size="sm" onClick={handleStartCreate}>
                + Chuyển sang tạo mới
              </Button>
            )}
          </div>

          <form className="stack-form" onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Tên điểm tham quan (tối đa 255 ký tự) *</span>
              <input
                required
                maxLength={255}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ví dụ: Bảo tàng Lịch sử Thành phố"
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
              <span>Mô tả chi tiết (tối đa 2000 ký tự)</span>
              <textarea
                maxLength={2000}
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Giới thiệu vị trí, nét đặc trưng hoặc hướng dẫn tìm kiếm..."
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
                {saving ? 'Đang lưu…' : editingPoi ? 'Lưu cập nhật' : '+ Tạo điểm tham quan'}
              </Button>
              {editingPoi && (
                <Button type="button" variant="secondary" size="md" onClick={handleStartCreate} disabled={saving}>
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
                Điểm hiện có
              </h2>
            </div>

            <SearchInput
              value={search}
              onChange={handleSearchChange}
              placeholder="Tìm theo tên điểm..."
              debounceMs={300}
              style={{ width: '220px' }}
            />
          </div>

          {loading ? (
            <div style={{ padding: '60px 0', textAlign: 'center' }}>
              <Spinner />
              <p style={{ marginTop: '12px', color: '#64748b' }}>Đang nạp danh sách điểm…</p>
            </div>
          ) : pois.length === 0 ? (
            <div style={{ padding: '40px 16px', textAlign: 'center', color: '#64748b' }}>
              Chưa có điểm tham quan nào phù hợp.
            </div>
          ) : (
            <div className="simple-list" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {pois.map((poi) => (
                <div
                  className="list-row"
                  key={poi.poiId}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '14px 16px',
                    border: '1px solid var(--border-light)',
                    borderRadius: '6px',
                    backgroundColor: '#ffffff',
                    gap: '14px',
                    flexWrap: 'wrap',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flex: 1, minWidth: '240px' }}>
                    <QrThumbnail
                      value={poi.qrCode}
                      onClick={() => setQrModalPoi(poi)}
                      title="Nhấn để xem / tải mã QR"
                    />

                    <div style={{ flex: 1, minWidth: 0 }}>
                      <strong style={{ fontSize: '15px', color: 'var(--text-main)' }}>{poi.name}</strong>
                      <div style={{ fontSize: '13px', color: '#475569', margin: '4px 0' }}>
                        {poi.description || <em>Chưa có mô tả.</em>}
                      </div>
                      <div style={{ fontSize: '12px', color: '#64748b', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                        <span>Mã QR: <code>{poi.qrCode || '—'}</code></span>
                      </div>
                    </div>
                  </div>

                  <div className="actions-row" style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setPreviewPoiId(poi.poiId)}
                      title="Nghe thử các bản thuyết minh đa ngôn ngữ đã xuất bản"
                      icon={<span>▶</span>}
                    >
                      Nghe thử
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setQrModalPoi(poi)}
                      title="Xem mã QR lớn, tải PNG, SVG hoặc In"
                    >
                      Mã QR
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => navigate(`/contents?poiId=${encodeURIComponent(poi.poiId)}`)}
                    >
                      Nội dung →
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => handleStartEdit(poi)}
                    >
                      Sửa
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => {
                        setDeletingPoi(poi);
                        setDeleteConflict(null);
                      }}
                    >
                      Xóa
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="pagination" style={{ display: 'flex', justifyContent: 'center', gap: '12px', marginTop: '20px' }}>
              <Button
                variant="secondary"
                size="sm"
                disabled={page <= 1 || loading}
                onClick={() => loadPois(page - 1)}
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
                onClick={() => loadPois(page + 1)}
              >
                Trang sau →
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Audio Preview Modal */}
      {previewPoiId && (
        <AudioPreviewModal
          poiId={previewPoiId}
          onClose={() => setPreviewPoiId(null)}
        />
      )}

      {/* QR Code Modal */}
      {qrModalPoi && (
        <QrCodeModal
          poi={qrModalPoi}
          onClose={() => setQrModalPoi(null)}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deletingPoi && (
        <div className="modal-backdrop" onClick={() => !deleting && setDeletingPoi(null)}>
          <div
            className="modal-dialog"
            style={{ maxWidth: '460px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 style={{ margin: 0, fontSize: '17px', color: '#b91c1c' }}>Xác nhận xóa điểm tham quan</h3>
              <button
                type="button"
                className="btn-link"
                onClick={() => setDeletingPoi(null)}
                disabled={deleting}
                style={{ fontSize: '18px', cursor: 'pointer', background: 'none', border: 'none' }}
              >
                ✕
              </button>
            </div>

            <div className="modal-body" style={{ padding: '20px' }}>
              <p style={{ margin: 0, fontSize: '14px', lineHeight: 1.5 }}>
                Bạn có chắc chắn muốn xóa điểm tham quan <strong>{deletingPoi.name}</strong> không?
              </p>
              <div
                style={{
                  background: '#fef2f2',
                  border: '1px solid #fee2e2',
                  borderRadius: '6px',
                  padding: '10px 12px',
                  color: '#991b1b',
                  fontSize: '12px',
                  marginTop: '12px',
                }}
              >
                Lưu ý: Nếu điểm vẫn còn nội dung thuyết minh đang hoạt động, hệ thống sẽ từ chối xóa để đảm bảo toàn vẹn dữ liệu.
              </div>
            </div>

            <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', padding: '14px 20px', borderTop: '1px solid var(--border-light)' }}>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setDeletingPoi(null)}
                disabled={deleting}
              >
                Hủy bỏ
              </Button>
              <Button
                type="button"
                variant="danger"
                size="sm"
                loading={deleting}
                disabled={deleting}
                onClick={handleConfirmDelete}
              >
                Xác nhận xóa
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
