import React, { useState, useEffect } from 'react';

export interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  style?: React.CSSProperties;
  inputStyle?: React.CSSProperties;
  debounceMs?: number;
  onSearch?: (value: string) => void;
  disabled?: boolean;
  'aria-label'?: string;
}

export const SearchInput: React.FC<SearchInputProps> = ({
  value,
  onChange,
  placeholder = 'Tìm kiếm...',
  className = '',
  style,
  inputStyle,
  debounceMs = 0,
  onSearch,
  disabled = false,
  'aria-label': ariaLabel = 'Tìm kiếm',
}) => {
  const [innerValue, setInnerValue] = useState(value);

  useEffect(() => {
    setInnerValue(value);
  }, [value]);

  useEffect(() => {
    if (debounceMs <= 0) return;
    const timer = setTimeout(() => {
      if (innerValue !== value) {
        onChange(innerValue);
      }
    }, debounceMs);
    return () => clearTimeout(timer);
  }, [innerValue, debounceMs, onChange, value]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const next = e.target.value;
    setInnerValue(next);
    if (debounceMs <= 0) {
      onChange(next);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      if (debounceMs > 0 && innerValue !== value) {
        onChange(innerValue);
      }
      onSearch?.(innerValue);
    }
  };

  return (
    <div
      className={`search-input-container ${className}`.trim()}
      style={{
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        width: '100%',
        maxWidth: '360px',
        ...style,
      }}
    >
      <input
        type="text"
        value={innerValue}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled}
        aria-label={ariaLabel}
        style={{
          width: '100%',
          height: '40px',
          padding: '0 38px 0 12px',
          fontSize: '14px',
          fontFamily: 'inherit',
          color: '#2d3748',
          backgroundColor: disabled ? '#f7fafc' : '#ffffff',
          border: '1px solid #cbd5e0',
          borderRadius: '4px',
          outline: 'none',
          boxSizing: 'border-box',
          lineHeight: '40px',
          transition: 'border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out',
          ...inputStyle,
        }}
        onFocus={(e) => {
          e.currentTarget.style.borderColor = '#1a365d';
          e.currentTarget.style.boxShadow = '0 0 0 1px #1a365d';
        }}
        onBlur={(e) => {
          e.currentTarget.style.borderColor = '#cbd5e0';
          e.currentTarget.style.boxShadow = 'none';
        }}
      />
      <span
        style={{
          position: 'absolute',
          right: '12px',
          top: '50%',
          transform: 'translateY(-50%)',
          pointerEvents: 'none',
          color: '#718096',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: '18px',
          height: '18px',
        }}
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
      </span>
    </div>
  );
};
