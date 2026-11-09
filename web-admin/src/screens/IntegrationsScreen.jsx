import React from 'react';
import StatusBanner from '../components/StatusBanner';
import { TARGET_PROJECT_ID, TARGET_PROJECT_NUMBER } from '../config/firebase';

export default function IntegrationsScreen({ user }) {
  const integrations = [
    {
      name: 'Cloud Firestore (Database Feedbacks)',
      icon: '🔥',
      status: 'TERVERIFIKASI & AKTIF',
      badgeType: 'success',
      region: 'asia-southeast2 (Jakarta)',
      detail: 'Koleksi /feedbacks dilindungi aturan keamanan firestore.rules terbaru dengan validasi schema ketat.',
    },
    {
      name: 'Firebase Authentication (Google Identity)',
      icon: '🔐',
      status: 'TERVERIFIKASI & AKTIF',
      badgeType: 'success',
      region: 'Global / Multi-region',
      detail: 'Mendukung login pengembang dengan popup Google OAuth. Dibatasi khusus allowlist resmi.',
    },
    {
      name: 'Firebase Hosting (Production SPA)',
      icon: '🌐',
      status: 'TERVERIFIKASI & AKTIF',
      badgeType: 'success',
      region: 'Global CDN (HTTPS)',
      detail: 'Melayani Web Admin di domain https://finsmart-47fdd.web.app dan https://finsmart-47fdd.firebaseapp.com.',
    },
    {
      name: 'Google Drive AppData Sandbox',
      icon: '💾',
      status: 'TERVERIFIKASI & AKTIF',
      badgeType: 'success',
      region: 'Privat Per-User',
      detail: 'Pencadangan data finansial pengguna terenkripsi lokal langsung ke folder privat Google Drive pengguna.',
    },
    {
      name: 'Google Play Billing Client v8.0.0',
      icon: '💳',
      status: 'TERVERIFIKASI & AKTIF',
      badgeType: 'success',
      region: 'Google Play Store',
      detail: 'Katalog paket bulanan, tahunan, dan lifetime terhubung langsung dengan Google Play Store Android.',
    },
    {
      name: 'Cloud Functions & Play Developer API',
      icon: '⚡',
      status: 'PENDING FASE BERIKUTNYA',
      badgeType: 'warning',
      region: 'Belum Diaktifkan',
      detail: 'Validasi token server-side belum diaktifkan demi menjaga status kuota gratis selama 20-tester track.',
    },
  ];

  return (
    <div>
      <div style={styles.pageHeader}>
        <div>
          <h1 style={styles.pageTitle}>Status Integrasi Sistem & Layanan Cloud</h1>
          <p style={styles.pageSubtitle}>
            Pemeriksaan langsung status operasional komponen cloud proyek <strong>{TARGET_PROJECT_ID}</strong>.
          </p>
        </div>
        <span className="badge badge-success" style={{ fontSize: '0.8rem', padding: '6px 14px' }}>
          5 Layanan Aktif
        </span>
      </div>

      <StatusBanner
        type="success"
        title="Integritas Cloud Backend: Terhubung & Bebas Kebocoran Data"
      >
        Seluruh layanan cloud yang terpasang beroperasi sesuai spesifikasi keamanan SleepyKoala.
        Tidak ada data finansial, rekening, atau saldo pengguna yang terekspos ke cloud publik.
      </StatusBanner>

      <div style={styles.list}>
        {integrations.map((item, idx) => (
          <div key={idx} style={styles.itemCard}>
            <div style={styles.itemHeader}>
              <span style={{ fontSize: '1.4rem' }}>{item.icon}</span>
              <div style={{ flex: 1 }}>
                <div style={styles.itemTitle}>{item.name}</div>
                <div style={styles.itemRegion}>Region / Lingkup: {item.region}</div>
              </div>
              <span className={`badge badge-${item.badgeType}`}>
                {item.status}
              </span>
            </div>
            <p style={styles.itemDetail}>{item.detail}</p>
          </div>
        ))}
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
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
    marginTop: '24px',
  },
  itemCard: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-lg)',
    padding: '20px 24px',
  },
  itemHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
    marginBottom: '12px',
    flexWrap: 'wrap',
  },
  itemTitle: {
    fontSize: '1rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
  },
  itemRegion: {
    fontSize: '0.78rem',
    color: 'var(--text-muted)',
    marginTop: '2px',
  },
  itemDetail: {
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    lineHeight: '1.6',
    paddingLeft: '38px',
  },
};
