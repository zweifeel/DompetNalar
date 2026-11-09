import React from 'react';
import { PRIMARY_ADMIN_EMAIL, PROJECT_OWNER_EMAIL } from '../config/adminAuth';

export default function UnauthorizedScreen({ user, onSignOut }) {
  return (
    <div style={styles.wrapper}>
      <div style={styles.card}>
        <div style={styles.iconBadge}>🛑</div>
        <h2 style={styles.title}>Akses Administrator Ditolak</h2>
        <p style={styles.subtitle}>
          Akun Google yang Anda gunakan tidak memiliki hak akses ke FinSmart Developer Panel.
        </p>

        <div style={styles.accountBox}>
          <div style={styles.accountLabel}>Akun Terdeteksi:</div>
          <div style={styles.accountEmail}>{user?.email || '(Email tidak diketahui)'}</div>
          <span className="badge badge-danger" style={{ marginTop: '6px' }}>
            Bukan Administrator Resmi
          </span>
        </div>

        <div style={styles.infoBox}>
          <div style={styles.infoTitle}>📋 Informasi Hak Akses:</div>
          <p style={styles.infoText}>
            Panel ini hanya dapat diakses oleh administrator resmi SleepyKoala:
          </p>
          <ul style={styles.list}>
            <li>
              Developer Utama: <code>{PRIMARY_ADMIN_EMAIL}</code>
            </li>
            <li>
              Project Owner: <code>{PROJECT_OWNER_EMAIL}</code>
            </li>
          </ul>
        </div>

        <div style={styles.actionSection}>
          <button onClick={onSignOut} style={styles.signOutBtn}>
            Keluar & Gunakan Akun Lain
          </button>
        </div>
      </div>
    </div>
  );
}

const styles = {
  wrapper: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '24px',
    background: 'radial-gradient(circle at 50% 20%, #131B2E 0%, #0B0F19 80%)',
  },
  card: {
    width: '100%',
    maxWidth: '500px',
    background: 'var(--bg-card)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: 'var(--radius-xl)',
    padding: '36px 32px',
    textAlign: 'center',
    boxShadow: 'var(--shadow-lg)',
  },
  iconBadge: {
    fontSize: '3rem',
    marginBottom: '16px',
    lineHeight: 1,
  },
  title: {
    fontSize: '1.35rem',
    fontWeight: '800',
    color: '#F87171',
    letterSpacing: '-0.02em',
    marginBottom: '8px',
  },
  subtitle: {
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.5,
    marginBottom: '20px',
  },
  accountBox: {
    background: 'rgba(15, 23, 42, 0.8)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-md)',
    padding: '14px',
    marginBottom: '20px',
  },
  accountLabel: {
    fontSize: '0.72rem',
    color: 'var(--text-muted)',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    marginBottom: '4px',
  },
  accountEmail: {
    fontSize: '0.95rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
    fontFamily: 'var(--font-mono)',
  },
  infoBox: {
    textAlign: 'left',
    background: 'rgba(59, 130, 246, 0.08)',
    border: '1px solid rgba(59, 130, 246, 0.2)',
    borderRadius: 'var(--radius-md)',
    padding: '14px 16px',
    marginBottom: '24px',
  },
  infoTitle: {
    fontSize: '0.8rem',
    fontWeight: '700',
    color: '#93C5FD',
    marginBottom: '6px',
  },
  infoText: {
    fontSize: '0.78rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.4,
    marginBottom: '6px',
  },
  list: {
    paddingLeft: '18px',
    fontSize: '0.78rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.5,
  },
  actionSection: {
    display: 'flex',
    justifyContent: 'center',
  },
  signOutBtn: {
    padding: '12px 24px',
    borderRadius: 'var(--radius-lg)',
    background: 'var(--color-primary)',
    color: '#FFFFFF',
    fontSize: '0.88rem',
    fontWeight: '700',
    transition: 'all 0.15s ease',
  },
};
