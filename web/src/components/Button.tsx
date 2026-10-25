import React from 'react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md';
  icon?: React.ReactNode;
  loading?: boolean;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'secondary',
  size = 'md',
  icon,
  loading = false,
  disabled = false,
  className = '',
  style,
  ...props
}) => {
  const isSm = size === 'sm';
  const minHeight = isSm ? '32px' : '36px';
  const padding = isSm ? '4px 10px' : '6px 14px';
  const fontSize = isSm ? '13px' : '14px';

  let bg = '#ffffff';
  let color = '#2d3748';
  let border = '1px solid #cbd5e0';
  let hoverBg = '#f7fafc';

  if (variant === 'primary') {
    bg = '#1a365d';
    color = '#ffffff';
    border = '1px solid #1a365d';
    hoverBg = '#2a4365';
  } else if (variant === 'danger') {
    bg = '#e53e3e';
    color = '#ffffff';
    border = '1px solid #e53e3e';
    hoverBg = '#c53030';
  } else if (variant === 'ghost') {
    bg = 'transparent';
    color = '#2b6cb0';
    border = '1px solid transparent';
    hoverBg = '#edf2f7';
  }

  const [isHovered, setIsHovered] = React.useState(false);

  return (
    <button
      className={`btn btn-${variant} btn-${size} ${className}`.trim()}
      disabled={disabled || loading}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '6px',
        minHeight,
        padding,
        fontSize,
        fontFamily: 'inherit',
        fontWeight: 500,
        borderRadius: '4px',
        border,
        backgroundColor: disabled ? '#e2e8f0' : isHovered ? hoverBg : bg,
        color: disabled ? '#a0aec0' : color,
        cursor: disabled || loading ? 'not-allowed' : 'pointer',
        boxSizing: 'border-box',
        transition: 'all 0.15s ease-in-out',
        textDecoration: 'none',
        lineHeight: 1.2,
        outline: 'none',
        whiteSpace: 'nowrap',
        ...style,
      }}
      {...props}
    >
      {loading ? (
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{ animation: 'spin 1s linear infinite' }}
        >
          <circle cx="12" cy="12" r="10" strokeDasharray="32" strokeDashoffset="12" />
        </svg>
      ) : (
        icon && <span style={{ display: 'inline-flex', alignItems: 'center' }}>{icon}</span>
      )}
      {children}
    </button>
  );
};
