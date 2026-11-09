import React from 'react';
import MetricPlaceholderCard from '../components/MetricPlaceholderCard';
import StatusBanner from '../components/StatusBanner';
import { TARGET_PROJECT_ID, TARGET_PROJECT_NUMBER } from '../config/firebase';
import { PRIMARY_ADMIN_EMAIL, PROJECT_OWNER_EMAIL, getAdminRoleLabel } from '../config/adminAuth';
import { APP_RELEASE_CONFIG } from '../config/appReleaseConfig';

export default function DashboardScreen({ user, onNavigateTab }) {
  const roleLabel = getAdminRoleLabel(user?.email);

  return (
    <div>
      {/* Header Salam Pengembang */}
      <div style={styles.pageHeader}>
        <div>
          <h1 style={styles.pageTitle}>Dashboard Ringkasan Administrator</h1>
          <p style={styles.pageSubtitle}>
            Selamat datang kembali, <strong>{user?.displayName || user?.email}</strong> ({roleLabel})
          </p>
        </div>
        <div style={styles.liveClock}>
          <span style={styles.liveDot} />
          <span>Sesi Aktif Terotentikasi</span>
        </div>
      </div>

      {/* Quick Action: Kelola Smart Master Grants Langsung */}
      <div style={styles.quickActionCard}>
        <div style={styles.quickActionLeft}>
          <div style={styles.quickActionBadge}>👑 Akses Khusus Tester</div>
          <h3 style={styles.quickActionTitle}>Pengelolaan Remote Smart Master Grants</h3>
          <p style={styles.quickActionSubtitle}>
            Beri atau cabut akses Smart Master untuk email tester Google Play secara remote tanpa biaya cloud (Firebase Spark).
            Dukungan masa berlaku 7, 30, 90 hari, atau permanen.
          </p>
        </div>
        <button
          type="button"
          onClick={() => onNavigateTab && onNavigateTab('membership')}
          style={styles.quickActionBtn}
          title="Buka panel pemberian dan pengelolaan Smart Master Grants"
        >
          <span>Kelola Grant Tester</span>
          <span style={{ fontSize: '1.15rem' }}>→</span>
        </button>
      </div>

      {/* Status Banner Fase 4B.3 */}
      <StatusBanner
        type="success"
        title="Fase 4B.3 Aktif: Remote Smart Master Grants & Integrasi Audit Selesai"
      >
        Antarmuka administrator terhubung ke Firebase Project{' '}
        <strong style={{ color: '#93C5FD' }}>{TARGET_PROJECT_ID}</strong>. Admin resmi dapat menerbitkan,
        memantau, mencabut, dan menghapus grant tester secara remote melalui tab{' '}
        <strong>Smart Master Grants</strong> di sidebar navigasi.
      </StatusBanner>

      {/* Grid Kartu Metrik */}
      <div style={styles.metricGrid}>
        <MetricPlaceholderCard
          icon="📥"
          title="Masukan & Bug Pengguna"
          subtitle="Koleksi /feedbacks"
          badgeText="Fase 3 Aktif"
          badgeType="info"
          phaseNote="Koleksi Firestore /feedbacks"
          description="Membaca masukan nyata dari pengguna aplikasi Android secara real-time untuk triage dan audit."
        />

        <MetricPlaceholderCard
          icon="🧪"
          title="Status Google Play"
          subtitle="Closed Testing Aktif"
          badgeText="20 Tester Track"
          badgeType="success"
          phaseNote="Track Tertutup Play Console"
          description="DompetNalar sedang menjalani masa uji coba tertutup 20 tester sebelum rilis publik di Google Play Store."
        />

        <MetricPlaceholderCard
          icon="🛡️"
          title="Keamanan Firestore Cloud"
          subtitle="firestore.rules v4B.1"
          badgeText="Audit Rules Lolos"
          badgeType="neutral"
          phaseNote="Emulator & Rules Verified"
          description="Aturan keamanan membatasi akses koleksi tester_grants dan user_registry hanya untuk admin resmi."
        />

        <MetricPlaceholderCard
          icon="👑"
          title="Smart Master Grants"
          subtitle="Remote Tester Grants"
          badgeText="Siap Digunakan"
          badgeType="success"
          phaseNote="Koleksi /tester_grants"
          description="Grant tester diterbitkan remote tanpa mengubah Google Play Billing atau menimpa entitlement lokal."
        />
      </div>

      {/* Tabel Rincian Arsitektur & Lingkungan Aktual */}
      <div style={styles.sectionCard}>
        <div style={styles.sectionHeader}>
          <h2 style={styles.sectionTitle}>Ringkasan Identitas & Lingkungan Sistem</h2>
          <span className="badge badge-info">Verified Metadata</span>
        </div>

        <div style={styles.detailsGrid}>
          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Firebase Project ID:</span>
            <code className="code-inline">{TARGET_PROJECT_ID}</code>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Firebase Project Number:</span>
            <code className="code-inline">{TARGET_PROJECT_NUMBER}</code>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Android Application ID:</span>
            <code className="code-inline">com.aistudio.finsmart.kptxvw</code>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Versi Rilis Android Terkonfigurasi:</span>
            <span style={styles.detailValue}>
              {APP_RELEASE_CONFIG.configuredAppReleaseVersion}
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '6px' }}>
                (Konfigurasi target rilis)
              </span>
            </span>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Versi Web Admin:</span>
            <span style={styles.detailValue}>{APP_RELEASE_CONFIG.webAdminVersion}</span>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Schema Database Android:</span>
            <span style={styles.detailValue}>{APP_RELEASE_CONFIG.targetDatabaseSchema}</span>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Primary Developer Email:</span>
            <span style={styles.detailValue}>{PRIMARY_ADMIN_EMAIL}</span>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Project Owner Email:</span>
            <span style={styles.detailValue}>{PROJECT_OWNER_EMAIL}</span>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Data Finansial Pengguna:</span>
            <span style={{ ...styles.detailValue, color: '#34D399', fontWeight: '600' }}>
              🔒 100% Terisolasi di HP Klien (SQLite Room v9 & Drive Privat)
            </span>
          </div>

          <div style={styles.detailRow}>
            <span style={styles.detailLabel}>Metode Autentikasi Web:</span>
            <span style={styles.detailValue}>Google Identity Services via Firebase Auth Web SDK</span>
          </div>
        </div>
      </div>

      {/* Rencana Kerja & Roadmap */}
      <div style={styles.roadmapCard}>
        <div style={styles.roadmapHeader}>
          <span style={{ fontSize: '1.25rem' }}>🚀</span>
          <div>
            <h3 style={styles.roadmapTitle}>Panduan Operasional Administrator</h3>
            <p style={styles.roadmapSubtitle}>
              Fitur remote grant dan monitoring dirancang agar mematuhi batasan kuota gratis Firebase Spark.
            </p>
          </div>
        </div>

        <div style={styles.roadmapSteps}>
          <div style={styles.stepItem}>
            <div style={styles.stepNumber}>1</div>
            <div style={styles.stepContent}>
              <strong>Beri Akses Tester:</strong> Buka tab <em>Smart Master Grants</em> di sidebar untuk memasukkan email tester Google Play dan menentukan durasi grant.
            </div>
          </div>

          <div style={styles.stepItem}>
            <div style={styles.stepNumber}>2</div>
            <div style={styles.stepContent}>
              <strong>Cabut Kapan Saja:</strong> Gunakan tombol <em>Cabut Akses</em> untuk menonaktifkan grant seketika pada Firestore.
            </div>
          </div>

          <div style={styles.stepItem}>
            <div style={styles.stepNumber}>3</div>
            <div style={styles.stepContent}>
              <strong>Zero Cost & Zero Cloud Backend:</strong> Seluruh verifikasi berjalan client-side dan direct-to-Firestore tanpa Cloud Functions berbayar.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

