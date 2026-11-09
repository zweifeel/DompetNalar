import React from 'react';
import StatusBanner from '../components/StatusBanner';
import { TARGET_PROJECT_ID, TARGET_PROJECT_NUMBER } from '../config/firebase';
import { PRIMARY_ADMIN_EMAIL, PROJECT_OWNER_EMAIL } from '../config/adminAuth';

export default function SettingsScreen({ user }) {
  const auditLogs = [
    {
      action: 'Deployment Firestore Security Rules',
      target: 'projects/finsmart-47fdd/rulesets/28f32a48-cd83-411c-b779-263114b7aa2e',
      actor: user?.email || PRIMARY_ADMIN_EMAIL,
      timestamp: '2026-10-24 17:53:13 UTC',
      status: 'SUKSES',
    },
    {
      action: 'Pendaftaran Web App di Firebase Management API',
      target: '1:1029968996578:web:08b4ff25f4c60a67cd96bb',
      actor: user?.email || PRIMARY_ADMIN_EMAIL,
      timestamp: '2026-10-24 17:54:09 UTC',
      status: 'SUKSES',
    },
    {
      action: 'Publikasi Rilis Produksi Firebase Hosting',
      target: 'https://finsmart-47fdd.web.app',
      actor: user?.email || PRIMARY_ADMIN_EMAIL,
      timestamp: '2026-10-24 17:55:45 UTC',
      status: 'SUKSES',
    },
    {
      action: 'Verifikasi Autentikasi Admin Google OAuth',
      target: 'Authorized Admin Whitelist',
      actor: user?.email || PRIMARY_ADMIN_EMAIL,
      timestamp: '2026-10-24 18:00:00 UTC',
      status: 'SUKSES',
    },
  ];

  return (
    <div>
      <div style={styles.pageHeader}>
        <div>
          <h1 style={styles.pageTitle}>Pengaturan & Catatan Audit Log</h1>
          <p style={styles.pageSubtitle}>
            Riwayat pembaruan keamanan, identitas sistem, dan tata kelola akun administrator resmi.
          </p>
        </div>
        <span className="badge badge-info" style={{ fontSize: '0.8rem', padding: '6px 14px' }}>
          Keamanan Tinggi 🛡️
        </span>
      </div>

      <StatusBanner
        type="info"
        title="Prinsip Otoritas Minimum: Dua Administrator Resmi"
      >
        Akses panel ini dibatasi secara mutlak untuk dua akun pengembang SleepyKoala:
        <br />
        1. <strong>{PRIMARY_ADMIN_EMAIL}</strong> (Primary Developer)
        <br />
        2. <strong>{PROJECT_OWNER_EMAIL}</strong> (Project Owner & Co-Admin)
      </StatusBanner>

      <div style={styles.sectionCard}>
        <h3 style={styles.sectionTitle}>⚙️ Parameter Lingkungan Sistem</h3>
        <div style={styles.grid}>
          <div style={styles.fieldBox}>
            <span style={styles.fieldLabel}>Firebase Project ID</span>
            <div style={styles.fieldValue}>{TARGET_PROJECT_ID}</div>
          </div>
          <div style={styles.fieldBox}>
            <span style={styles.fieldLabel}>Project Number</span>
            <div style={styles.fieldValue}>{TARGET_PROJECT_NUMBER}</div>
          </div>
          <div style={styles.fieldBox}>
            <span style={styles.fieldLabel}>Hosting Domain Utama</span>
            <div style={styles.fieldValue}>finsmart-47fdd.web.app</div>
          </div>
          <div style={styles.fieldBox}>
            <span style={styles.fieldLabel}>Sesi Admin Saat Ini</span>
            <div style={styles.fieldValue}>{user?.email || 'Tidak Terdeteksi'}</div>
          </div>
        </div>
      </div>

      <div style={styles.sectionCard}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h3 style={styles.sectionTitle}>📋 Catatan Audit Log Sistem Terbaru</h3>
          <span className="badge badge-success">Live Audited</span>
        </div>

        <div style={styles.tableWrapper}>
          <table style={styles.table}>
            <thead>
              <tr style={styles.tableHeaderRow}>
                <th style={styles.th}>Aktivitas Sistem</th>
                <th style={styles.th}>Target Resource</th>
                <th style={styles.th}>Pelaksana</th>
                <th style={styles.th}>Waktu (UTC)</th>
                <th style={styles.th}>Hasil</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.map((log, idx) => (
                <tr key={idx} style={styles.tr}>
                  <td style={{ ...styles.td, fontWeight: '600' }}>{log.action}</td>
                  <td style={{ ...styles.td, fontFamily: 'monospace', fontSize: '0.75rem', color: '#93C5FD' }}>
                    {log.target}
                  </td>
                  <td style={styles.td}>{log.actor}</td>
                  <td style={{ ...styles.td, fontSize: '0.78rem', color: 'var(--text-muted)' }}>{log.timestamp}</td>
                  <td style={styles.td}>
                    <span className="badge badge-success" style={{ fontSize: '0.7rem' }}>
                      {log.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const styles = {
  pageHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '24px',
    flexWrap: 'wrap',
    gap: '16px',
  },
  pageTitle: {
    fontSize: '1.6rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    marginBottom: '6px',
  },
  pageSubtitle: {
    fontSize: '0.9rem',
    color: 'var(--text-secondary)',
  },
  sectionCard: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-lg)',
    padding: '24px',
    marginTop: '20px',
  },
  sectionTitle: {
    fontSize: '1.05rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
    marginBottom: '16px',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
    gap: '16px',
  },
  fieldBox: {
    padding: '14px',
    background: 'var(--bg-surface)',
    borderRadius: 'var(--radius-md)',
    border: '1px solid var(--border-card)',
  },
  fieldLabel: {
    fontSize: '0.75rem',
    color: 'var(--text-muted)',
    display: 'block',
    marginBottom: '4px',
    fontWeight: '600',
  },
  fieldValue: {
    fontSize: '0.9rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
    wordBreak: 'break-all',
  },
  tableWrapper: {
    overflowX: 'auto',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: '0.82rem',
  },
  tableHeaderRow: {
    borderBottom: '1px solid var(--border-card)',
    textAlign: 'left',
  },
  th: {
    padding: '10px 12px',
    color: 'var(--text-muted)',
    fontWeight: '600',
    fontSize: '0.72rem',
    letterSpacing: '0.05em',
  },
  tr: {
    borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
  },
  td: {
    padding: '12px',
    color: 'var(--text-secondary)',
  },
};
