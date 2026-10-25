import React, { useEffect, useRef, useState } from 'react';
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

declare const L: any;



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
  const [latitude, setLatitude] = useState<number | ''>(10.7769);
  const [longitude, setLongitude] = useState<number | ''>(106.7009);
  const [triggerRadius, setTriggerRadius] = useState<number>(50);
  const [saving, setSaving] = useState(false);
  const [actionNotice, setActionNotice] = useState('');

  // Map state & refs
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const tileLayerRef = useRef<any>(null);
  const markerGroupRef = useRef<any>(null);
  const tempMarkerRef = useRef<any>(null);
  const [mapMode, setMapMode] = useState<'streets' | 'satellite'>('streets');

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

  // Initialize and update Google Maps via Leaflet
  useEffect(() => {
    if (!mapContainerRef.current) return;

    if (typeof L === 'undefined') {
      console.warn('Leaflet not loaded on window.');
      return;
    }

    if (!mapInstanceRef.current) {
      const initialLat = 10.7769;
      const initialLng = 106.7009;
      const map = L.map(mapContainerRef.current, {
        center: [initialLat, initialLng],
        zoom: 14,
        zoomControl: false,
      });

      L.control.zoom({ position: 'topright' }).addTo(map);

      // Google Maps Streets Layer
      const lyrsCode = mapMode === 'satellite' ? 'y' : 'm';
      const streetLayer = L.tileLayer(
        `https://{s}.google.com/vt/lyrs=${lyrsCode}&x={x}&y={y}&z={z}`,
        {
          attribution: '&copy; Google Maps',
          maxZoom: 20,
          subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
        }
      ).addTo(map);

      tileLayerRef.current = streetLayer;

      const markerGroup = L.layerGroup().addTo(map);
      markerGroupRef.current = markerGroup;

      // Handle map clicks to place new POI marker
      map.on('click', (e: any) => {
        const clickedLat = Number(e.latlng.lat.toFixed(6));
        const clickedLng = Number(e.latlng.lng.toFixed(6));
        setLatitude(clickedLat);
        setLongitude(clickedLng);
        setActionNotice(`Đã chọn tọa độ trên bản đồ: [${clickedLat}, ${clickedLng}]. Hãy nhập nội dung và bấm Lưu.`);

        updateTempMarker(clickedLat, clickedLng, map);
      });

      mapInstanceRef.current = map;

      // Prevent gray canvas
      setTimeout(() => {
        map.invalidateSize();
      }, 300);
    }

    const map = mapInstanceRef.current;

    // Switch between street and satellite
    if (tileLayerRef.current && map) {
      map.removeLayer(tileLayerRef.current);
      const lyrsCode = mapMode === 'satellite' ? 'y' : 'm';
      const newLayer = L.tileLayer(
        `https://{s}.google.com/vt/lyrs=${lyrsCode}&x={x}&y={y}&z={z}`,
        {
          attribution: '&copy; Google Maps',
          maxZoom: 20,
          subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
        }
      ).addTo(map);
      tileLayerRef.current = newLayer;
    }

    // Refresh markers for current list of POIs
    if (markerGroupRef.current) {
      markerGroupRef.current.clearLayers();
      pois.forEach((poi, index) => {
        const pLat = poi.latitude ?? (10.772 + (index * 0.003));
        const pLng = poi.longitude ?? (106.700 + (index * 0.002));

        const poiIcon = L.divIcon({
          className: 'admin-poi-pin',
          html: `<div style="background-color:#0284c7;color:#ffffff;border:2px solid white;border-radius:50%;width:28px;height:28px;display:flex;align-items:center;justify-content:center;box-shadow:0 3px 8px rgba(2,132,199,0.4);font-size:12px;font-weight:700;">${index + 1}</div>`,
          iconSize: [28, 28],
          iconAnchor: [14, 28],
        });

        const marker = L.marker([pLat, pLng], { icon: poiIcon }).addTo(markerGroupRef.current);
        marker.bindPopup(`
          <div style="font-family: inherit; font-size: 13px; line-height: 1.4; min-width: 170px;">
            <strong style="color: #0f172a; font-size: 14px;">${poi.name}</strong>
            <p style="margin: 4px 0 6px 0; color: #475569; font-size: 12px;">${poi.description || 'Chưa có mô tả'}</p>
            <div style="font-size: 11px; color: #0284c7; font-weight:600; margin-bottom: 8px;">Tọa độ: ${pLat.toFixed(4)}, ${pLng.toFixed(4)}</div>
            <button id="admin-marker-btn-${poi.poiId}" style="width:100%; background:#0284c7; color:white; border:none; border-radius:6px; padding:5px 8px; font-size:12px; font-weight:600; cursor:pointer;">
              Sửa điểm này
            </button>
          </div>
        `);

        marker.on('popupopen', () => {
          const btn = document.getElementById(`admin-marker-btn-${poi.poiId}`);
          if (btn) {
            btn.onclick = () => handleStartEdit(poi);
          }
        });
      });
    }

    setTimeout(() => {
      map.invalidateSize();
    }, 200);
  }, [pois, mapMode]);

  const updateTempMarker = (lat: number, lng: number, map: any) => {
    if (tempMarkerRef.current) {
      tempMarkerRef.current.setLatLng([lat, lng]);
    } else {
      const customPin = L.divIcon({
        className: 'admin-temp-pin',
        html: `<div style="background-color:#ea580c;color:#ffffff;border:2.5px solid white;border-radius:50%;width:32px;height:32px;display:flex;align-items:center;justify-content:center;box-shadow:0 3px 10px rgba(0,0,0,0.35);font-size:15px;font-weight:bold;">📍</div>`,
        iconSize: [32, 32],
        iconAnchor: [16, 32],
      });
      const marker = L.marker([lat, lng], { icon: customPin, draggable: true }).addTo(map);
      marker.on('dragend', (ev: any) => {
        const pos = ev.target.getLatLng();
        setLatitude(Number(pos.lat.toFixed(6)));
        setLongitude(Number(pos.lng.toFixed(6)));
      });
      tempMarkerRef.current = marker;
    }
  };

  const handleSearchChange = (val: string) => {
    setSearch(val);
    loadPois(1, val);
  };

  const handleStartCreate = () => {
    setEditingPoi(null);
    setName('');
    setDescription('');
    setLatitude(10.7769);
    setLongitude(106.7009);
    setTriggerRadius(50);
    if (tempMarkerRef.current && mapInstanceRef.current) {
      mapInstanceRef.current.removeLayer(tempMarkerRef.current);
      tempMarkerRef.current = null;
    }
  };

  const handleStartEdit = (poi: PoiItem) => {
    setEditingPoi(poi);
    setName(poi.name);
    setDescription(poi.description || '');
    const pLat = poi.latitude ?? 10.7769;
    const pLng = poi.longitude ?? 106.7009;
    setLatitude(pLat);
    setLongitude(pLng);
    setTriggerRadius(poi.triggerRadius || 50);

    // Pan map to POI location
    if (mapInstanceRef.current) {
      mapInstanceRef.current.setView([pLat, pLng], 16, { animate: true });
      updateTempMarker(pLat, pLng, mapInstanceRef.current);
    }
  };

  const handleSave = async (e: React.FormEvent, createJobAfter = false) => {
    e.preventDefault();
    if (!name.trim()) return;

    setSaving(true);
    setError(null);
    try {
      let savedPoiId = '';
      if (editingPoi) {
        await contentApi.updatePoi(editingPoi.poiId, {
          name: name.trim(),
          description: description.trim(),
          latitude: typeof latitude === 'number' ? latitude : undefined,
          longitude: typeof longitude === 'number' ? longitude : undefined,
          triggerRadius,
        });
        savedPoiId = editingPoi.poiId;
        setActionNotice(`Đã cập nhật điểm tham quan "${name.trim()}".`);
      } else {
        const created = await contentApi.createPoi({
          name: name.trim(),
          description: description.trim(),
          latitude: typeof latitude === 'number' ? latitude : undefined,
          longitude: typeof longitude === 'number' ? longitude : undefined,
          triggerRadius,
        });
        savedPoiId = created.poiId;
        setActionNotice(`Đã tạo điểm tham quan mới "${name.trim()}".`);
      }

      if (createJobAfter && savedPoiId) {
        navigate(`/jobs/new?poiId=${encodeURIComponent(savedPoiId)}&poiName=${encodeURIComponent(name.trim())}`);
        return;
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
    <div className="admin-page" style={{ padding: '0 4px', fontFamily: '"Plus Jakarta Sans", "Inter", sans-serif' }}>
      <PageHeader
        eyebrow="QUẢN LÝ ĐIỂM THAM QUAN & BẢN ĐỒ"
        title="Bản đồ số & Điểm tham quan"
        description="Tìm chọn địa điểm thực tế có sẵn hoặc nhấp vào bản đồ để tạo điểm, nhập thuyết minh và tạo tác vụ tự động."
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button
              variant="secondary"
              size="md"
              onClick={() => setMapMode(mapMode === 'streets' ? 'satellite' : 'streets')}
            >
              {mapMode === 'streets' ? '🛰 Chế độ Vệ tinh' : '🗺 Chế độ Đường phố'}
            </Button>
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
          </div>
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
            background: '#ecfdf5',
            border: '1px solid #a7f3d0',
            borderRadius: '8px',
            padding: '12px 16px',
            margin: '14px 0',
            color: '#065f46',
            fontSize: '13px',
            fontWeight: 500,
          }}
        >
          ✓ {actionNotice}
        </div>
      )}

      {/* Google Maps GIS Card */}
      <div
        className="panel"
        style={{
          background: '#ffffff',
          border: '1px solid #e2e8f0',
          borderRadius: '12px',
          padding: '16px',
          marginTop: '16px',
          boxShadow: '0 4px 20px -2px rgba(15, 23, 42, 0.06)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
          <div>
            <span style={{ fontSize: '13px', fontWeight: '700', color: '#0284c7', textTransform: 'uppercase' }}>
              Bản đồ số tương tác Google Maps
            </span>
            <p style={{ margin: '2px 0 0', fontSize: '13px', color: '#64748b' }}>
              Nhấp trực tiếp lên bản đồ hoặc tìm kiếm địa điểm bên dưới để tự động xác định tọa độ.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '14px', fontSize: '12px', fontWeight: 600 }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#0284c7' }}></span> Điểm hiện có
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#ea580c' }}></span> Vị trí đang chọn
            </span>
          </div>
        </div>

        <div
          ref={mapContainerRef}
          style={{
            width: '100%',
            height: '360px',
            borderRadius: '8px',
            border: '1px solid #cbd5e1',
            zIndex: 1,
            backgroundColor: '#e5e7eb',
          }}
        />
      </div>

      <div className="manager-grid" style={{ marginTop: '20px', display: 'grid', gridTemplateColumns: '1fr 1.35fr', gap: '20px' }}>
        {/* Form Panel: Place Discovery & Content Form */}
        <div
          className="panel"
          style={{
            background: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '20px 22px',
            boxShadow: '0 4px 20px -2px rgba(15, 23, 42, 0.06)',
          }}
        >
          <div
            className="panel-heading"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '16px',
              borderBottom: '1px solid #f1f5f9',
              paddingBottom: '12px',
            }}
          >
            <div>
              <div style={{ fontSize: '11px', fontWeight: '700', color: '#0284c7', textTransform: 'uppercase' }}>
                THÔNG TIN ĐIỂM
              </div>
              <h2 style={{ margin: '4px 0 0', fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>
                {editingPoi ? 'Cập nhật điểm tham quan' : 'Thêm điểm tham quan mới'}
              </h2>
            </div>
            {editingPoi && (
              <Button variant="ghost" size="sm" onClick={handleStartCreate}>
                + Tạo mới
              </Button>
            )}
          </div>

          <form className="stack-form" onSubmit={(e) => handleSave(e, false)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '13px', fontWeight: '600', color: '#334155' }}>
              <span>Tên điểm tham quan *</span>
              <input
                required
                maxLength={255}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ví dụ: Bến Nhà Rồng, Chùa Một Cột, Dinh Độc Lập..."
                disabled={saving}
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                  outline: 'none',
                }}
              />
            </label>

            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '13px', fontWeight: '600', color: '#334155' }}>
              <span>Nội dung mô tả / thuyết minh ban đầu</span>
              <textarea
                maxLength={2000}
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Nhập nội dung thuyết minh lịch sử, nét đặc sắc hoặc địa chỉ..."
                disabled={saving}
                style={{
                  padding: '10px 12px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                  fontFamily: 'inherit',
                  outline: 'none',
                }}
              />
            </label>

            {/* GPS Coordinates Section */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <span style={{ fontSize: '13px', fontWeight: '600', color: '#334155' }}>
                  Tọa độ vị trí (GPS) *
                </span>
                <span style={{ fontSize: '11px', color: '#0284c7', fontWeight: 500 }}>
                  📍 Nhấp chuột trên bản đồ để tự động lấy tọa độ
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '12px', color: '#475569' }}>
                  <span>Vĩ độ (Latitude)</span>
                  <input
                    type="number"
                    step="any"
                    required
                    value={latitude}
                    onChange={(e) => setLatitude(e.target.value === '' ? '' : parseFloat(e.target.value))}
                    placeholder="10.7769"
                    disabled={saving}
                    style={{
                      height: '38px',
                      padding: '0 10px',
                      borderRadius: '6px',
                      border: '1px solid #cbd5e1',
                      fontSize: '13px',
                      outline: 'none',
                    }}
                  />
                </label>

                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '12px', color: '#475569' }}>
                  <span>Kinh độ (Longitude)</span>
                  <input
                    type="number"
                    step="any"
                    required
                    value={longitude}
                    onChange={(e) => setLongitude(e.target.value === '' ? '' : parseFloat(e.target.value))}
                    placeholder="106.7009"
                    disabled={saving}
                    style={{
                      height: '38px',
                      padding: '0 10px',
                      borderRadius: '6px',
                      border: '1px solid #cbd5e1',
                      fontSize: '13px',
                      outline: 'none',
                    }}
                  />
                </label>
              </div>
            </div>

            <div className="button-row" style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '6px' }}>
              <Button
                type="button"
                variant="primary"
                size="md"
                loading={saving}
                disabled={saving}
                onClick={(e) => handleSave(e, true)}
                style={{ backgroundColor: '#0284c7', borderColor: '#0284c7', height: '42px', fontWeight: 600 }}
              >
                ⚡ Lưu & Tạo tác vụ thuyết minh
              </Button>

              <div style={{ display: 'flex', gap: '8px' }}>
                <Button type="submit" variant="secondary" size="md" loading={saving} disabled={saving} style={{ flex: 1, height: '38px' }}>
                  {saving ? 'Đang lưu…' : editingPoi ? 'Lưu cập nhật' : 'Chỉ lưu điểm'}
                </Button>
                {editingPoi && (
                  <Button type="button" variant="ghost" size="md" onClick={handleStartCreate} disabled={saving} style={{ height: '38px' }}>
                    Hủy
                  </Button>
                )}
              </div>
            </div>
          </form>
        </div>

        {/* List Panel: POI Table */}
        <div
          className="panel"
          style={{
            background: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            padding: '20px 22px',
            boxShadow: '0 4px 20px -2px rgba(15, 23, 42, 0.06)',
          }}
        >
          <div
            className="panel-heading"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '16px',
              borderBottom: '1px solid #f1f5f9',
              paddingBottom: '12px',
              flexWrap: 'wrap',
              gap: '12px',
            }}
          >
            <div>
              <div style={{ fontSize: '11px', fontWeight: '700', color: '#64748b', textTransform: 'uppercase' }}>
                DANH SÁCH ĐIỂM
              </div>
              <h2 style={{ margin: '4px 0 0', fontSize: '17px', fontWeight: '700', color: '#0f172a' }}>
                Điểm tham quan đã lưu
              </h2>
            </div>
            <div style={{ width: '220px' }}>
              <SearchInput
                value={search}
                onChange={handleSearchChange}
                placeholder="Lọc theo tên điểm..."
              />
            </div>
          </div>

          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}>
              <Spinner size="lg" />
            </div>
          ) : pois.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#64748b' }}>
              Chưa có điểm tham quan nào. Hãy tìm kiếm địa điểm ở khung bên trái hoặc nhấn lên bản đồ để tạo điểm đầu tiên.
            </div>
          ) : (
            <>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ borderBottom: '1.5px solid #e2e8f0', color: '#475569', fontSize: '12px' }}>
                      <th style={{ padding: '8px 10px', width: '45px' }}>#</th>
                      <th style={{ padding: '8px 10px', width: '55px' }}>Mã QR</th>
                      <th style={{ padding: '8px 10px' }}>Tên điểm</th>
                      <th style={{ padding: '8px 10px' }}>Tọa độ (GPS)</th>
                      <th style={{ padding: '8px 10px', textAlign: 'right', width: '200px' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pois.map((poi, idx) => (
                      <tr
                        key={poi.poiId}
                        style={{
                          borderBottom: '1px solid #f1f5f9',
                          backgroundColor: editingPoi?.poiId === poi.poiId ? '#f0f9ff' : 'transparent',
                        }}
                      >
                        <td style={{ padding: '10px 10px', color: '#94a3b8', fontWeight: 600 }}>
                          {(page - 1) * 10 + idx + 1}
                        </td>
                        <td style={{ padding: '10px 10px' }}>
                          <QrThumbnail
                            value={poi.qrCode || poi.poiId}
                            qrCode={poi.qrCode}
                            poiId={poi.poiId}
                            onClick={() => setQrModalPoi(poi)}
                          />
                        </td>
                        <td style={{ padding: '10px 10px' }}>
                          <strong style={{ color: '#0f172a', display: 'block', fontSize: '13px' }}>
                            {poi.name}
                          </strong>
                          <span
                            style={{
                              fontSize: '12px',
                              color: '#64748b',
                              display: '-webkit-box',
                              WebkitLineClamp: 1,
                              WebkitBoxOrient: 'vertical',
                              overflow: 'hidden',
                            }}
                          >
                            {poi.description || 'Không có mô tả'}
                          </span>
                        </td>
                        <td style={{ padding: '10px 10px', fontSize: '12px' }}>
                          {poi.latitude != null && poi.longitude != null && !isNaN(poi.latitude) ? (
                            <span style={{ display: 'inline-flex', alignItems: 'center', background: '#e0f2fe', color: '#0369a1', padding: '3px 8px', borderRadius: '4px', fontWeight: 600 }}>
                              {poi.latitude.toFixed(4)}, {poi.longitude.toFixed(4)}
                            </span>
                          ) : (
                            <span style={{ color: '#94a3b8' }}>Chưa có tọa độ</span>
                          )}
                        </td>
                        <td style={{ padding: '10px 10px', textAlign: 'right' }}>
                          {/* 3 Explicit Rectangular Buttons with Clear Vietnamese Text */}
                          <div style={{ display: 'inline-flex', gap: '6px' }}>
                            <button
                              type="button"
                              onClick={() => setPreviewPoiId(poi.poiId)}
                              title="Nghe thử thuyết minh đa ngôn ngữ"
                              style={{
                                backgroundColor: '#E0F2FE',
                                color: '#0369A1',
                                border: '1px solid #BAE6FD',
                                borderRadius: '6px',
                                padding: '5px 12px',
                                fontWeight: 700,
                                fontSize: '12px',
                                cursor: 'pointer',
                              }}
                            >
                              Phát
                            </button>
                            <button
                              type="button"
                              onClick={() => handleStartEdit(poi)}
                              title="Chỉnh sửa điểm tham quan"
                              style={{
                                backgroundColor: '#F1F5F9',
                                color: '#334155',
                                border: '1px solid #CBD5E1',
                                borderRadius: '6px',
                                padding: '5px 12px',
                                fontWeight: 700,
                                fontSize: '12px',
                                cursor: 'pointer',
                              }}
                            >
                              Sửa
                            </button>
                            <button
                              type="button"
                              onClick={() => setDeletingPoi(poi)}
                              title="Xóa điểm tham quan"
                              style={{
                                backgroundColor: '#FEE2E2',
                                color: '#DC2626',
                                border: '1px solid #FECACA',
                                borderRadius: '6px',
                                padding: '5px 12px',
                                fontWeight: 700,
                                fontSize: '12px',
                                cursor: 'pointer',
                              }}
                            >
                              Xóa
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '16px' }}>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={page <= 1}
                    onClick={() => loadPois(page - 1)}
                  >
                    ← Trước
                  </Button>
                  <span style={{ display: 'flex', alignItems: 'center', padding: '0 8px', fontSize: '13px', fontWeight: 600 }}>
                    Trang {page} / {totalPages}
                  </span>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={page >= totalPages}
                    onClick={() => loadPois(page + 1)}
                  >
                    Sau →
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* QR Modal */}
      {qrModalPoi && (
        <QrCodeModal
          poi={qrModalPoi}
          onClose={() => setQrModalPoi(null)}
        />
      )}

      {/* Audio Preview Modal */}
      {previewPoiId && (
        <AudioPreviewModal
          poiId={previewPoiId}
          onClose={() => setPreviewPoiId(null)}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deletingPoi && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            backdropFilter: 'blur(4px)',
          }}
          onClick={() => setDeletingPoi(null)}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '12px',
              padding: '24px',
              maxWidth: '420px',
              width: '90%',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ margin: '0 0 8px 0', fontSize: '18px', fontWeight: 700, color: '#dc2626' }}>
              Xác nhận xóa điểm tham quan
            </h3>
            <p style={{ margin: '0 0 18px 0', fontSize: '14px', color: '#475569', lineHeight: 1.5 }}>
              Bạn có chắc chắn muốn xóa điểm <strong>"{deletingPoi.name}"</strong>? Thao tác này không thể hoàn tác nếu điểm chưa có nội dung ràng buộc.
            </p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <Button
                variant="secondary"
                size="md"
                onClick={() => setDeletingPoi(null)}
                disabled={deleting}
              >
                Hủy bỏ
              </Button>
              <Button
                variant="danger"
                size="md"
                loading={deleting}
                disabled={deleting}
                onClick={handleConfirmDelete}
              >
                Xác nhận Xóa
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
