import React from 'react';

export default function MetricPlaceholderCard({
  icon,
  title,
  subtitle,
  badgeText,
  badgeType = 'neutral', // 'success' | 'warning' | 'info' | 'neutral'
  phaseNote = 'Fase 3/4',
  description,
}) {
  return (
    <div style={styles.card}>
      <div style={styles.header}>
        <div style={styles.iconBox}>{icon}</div>
        <span className={`badge badge-${badgeType}`}>{badgeText}</span>
      </div>

      <div style={styles.body}>
        <div style={styles.title}>{title}</div>
        <div style={styles.subtitle}>{subtitle}</div>
        <div style={styles.placeholderBox}>
          <span style={styles.phaseTag}>🔒 {phaseNote}</span>
          <span style={styles.placeholderText}>{description}</span>
        </div>
      </div>
    </div>
  );
}

const styles = {
  card: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-lg)',
    padding: '20px',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    transition: 'border-color 0.2s ease',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: '16px',
  },
  iconBox: {
    width: '42px',
    height: '42px',
    borderRadius: 'var(--radius-md)',
    background: 'rgba(59, 130, 246, 0.1)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '1.25rem',
  },
  body: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  title: {
    fontSize: '0.9rem',
    fontWeight: '600',
    color: 'var(--text-secondary)',
  },
  subtitle: {
    fontSize: '1.35rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    letterSpacing: '-0.02em',
  },
  placeholderBox: {
    marginTop: '10px',
    padding: '10px 12px',
    borderRadius: 'var(--radius-md)',
    background: 'rgba(15, 23, 42, 0.6)',
    border: '1px dashed var(--border-card)',
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  phaseTag: {
    fontSize: '0.7rem',
    fontWeight: '700',
    color: 'var(--color-gold)',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  placeholderText: {
    fontSize: '0.78rem',
    color: 'var(--text-muted)',
    lineHeight: 1.4,
  },
};
