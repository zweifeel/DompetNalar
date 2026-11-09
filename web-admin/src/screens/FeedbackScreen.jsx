import React, { useState, useEffect, useMemo } from 'react';
import { collection, query, orderBy, limit, getDocs } from 'firebase/firestore';
import { db, configStatus, auth } from '../config/firebase';

const CATEGORIES = ['Semua Kategori', 'Bug / Kendala', 'Saran Fitur', 'Desain / UI', 'Lainnya'];
const RATINGS = ['Semua Rating', '5 Bintang ⭐⭐⭐⭐⭐', '4 Bintang ⭐⭐⭐⭐', '3 Bintang ⭐⭐⭐', '2 Bintang ⭐⭐', '1 Bintang ⭐'];

export default function FeedbackScreen({ user }) {
  const [feedbacks, setFeedbacks] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorStatus, setErrorStatus] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState('Semua Kategori');
  const [selectedRating, setSelectedRating] = useState('Semua Rating');
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFeedback, setActiveFeedback] = useState(null);
  const [copiedId, setCopiedId] = useState(null);

  const fetchFeedbacks = async () => {
    setIsLoading(true);
    setErrorStatus(null);

    if (!configStatus.isConfigured || !db) {
      setErrorStatus({
        type: 'CONFIG_REQUIRED',
        title: 'Konfigurasi Firebase Web Belum Lengkap',
        message: 'File .env belum memiliki kredensial resmi Firebase Web App untuk proyek finsmart-47fdd.',
      });
      setIsLoading(false);
      return;
    }

    if (!auth || !auth.currentUser) {
      setErrorStatus({
        type: 'AUTH_REQUIRED',
        title: 'Sesi Firebase Auth Tidak Ditemukan',
        message: 'Silakan masuk menggunakan Google Sign-In dengan email administrator resmi.',
      });
      setIsLoading(false);
      return;
    }

    try {
      const feedbacksCol = collection(db, 'feedbacks');
      let querySnapshot;

      try {
        const q = query(feedbacksCol, orderBy('createdAt', 'desc'), limit(50));
        querySnapshot = await getDocs(q);
      } catch (err) {
        // Fallback: Jika index 'createdAt' belum dibangun di Firestore Console
        const isIndexError = err.message && (err.message.includes('index') || err.message.includes('FAILED_PRECONDITION'));
        if (isIndexError) {
          console.warn('[FeedbackCenter] Index createdAt belum tersedia, fallback ke un-ordered query');
          const fallbackQ = query(feedbacksCol, limit(50));
          querySnapshot = await getDocs(fallbackQ);
        } else {
          throw err;
        }
      }

      const items = [];
      querySnapshot.forEach((doc) => {
        const data = doc.data();
        let createdAtMillis = Date.now();
        if (data.createdAt?.toDate) {
          createdAtMillis = data.createdAt.toDate().getTime();
        } else if (typeof data.createdAt === 'number') {
          createdAtMillis = data.createdAt;
        }

        items.push({
          id: doc.id,
          feedbackId: data.feedbackId || doc.id,
          userName: data.userName || 'Pengguna FinSmart',
          userEmail: data.userEmail || '',
          rating: typeof data.rating === 'number' ? data.rating : 5,
          category: data.category || 'Lainnya',
          message: data.message || '',
          appVersion: data.appVersion || 'Tidak dilaporkan',
          createdAt: createdAtMillis,
          userId: data.userId || null,
        });
      });

      // Urutkan menurun berdasarkan createdAt jika query fallback digunakan
      items.sort((a, b) => b.createdAt - a.createdAt);
      setFeedbacks(items);
    } catch (err) {
      console.error('[FeedbackCenter] Gagal mengambil feedback:', err);
      if (err.code === 'permission-denied') {
        setErrorStatus({
          type: 'PERMISSION_DENIED',
          title: 'Akses Firestore Ditolak (PERMISSION_DENIED)',
          message: 'Aturan firestore.rules di Firebase Console belum mengizinkan email Anda atau aturan lokal belum dipublikasikan ke server cloud.',
          hint: 'Buka Firebase Console -> Firestore Database -> Rules -> Pastikan isAdmin() mencakup email admin Anda dan klik Publish.',
        });
      } else {
        setErrorStatus({
          type: 'QUERY_ERROR',
          title: 'Gagal Memuat Laporan Feedback',
          message: err.message || 'Terjadi kesalahan saat berkomunikasi dengan Firestore.',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchFeedbacks();
  }, []);

  // Filter & Search
  const filteredFeedbacks = useMemo(() => {
    return feedbacks.filter((item) => {
      // Category filter
      if (selectedCategory !== 'Semua Kategori' && item.category !== selectedCategory) {
        return false;
      }
      // Rating filter
      if (selectedRating !== 'Semua Rating') {
        const targetRating = parseInt(selectedRating.charAt(0), 10);
        if (item.rating !== targetRating) return false;
      }
      // Search query filter
      if (searchQuery.trim()) {
        const queryLower = searchQuery.trim().toLowerCase();
        const msgMatch = item.message.toLowerCase().includes(queryLower);
        const userMatch = item.userName.toLowerCase().includes(queryLower);
        const emailMatch = item.userEmail.toLowerCase().includes(queryLower);
        const verMatch = item.appVersion.toLowerCase().includes(queryLower);
        if (!msgMatch && !userMatch && !emailMatch && !verMatch) return false;
      }
      return true;
    });
  }, [feedbacks, selectedCategory, selectedRating, searchQuery]);

  const handleCopyReport = (item) => {
    const reportText = `[FINSMART BUG REPORT]\nID: ${item.id}\nTanggal: ${new Date(item.createdAt).toLocaleString('id-ID')}\nPengirim: ${item.userName} (${item.userEmail || 'Tanpa Email'})\nVersi: ${item.appVersion}\nKategori: ${item.category}\nRating: ${item.rating}/5 ⭐\nPesan:\n${item.message}`;
    navigator.clipboard.writeText(reportText).then(() => {
      setCopiedId(item.id);
      setTimeout(() => setCopiedId(null), 2500);
    });
  };

  const formatDate = (millis) => {
    try {
      return new Date(millis).toLocaleString('id-ID', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return '-';
    }
  };

  return (
    <div style={styles.container}>
      {/* Header Halaman */}
      <div style={styles.header}>
        <div>
          <h1 style={styles.title}>Pusat Masukan & Laporan Bug Tester</h1>
          <p style={styles.subtitle}>
            Memantau masukan langsung dari tester Google Play Closed Testing (Koleksi Firestore: <code style={styles.code}>feedbacks</code>)
          </p>
        </div>
        <button onClick={fetchFeedbacks} disabled={isLoading} style={styles.refreshButton}>
          {isLoading ? 'Memuat...' : '🔄 Segarkan Data'}
        </button>
      </div>

      {/* Banner Error / Peringatan Keamanan */}
      {errorStatus && (
        <div style={styles.errorBanner}>
          <div style={styles.errorIcon}>⚠️</div>
          <div style={styles.errorContent}>
            <div style={styles.errorTitle}>{errorStatus.title}</div>
            <div style={styles.errorDescription}>{errorStatus.message}</div>
            {errorStatus.hint && <div style={styles.errorHint}>{errorStatus.hint}</div>}
          </div>
        </div>
      )}

      {/* Kontrol Pencarian & Filter */}
      <div style={styles.filterBar}>
        <div style={styles.searchWrapper}>
          <span style={styles.searchIcon}>🔍</span>
          <input
            type="text"
            placeholder="Cari pesan bug, kata kunci, nama pengguna, atau versi..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={styles.searchInput}
          />
          {searchQuery && (
            <button onClick={() => setSearchQuery('')} style={styles.clearSearchBtn}>
              ✕
            </button>
          )}
        </div>

        <div style={styles.dropdownGroup}>
          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            style={styles.selectInput}
          >
            {CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>

          <select
            value={selectedRating}
            onChange={(e) => setSelectedRating(e.target.value)}
            style={styles.selectInput}
          >
            {RATINGS.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Ringkasan Metrik Singkat */}
      <div style={styles.metricRow}>
        <div style={styles.metricCard}>
          <span style={styles.metricLabel}>Total Masukan Termuat</span>
          <span style={styles.metricValue}>{feedbacks.length}</span>
        </div>
        <div style={styles.metricCard}>
          <span style={styles.metricLabel}>Laporan Bug Terfilter</span>
          <span style={{ ...styles.metricValue, color: '#F87171' }}>
            {feedbacks.filter((f) => f.category === 'Bug / Kendala').length}
          </span>
        </div>
        <div style={styles.metricCard}>
          <span style={styles.metricLabel}>Saran Fitur Terfilter</span>
          <span style={{ ...styles.metricValue, color: '#60A5FA' }}>
            {feedbacks.filter((f) => f.category === 'Saran Fitur').length}
          </span>
        </div>
        <div style={styles.metricCard}>
          <span style={styles.metricLabel}>Sesuai Kriteria Filter</span>
          <span style={{ ...styles.metricValue, color: '#34D399' }}>{filteredFeedbacks.length}</span>
        </div>
      </div>

      {/* Tabel Data Feedback */}
      <div style={styles.tableWrapper}>
        {isLoading ? (
          <div style={styles.stateContainer}>
            <span className="spinner" style={{ width: '28px', height: '28px' }} />
            <span style={{ color: 'var(--text-secondary)' }}>Mengambil dokumen feedback dari Firestore...</span>
          </div>
        ) : filteredFeedbacks.length === 0 ? (
          <div style={styles.stateContainer}>
            <span style={{ fontSize: '2rem' }}>📭</span>
            <span style={{ fontWeight: '600', color: 'var(--text-primary)' }}>
              {feedbacks.length === 0 ? 'Belum Ada Laporan Feedback di Firestore' : 'Tidak Ada Laporan yang Cocok dengan Filter'}
            </span>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              {feedbacks.length === 0
                ? 'Laporan yang dikirim oleh tester melalui aplikasi Android akan muncul secara otomatis di sini.'
                : 'Coba ubah kata kunci pencarian atau reset filter kategori/rating.'}
            </span>
          </div>
        ) : (
          <table style={styles.table}>
            <thead>
              <tr style={styles.tableHeaderRow}>
                <th style={{ ...styles.th, width: '120px' }}>Kategori</th>
                <th style={{ ...styles.th, width: '90px' }}>Rating</th>
                <th style={styles.th}>Pesan & Kendala</th>
                <th style={{ ...styles.th, width: '150px' }}>Pengirim</th>
                <th style={{ ...styles.th, width: '80px' }}>Versi</th>
                <th style={{ ...styles.th, width: '130px' }}>Waktu</th>
                <th style={{ ...styles.th, width: '100px', textAlign: 'center' }}>Aksi</th>
              </tr>
            </thead>
            <tbody>
              {filteredFeedbacks.map((item) => {
                const isBug = item.category === 'Bug / Kendala';
                return (
                  <tr key={item.id} style={styles.tableRow}>
                    <td style={styles.td}>
                      <span
                        className={`badge ${
                          isBug ? 'badge-danger' : item.category === 'Saran Fitur' ? 'badge-info' : 'badge-neutral'
                        }`}
                        style={{ fontSize: '0.75rem' }}
                      >
                        {item.category}
                      </span>
                    </td>
                    <td style={styles.td}>
                      <span style={{ color: '#FBBF24', fontWeight: 'bold' }}>
                        {'⭐'.repeat(Math.max(1, Math.min(5, item.rating)))}
                      </span>
                    </td>
                    <td style={{ ...styles.td, maxWidth: '340px' }}>
                      <div style={styles.messageCell} title={item.message}>
                        {item.message || '(Pesan kosong)'}
                      </div>
                    </td>
                    <td style={styles.td}>
                      <div style={styles.senderName}>{item.userName}</div>
                      {item.userEmail && (
                        <div style={styles.senderEmail} title={item.userEmail}>
                          {item.userEmail.replace(/^(.)(.*)(@.*)$/, (_, a, b, c) => `${a}***${c}`)}
                        </div>
                      )}
                    </td>
                    <td style={styles.td}>
                      <code style={styles.verCode}>{item.appVersion}</code>
                    </td>
                    <td style={{ ...styles.td, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      {formatDate(item.createdAt)}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'center' }}>
                      <div style={styles.actionButtonGroup}>
                        <button
                          onClick={() => setActiveFeedback(item)}
                          style={styles.detailButton}
                          title="Lihat Detail Lengkap"
                        >
                          👁️ Detail
                        </button>
                        <button
                          onClick={() => handleCopyReport(item)}
                          style={styles.copyButton}
                          title="Salin Detail untuk Bug Tracking"
                        >
                          {copiedId === item.id ? '✓ Tersalin' : '📋 Salin'}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Modal Detail Laporan */}
      {activeFeedback && (
        <div style={styles.modalOverlay} onClick={() => setActiveFeedback(null)}>
          <div style={styles.modalContent} onClick={(e) => e.stopPropagation()}>
            <div style={styles.modalHeader}>
              <div>
                <h3 style={styles.modalTitle}>Detail Laporan Masukan</h3>
                <span style={styles.modalSubtitle}>ID Dokumen: {activeFeedback.id}</span>
              </div>
              <button onClick={() => setActiveFeedback(null)} style={styles.modalCloseBtn}>
                ✕
              </button>
            </div>

            <div style={styles.modalBody}>
              <div style={styles.detailGrid}>
                <div style={styles.detailItem}>
                  <span style={styles.detailLabel}>Kategori</span>
                  <span
                    className={`badge ${
                      activeFeedback.category === 'Bug / Kendala' ? 'badge-danger' : 'badge-info'
                    }`}
                  >
                    {activeFeedback.category}
                  </span>
                </div>
                <div style={styles.detailItem}>
                  <span style={styles.detailLabel}>Rating Kepuasan</span>
                  <span style={{ color: '#FBBF24', fontWeight: 'bold' }}>
                    {activeFeedback.rating} dari 5 ⭐
                  </span>
                </div>
                <div style={styles.detailItem}>
                  <span style={styles.detailLabel}>Versi FinSmart</span>
                  <code>{activeFeedback.appVersion}</code>
                </div>
                <div style={styles.detailItem}>
                  <span style={styles.detailLabel}>Waktu Pengiriman</span>
                  <span>{formatDate(activeFeedback.createdAt)}</span>
                </div>
                <div style={styles.detailItem}>
                  <span style={styles.detailLabel}>Nama Pengirim</span>
                  <strong>{activeFeedback.userName}</strong>
                </div>
                <div style={styles.detailItem}>
                  <span style={styles.detailLabel}>Email Terdaftar</span>
                  <span>{activeFeedback.userEmail || '(Tidak dilampirkan)'}</span>
                </div>
              </div>

              <div style={styles.messageBox}>
                <div style={styles.messageBoxLabel}>Isi Pesan / Kendala yang Dilaporkan:</div>
                <div style={styles.messageBoxText}>{activeFeedback.message}</div>
              </div>
            </div>

            <div style={styles.modalFooter}>
              <button
                onClick={() => handleCopyReport(activeFeedback)}
                style={styles.modalCopyBtn}
              >
                {copiedId === activeFeedback.id ? '✓ Laporan Berhasil Disalin ke Clipboard' : '📋 Salin Ringkasan Bug'}
              </button>
              <button onClick={() => setActiveFeedback(null)} style={styles.modalDismissBtn}>
                Tutup
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: '16px',
    flexWrap: 'wrap',
  },
  title: {
    fontSize: '1.4rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    margin: 0,
  },
  subtitle: {
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    marginTop: '4px',
  },
  code: {
    background: 'rgba(255, 255, 255, 0.08)',
    padding: '2px 6px',
    borderRadius: '4px',
    color: '#93C5FD',
    fontSize: '0.85rem',
  },
  refreshButton: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    padding: '8px 16px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  errorBanner: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '12px',
    padding: '16px 20px',
    borderRadius: 'var(--radius-lg)',
    background: 'rgba(239, 68, 68, 0.12)',
    border: '1px solid rgba(239, 68, 68, 0.35)',
    color: '#FCA5A5',
  },
  errorIcon: {
    fontSize: '1.3rem',
  },
  errorContent: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  errorTitle: {
    fontWeight: '700',
    fontSize: '0.9rem',
    color: '#F87171',
  },
  errorDescription: {
    fontSize: '0.8rem',
    lineHeight: '1.4',
  },
  errorHint: {
    fontSize: '0.75rem',
    color: '#FBBF24',
    marginTop: '4px',
    background: 'rgba(0,0,0,0.2)',
    padding: '4px 8px',
    borderRadius: '4px',
  },
  filterBar: {
    display: 'flex',
    gap: '12px',
    flexWrap: 'wrap',
    alignItems: 'center',
  },
  searchWrapper: {
    position: 'relative',
    flex: '1',
    minWidth: '280px',
    display: 'flex',
    alignItems: 'center',
  },
  searchIcon: {
    position: 'absolute',
    left: '12px',
    fontSize: '0.9rem',
    opacity: 0.6,
  },
  searchInput: {
    width: '100%',
    padding: '10px 36px 10px 36px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-surface)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    outline: 'none',
  },
  clearSearchBtn: {
    position: 'absolute',
    right: '12px',
    background: 'none',
    border: 'none',
    color: 'var(--text-muted)',
    cursor: 'pointer',
  },
  dropdownGroup: {
    display: 'flex',
    gap: '8px',
  },
  selectInput: {
    padding: '10px 14px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-surface)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    outline: 'none',
    cursor: 'pointer',
  },
  metricRow: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
    gap: '12px',
  },
  metricCard: {
    padding: '14px 18px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-surface)',
    border: '1px solid var(--border-card)',
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
  },
  metricLabel: {
    fontSize: '0.72rem',
    fontWeight: '700',
    color: 'var(--text-muted)',
    letterSpacing: '0.04em',
  },
  metricValue: {
    fontSize: '1.4rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
  },
  tableWrapper: {
    background: 'var(--bg-surface)',
    borderRadius: 'var(--radius-lg)',
    border: '1px solid var(--border-card)',
    overflowX: 'auto',
    minHeight: '260px',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    textAlign: 'left',
  },
  tableHeaderRow: {
    borderBottom: '1px solid var(--border-card)',
    background: 'rgba(255, 255, 255, 0.02)',
  },
  th: {
    padding: '12px 16px',
    fontSize: '0.72rem',
    fontWeight: '700',
    color: 'var(--text-muted)',
    letterSpacing: '0.05em',
    textTransform: 'uppercase',
  },
  tableRow: {
    borderBottom: '1px solid var(--border-card)',
    transition: 'background 0.15s ease',
  },
  td: {
    padding: '14px 16px',
    fontSize: '0.85rem',
    verticalAlign: 'middle',
  },
  messageCell: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
  },
  senderName: {
    fontWeight: '600',
    color: 'var(--text-primary)',
    fontSize: '0.82rem',
  },
  senderEmail: {
    fontSize: '0.72rem',
    color: 'var(--text-muted)',
  },
  verCode: {
    background: 'rgba(255, 255, 255, 0.06)',
    padding: '2px 5px',
    borderRadius: '4px',
    fontSize: '0.75rem',
  },
  actionButtonGroup: {
    display: 'flex',
    gap: '6px',
    justifyContent: 'center',
  },
  detailButton: {
    padding: '6px 10px',
    borderRadius: 'var(--radius-sm)',
    background: 'rgba(59, 130, 246, 0.12)',
    border: '1px solid rgba(59, 130, 246, 0.25)',
    color: '#93C5FD',
    fontSize: '0.75rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  copyButton: {
    padding: '6px 10px',
    borderRadius: 'var(--radius-sm)',
    background: 'rgba(255, 255, 255, 0.05)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-secondary)',
    fontSize: '0.75rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  stateContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '10px',
    padding: '60px 20px',
    textAlign: 'center',
  },
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0, 0, 0, 0.75)',
    backdropFilter: 'blur(4px)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    padding: '20px',
  },
  modalContent: {
    width: '100%',
    maxWidth: '620px',
    background: 'var(--bg-surface)',
    borderRadius: 'var(--radius-xl)',
    border: '1px solid var(--border-card)',
    boxShadow: 'var(--shadow-lg)',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  modalHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    padding: '20px 24px',
    borderBottom: '1px solid var(--border-card)',
  },
  modalTitle: {
    margin: 0,
    fontSize: '1.1rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
  },
  modalSubtitle: {
    fontSize: '0.72rem',
    color: 'var(--text-muted)',
    marginTop: '4px',
    display: 'block',
  },
  modalCloseBtn: {
    background: 'none',
    border: 'none',
    color: 'var(--text-muted)',
    fontSize: '1.2rem',
    cursor: 'pointer',
  },
  modalBody: {
    padding: '24px',
    display: 'flex',
    flexDirection: 'column',
    gap: '18px',
  },
  detailGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, 1fr)',
    gap: '12px',
    padding: '14px',
    borderRadius: 'var(--radius-md)',
    background: 'rgba(255, 255, 255, 0.02)',
    border: '1px solid var(--border-card)',
  },
  detailItem: {
    display: 'flex',
    flexDirection: 'column',
    gap: '3px',
    fontSize: '0.82rem',
  },
  detailLabel: {
    fontSize: '0.68rem',
    fontWeight: '700',
    color: 'var(--text-muted)',
    textTransform: 'uppercase',
  },
  messageBox: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  messageBoxLabel: {
    fontSize: '0.75rem',
    fontWeight: '700',
    color: 'var(--text-secondary)',
  },
  messageBoxText: {
    padding: '14px 16px',
    borderRadius: 'var(--radius-md)',
    background: 'rgba(0, 0, 0, 0.3)',
    border: '1px solid var(--border-card)',
    fontSize: '0.9rem',
    lineHeight: '1.5',
    color: 'var(--text-primary)',
    whiteSpace: 'pre-wrap',
  },
  modalFooter: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: '10px',
    padding: '16px 24px',
    borderTop: '1px solid var(--border-card)',
    background: 'rgba(255, 255, 255, 0.01)',
  },
  modalCopyBtn: {
    padding: '8px 16px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--color-primary)',
    border: 'none',
    color: 'white',
    fontSize: '0.85rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  modalDismissBtn: {
    padding: '8px 16px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    cursor: 'pointer',
  },
};
