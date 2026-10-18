import React from 'react';

interface SpinnerProps {
  label?: string;
}

export const Spinner: React.FC<SpinnerProps> = ({ label = 'Đang tải dữ liệu…' }) => {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '12px',
        padding: '30px',
        color: '#65756a',
        fontSize: '13px',
      }}
      role="status"
    >
      <span
        style={{
          width: '18px',
          height: '18px',
          border: '2px solid #cbd7cd',
          borderTopColor: '#386653',
          borderRadius: '50%',
          display: 'inline-block',
          animation: 'spin 0.8s linear infinite',
        }}
      />
      <span>{label}</span>
      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};
