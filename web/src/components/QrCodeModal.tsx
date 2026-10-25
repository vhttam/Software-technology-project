import React, { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { PoiItem } from '../api/types';

interface QrCodeModalProps {
  poi: PoiItem | null;
  onClose: () => void;
}

export const QrCodeModal: React.FC<QrCodeModalProps> = ({ poi, onClose }) => {
  const [dataUrl, setDataUrl] = useState<string>('');
  const [svgString, setSvgString] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const publicBaseUrl = (
    import.meta.env.VITE_PUBLIC_BASE_URL ||
    (typeof window !== 'undefined' ? window.location.origin : '')
  ).replace(/\/+$/, '');

  const qrCodeValue = poi?.qrCode || poi?.poiId || '';
  const listenUrl = qrCodeValue ? `${publicBaseUrl}/?qr=${encodeURIComponent(qrCodeValue)}` : '';

  useEffect(() => {
    if (!poi || !listenUrl) return;

    let isMounted = true;
    setError(null);

    // Generate PNG Data URL (Large: 320px)
    QRCode.toDataURL(listenUrl, {
      width: 320,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
      .then((url) => {
        if (isMounted) setDataUrl(url);
      })
      .catch((err) => {
        if (isMounted) setError('Không thể tạo mã QR PNG: ' + String(err));
      });

    // Generate SVG string
    QRCode.toString(listenUrl, {
      type: 'svg',
      margin: 2,
      errorCorrectionLevel: 'M',
    })
      .then((svg) => {
        if (isMounted) setSvgString(svg);
      })
      .catch((err) => {
        if (isMounted) setError('Không thể tạo mã QR SVG: ' + String(err));
      });

    return () => {
      isMounted = false;
    };
  }, [poi, listenUrl]);

  if (!poi) return null;

  const handleDownloadPng = () => {
    if (!dataUrl) return;
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = `QRCode_${qrCodeValue}_${poi.name.replace(/\s+/g, '_')}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const handleDownloadSvg = () => {
    if (!svgString) return;
    const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = `QRCode_${qrCodeValue}_${poi.name.replace(/\s+/g, '_')}.svg`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
  };

  const handlePrint = () => {
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;

    printWindow.document.write(`
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="utf-8" />
          <title>In mã QR - ${poi.name}</title>
          <style>
            body {
              font-family: 'Plus Jakarta Sans', 'Inter', -apple-system, sans-serif;
              text-align: center;
              padding: 40px;
              color: #1e293b;
            }
            .title {
              font-size: 26px;
              font-weight: bold;
              margin-bottom: 8px;
            }
            .desc {
              font-size: 14px;
              color: #475569;
              margin-bottom: 20px;
            }
            .qr-image {
              width: 320px;
              height: 320px;
              margin: 0 auto 16px;
              border: 1px solid #cbd5e1;
              padding: 10px;
            }
            .url {
              font-size: 13px;
              font-family: monospace;
              color: #64748b;
              word-break: break-all;
            }
          </style>
        </head>
        <body>
          <div class="title">${poi.name}</div>
          <div class="desc">Quét mã QR để nghe thuyết minh tự động đa ngôn ngữ</div>
          <img class="qr-image" src="${dataUrl}" alt="QR Code" />
          <div class="url">${listenUrl}</div>
          <script>
            window.onload = function() {
              window.focus();
              window.print();
              setTimeout(function() { window.close(); }, 500);
            };
          </script>
        </body>
      </html>
    `);
    printWindow.document.close();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-dialog"
        style={{ maxWidth: '520px' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h3>Mã QR Điểm tham quan</h3>
          <button
            type="button"
            className="btn-link"
            onClick={onClose}
            style={{ fontSize: '18px', cursor: 'pointer' }}
          >
            ✕
          </button>
        </div>

        <div className="modal-body" style={{ alignItems: 'center', textAlign: 'center' }}>
          {error && <div className="alert alert-danger">{error}</div>}

          <div>
            <h2 style={{ margin: '0 0 6px 0', fontSize: '20px', color: 'var(--text-main)' }}>
              {poi.name}
            </h2>
            <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-muted)' }}>
              Mã nhận diện QR: <code>{poi.qrCode}</code>
            </p>
          </div>

          <div
            style={{
              padding: '16px',
              backgroundColor: '#ffffff',
              border: '1px solid var(--border-line)',
              borderRadius: '8px',
              boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            }}
          >
            {dataUrl ? (
              <img
                src={dataUrl}
                alt={`Mã QR ${poi.name}`}
                style={{ width: '260px', height: '260px', display: 'block' }}
              />
            ) : (
              <div
                style={{
                  width: '260px',
                  height: '260px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--text-muted)',
                }}
              >
                Đang tạo mã QR…
              </div>
            )}
          </div>

          <div
            style={{
              width: '100%',
              padding: '8px 12px',
              backgroundColor: '#f8fafc',
              border: '1px solid var(--border-light)',
              borderRadius: '4px',
              fontSize: '12px',
              color: 'var(--text-muted)',
              wordBreak: 'break-all',
              textAlign: 'left',
            }}
          >
            <strong>URL quét mã:</strong>
            <div style={{ fontFamily: 'monospace', marginTop: '4px', color: 'var(--primary)' }}>
              {listenUrl}
            </div>
          </div>
        </div>

        <div className="modal-footer" style={{ justifyContent: 'space-between' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Đóng
          </button>

          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handlePrint}
              disabled={!dataUrl}
            >
              🖨 In mã QR
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleDownloadSvg}
              disabled={!svgString}
            >
              ⬇ Tải SVG
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleDownloadPng}
              disabled={!dataUrl}
            >
              ⬇ Tải PNG
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
