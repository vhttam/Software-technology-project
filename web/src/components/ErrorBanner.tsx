import React, { useState } from 'react';
import { ApiError } from '../api/types';

interface ErrorBannerProps {
  error: unknown;
  onDismiss?: () => void;
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({ error, onDismiss }) => {
  const [copied, setCopied] = useState(false);
  if (!error) return null;

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  if (error instanceof ApiError) {
    const detailsObj = error.details;
    const hasDetails = detailsObj && typeof detailsObj === 'object' && Object.keys(detailsObj).length > 0;
    const hasTechnicalInfo = Boolean(error.errorCode || error.correlationId || hasDetails);

    return (
      <div
        role="alert"
        style={{
          background: '#fff0ed',
          border: '1px solid #f2c0b6',
          borderRadius: '8px',
          padding: '14px 18px',
          margin: '16px 0',
          color: '#8b2e24',
          position: 'relative',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1 }}>
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#c53030"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{ flexShrink: 0 }}
            >
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span style={{ fontWeight: 500, fontSize: '14px' }}>{error.message}</span>
          </div>
          {onDismiss && (
            <button
              type="button"
              onClick={onDismiss}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                fontSize: '18px',
                color: '#8b2e24',
                lineHeight: 1,
                padding: '0 4px',
              }}
              aria-label="Đóng thông báo lỗi"
            >
              ×
            </button>
          )}
        </div>

        {hasTechnicalInfo && (
          <details style={{ marginTop: '10px', fontSize: '12px' }}>
            <summary style={{ cursor: 'pointer', color: '#a2554b', fontWeight: 500, outline: 'none' }}>
              Chi tiết kỹ thuật
            </summary>
            <div
              style={{
                marginTop: '8px',
                background: '#fdf4f2',
                border: '1px solid #fae1dd',
                padding: '10px 12px',
                borderRadius: '6px',
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
              }}
            >
              {error.errorCode && (
                <div>
                  <span style={{ color: '#718096' }}>Mã lỗi: </span>
                  <code>{error.errorCode}</code>
                </div>
              )}
              {error.correlationId && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                  <span style={{ color: '#718096' }}>Correlation ID: </span>
                  <code>{error.correlationId}</code>
                  <button
                    type="button"
                    onClick={() => handleCopy(error.correlationId || '')}
                    style={{
                      padding: '2px 8px',
                      fontSize: '11px',
                      background: '#fff',
                      border: '1px solid #cbd5e0',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      color: '#2d3748',
                    }}
                  >
                    {copied ? '✓ Đã sao chép' : 'Sao chép'}
                  </button>
                </div>
              )}
              {hasDetails && (
                <div style={{ marginTop: '4px' }}>
                  <div style={{ fontWeight: 600, color: '#718096', marginBottom: '2px' }}>Dữ liệu chi tiết:</div>
                  <pre
                    style={{
                      margin: 0,
                      padding: '6px 8px',
                      background: '#fff',
                      border: '1px solid #eee',
                      borderRadius: '4px',
                      whiteSpace: 'pre-wrap',
                      fontFamily: 'monospace',
                      fontSize: '11px',
                    }}
                  >
                    {JSON.stringify(detailsObj, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          </details>
        )}
      </div>
    );
  }

  const message = error instanceof Error ? error.message : String(error);
  return (
    <div
      role="alert"
      style={{
        background: '#fff0ed',
        border: '1px solid #f2c0b6',
        borderRadius: '8px',
        padding: '14px 18px',
        margin: '16px 0',
        color: '#8b2e24',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
      }}
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="#c53030"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        style={{ flexShrink: 0 }}
      >
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <span>{message}</span>
    </div>
  );
};
