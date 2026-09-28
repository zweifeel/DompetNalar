const fs = require('fs');
const path = require('path');
const { GoogleGenerativeAI } = require('@google/generative-ai');

async function run() {
  const apiKey = process.env.GEMINI_API_KEY;
  const updateNotes = process.env.UPDATE_NOTES || 'Pembaruan fitur dan perbaikan bug terkini.';
  
  if (!apiKey) {
    console.error('Error: GEMINI_API_KEY tidak ditemukan.');
    process.exit(1);
  }

  const prdPath = path.join(__dirname, '../PRD.md');
  let currentPrd = '';
  
  if (fs.existsSync(prdPath)) {
    currentPrd = fs.readFileSync(prdPath, 'utf8');
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  
  // Menggunakan model gemini-3.8-flash dengan limit token yang cukup besar
  const model = genAI.getGenerativeModel({ 
    model: 'gemini-3.8-flash',
    generationConfig: {
      maxOutputTokens: 8192,
    }
  });

  const prompt = `
Kamu adalah seorang Technical Product Manager senior untuk aplikasi Android FinSmart.
Tugasmu adalah membuat/memperbarui dokumen PRD.md secara LENGKAP dan UTUH dari Bab 1 hingga Bab 14 berdasarkan catatan pembaruan berikut.

--- CATATAN PEMBARUAN / COMMIT TERBARU ---
${updateNotes}

--- DOKUMEN PRD SAAT INI ---
${currentPrd}

--- STRUKTUR WAJIB PRD (BAB 1 - 14) ---
Dokumen harus mencakup seluruh bab berikut secara detail:
1. Overview & Vision
2. Target Audience & User Personas
3. User Stories & Use Cases
4. Functional Requirements (Fitur Core, Manajemen Transaksi, Laporan, dll)
5. Non-Functional Requirements (Performa, Keamanan, Aksesibilitas)
6. System Architecture & Tech Stack (Android/Kotlin, Firebase, Local DB)
7. UI/UX Design Guidelines & User Flow
8. Data Models & Database Schema
9. API & Third-Party Integration
10. Analytics & Telemetry Metrics
11. Security & Compliance (Data Privacy, Encryption)
12. Release Strategy & Rollout Plan
13. Risks, Assumptions & Dependencies
14. Appendix & Changelog (Sertakan Tabel Riwayat Perubahan)

--- INSTRUKSI SANGAT PENTING ---
1. TULISKAN SELURUH BAB 1 SAMPAI BAB 14 SECARA UTUH DAN DETAIL. JANGAN MENGATUR/MEMOTONG/MENGHAPUS BAB APAPUN.
2. Integrasikan informasi dari Catatan Pembaruan ke bab yang relevan (misal: perubahan skrip CI/CD ke Bab 6, 12, atau 14).
3. Kembalikan HANYA dokumen Markdown PRD tanpa kalimat pembuka atau penutup tambahan.
`;

  console.log('Mengirim permintaan pembuatan PRD Bab 1-14 ke Gemini API...');
  const result = await model.generateContent(prompt);
  let responseText = result.response.text();

  // Bersihkan pembungkus format markdown jika ada
  responseText = responseText.replace(/^```markdown\n/, '').replace(/^```\n/, '').replace(/\n```$/, '');

  fs.writeFileSync(prdPath, responseText.trim(), 'utf8');
  console.log('PRD.md lengkap (Bab 1-14) berhasil diperbarui!');
}

run().catch((err) => {
  console.error('Gagal memperbarui PRD:', err);
  process.exit(1);
});
