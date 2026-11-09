import React, { useState, useEffect } from 'react';
import {
  collection,
  query,
  getDocs,
  doc,
  setDoc,
  deleteDoc,
  updateDoc,
  runTransaction,
} from 'firebase/firestore';
import { db, configStatus, auth } from '../config/firebase';
import { isAuthorizedAdmin } from '../config/adminAuth';
import StatusBanner from '../components/StatusBanner';
import {
  normalizeGrantEmail,
  buildAccumulatedGrantPayload,
} from '../utils/grantAccumulation';

async function computeGrantDocId(email) {
  const cleanEmail = email.trim().toLowerCase();
  try {
    const msgUint8 = new TextEncoder().encode(cleanEmail);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  } catch {
    return cleanEmail.replace(/[^a-zA-Z0-9]/g, '_');
  }
}

const DURATION_OPTIONS = [
  { label: '7 Hari', days: 7 },
  { label: '14 Hari (2 Minggu)', days: 14 },
  { label: '30 Hari (1 Bulan)', days: 30 },
  { label: '90 Hari (3 Bulan)', days: 90 },
  { label: '365 Hari (1 Tahun)', days: 365 },
  { label: 'Permanen (Tanpa Batas)', days: null },
];

export default function MembershipScreen({ user }) {
  // Grants State
  const [grants, setGrants] = useState([]);
  const [isGrantsLoading, setIsGrantsLoading] = useState(true);
  const [grantsError, setGrantsError] = useState(null);

  // Grant Form State
  const [targetEmail, setTargetEmail] = useState('');
  const [selectedDurationDays, setSelectedDurationDays] = useState(30);
  const [grantNotes, setGrantNotes] = useState('');
  const [isSubmittingGrant, setIsSubmittingGrant] = useState(false);
  const [formFeedback, setFormFeedback] = useState(null);

  // User Registry State
  const [syncedUsers, setSyncedUsers] = useState([]);
  const [isRegistryLoading, setIsRegistryLoading] = useState(true);
  const [registryError, setRegistryError] = useState(null);

  // Refresh State
  const [isRefreshing, setIsRefreshing] = useState(false);

  const fetchGrants = async () => {
    setIsGrantsLoading(true);
    setGrantsError(null);

    const activeUser = auth?.currentUser || user;
    if (!configStatus.isConfigured || !db || !activeUser) {
      setIsGrantsLoading(false);
      return;
    }

    try {
      const q = query(collection(db, 'tester_grants'));
      const snapshot = await getDocs(q);
      const items = [];
      snapshot.forEach((d) => {
        const data = d.data();
        let grantedAtMillis = data.grantedAt?.toDate ? data.grantedAt.toDate().getTime() : (typeof data.grantedAt === 'number' ? data.grantedAt : Date.now());
        let expiresAtMillis = data.expiresAt?.toDate ? data.expiresAt.toDate().getTime() : (typeof data.expiresAt === 'number' ? data.expiresAt : null);
        let updatedAtMillis = data.updatedAt?.toDate ? data.updatedAt.toDate().getTime() : (typeof data.updatedAt === 'number' ? data.updatedAt : Date.now());

        items.push({
          id: d.id,
          email: data.email || '',
          role: data.role || 'SMART_MASTER',
          grantedBy: data.grantedBy || '',
          grantedAt: grantedAtMillis,
          expiresAt: expiresAtMillis,
          revoked: !!data.revoked,
          updatedAt: updatedAtMillis,
          notes: data.notes || '',
        });
      });

      items.sort((a, b) => b.updatedAt - a.updatedAt);
      setGrants(items);
    } catch (err) {
      console.error('[Membership] Gagal mengambil tester_grants:', err);
      const isPermissionDenied = err.code === 'permission-denied' || (err.message && err.message.toLowerCase().includes('permission'));
      setGrantsError({
        message: err.message || 'Gagal memuat data remote grants dari Firestore.',
        code: err.code,
        isPermissionDenied,
      });
    } finally {
      setIsGrantsLoading(false);
    }
  };

  const fetchUserRegistry = async () => {
    setIsRegistryLoading(true);
    setRegistryError(null);

    const activeUser = auth?.currentUser || user;
    if (!configStatus.isConfigured || !db || !activeUser) {
      setIsRegistryLoading(false);
      return;
    }

    try {
      const q = query(collection(db, 'user_registry'));
      const snapshot = await getDocs(q);
      const items = [];
      snapshot.forEach((d) => {
        const data = d.data();
        let firstSeenMillis = data.firstSeenAt?.toDate ? data.firstSeenAt.toDate().getTime() : (typeof data.firstSeenAt === 'number' ? data.firstSeenAt : Date.now());
        let lastSeenMillis = data.lastSeenAt?.toDate ? data.lastSeenAt.toDate().getTime() : (typeof data.lastSeenAt === 'number' ? data.lastSeenAt : Date.now());

        items.push({
          id: d.id,
          uid: data.uid || d.id,
          email: data.email || '',
          displayName: data.displayName || 'Pengguna DompetNalar',
          appVersion: data.appVersion || 'Tidak dilaporkan',
          platform: data.platform || 'Android',
          firstSeenAt: firstSeenMillis,
          lastSeenAt: lastSeenMillis,
        });
      });

      items.sort((a, b) => b.lastSeenAt - a.lastSeenAt);
      setSyncedUsers(items);
    } catch (err) {
      console.error('[Membership] Gagal mengambil user_registry:', err);
      const isPermissionDenied = err.code === 'permission-denied' || (err.message && err.message.toLowerCase().includes('permission'));
      setRegistryError({
        message: err.message || 'Gagal memuat data user registry dari Firestore.',
        code: err.code,
        isPermissionDenied,
      });
    } finally {
      setIsRegistryLoading(false);
    }
  };

  const handleRefreshAll = async () => {
    setIsRefreshing(true);
    try {
      if (auth?.currentUser) {
        await auth.currentUser.getIdToken(true);
      }
    } catch (tokenErr) {
      console.warn('[Membership] Gagal refresh ID token:', tokenErr);
    }
    await Promise.all([fetchGrants(), fetchUserRegistry()]);
    setIsRefreshing(false);
  };

  useEffect(() => {
    fetchGrants();
    fetchUserRegistry();
  }, [user]);

  const handleCreateOrUpdateGrant = async (e) => {
    e?.preventDefault();
    setFormFeedback(null);

    const cleanEmail = normalizeGrantEmail(targetEmail);
    if (!cleanEmail || !cleanEmail.includes('@')) {
      setFormFeedback({ type: 'error', message: 'Masukkan alamat email tester yang valid.' });
      return;
    }

    if (!isAuthorizedAdmin(user?.email)) {
      setFormFeedback({ type: 'error', message: 'Akses ditolak: Hanya administrator resmi yang berwenang memberikan grant.' });
      return;
    }

    setIsSubmittingGrant(true);
    try {
      const docId = await computeGrantDocId(cleanEmail);
      const grantDocRef = doc(db, 'tester_grants', docId);

      let finalPayload = null;
      let wasAccumulated = false;

      // Gunakan Firestore Transaction untuk menjamin perhitungan atomik dan aman terhadap permintaan bersamaan
      await runTransaction(db, async (transaction) => {
        const grantSnap = await transaction.get(grantDocRef);
        const existingData = grantSnap.exists() ? grantSnap.data() : null;
        const now = Date.now();

        let existingExpiresAt = null;
        if (existingData?.expiresAt && typeof existingData.expiresAt.toDate === 'function') {
          existingExpiresAt = existingData.expiresAt.toDate().getTime();
        } else if (typeof existingData?.expiresAt === 'number') {
          existingExpiresAt = existingData.expiresAt;
        }

        const isExistingActive = existingData && !existingData.revoked && (existingExpiresAt === null || existingExpiresAt > now);

        finalPayload = buildAccumulatedGrantPayload(existingData, {
          email: cleanEmail,
          durationDays: selectedDurationDays,
          adminEmail: user.email,
          notes: grantNotes,
          now: now,
        });

        transaction.set(grantDocRef, finalPayload);
        wasAccumulated = Boolean(isExistingActive && selectedDurationDays !== null && existingExpiresAt !== null);
      });

      const expiryDisplay = finalPayload?.expiresAt ? formatDate(finalPayload.expiresAt) : 'Permanen';
      const successMsg = wasAccumulated
        ? `Grant Smart Master berhasil diakumulasikan untuk "${cleanEmail}" (+${selectedDurationDays} hari, masa berlaku diperpanjang hingga: ${expiryDisplay}).`
        : `Grant Smart Master berhasil diterbitkan untuk "${cleanEmail}" (${selectedDurationDays ? `${selectedDurationDays} hari, berlaku hingga: ${expiryDisplay}` : 'Permanen'}).`;

      setFormFeedback({
        type: 'success',
        message: successMsg,
      });
      setTargetEmail('');
      setGrantNotes('');
      fetchGrants();
    } catch (err) {
      console.error('[Membership] Gagal menerbitkan grant:', err);
      const isPermissionDenied = err.code === 'permission-denied' || (err.message && err.message.toLowerCase().includes('permission'));
      const errorMsg = isPermissionDenied
        ? 'Akses Firestore Ditolak (PERMISSION_DENIED): Aturan firestore.rules di Firebase Console belum mengizinkan penulisan ke koleksi tester_grants. Pastikan aturan lokal telah dipublikasikan di Firebase Console.'
        : `Gagal menerbitkan grant: ${err.message}`;
      setFormFeedback({ type: 'error', message: errorMsg });
    } finally {
      setIsSubmittingGrant(false);
    }
  };

  const handleToggleRevoke = async (item) => {
    const newRevoked = !item.revoked;
    const confirmMsg = newRevoked
      ? `Cabut hak Smart Master dari tester ${item.email}?`
      : `Aktifkan kembali hak Smart Master untuk tester ${item.email}?`;

    if (!window.confirm(confirmMsg)) return;

    try {
      await updateDoc(doc(db, 'tester_grants', item.id), {
        revoked: newRevoked,
        updatedAt: Date.now(),
      });
      fetchGrants();
    } catch (err) {
      console.error('[Membership] Gagal toggle revoke grant:', err);
      const isPermissionDenied = err.code === 'permission-denied' || (err.message && err.message.toLowerCase().includes('permission'));
      const msg = isPermissionDenied
        ? 'Gagal mengubah status: Akses Firestore Ditolak (PERMISSION_DENIED). Periksa firestore.rules di Firebase Console.'
        : `Gagal mengubah status grant: ${err.message}`;
      alert(msg);
    }
  };

  const handleDeleteGrant = async (item) => {
    if (!window.confirm(`Hapus permanen grant untuk ${item.email}? Tester akan kehilangan akses pada verifikasi berikutnya.`)) return;

    try {
      await deleteDoc(doc(db, 'tester_grants', item.id));
      fetchGrants();
    } catch (err) {
      console.error('[Membership] Gagal menghapus grant:', err);
      const isPermissionDenied = err.code === 'permission-denied' || (err.message && err.message.toLowerCase().includes('permission'));
      const msg = isPermissionDenied
        ? 'Gagal menghapus: Akses Firestore Ditolak (PERMISSION_DENIED). Periksa firestore.rules di Firebase Console.'
        : `Gagal menghapus grant: ${err.message}`;
      alert(msg);
    }
  };

  const handleQuickGrant = (userEmail) => {
    setTargetEmail(userEmail);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const formatDate = (millis) => {
    if (!millis) return '-';
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
      <div style={styles.pageHeader}>
        <div>
          <h1 style={styles.pageTitle}>Remote Smart Master Grants & User Registry</h1>
          <p style={styles.pageSubtitle}>
            Pemberian akses VIP Smart Master jarak jauh untuk tester & pemantauan direktori akun tersinkron.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={handleRefreshAll}
            disabled={isRefreshing || isGrantsLoading || isRegistryLoading}
            style={{
              ...styles.refreshBtn,
              opacity: (isRefreshing || isGrantsLoading || isRegistryLoading) ? 0.7 : 1,
            }}
            title="Segarkan token autentikasi dan muat ulang data Firestore"
          >
            {isRefreshing ? '🔄 Menyegarkan Token & Data...' : '🔄 Segarkan Semua Data'}
          </button>
        </div>
      </div>

      {/* Banner Diagnostik jika Izin Firestore Cloud Ditolak */}
      {(grantsError?.isPermissionDenied || registryError?.isPermissionDenied) && (
        <StatusBanner
          type="warning"
          title="Verifikasi Firestore Cloud: Aturan firestore.rules Perlu Dipublikasikan di Firebase Console"
        >
          Koneksi ke Firestore cloud menolak permintaan pembacaan (<code>permission-denied</code>).
          Hal ini terjadi karena aturan keamanan lokal terbaru pada <code>firestore.rules</code> belum dipublikasikan ke server cloud Firebase Console untuk proyek{' '}
          <strong>finsmart-47fdd</strong>.
          <br /><br />
          <strong>Langkah Penyelesaian Mandiri:</strong>
          <ol style={{ margin: '8px 0 0 18px', padding: 0 }}>
            <li>Buka <em>Firebase Console &gt; Firestore Database &gt; Rules</em>.</li>
            <li>Salin seluruh isi file lokal <code>firestore.rules</code> dari repository ini.</li>
            <li>Klik tombol <strong>Publish</strong> di Firebase Console.</li>
            <li>Setelah dipublikasikan, klik tombol <strong>🔄 Segarkan Semua Data</strong> di atas.</li>
          </ol>
        </StatusBanner>
      )}

      {/* Bagian 1: Form Penerbitan Remote Grant */}
      <div style={styles.card}>
        <div style={styles.cardHeader}>
          <span style={{ fontSize: '1.4rem' }}>👑</span>
          <div>
            <h3 style={styles.cardTitle}>Beri Akses Smart Master (Remote Grant)</h3>
            <p style={styles.cardSubtitle}>
              Berikan hak akses Smart Master kepada akun Google tester tanpa mewajibkan pembelian di Google Play.
            </p>
          </div>
          <span className="badge badge-warning" style={{ marginLeft: 'auto' }}>Admin Only</span>
        </div>

        <form onSubmit={handleCreateOrUpdateGrant} style={styles.grantForm}>
          {formFeedback && (
            <div style={{
              ...styles.alertBanner,
              background: formFeedback.type === 'error' ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
              borderColor: formFeedback.type === 'error' ? 'rgba(239, 68, 68, 0.4)' : 'rgba(16, 185, 129, 0.4)',
              color: formFeedback.type === 'error' ? '#FCA5A5' : '#6EE7B7'
            }}>
              {formFeedback.type === 'error' ? '⚠️' : '✓'} {formFeedback.message}
            </div>
          )}

          <div style={styles.formRow}>
            <div style={{ flex: '2', minWidth: '260px' }}>
              <label style={styles.inputLabel}>Alamat Email Google Akun Tester *</label>
              <input
                type="email"
                placeholder="contoh: tester.lukas@gmail.com"
                value={targetEmail}
                onChange={(e) => setTargetEmail(e.target.value)}
                style={styles.textInput}
                required
              />
            </div>

            <div style={{ flex: '1', minWidth: '180px' }}>
              <label style={styles.inputLabel}>Masa Berlaku Grant *</label>
              <select
                value={selectedDurationDays === null ? 'null' : selectedDurationDays}
                onChange={(e) => setSelectedDurationDays(e.target.value === 'null' ? null : parseInt(e.target.value, 10))}
                style={styles.selectInput}
              >
                {DURATION_OPTIONS.map((opt) => (
                  <option key={opt.label} value={opt.days === null ? 'null' : opt.days}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label style={styles.inputLabel}>Catatan / Keperluan Pengujian (Opsional)</label>
            <input
              type="text"
              placeholder="contoh: Uji coba fitur OCR & Gemini AI untuk Closed Testing Track"
              value={grantNotes}
              onChange={(e) => setGrantNotes(e.target.value)}
              style={styles.textInput}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
            <button
              type="submit"
              disabled={isSubmittingGrant}
              style={{
                ...styles.submitBtn,
                opacity: isSubmittingGrant ? 0.7 : 1,
              }}
            >
              {isSubmittingGrant ? 'Menerbitkan Grant...' : '🚀 Terbitkan Grant Smart Master'}
            </button>
          </div>
        </form>
      </div>

      {/* Bagian 2: Tabel Remote Tester Grants */}
      <div style={styles.card}>
        <div style={styles.cardHeader}>
          <span style={{ fontSize: '1.4rem' }}>📋</span>
          <div>
            <h3 style={styles.cardTitle}>Daftar Remote Smart Master Grants Aktif</h3>
            <p style={styles.cardSubtitle}>
              Koleksi Firestore: <code style={styles.codeBadge}>tester_grants</code>
            </p>
          </div>
          <span
            className={`badge ${isGrantsLoading ? 'badge-neutral' : grantsError ? 'badge-danger' : 'badge-info'}`}
            style={{ marginLeft: 'auto' }}
          >
            {isGrantsLoading ? 'Memuat...' : grantsError ? 'Tidak tersedia (Error)' : `${grants.length} Akun Terdaftar`}
          </span>
        </div>

        {grantsError && (
          <div style={{ ...styles.alertBanner, background: 'rgba(239, 68, 68, 0.15)', borderColor: 'rgba(239, 68, 68, 0.4)', color: '#FCA5A5' }}>
            ⚠️ {grantsError.message}
          </div>
        )}

        <div style={styles.tableWrapper}>
          {isGrantsLoading ? (
            <div style={styles.stateContainer}>
              <span className="spinner" style={{ width: '24px', height: '24px' }} />
              <span>Memuat daftar grant dari Firestore...</span>
            </div>
          ) : grantsError ? (
            <div style={styles.errorStateCard}>
              <div style={styles.errorTitle}>
                {grantsError.isPermissionDenied ? '🔒 Akses Koleksi Ditolak (PERMISSION_DENIED)' : '⚠️ Gagal Memuat Data Grant'}
              </div>
              <div style={styles.errorDesc}>
                {grantsError.message}
              </div>
              {grantsError.isPermissionDenied && (
                <div style={styles.errorHint}>
                  <strong>Penyebab:</strong> Aturan keamanan pada <code>firestore.rules</code> di Firebase Console belum mengizinkan email Anda untuk melakukan <code>list</code> pada koleksi <code>tester_grants</code>, atau aturan Phase 4B belum dipublikasikan di Console Cloud.
                  <br />
                  <strong>Solusi:</strong> Salin aturan <code>firestore.rules</code> dari repository lokal ke <em>Firebase Console &gt; Firestore Database &gt; Rules</em> lalu klik <em>Publish</em>.
                </div>
              )}
              <button onClick={handleRefreshAll} style={styles.retryBtn}>
                🔄 Coba Muat Ulang Data
              </button>
            </div>
          ) : grants.length === 0 ? (
            <div style={styles.stateContainer}>
              <span>📭 Belum ada tester grant yang diterbitkan. Gunakan formulir di atas untuk menambahkan.</span>
            </div>
          ) : (
            <table style={styles.table}>
              <thead>
                <tr style={styles.thRow}>
                  <th style={styles.th}>Email Tester</th>
                  <th style={styles.th}>Peran Diberikan</th>
                  <th style={styles.th}>Status</th>
                  <th style={styles.th}>Kedaluwarsa</th>
                  <th style={styles.th}>Diberikan Oleh</th>
                  <th style={styles.th}>Catatan</th>
                  <th style={{ ...styles.th, textAlign: 'center' }}>Aksi</th>
                </tr>
              </thead>
              <tbody>
                {grants.map((item) => {
                  const now = Date.now();
                  const isExpired = item.expiresAt && item.expiresAt <= now;
                  const isRevoked = item.revoked;

                  let badgeLabel = 'Aktif 👑';
                  let badgeClass = 'badge-success';
                  if (isRevoked) {
                    badgeLabel = 'Dicabut 🚫';
                    badgeClass = 'badge-danger';
                  } else if (isExpired) {
                    badgeLabel = 'Kedaluwarsa ⌛';
                    badgeClass = 'badge-neutral';
                  }

                  return (
                    <tr key={item.id} style={styles.tr}>
                      <td style={styles.td}>
                        <strong>{item.email}</strong>
                      </td>
                      <td style={styles.td}>
                        <span className="badge badge-info" style={{ fontSize: '0.75rem' }}>
                          {item.role}
                        </span>
                      </td>
                      <td style={styles.td}>
                        <span className={`badge ${badgeClass}`} style={{ fontSize: '0.75rem' }}>
                          {badgeLabel}
                        </span>
                      </td>
                      <td style={styles.td}>
                        {item.expiresAt ? formatDate(item.expiresAt) : <span style={{ color: '#34D399' }}>Permanen</span>}
                      </td>
                      <td style={{ ...styles.td, fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        {item.grantedBy}
                      </td>
                      <td style={{ ...styles.td, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                        {item.notes || '-'}
                      </td>
                      <td style={{ ...styles.td, textAlign: 'center' }}>
                        <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
                          <button
                            onClick={() => handleToggleRevoke(item)}
                            style={{
                              ...styles.actionBtn,
                              background: isRevoked ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                              color: isRevoked ? '#34D399' : '#F87171',
                            }}
                          >
                            {isRevoked ? 'Aktifkan' : 'Cabut'}
                          </button>
                          <button
                            onClick={() => handleDeleteGrant(item)}
                            style={{
                              ...styles.actionBtn,
                              background: 'rgba(255, 255, 255, 0.05)',
                              color: 'var(--text-muted)',
                            }}
                          >
                            Hapus
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
      </div>

      {/* Bagian 3: Direktori Akun Tersinkron (User Registry) */}
      <div style={styles.card}>
        <div style={styles.cardHeader}>
          <span style={{ fontSize: '1.4rem' }}>👥</span>
          <div>
            <h3 style={styles.cardTitle}>Direktori Akun Tersinkron (User Registry)</h3>
            <p style={styles.cardSubtitle}>
              Koleksi Firestore: <code style={styles.codeBadge}>user_registry</code>
            </p>
          </div>
          <span
            className={`badge ${isRegistryLoading ? 'badge-neutral' : registryError ? 'badge-danger' : 'badge-neutral'}`}
            style={{ marginLeft: 'auto' }}
          >
            {isRegistryLoading ? 'Memuat...' : registryError ? 'Tidak tersedia (Error)' : `${syncedUsers.length} Pengguna Aktif`}
          </span>
        </div>

        {/* Banner Batasan Teknis Jujur */}
        <StatusBanner
          type="info"
          title="Batasan Cakupan Direktori Akun (Zero-Cost Architecture)"
        >
          Direktori di bawah ini merekam akun yang telah login ke aplikasi DompetNalar Android dan berhasil menyinkronkan profil dasar ke Firestore.
          <strong> Direktori ini BUKAN daftar seluruh Firebase Authentication</strong> (karena Web Client SDK tidak menyediakan API <code>listUsers()</code> tanpa server berbayar).
          Privasi terjamin: <em>Hanya metadata administratif minimum yang disimpan. Tidak ada data saldo, transaksi, rekening, atau data finansial yang dikirim.</em>
        </StatusBanner>

        {registryError && (
          <div style={{ ...styles.alertBanner, background: 'rgba(239, 68, 68, 0.15)', borderColor: 'rgba(239, 68, 68, 0.4)', color: '#FCA5A5' }}>
            ⚠️ {registryError.message}
          </div>
        )}

        <div style={styles.tableWrapper}>
          {isRegistryLoading ? (
            <div style={styles.stateContainer}>
              <span className="spinner" style={{ width: '24px', height: '24px' }} />
              <span>Memuat user registry dari Firestore...</span>
            </div>
          ) : registryError ? (
            <div style={styles.errorStateCard}>
              <div style={styles.errorTitle}>
                {registryError.isPermissionDenied ? '🔒 Akses Direktori Ditolak (PERMISSION_DENIED)' : '⚠️ Gagal Memuat Direktori Akun'}
              </div>
              <div style={styles.errorDesc}>
                {registryError.message}
              </div>
              {registryError.isPermissionDenied && (
                <div style={styles.errorHint}>
                  <strong>Penyebab:</strong> Koleksi <code>user_registry</code> dibatasi hanya untuk administrator resmi di <code>firestore.rules</code>. Pastikan akun login Anda adalah <code>lunachroc01@gmail.com</code> atau <code>zweifeel@gmail.com</code> dan aturan sudah dipublikasikan di Firebase Console.
                </div>
              )}
              <button onClick={handleRefreshAll} style={styles.retryBtn}>
                🔄 Coba Muat Ulang Data
              </button>
            </div>
          ) : syncedUsers.length === 0 ? (
            <div style={styles.stateContainer}>
              <span>Belum ada akun yang tersinkron di koleksi user_registry.</span>
            </div>
          ) : (
            <table style={styles.table}>
              <thead>
                <tr style={styles.thRow}>
                  <th style={styles.th}>Email Pengguna</th>
                  <th style={styles.th}>Nama Tampilan</th>
                  <th style={styles.th}>Versi App</th>
                  <th style={styles.th}>Platform</th>
                  <th style={styles.th}>Pertama Aktif</th>
                  <th style={styles.th}>Terakhir Aktif</th>
                  <th style={{ ...styles.th, textAlign: 'center' }}>Aksi Cepat</th>
                </tr>
              </thead>
              <tbody>
                {syncedUsers.map((u) => (
                  <tr key={u.id} style={styles.tr}>
                    <td style={styles.td}>
                      <strong>{u.email}</strong>
                    </td>
                    <td style={styles.td}>{u.displayName}</td>
                    <td style={styles.td}>
                      {u.appVersion && u.appVersion !== 'Tidak dilaporkan' ? (
                        <code style={styles.codeBadge}>v{u.appVersion}</code>
                      ) : (
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Tidak dilaporkan</span>
                      )}
                    </td>
                    <td style={styles.td}>{u.platform}</td>
                    <td style={{ ...styles.td, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      {formatDate(u.firstSeenAt)}
                    </td>
                    <td style={{ ...styles.td, fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      {formatDate(u.lastSeenAt)}
                    </td>
                    <td style={{ ...styles.td, textAlign: 'center' }}>
                      <button
                        onClick={() => handleQuickGrant(u.email)}
                        style={styles.quickGrantBtn}
                        title="Beri grant Smart Master untuk akun ini"
                      >
                        ⚡ Beri Grant
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '24px',
  },
  pageHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    flexWrap: 'wrap',
    gap: '16px',
  },
  pageTitle: {
    fontSize: '1.5rem',
    fontWeight: '800',
    color: 'var(--text-primary)',
    marginBottom: '4px',
  },
  pageSubtitle: {
    fontSize: '0.88rem',
    color: 'var(--text-secondary)',
  },
  refreshBtn: {
    padding: '8px 16px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  card: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    borderRadius: 'var(--radius-lg)',
    padding: '24px',
    display: 'flex',
    flexDirection: 'column',
    gap: '18px',
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  cardTitle: {
    fontSize: '1.05rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
    margin: 0,
  },
  cardSubtitle: {
    fontSize: '0.8rem',
    color: 'var(--text-secondary)',
    marginTop: '2px',
  },
  codeBadge: {
    background: 'rgba(255, 255, 255, 0.08)',
    padding: '2px 6px',
    borderRadius: '4px',
    fontSize: '0.78rem',
    color: '#93C5FD',
  },
  grantForm: {
    display: 'flex',
    flexDirection: 'column',
    gap: '14px',
    background: 'var(--bg-surface)',
    padding: '20px',
    borderRadius: 'var(--radius-md)',
    border: '1px solid var(--border-card)',
  },
  formRow: {
    display: 'flex',
    gap: '16px',
    flexWrap: 'wrap',
  },
  inputLabel: {
    display: 'block',
    fontSize: '0.75rem',
    fontWeight: '700',
    color: 'var(--text-muted)',
    marginBottom: '6px',
    textTransform: 'uppercase',
    letterSpacing: '0.04em',
  },
  textInput: {
    width: '100%',
    padding: '10px 14px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    outline: 'none',
  },
  selectInput: {
    width: '100%',
    padding: '10px 14px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg-card)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.85rem',
    outline: 'none',
    cursor: 'pointer',
  },
  submitBtn: {
    padding: '10px 20px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--color-primary)',
    border: 'none',
    color: '#FFFFFF',
    fontSize: '0.85rem',
    fontWeight: '700',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  alertBanner: {
    padding: '12px 16px',
    borderRadius: 'var(--radius-md)',
    border: '1px solid',
    fontSize: '0.85rem',
  },
  tableWrapper: {
    background: 'var(--bg-surface)',
    borderRadius: 'var(--radius-md)',
    border: '1px solid var(--border-card)',
    overflowX: 'auto',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    textAlign: 'left',
  },
  thRow: {
    borderBottom: '1px solid var(--border-card)',
    background: 'rgba(255, 255, 255, 0.02)',
  },
  th: {
    padding: '12px 14px',
    fontSize: '0.72rem',
    fontWeight: '700',
    color: 'var(--text-muted)',
    letterSpacing: '0.05em',
    textTransform: 'uppercase',
  },
  tr: {
    borderBottom: '1px solid var(--border-card)',
  },
  td: {
    padding: '12px 14px',
    fontSize: '0.84rem',
    verticalAlign: 'middle',
  },
  actionBtn: {
    padding: '4px 10px',
    borderRadius: 'var(--radius-sm)',
    border: 'none',
    fontSize: '0.75rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  quickGrantBtn: {
    padding: '6px 12px',
    borderRadius: 'var(--radius-sm)',
    background: 'rgba(59, 130, 246, 0.15)',
    border: '1px solid rgba(59, 130, 246, 0.3)',
    color: '#93C5FD',
    fontSize: '0.75rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  stateContainer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '10px',
    padding: '40px 20px',
    color: 'var(--text-secondary)',
    fontSize: '0.85rem',
  },
  errorStateCard: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    gap: '10px',
    padding: '24px',
    background: 'rgba(239, 68, 68, 0.08)',
    borderLeft: '4px solid #EF4444',
  },
  errorTitle: {
    fontSize: '0.95rem',
    fontWeight: '700',
    color: '#F87171',
  },
  errorDesc: {
    fontSize: '0.85rem',
    color: 'var(--text-secondary)',
    lineHeight: 1.5,
  },
  errorHint: {
    fontSize: '0.82rem',
    color: 'var(--text-muted)',
    lineHeight: 1.55,
    background: 'rgba(0, 0, 0, 0.2)',
    padding: '12px 14px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    width: '100%',
    boxSizing: 'border-box',
  },
  retryBtn: {
    padding: '8px 16px',
    borderRadius: 'var(--radius-sm)',
    background: 'rgba(255, 255, 255, 0.08)',
    border: '1px solid var(--border-card)',
    color: 'var(--text-primary)',
    fontSize: '0.8rem',
    fontWeight: '600',
    cursor: 'pointer',
    marginTop: '6px',
  },
};
