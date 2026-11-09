/**
 * FINSMART DEVELOPER ADMIN AUTHORIZATION CONFIGURATION (PHASE 2)
 *
 * Disinkronkan dengan:
 * 1. app/src/main/java/com/example/config/DeveloperAccountConfig.kt
 * 2. firestore.rules (Fungsi isAdmin())
 *
 * PERINGATAN KEAMANAN KRITIS:
 * Whitelist di bawah ini hanya berfungsi sebagai UI-GATING (pembatas tampilan antarmuka).
 * Otorisasi sesungguhnya untuk membaca/menulis data Firestore diatur di sisi server
 * oleh aturan Firestore (firestore.rules) melalui token kriptografis Firebase Auth.
 */

// Administrator Utama Pengembang (Google Play Console Contact & Firestore Admin)
export const PRIMARY_ADMIN_EMAIL = 'lunachroc01@gmail.com';

// Pemilik Proyek Firebase (Firebase Project Owner & Google Play Project Owner)
export const PROJECT_OWNER_EMAIL = 'zweifeel@gmail.com';

// Daftar Email Pengembang Resmi yang Memiliki Hak Akses Administrator (Tunggal & Terverifikasi)
export const AUTHORIZED_ADMIN_EMAILS = [
  PRIMARY_ADMIN_EMAIL.toLowerCase(),
  PROJECT_OWNER_EMAIL.toLowerCase(),
];

/**
 * Memeriksa apakah suatu alamat email termasuk administrator resmi FinSmart.
 *
 * @param {string|null|undefined} email
 * @returns {boolean}
 */
export function isAuthorizedAdmin(email) {
  if (!email || typeof email !== 'string') return false;
  const cleanEmail = email.trim().toLowerCase();
  return AUTHORIZED_ADMIN_EMAILS.includes(cleanEmail);
}

/**
 * Metadata peran pengembang untuk tampilan antarmuka
 */
export function getAdminRoleLabel(email) {
  if (!email) return 'Tamu';
  const cleanEmail = email.trim().toLowerCase();
  if (cleanEmail === PRIMARY_ADMIN_EMAIL) {
    return 'Primary Developer (Master Dev 👑)';
  }
  if (cleanEmail === PROJECT_OWNER_EMAIL) {
    return 'Project Owner & Co-Administrator 🛡️';
  }
  if (isAuthorizedAdmin(cleanEmail)) {
    return 'Developer Admin Team 🚀';
  }
  return 'Akses Ditolak (Bukan Admin)';
}
