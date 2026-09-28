const fs = require('fs');
const path = require('path');
const { GoogleGenerativeAI } = require('@google/generative-ai');

// Fungsi penunda (delay)
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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
  
  // Daftar model utama dan fallback jika server 503/sibuk
  const modelsToTry = ['gemini-3.8-flash', 'gemini-1.5-flash', 'gemini-2.0-flash'];

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
2. Integrasikan informasi dari Catatan Pembaruan ke bab yang relevan.
3. Kembalikan HANYA dokumen Markdown PRD tanpa kalimat pembuka atau penutup tambahan.
`;

  let responseText = null;

  for (const modelName of modelsToTry) {
    console.log(`Mencoba menggunakan model: ${modelName}...`);
    const model = genAI.getGenerativeModel({ 
      model: modelName,
      generationConfig: { maxOutputTokens: 8192 }
    });

    // Coba hingga 3 kali per model jika mendapat error 503
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        console.log(`Mengirim permintaan ke Gemini API (${modelName}, percobaan ${attempt})...`);
        const result = await model.generateContent(prompt);
        responseText = result.response.text();
        if (responseText) break;
      } catch (err) {
        console.warn(`Peringatan (${modelName} percobaan ${attempt}): ${err.message}`);
        if (attempt < 3) {
          console.log('Menunggu 5 detik sebelum mencoba lagi...');
          await delay(5000);
        }
      }
    }

    if (responseText) break;
  }

  if (!responseText) {
    console.error('Gagal mendapatkan respon dari semua model Gemini setelah beberapa kali percobaan.');
    process.exit(1);
  }

  // Bersihkan pembungkus format markdown jika ada
  responseText = responseText.replace(/^```markdown\n/, '').replace(/^```\n/, '').replace(/\n```$/, '');

  fs.writeFileSync(prdPath, responseText.trim(), 'utf8');
  console.log('PRD.md lengkap (Bab 1-14) berhasil diperbarui!');
}

run().catch((err) => {
  console.error('Gagal memperbarui PRD:', err);
  process.exit(1);
});
