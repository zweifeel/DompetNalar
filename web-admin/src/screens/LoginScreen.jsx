import React, { useState } from 'react';
import { signInWithGoogle, TARGET_PROJECT_ID } from '../config/firebase';
import { PRIMARY_ADMIN_EMAIL, PROJECT_OWNER_EMAIL } from '../config/adminAuth';

export default function LoginScreen({ onLoginSuccess }) {
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [errorDetails, setErrorDetails] = useState(null);

  const handleGoogleSignIn = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    setErrorDetails(null);

    try {
      const { user } = await signInWithGoogle();
      if (onLoginSuccess) {
        onLoginSuccess(user);
      }
    } catch (error) {
      console.warn('[LoginScreen] Sesi masuk Google dibatalkan atau gagal:', error);

      if (error.code === 'USER_CANCELED') {
        setErrorMessage('Proses login dibatalkan. Anda menutup dialog pemilihan akun Google.');
      } else if (error.code === 'UNAUTHORIZED_DOMAIN') {
        setErrorMessage('Domain belum diotorisasi di Firebase Console.');
        setErrorDetails(error.message);
      } else if (error.code === 'NETWORK_ERROR') {
        setErrorMessage('Gagal menghubungi server Google. Periksa koneksi internet Anda.');
      } else {
        setErrorMessage('Terjadi kendala saat melakukan autentikasi Google.');
        setErrorDetails(error.message || String(error));
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={styles.wrapper}>
      <div style={styles.card}>
        {/* Brand Header */}
        <div style={styles.brandContainer}>
          <div style={styles.logoBadge}>
            <span style={{ fontSize: '2rem' }}>🪙</span>
          </div>
          <h1 style={styles.title}>FinSmart Developer Admin</h1>
          <p style={styles.subtitle}>
            Portal Manajemen & Audit Internal Pengembang SleepyKoala
          </p>
          <div style={styles.projectPill}>
            <span>Proyek Firebase:</span>
            <code style={styles.projectCode}>{TARGET_PROJECT_ID}</code>
          </div>
        </div>

        {/* Error Alert Box */}
        {errorMessage && (
          <div style={styles.errorBox}>
            <div style={styles.errorTitle}>⚠️ Gagal Masuk</div>
            <div style={styles.errorText}>{errorMessage}</div>
            {errorDetails && (
              <div style={styles.errorDetailsBox}>
                <code>{errorDetails}</code>
              </div>
            )}
          </div>
        )}

        {/* Action Button */}
        <div style={styles.actionSection}>
          <button
            onClick={handleGoogleSignIn}
            disabled={isLoading}
            style={{
              ...styles.googleButton,
              ...(isLoading ? styles.googleButtonDisabled : {}),
            }}
          >
            {isLoading ? (
              <>
                <span className="spinner" />
                <span>Menghubungkan ke Akun Google...</span>
              </>
            ) : (
              <>
                <svg style={styles.googleIcon} viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                <span>Masuk dengan Akun Google Developer</span>
              </>
            )}
          </button>
        </div>

        {/* Security Notice */}
        <div style={styles.footerNote}>
          <div style={styles.securityTitle}>🔒 Proteksi Akses Ketat:</div>
          <p>
            Hanya identitas resmi pengembang (<strong>{PRIMARY_ADMIN_EMAIL}</strong> atau{' '}
            <strong>{PROJECT_OWNER_EMAIL}</strong>) yang diizinkan masuk ke panel ini.
          </p>
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
    maxWidth: '460px',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-xl)',
    padding: '36px 32px',
    boxShadow: 'var(--shadow-lg)',
  },
  brandContainer: {
    textAlign: 'center',
    marginBottom: '28px',
  },
  logoBadge: {
    width: '64px',
    height: '64px',
    borderRadius: 'var(--radius-lg)',
    background: 'linear-gradient(135deg, #0B57D0, #1D4ED8)',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: 'var(--shadow-glow)',
    marginBottom: '16px',
  },
  title: {
    fontSize: '1.4rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    letterSpacing: '-0.02em',
    marginBottom: '6px',
  },
  subtitle: {
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.4,
    marginBottom: '14px',
  },
  projectPill: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    padding: '4px 12px',
    borderRadius: 'var(--radius-full)',
    background: 'rgba(59, 130, 246, 0.1)',
    border: '1px solid rgba(59, 130, 246, 0.25)',
    color: 'var(--text-secondary)',
    fontSize: '0.75rem',
  },
  projectCode: {
    fontFamily: 'var(--font-mono)',
    color: '#93C5FD',
    fontWeight: '600',
  },
  errorBox: {
    background: 'var(--color-danger-bg)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: 'var(--radius-md)',
    padding: '14px 16px',
    marginBottom: '20px',
  },
  errorTitle: {
    fontSize: '0.85rem',
    fontWeight: '700',
    color: '#F87171',
    marginBottom: '4px',
  },
  errorText: {
    fontSize: '0.8rem',
    color: '#FECACA',
    lineHeight: 1.4,
  },
  errorDetailsBox: {
    marginTop: '8px',
    padding: '6px 10px',
    borderRadius: 'var(--radius-sm)',
    background: 'rgba(0, 0, 0, 0.3)',
    fontSize: '0.72rem',
    color: '#FCA5A5',
    overflowX: 'auto',
  },
  actionSection: {
    marginBottom: '24px',
  },
  googleButton: {
    width: '100%',
    padding: '14px 20px',
    borderRadius: 'var(--radius-lg)',
    background: '#FFFFFF',
    color: '#1F2937',
    fontSize: '0.92rem',
    fontWeight: '700',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '12px',
    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.2)',
    transition: 'all 0.15s ease',
  },
  googleButtonDisabled: {
    opacity: 0.7,
    cursor: 'wait',
  },
  googleIcon: {
    width: '20px',
    height: '20px',
  },
  footerNote: {
    background: 'rgba(15, 23, 42, 0.6)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-md)',
    padding: '12px 14px',
    fontSize: '0.75rem',
    color: 'var(--text-muted)',
    lineHeight: 1.5,
  },
  securityTitle: {
    color: 'var(--color-gold)',
    fontWeight: '700',
    marginBottom: '4px',
  },
};
