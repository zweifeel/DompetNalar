import React from 'react';
import { getAdminRoleLabel } from '../config/adminAuth';
import { TARGET_PROJECT_ID } from '../config/firebase';

export default function Navbar({ user, onSignOut }) {
  const roleLabel = getAdminRoleLabel(user?.email);

  return (
    <header style={styles.header}>
      <div style={styles.brandGroup}>
        <div style={styles.logoBadge}>
          <span style={{ fontSize: '1.25rem' }}>🪙</span>
        </div>
        <div>
          <div style={styles.brandTitle}>
            FinSmart <span style={styles.brandSub}>Console</span>
          </div>
          <div style={styles.brandMeta}>SleepyKoala • Developer Admin</div>
        </div>
        <div style={styles.projectPill}>
          <span style={styles.statusDot}></span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>{TARGET_PROJECT_ID}</span>
        </div>
      </div>

      <div style={styles.userGroup}>
        <div style={styles.userDetails}>
          <div style={styles.userEmail}>{user?.email}</div>
          <div style={styles.userRoleBadge}>{roleLabel}</div>
        </div>

        {user?.photoURL ? (
          <img
            src={user.photoURL}
            alt={user.displayName || 'Admin'}
            style={styles.avatar}
            onError={(e) => { e.target.style.display = 'none'; }}
          />
        ) : (
          <div style={styles.avatarPlaceholder}>
            {(user?.email?.[0] || 'A').toUpperCase()}
          </div>
        )}

        <button onClick={onSignOut} style={styles.signOutBtn} title="Keluar dari sesi administrator">
          Keluar
        </button>
      </div>
    </header>
  );
}

const styles = {
  header: {
    height: '70px',
    background: 'var(--bg-surface)',
    borderBottom: '1px solid var(--border-card)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 24px',
    position: 'sticky',
    top: 0,
    zIndex: 100,
  },
  brandGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '14px',
  },
  logoBadge: {
    width: '40px',
    height: '40px',
    borderRadius: 'var(--radius-md)',
    background: 'linear-gradient(135deg, #0B57D0, #1D4ED8)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: 'var(--shadow-glow)',
  },
  brandTitle: {
    fontSize: '1.05rem',
    fontWeight: '800',
    letterSpacing: '-0.02em',
    color: 'var(--text-primary)',
  },
  brandSub: {
    color: 'var(--color-primary-light)',
    fontWeight: '600',
  },
  brandMeta: {
    fontSize: '0.72rem',
    color: 'var(--text-secondary)',
  },
  projectPill: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    padding: '4px 10px',
    borderRadius: 'var(--radius-full)',
    background: 'rgba(16, 185, 129, 0.1)',
    border: '1px solid rgba(16, 185, 129, 0.25)',
    color: '#34D399',
    fontSize: '0.75rem',
    fontWeight: '600',
    marginLeft: '12px',
  },
  statusDot: {
    width: '6px',
    height: '6px',
    borderRadius: '50%',
    background: '#10B981',
    boxShadow: '0 0 8px #10B981',
  },
  userGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '14px',
  },
  userDetails: {
    textAlign: 'right',
  },
  userEmail: {
    fontSize: '0.85rem',
    fontWeight: '600',
    color: 'var(--text-primary)',
  },
  userRoleBadge: {
    fontSize: '0.7rem',
    color: 'var(--color-gold)',
    fontWeight: '500',
  },
  avatar: {
    width: '36px',
    height: '36px',
    borderRadius: '50%',
    border: '2px solid var(--border-highlight)',
  },
  avatarPlaceholder: {
    width: '36px',
    height: '36px',
    borderRadius: '50%',
    background: 'var(--color-primary-dark)',
    color: '#FFFFFF',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: '700',
    fontSize: '0.9rem',
    border: '2px solid var(--border-highlight)',
  },
  signOutBtn: {
    padding: '7px 14px',
    borderRadius: 'var(--radius-md)',
    background: 'rgba(239, 68, 68, 0.12)',
    color: '#F87171',
    border: '1px solid rgba(239, 68, 68, 0.25)',
    fontSize: '0.8rem',
    fontWeight: '600',
    transition: 'all 0.15s ease',
  },
};
