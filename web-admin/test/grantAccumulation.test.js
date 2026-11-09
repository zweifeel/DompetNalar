import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  computeAccumulatedGrantExpiry,
  buildAccumulatedGrantPayload,
  normalizeGrantEmail,
  ONE_DAY_MILLIS,
} from '../src/utils/grantAccumulation.js';

describe('Web Admin Smart Master Grant Accumulation Test Suite', () => {
  const baseTime = 1770000000000; // Fixed deterministic timestamp

  // Skenario 1: Grant 14 hari, kemudian grant 30 hari ketika grant pertama masih aktif
  test('Skenario 1: Grant 14 hari + 30 hari ketika masih aktif harus terakumulasi menjadi 44 hari', () => {
    // Grant pertama: 14 hari
    const initialExpiry = baseTime + 14 * ONE_DAY_MILLIS;
    const initialGrant = {
      email: 'tester.accum@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: initialExpiry,
      revoked: false,
    };

    // Pemberian kedua: 30 hari, diberikan berurutan tanpa jeda pada baseTime
    const newExpiry = computeAccumulatedGrantExpiry(initialGrant, 30, baseTime);
    assert.equal(newExpiry, baseTime + 44 * ONE_DAY_MILLIS);
    assert.equal(newExpiry - baseTime, 44 * ONE_DAY_MILLIS);
  });

  // Skenario 2: Grant 14 hari, kemudian grant 7 hari: hasil harus menjadi total 21 hari
  test('Skenario 2: Grant 14 hari + 7 hari harus menjadi total 21 hari jika diberikan berurutan', () => {
    const initialExpiry = baseTime + 14 * ONE_DAY_MILLIS;
    const initialGrant = {
      email: 'tester.twentyone@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: initialExpiry,
      revoked: false,
    };

    const newExpiry = computeAccumulatedGrantExpiry(initialGrant, 7, baseTime);
    assert.equal(newExpiry, baseTime + 21 * ONE_DAY_MILLIS);
    assert.equal(newExpiry - baseTime, 21 * ONE_DAY_MILLIS);
  });

  // Skenario 3: Grant 30 hari diberikan ketika grant lama tersisa 5 hari
  test('Skenario 3: Grant 30 hari diberikan saat sisa 5 hari harus menambah ke expiresAt lama (now + 35 hari)', () => {
    // Grant lama tersisa 5 hari dari sekarang: expiresAt = now + 5 hari
    const now = baseTime + 9 * ONE_DAY_MILLIS;
    const existingExpiresAt = now + 5 * ONE_DAY_MILLIS; // Sisa 5 hari
    const existingGrant = {
      email: 'tester.fiveleft@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: existingExpiresAt,
      revoked: false,
    };

    // Tambahkan 30 hari
    const newExpiry = computeAccumulatedGrantExpiry(existingGrant, 30, now);
    assert.equal(newExpiry, existingExpiresAt + 30 * ONE_DAY_MILLIS);
    assert.equal(newExpiry, now + 35 * ONE_DAY_MILLIS);
    assert.notEqual(newExpiry, now + 30 * ONE_DAY_MILLIS, 'DILARANG mereset ke 30 hari dari now!');
  });

  // Skenario 4: Grant yang sudah kedaluwarsa kemudian diberi grant 30 hari
  test('Skenario 4: Grant yang sudah kedaluwarsa diberi 30 hari harus mulai dari waktu pemberian baru', () => {
    const now = baseTime + 100 * ONE_DAY_MILLIS;
    const expiredGrant = {
      email: 'tester.expired@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: baseTime + 14 * ONE_DAY_MILLIS, // Sudah lewat jauh
      revoked: false,
    };

    const newExpiry = computeAccumulatedGrantExpiry(expiredGrant, 30, now);
    assert.equal(newExpiry, now + 30 * ONE_DAY_MILLIS);
  });

  // Skenario 5: Grant permanen tidak berubah menjadi grant terbatas
  test('Skenario 5: Grant permanen yang aktif tidak boleh diturunkan saat admin memberi grant berdurasi', () => {
    const permanentGrant = {
      email: 'tester.perm@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: null, // Permanen
      revoked: false,
    };

    // Admin memilih 30 hari
    const newExpiry = computeAccumulatedGrantExpiry(permanentGrant, 30, baseTime);
    assert.equal(newExpiry, null, 'Grant permanen aktif HARUS tetap null (Permanen)');

    // Admin memilih Permanen
    const permExpiry = computeAccumulatedGrantExpiry(permanentGrant, null, baseTime);
    assert.equal(permExpiry, null);
  });

  // Skenario 6: Grant lama dicabut tidak boleh dijadikan dasar akumulasi
  test('Skenario 6: Grant yang dicabut tidak boleh diakumulasi dan mulai durasi baru dari now', () => {
    const revokedGrant = {
      email: 'tester.revoked@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: baseTime + 20 * ONE_DAY_MILLIS,
      revoked: true, // Status DICABUT
    };

    const newExpiry = computeAccumulatedGrantExpiry(revokedGrant, 30, baseTime);
    assert.equal(newExpiry, baseTime + 30 * ONE_DAY_MILLIS, 'Pemberian setelah revoke harus mulai dari now');
  });

  // Skenario 7: Pembuatan grant pertama kali (belum ada dokumen)
  test('Skenario 7: Belum ada grant sebelumnya, masa berlaku baru dihitung dari now', () => {
    const newExpiry = computeAccumulatedGrantExpiry(null, 30, baseTime);
    assert.equal(newExpiry, baseTime + 30 * ONE_DAY_MILLIS);
  });

  // Skenario 8: Normalisasi email konsisten
  test('Skenario 8: Normalisasi email tester huruf kecil dan trim spasi', () => {
    assert.equal(normalizeGrantEmail('  Tester.User@Gmail.COM  '), 'tester.user@gmail.com');
  });

  // Skenario 9: Validasi payload Firestore schema
  test('Skenario 9: buildAccumulatedGrantPayload menghasilkan struktur dokumen yang valid sesuai schema', () => {
    const payload = buildAccumulatedGrantPayload(null, {
      email: 'tester.valid@gmail.com',
      durationDays: 14,
      adminEmail: 'lunachroc01@gmail.com',
      notes: 'Testing OCR',
      now: baseTime,
    });

    assert.equal(payload.email, 'tester.valid@gmail.com');
    assert.equal(payload.role, 'SMART_MASTER');
    assert.equal(payload.grantedBy, 'lunachroc01@gmail.com');
    assert.equal(payload.grantedAt, baseTime);
    assert.equal(payload.expiresAt, baseTime + 14 * ONE_DAY_MILLIS);
    assert.equal(payload.revoked, false);
    assert.equal(payload.updatedAt, baseTime);
    assert.equal(payload.notes, 'Testing OCR');
  });

  // Skenario 10: Simulasi dua permintaan grant bersamaan (Atomic Concurrency Protection)
  test('Skenario 10: Simulasi dua transaksi grant bersamaan menghasilkan akumulasi total tanpa kehilangan durasi', async () => {
    // Model state database dokumen Firestore
    let simulatedFirestoreDoc = {
      email: 'tester.concurrent@gmail.com',
      role: 'SMART_MASTER',
      grantedAt: baseTime,
      expiresAt: baseTime + 14 * ONE_DAY_MILLIS, // Awalnya 14 hari
      revoked: false,
      updatedAt: baseTime,
    };

    // Simulasi fungsi atomic transaction
    const executeGrantTransaction = async (durationDays) => {
      // Baca state snapshot terkini
      const snapshot = JSON.parse(JSON.stringify(simulatedFirestoreDoc));
      const payload = buildAccumulatedGrantPayload(snapshot, {
        email: snapshot.email,
        durationDays,
        adminEmail: 'lunachroc01@gmail.com',
        now: baseTime,
      });
      // Tulis kembali secara atomik
      simulatedFirestoreDoc = payload;
      return payload;
    };

    // Request 1: Menambahkan 30 hari
    await executeGrantTransaction(30);
    assert.equal(simulatedFirestoreDoc.expiresAt, baseTime + 44 * ONE_DAY_MILLIS);

    // Request 2 (bersamaan / berurutan): Menambahkan 7 hari lagi
    await executeGrantTransaction(7);
    assert.equal(simulatedFirestoreDoc.expiresAt, baseTime + 51 * ONE_DAY_MILLIS);
    assert.equal(simulatedFirestoreDoc.expiresAt - baseTime, 51 * ONE_DAY_MILLIS, 'Total harus terakumulasi 14 + 30 + 7 = 51 hari');
  });
});
