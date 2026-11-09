import React, { useState } from 'react';
import Navbar from './Navbar';
import { APP_RELEASE_CONFIG } from '../config/appReleaseConfig';

export default function AdminShell({ user, onSignOut, activeTab: propActiveTab, onTabChange, children }) {
  const [localActiveTab, setLocalActiveTab] = useState('dashboard');
  const activeTab = propActiveTab !== undefined ? propActiveTab : localActiveTab;

  const handleTabClick = (tabId) => {
    if (onTabChange) {
      onTabChange(tabId);
    } else {
      setLocalActiveTab(tabId);
    }
  };

  const navItems = [
    { id: 'dashboard', label: 'Dashboard Ringkasan', icon: '📊', phase: 'Aktif' },
    { id: 'feedback', label: 'Pusat Feedback & Bug', icon: '📥', phase: 'Aktif' },
    { id: 'membership', label: 'Smart Master Grants', icon: '👑', phase: 'Aktif' },
    { id: 'integrations', label: 'Status Integrasi Sistem', icon: '🔌', phase: 'Aktif' },
    { id: 'settings', label: 'Pengaturan & Audit Log', icon: '⚙️', phase: 'Fase 5' },
  ];

  return (
    <div style={styles.container}>
      <Navbar user={user} onSignOut={onSignOut} />

      <div style={styles.mainLayout}>
        {/* Sidebar Navigasi */}
        <aside style={styles.sidebar}>
          <div style={styles.sidebarSectionLabel}>NAVIGASI UTAMA</div>
          <nav style={styles.navList}>
            {navItems.map((item) => {
              const isSelected = activeTab === item.id;

              return (
                <button
                  key={item.id}
                  onClick={() => handleTabClick(item.id)}
                  style={{
                    ...styles.navButton,
                    ...(isSelected ? styles.navButtonActive : {}),
                  }}
                  title={item.label}
                >
                  <span style={styles.navIcon}>{item.icon}</span>
                  <span style={styles.navLabel}>{item.label}</span>
                  <span
                    className={`badge ${isSelected ? 'badge-info' : 'badge-neutral'}`}
                    style={{ marginLeft: 'auto', fontSize: '0.65rem' }}
                  >
                    {item.phase}
                  </span>
                </button>
              );
            })}
          </nav>

          <div style={styles.sidebarFooter}>
            <div style={styles.buildInfoTitle}>STATUS LINGKUNGAN</div>
            <div style={styles.buildInfoItem}>
              <span>Status:</span> <strong style={{ color: '#34D399' }}>{APP_RELEASE_CONFIG.phaseStatus}</strong>
            </div>
            <div style={styles.buildInfoItem}>
              <span>Target Rilis:</span> <span style={{ fontWeight: '600', color: '#93C5FD' }}>{APP_RELEASE_CONFIG.configuredAppReleaseVersion}</span>
            </div>
            <div style={styles.buildInfoItem}>
              <span>Database:</span> <span>{APP_RELEASE_CONFIG.targetDatabaseSchema}</span>
            </div>
            <div style={styles.buildInfoItem}>
              <span>Web Admin:</span> <span style={{ color: 'var(--text-muted)' }}>{APP_RELEASE_CONFIG.webAdminVersion}</span>
            </div>
          </div>
        </aside>

        {/* Konten Utama */}
        <main style={styles.content}>
          {children}
        </main>
      </div>
    </div>
  );
}

const styles = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
    background: 'var(--bg-app)',
  },
  mainLayout: {
    display: 'flex',
    flex: 1,
  },
  sidebar: {
    width: '270px',
    background: 'var(--bg-surface)',
    borderRight: '1px solid var(--border-card)',
    padding: '24px 16px',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    minHeight: 'calc(100vh - 70px)',
  },
  sidebarSectionLabel: {
    fontSize: '0.7rem',
    fontWeight: '800',
    color: 'var(--text-muted)',
    letterSpacing: '0.08em',
    marginBottom: '12px',
    paddingLeft: '10px',
  },
  navList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  navButton: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    padding: '10px 14px',
    borderRadius: 'var(--radius-md)',
    color: 'var(--text-secondary)',
    fontSize: '0.85rem',
    fontWeight: '600',
    textAlign: 'left',
    transition: 'all 0.15s ease',
  },
  navButtonActive: {
    background: 'rgba(11, 87, 208, 0.18)',
    color: '#93C5FD',
    border: '1px solid rgba(59, 130, 246, 0.35)',
  },
  navButtonDisabled: {
    opacity: 0.65,
    cursor: 'not-allowed',
  },
  navIcon: {
    fontSize: '1.1rem',
  },
  navLabel: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  sidebarFooter: {
    marginTop: 'auto',
    padding: '14px',
    borderRadius: 'var(--radius-md)',
    background: 'rgba(15, 23, 42, 0.8)',
    border: '1px solid var(--border-card)',
    fontSize: '0.75rem',
  },
  buildInfoTitle: {
    fontSize: '0.68rem',
    fontWeight: '700',
    color: 'var(--color-gold)',
    letterSpacing: '0.05em',
    marginBottom: '8px',
  },
  buildInfoItem: {
    display: 'flex',
    justifyContent: 'space-between',
    color: 'var(--text-secondary)',
    marginBottom: '4px',
  },
  content: {
    flex: 1,
    padding: '32px 36px',
    maxWidth: '1280px',
  },
};
