import React, { useEffect, useState } from 'react';
import QRCode from 'qrcode';

interface QrThumbnailProps {
  value?: string;
  size?: number;
  onClick?: () => void;
  title?: string;
}

export const QrThumbnail: React.FC<QrThumbnailProps> = ({
  value,
  size = 56,
  onClick,
  title = 'Nhấn để xem / tải mã QR',
}) => {
  const [dataUrl, setDataUrl] = useState<string>('');

  const publicBaseUrl = (
    import.meta.env.VITE_PUBLIC_BASE_URL ||
    (typeof window !== 'undefined' ? window.location.origin : '')
  ).replace(/\/+$/, '');

  const listenUrl = value ? `${publicBaseUrl}/listen/${encodeURIComponent(value)}` : '';

  useEffect(() => {
    if (!value || !listenUrl) {
      setDataUrl('');
      return;
    }

    let isMounted = true;
    QRCode.toDataURL(listenUrl, {
      width: size * 2,
      margin: 1,
      errorCorrectionLevel: 'M',
    })
      .then((url) => {
        if (isMounted) setDataUrl(url);
      })
      .catch(() => {});

    return () => {
      isMounted = false;
    };
  }, [listenUrl, size]);

  return (
    <div
      onClick={onClick}
      title={title}
      style={{
        width: size,
        height: size,
        minWidth: size,
        border: '1px solid var(--border-line)',
        borderRadius: '4px',
        overflow: 'hidden',
        cursor: onClick ? 'pointer' : 'default',
        backgroundColor: '#ffffff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
      }}
    >
      {dataUrl ? (
        <img
          src={dataUrl}
          alt={`Mã QR ${value}`}
          style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        />
      ) : (
        <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>QR</span>
      )}
    </div>
  );
};
