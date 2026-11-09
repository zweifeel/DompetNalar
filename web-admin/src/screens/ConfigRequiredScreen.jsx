import React from 'react';
import { TARGET_PROJECT_ID, TARGET_PROJECT_NUMBER } from '../config/firebase';

export default function ConfigRequiredScreen({ errors = [] }) {
  return (
    <div style={styles.wrapper}>
      <div style={styles.card}>
        <div style={styles.header}>
          <div style={styles.iconBadge}>⚙️</div>
          <div>
            <h1 style={styles.title}>Konfigurasi Firebase Web Diperlukan</h1>
            <p style={styles.subtitle}>
              FinSmart Admin Panel memerlukan konfigurasi resmi Firebase Web App untuk proyek{' '}
              <strong style={{ color: '#93C5FD' }}>{TARGET_PROJECT_ID}</strong>.
            </p>
          </div>
        </div>

        {/* Warning Alert */}
        <div style={styles.alertBox}>
          <div style={styles.alertTitle}>⚠️ Catatan Penting Arsitektur:</div>
          <p style={styles.alertText}>
            File <code className="code-inline">google-services.json</code> di folder Android{' '}
            hanya berisi konfigurasi untuk aplikasi mobile Android dan <strong>tidak memiliki Web App ID</strong>.
            Admin Panel Web membutuhkan pendaftaran Web App terpisah di Firebase Console.
          </p>
        </div>

        {/* Error Items */}
        {errors.length > 0 && (
          <div style={styles.errorsContainer}>
            <div style={styles.errorsHeader}>STATUS DETEKSI SISTEM:</div>
            <ul style={styles.errorsList}>
              {errors.map((err, idx) => (
                <li key={idx} style={styles.errorItem}>
                  🛑 {err}
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Step-by-step Setup Guide */}
        <div style={styles.stepsContainer}>
          <h3 style={styles.stepsTitle}>Langkah Pengaturan Mandiri (Hanya 2 Menit):</h3>
          <ol style={styles.stepsList}>
            <li>
              Buka{' '}
              <a
                href={`https://console.firebase.google.com/project/${TARGET_PROJECT_ID}/settings/general`}
                target="_blank"
                rel="noreferrer"
                style={styles.link}
              >
                Firebase Console &gt; Project Settings &gt; General ↗
              </a>
            </li>
            <li>
              Gulir ke bawah ke bagian <strong>Your apps</strong>.
            </li>
            <li>
              Klik tombol <strong>Add app</strong> lalu pilih ikon Web (<code>&lt;/&gt;</code>).
              Beri nama <code>FinSmart Admin Panel</code>.
            </li>
            <li>
              Salin nilai-nilai dari objek <code>firebaseConfig</code> yang diberikan ke dalam file{' '}
              <code className="code-inline">E:\FinSmart\web-admin\.env</code> (gunakan template di bawah).
            </li>
            <li>
              Muat ulang (refresh) halaman ini setelah menyimpan file <code className="code-inline">.env</code>.
            </li>
          </ol>
        </div>

        {/* Code Snippet Box */}
        <div style={styles.codeSnippetContainer}>
          <div style={styles.codeSnippetHeader}>
            <span>Template isi berkas <code>web-admin/.env</code>:</span>
          </div>
          <pre style={styles.codeBlock}>
{`VITE_FIREBASE_API_KEY=AIzaSy_SALIN_DARI_KONSOL_FIREBASE
VITE_FIREBASE_AUTH_DOMAIN=${TARGET_PROJECT_ID}.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=${TARGET_PROJECT_ID}
VITE_FIREBASE_STORAGE_BUCKET=${TARGET_PROJECT_ID}.firebasestorage.app
VITE_FIREBASE_MESSAGING_SENDER_ID=${TARGET_PROJECT_NUMBER}
VITE_FIREBASE_APP_ID=1:${TARGET_PROJECT_NUMBER}:web:SALIN_DARI_KONSOL_FIREBASE
VITE_FIREBASE_OAUTH_CLIENT_ID=1029968996578-kd1pohf7nhge6mmm3m3uagr83e40tti6.apps.googleusercontent.com`}
          </pre>
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
    padding: '32px 20px',
    background: 'radial-gradient(circle at 50% 20%, #131B2E 0%, #0B0F19 80%)',
  },
  card: {
    width: '100%',
    maxWidth: '720px',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-xl)',
    padding: '36px 32px',
    boxShadow: 'var(--shadow-lg)',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    marginBottom: '24px',
  },
  iconBadge: {
    width: '56px',
    height: '56px',
    borderRadius: 'var(--radius-lg)',
    background: 'rgba(245, 158, 11, 0.12)',
    border: '1px solid rgba(245, 158, 11, 0.3)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '1.75rem',
    flexShrink: 0,
  },
  title: {
    fontSize: '1.35rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    letterSpacing: '-0.02em',
    marginBottom: '4px',
  },
  subtitle: {
    fontSize: '0.88rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.4,
  },
  alertBox: {
    background: 'rgba(245, 158, 11, 0.08)',
    border: '1px solid rgba(245, 158, 11, 0.25)',
    borderRadius: 'var(--radius-md)',
    padding: '14px 16px',
    marginBottom: '20px',
  },
  alertTitle: {
    fontSize: '0.82rem',
    fontWeight: '700',
    color: '#FBBF24',
    marginBottom: '4px',
  },
  alertText: {
    fontSize: '0.8rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.5,
  },
  errorsContainer: {
    background: 'var(--color-danger-bg)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: 'var(--radius-md)',
    padding: '14px 16px',
    marginBottom: '24px',
  },
  errorsHeader: {
    fontSize: '0.72rem',
    fontWeight: '800',
    color: '#F87171',
    letterSpacing: '0.05em',
    marginBottom: '8px',
  },
  errorsList: {
    listStyle: 'none',
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  errorItem: {
    fontSize: '0.8rem',
    color: '#FECACA',
    lineHeight: 1.4,
  },
  stepsContainer: {
    marginBottom: '24px',
  },
  stepsTitle: {
    fontSize: '0.95rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
    marginBottom: '12px',
  },
  stepsList: {
    paddingLeft: '20px',
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.5,
  },
  link: {
    color: '#60A5FA',
    fontWeight: '600',
    textDecoration: 'underline',
  },
  codeSnippetContainer: {
    borderRadius: 'var(--radius-md)',
    overflow: 'hidden',
    border: '1px solid var(--border-card)',
  },
  codeSnippetHeader: {
    background: 'rgba(15, 23, 42, 0.9)',
    padding: '8px 14px',
    fontSize: '0.75rem',
    color: 'var(--text-muted)',
    borderBottom: '1px solid var(--border-card)',
  },
  codeBlock: {
    background: '#070B14',
    padding: '14px 16px',
    fontFamily: 'var(--font-mono)',
    fontSize: '0.78rem',
    color: '#93C5FD',
    overflowX: 'auto',
    lineHeight: 1.5,
  },
};
