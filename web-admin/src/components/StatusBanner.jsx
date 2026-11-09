import React from 'react';

export default function StatusBanner({
  type = 'info', // 'info' | 'warning' | 'success' | 'danger'
  title,
  children,
  actionText,
  onAction,
}) {
  const typeStyles = {
    info: {
      bg: 'var(--color-info-bg)',
      border: 'rgba(59, 130, 246, 0.3)',
      text: '#93C5FD',
      icon: 'ℹ️',
    },
    warning: {
      bg: 'var(--color-warning-bg)',
      border: 'rgba(245, 158, 11, 0.3)',
      text: '#FDE68A',
      icon: '⚠️',
    },
    success: {
      bg: 'var(--color-success-bg)',
      border: 'rgba(16, 185, 129, 0.3)',
      text: '#A7F3D0',
      icon: '✅',
    },
    danger: {
      bg: 'var(--color-danger-bg)',
      border: 'rgba(239, 68, 68, 0.3)',
      text: '#FECACA',
      icon: '🛑',
    },
  }[type] || typeStyles.info;

  return (
    <div
      style={{
        background: typeStyles.bg,
        border: `1px solid ${typeStyles.border}`,
        borderRadius: 'var(--radius-lg)',
        padding: '16px 20px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: '14px',
        marginBottom: '20px',
      }}
    >
      <span style={{ fontSize: '1.25rem', lineHeight: 1 }}>{typeStyles.icon}</span>
      <div style={{ flex: 1 }}>
        {title && (
          <h4
            style={{
              fontSize: '0.95rem',
              fontWeight: '700',
              color: typeStyles.text,
              marginBottom: '4px',
            }}
          >
            {title}
          </h4>
        )}
        <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
          {children}
        </div>
      </div>
      {actionText && onAction && (
        <button
          onClick={onAction}
          style={{
            alignSelf: 'center',
            padding: '6px 14px',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(255, 255, 255, 0.1)',
            color: 'var(--text-primary)',
            fontSize: '0.8rem',
            fontWeight: '600',
            whiteSpace: 'nowrap',
          }}
        >
          {actionText}
        </button>
      )}
    </div>
  );
}
