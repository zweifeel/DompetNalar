/**
 * Utility untuk Perhitungan Akumulasi Durasi Remote Smart Master Grant.
 *
 * Menerapkan aturan bisnis resmi:
 * 1. Belum ada grant aktif -> masa berlaku = now + durasi baru.
 * 2. Grant lama masih aktif -> tambahkan durasi baru ke expiresAt lama yang masih berlaku (akumulasi).
 * 3. Grant lama tersisa n hari -> tambahkan durasi baru ke expiresAt lama (bukan reset dari now).
 * 4. Grant lama sudah kedaluwarsa -> mulai durasi baru dari now.
 * 5. Grant lama dicabut (revoked) -> tidak boleh diakumulasi, mulai durasi baru dari now.
 * 6. Grant permanen yang aktif -> tidak pernah diturunkan menjadi durasi terbatas (tetap permanen / null).
 * 7. Admin memilih Permanen -> selalu hasilkan null (Permanen).
 */

export const ONE_DAY_MILLIS = 86400000; // 24 * 60 * 60 * 1000

/**
 * Normalisasi alamat email tester secara konsisten.
 */
export function normalizeGrantEmail(email) {
  return (email || '').trim().toLowerCase();
}

/**
 * Menghitung masa berlaku (expiresAt) untuk remote grant Smart Master dengan aturan akumulasi.
 *
 * @param {Object|null} existingGrant Data grant terkini dari Firestore
 * @param {number|null} durationDays Durasi tambahan dalam hari, atau null untuk Permanen
 * @param {number} now Timestamp waktu saat ini (epoch millis)
 * @returns {number|null} Timestamp expiresAt dalam millis, atau null jika Permanen
 */
export function computeAccumulatedGrantExpiry(existingGrant, durationDays, now = Date.now()) {
  // 1. Jika durasi baru yang dipilih adalah Permanen (null), hasil selalu Permanen
  if (durationDays === null || durationDays === undefined) {
    return null;
  }

  const days = Number(durationDays);
  if (isNaN(days) || days <= 0) {
    throw new Error('Durasi hari grant harus berupa angka positif.');
  }

  const durationMillis = Math.round(days * ONE_DAY_MILLIS);

  // Jika belum ada grant sebelumnya
  if (!existingGrant) {
    return now + durationMillis;
  }

  // Parsing expiresAt lama (mendukung format number atau Firestore Timestamp)
  let existingExpiresAt = null;
  if (existingGrant.expiresAt && typeof existingGrant.expiresAt.toDate === 'function') {
    existingExpiresAt = existingGrant.expiresAt.toDate().getTime();
  } else if (typeof existingGrant.expiresAt === 'number') {
    existingExpiresAt = existingGrant.expiresAt;
  }

  const isRevoked = Boolean(existingGrant.revoked);
  const isPermanent = !isRevoked && existingExpiresAt === null && (existingGrant.role === 'SMART_MASTER' || !existingGrant.role);

  // 2. Grant permanen yang aktif TIDAK PERNAH diturunkan menjadi grant berdurasi terbatas
  if (isPermanent) {
    return null;
  }

  // 3. Grant yang dicabut (revoked) TIDAK BOLEH dianggap aktif atau diakumulasi
  if (isRevoked) {
    return now + durationMillis;
  }

  // 4. Grant lama sudah kedaluwarsa: mulai durasi baru dari waktu sekarang (now)
  if (existingExpiresAt !== null && existingExpiresAt <= now) {
    return now + durationMillis;
  }

  // 5. Grant lama masih aktif: tambahkan durasi baru ke expiresAt lama yang masih berlaku (AKUMULASI)
  if (existingExpiresAt !== null && existingExpiresAt > now) {
    return existingExpiresAt + durationMillis;
  }

  // Fallback default
  return now + durationMillis;
}

/**
 * Membangun payload dokumen Firestore yang siap disimpan via set/update transaction.
 *
 * @param {Object|null} existingGrant Data grant lama dari Firestore
 * @param {Object} options Detail penerbitan grant baru
 * @returns {Object} Payload dokumen Firestore valid sesuai firestore.rules
 */
export function buildAccumulatedGrantPayload(existingGrant, {
  email,
  durationDays,
  adminEmail,
  notes = '',
  now = Date.now(),
}) {
  const cleanEmail = normalizeGrantEmail(email);
  if (!cleanEmail || !cleanEmail.includes('@')) {
    throw new Error('Alamat email tester tidak valid.');
  }

  const newExpiresAt = computeAccumulatedGrantExpiry(existingGrant, durationDays, now);

  // Tentukan grantedAt:
  // Pertahankan grantedAt asli jika ini adalah akumulasi dari grant yang sedang aktif
  let grantedAt = now;
  if (existingGrant) {
    let existingGrantedAt = existingGrant.grantedAt && typeof existingGrant.grantedAt.toDate === 'function'
      ? existingGrant.grantedAt.toDate().getTime()
      : (typeof existingGrant.grantedAt === 'number' ? existingGrant.grantedAt : 0);

    let existingExpiresAt = existingGrant.expiresAt && typeof existingGrant.expiresAt.toDate === 'function'
      ? existingGrant.expiresAt.toDate().getTime()
      : (typeof existingGrant.expiresAt === 'number' ? existingGrant.expiresAt : null);

    const isExistingActive = !existingGrant.revoked && (existingExpiresAt === null || existingExpiresAt > now);
    if (isExistingActive && existingGrantedAt > 0) {
      grantedAt = existingGrantedAt;
    }
  }

  const cleanNotes = (notes || '').trim() || (existingGrant?.notes || '');

  return {
    email: cleanEmail,
    role: 'SMART_MASTER', // STRICT: HANYA SMART_MASTER, DILARANG MASTER_DEV!
    grantedBy: adminEmail || existingGrant?.grantedBy || '',
    grantedAt: grantedAt,
    expiresAt: newExpiresAt,
    revoked: false,
    updatedAt: now,
    notes: cleanNotes,
  };
}