const styles = {
  pageHeader: {
    display: 'flex',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    marginBottom: '24px',
    flexWrap: 'wrap',
    gap: '16px',
  },
  pageTitle: {
    fontSize: '1.65rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    letterSpacing: '-0.02em',
    marginBottom: '4px',
  },
  pageSubtitle: {
    fontSize: '0.9rem',
    color: 'var(--text-secondary)',
  },
  liveClock: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '6px 14px',
    borderRadius: 'var(--radius-full)',
    background: 'rgba(16, 185, 129, 0.1)',
    border: '1px solid rgba(16, 185, 129, 0.25)',
    color: '#34D399',
    fontSize: '0.78rem',
    fontWeight: '600',
  },
  liveDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#10B981',
    boxShadow: '0 0 10px #10B981',
  },
  quickActionCard: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '20px 24px',
    marginBottom: '20px',
    borderRadius: 'var(--radius-lg)',
    background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.12) 0%, rgba(30, 41, 59, 0.7) 100%)',
    border: '1px solid rgba(245, 158, 11, 0.35)',
    boxShadow: '0 4px 16px rgba(0, 0, 0, 0.2)',
    flexWrap: 'wrap',
    gap: '16px',
  },
  quickActionLeft: {
    flex: '1 1 340px',
  },
  quickActionBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    padding: '4px 10px',
    borderRadius: 'var(--radius-full)',
    background: 'rgba(245, 158, 11, 0.2)',
    border: '1px solid rgba(245, 158, 11, 0.4)',
    color: '#FBBF24',
    fontSize: '0.74rem',
    fontWeight: '700',
    letterSpacing: '0.03em',
    marginBottom: '8px',
    textTransform: 'uppercase',
  },
  quickActionTitle: {
    fontSize: '1.15rem',
    fontWeight: '800',
    color: '#FDE68A',
    marginBottom: '4px',
    letterSpacing: '-0.01em',
  },
  quickActionSubtitle: {
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.45,
    maxWidth: '680px',
  },
  quickActionBtn: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '10px',
    padding: '12px 22px',
    borderRadius: 'var(--radius-md)',
    background: 'linear-gradient(135deg, #F59E0B 0%, #D97706 100%)',
    color: '#FFFFFF',
    fontSize: '0.92rem',
    fontWeight: '700',
    border: 'none',
    cursor: 'pointer',
    boxShadow: '0 4px 12px rgba(245, 158, 11, 0.3)',
    transition: 'transform 0.15s ease, box-shadow 0.15s ease',
  },
  metricGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
    gap: '20px',
    marginBottom: '28px',
  },
  sectionCard: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-lg)',
    padding: '24px',
    marginBottom: '28px',
  },
  sectionHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: '20px',
    paddingBottom: '14px',
    borderBottom: '1px solid var(--border-card)',
  },
  sectionTitle: {
    fontSize: '1.05rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
  },
  detailsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
    gap: '14px 24px',
  },
  detailRow: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  detailLabel: {
    fontSize: '0.75rem',
    fontWeight: '600',
    color: 'var(--text-muted)',
    textTransform: 'uppercase',
    letterSpacing: '0.04em',
  },
  detailValue: {
    fontSize: '0.88rem',
    color: 'var(--text-primary)',
  },
  roadmapCard: {
    background: 'linear-gradient(135deg, rgba(11, 87, 208, 0.08) 0%, rgba(30, 41, 59, 0.5) 100%)',
    border: '1px solid rgba(59, 130, 246, 0.3)',
    borderRadius: 'var(--radius-lg)',
    padding: '24px',
  },
  roadmapHeader: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '14px',
    marginBottom: '20px',
  },
  roadmapTitle: {
    fontSize: '1.05rem',
    fontWeight: '700',
    color: '#93C5FD',
    marginBottom: '4px',
  },
  roadmapSubtitle: {
    fontSize: '0.84rem',
    color: 'var(--text-secondary)',
  },
  roadmapSteps: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
    gap: '16px',
  },
  stepItem: {
    display: 'flex',
    gap: '12px',
    background: 'rgba(15, 23, 42, 0.6)',
    padding: '14px 16px',
    borderRadius: 'var(--radius-md)',
    border: '1px solid var(--border-card)',
  },
  stepNumber: {
    width: '26px',
    height: '26px',
    borderRadius: '50%',
    background: 'var(--color-primary)',
    color: '#FFFFFF',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '0.75rem',
    fontWeight: '700',
    flexShrink: 0,
  },
  stepContent: {
    fontSize: '0.82rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.45,
  },
};
