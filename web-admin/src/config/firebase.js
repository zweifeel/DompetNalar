import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

/**
 * FINSMART FIREBASE WEB CLIENT CONFIGURATION (PHASE 2)
 *
 * Otoritas Target:
 * - Project ID: finsmart-47fdd
 * - Project Number: 1029968996578
 * - Web Client OAuth ID: 1029968996578-kd1pohf7nhge6mmm3m3uagr83e40tti6.apps.googleusercontent.com
 */

export const TARGET_PROJECT_ID = 'finsmart-47fdd';
export const TARGET_PROJECT_NUMBER = '1029968996578';
export const INVALID_LEGACY_PROJECT_ID = 'gen-lang-client-0960440438';

const envApiKey = import.meta.env.VITE_FIREBASE_API_KEY;
const envAuthDomain = import.meta.env.VITE_FIREBASE_AUTH_DOMAIN;
const envProjectId = import.meta.env.VITE_FIREBASE_PROJECT_ID;
const envStorageBucket = import.meta.env.VITE_FIREBASE_STORAGE_BUCKET;
const envMessagingSenderId = import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID;
const envAppId = import.meta.env.VITE_FIREBASE_APP_ID;

// Validasi Kelengkapan & Keamanan Konfigurasi
export function evaluateFirebaseConfiguration() {
  const errors = [];
  const isMissing = (val) => !val || val.includes('YOUR_') || val.trim() === '';

  if (isMissing(envApiKey)) {
    errors.push('VITE_FIREBASE_API_KEY belum diisi di file .env.');
  }

  if (isMissing(envAppId)) {
    errors.push('VITE_FIREBASE_APP_ID belum diisi di file .env.');
  }

  // Proteksi Keras: Tolak Proyek yang Salah (gen-lang-client-0960440438)
  if (envProjectId === INVALID_LEGACY_PROJECT_ID) {
    errors.push(
      `DETEKSI PROYEK SALAH: VITE_FIREBASE_PROJECT_ID bernilai "${INVALID_LEGACY_PROJECT_ID}". Proyek resmi FinSmart adalah "${TARGET_PROJECT_ID}".`
    );
  } else if (!envProjectId || envProjectId !== TARGET_PROJECT_ID) {
    errors.push(
      `Project ID tidak cocok: Ditemukan "${envProjectId || '(kosong)'}", wajib "${TARGET_PROJECT_ID}".`
    );
  }

  return {
    isConfigured: errors.length === 0,
    errors,
  };
}

export const configStatus = evaluateFirebaseConfiguration();

let app = null;
let auth = null;
let db = null;
let googleProvider = null;

if (configStatus.isConfigured) {
  const firebaseConfig = {
    apiKey: envApiKey,
    authDomain: envAuthDomain || `${TARGET_PROJECT_ID}.firebaseapp.com`,
    projectId: TARGET_PROJECT_ID,
    storageBucket: envStorageBucket || `${TARGET_PROJECT_ID}.firebasestorage.app`,
    messagingSenderId: envMessagingSenderId || TARGET_PROJECT_NUMBER,
    appId: envAppId,
  };

  try {
    app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
    auth = getAuth(app);
    db = getFirestore(app);
    googleProvider = new GoogleAuthProvider();
    googleProvider.setCustomParameters({
      prompt: 'select_account',
    });
  } catch (error) {
    console.error('[FinSmart Firebase] Gagal inisialisasi Firebase Web SDK:', error);
    configStatus.isConfigured = false;
    configStatus.errors.push(`Inisialisasi Firebase gagal: ${error.message}`);
  }
}

/**
 * Masuk menggunakan Google Sign-In (Firebase Popup)
 */
export async function signInWithGoogle() {
  if (!configStatus.isConfigured || !auth || !googleProvider) {
    throw new Error('Konfigurasi Firebase belum siap. Silakan lengkapi file .env terlebih dahulu.');
  }

  try {
    const result = await signInWithPopup(auth, googleProvider);
    return {
      user: result.user,
      token: await result.user.getIdToken(),
    };
  } catch (error) {
    // Terjemahkan kode error Firebase Auth ke dalam Bahasa Indonesia yang ramah
    if (error.code === 'auth/popup-closed-by-user') {
      const err = new Error('Jendela masuk Google ditutup sebelum proses selesai.');
      err.code = 'USER_CANCELED';
      throw err;
    }
    if (error.code === 'auth/unauthorized-domain') {
      const currentHost = window.location.hostname;
      const err = new Error(
        `Domain "${currentHost}" belum didaftarkan di Firebase Console. Buka Firebase Console > Authentication > Settings > Authorized Domains dan tambahkan "${currentHost}".`
      );
      err.code = 'UNAUTHORIZED_DOMAIN';
      throw err;
    }
    if (error.code === 'auth/network-request-failed') {
      const err = new Error('Koneksi internet bermasalah. Periksa jaringan Anda dan coba lagi.');
      err.code = 'NETWORK_ERROR';
      throw err;
    }
    throw error;
  }
}

/**
 * Keluar dari sesi pengembang
 */
export async function signOutAdmin() {
  if (!auth) return;
  await signOut(auth);
}

/**
 * Listener status autentikasi pengguna
 */
export function onAdminAuthStateChanged(callback) {
  if (!auth) {
    callback(null);
    return () => {};
  }
  return onAuthStateChanged(auth, callback);
}

export { auth, db, googleProvider };
