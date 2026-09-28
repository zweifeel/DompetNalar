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

  // Lokasi file PRD.md di folder utama (root)
  const prdPath = path.join(__dirname, '../PRD.md');
  let currentPrd = '';
  
  if (fs.existsSync(prdPath)) {
    currentPrd = fs.readFileSync(prdPath, 'utf8');
  } else {
    currentPrd = '# PRD FinSmart\n\nVersi Awal PRD.';
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  // Menggunakan model gemini-1.5-pro untuk pemrosesan dokumen panjang
  const model = genAI.getGenerativeModel({ model: 'gemini-1.5-pro' });

  const prompt = `
Kamu adalah seorang Technical Product Manager untuk aplikasi Android FinSmart.
Tugasmu adalah memperbarui dokumen PRD.md berdasarkan catatan pembaruan/fitur baru berikut:

--- CATATAN PEMBARUAN / COMMIT ---
${updateNotes}

--- DOKUMEN PRD SAAT INI ---
${currentPrd}

--- INSTRUKSI OUTPUT ---
1. Perbarui dokumen PRD di atas agar mencerminkan fitur baru atau perbaikan bug yang disebutkan.
2. Perbarui versi dokumen dan tambahkan entri baru pada tabel Riwayat Perubahan (Changelog).
3. Kembalikan HANYA dokumen Markdown PRD utuh tanpa pembuka/penutup berupa penjelasan percakapan.
`;

  console.log('Mengirim permintaan pembaruan PRD ke Gemini API...');
  const result = await model.generateContent(prompt);
  let responseText = result.response.text();

  // Bersihkan format pembungkus markdown jika ada
  responseText = responseText.replace(/^```markdown\n/, '').replace(/^```\n/, '').replace(/\n```$/, '');

  fs.writeFileSync(prdPath, responseText.trim(), 'utf8');
  console.log('PRD.md berhasil diperbarui!');
}

run().catch((err) => {
  console.error('Gagal memperbarui PRD:', err);
  process.exit(1);
});
