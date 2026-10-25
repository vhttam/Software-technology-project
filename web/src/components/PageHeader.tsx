import React from 'react';

interface PageHeaderProps {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  eyebrow,
  title,
  description,
  action,
}) => {
  return (
    <div
      className="page-header"
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        marginBottom: '24px',
        flexWrap: 'wrap',
        gap: '16px',
      }}
    >
      <div>
        {eyebrow && (
          <div
            className="eyebrow"
            style={{
              fontSize: '12px',
              fontWeight: 700,
              color: '#64748b',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '4px',
            }}
          >
            {eyebrow}
          </div>
        )}
        <h1
          style={{
            margin: '0 0 6px 0',
            fontSize: '24px',
            fontWeight: 700,
            color: 'var(--text-main, #0f172a)',
            lineHeight: 1.25,
          }}
        >
          {title}
        </h1>
        {description && (
          <p
            style={{
              margin: 0,
              color: 'var(--text-muted, #64748b)',
              fontSize: '14px',
              lineHeight: 1.5,
            }}
          >
            {description}
          </p>
        )}
      </div>

      {action && (
        <div
          className="page-header-actions"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            alignSelf: 'center',
          }}
        >
          {action}
        </div>
      )}
    </div>
  );
};
