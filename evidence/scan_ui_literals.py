import os
import re

UI_DIR = r'app\src\main\java\com\example\ui'

# Regex to find Text("...", Text(text = "...", label = { Text("...") }, etc.
# Specifically strings that do not call AppStrings.t( or stringResource(
text_literal_regex = re.compile(r'Text\s*\(\s*(?:text\s*=\s*)?"([^"]+)"')
title_literal_regex = re.compile(r'title\s*=\s*\{\s*Text\s*\(\s*(?:text\s*=\s*)?"([^"]+)"')
label_literal_regex = re.compile(r'label\s*=\s*\{\s*Text\s*\(\s*(?:text\s*=\s*)?"([^"]+)"')
placeholder_literal_regex = re.compile(r'placeholder\s*=\s*\{\s*Text\s*\(\s*(?:text\s*=\s*)?"([^"]+)"')
toast_literal_regex = re.compile(r'Toast\.makeText\s*\([^,]+,\s*"([^"]+)"')

id_words = [
    'simpan', 'batal', 'hapus', 'tambah', 'ubah', 'kembali', 'keluar', 'masuk',
    'pengeluaran', 'pemasukan', 'dompet', 'kategori', 'transaksi', 'catatan',
    'deskripsi', 'nominal', 'saldo', 'anggaran', 'hutang', 'piutang', 'aset',
    'cicilan', 'berhasil', 'gagal', 'konfirmasi', 'peringatan', 'pilih', 'riwayat',
    'laporan', 'pengaturan', 'akun', 'bantuan', 'bayar', 'lunas', 'jatuh tempo',
    'hari ini', 'minggu ini', 'bulan ini', 'tahun ini', 'mingguan', 'bulanan', 'tahunan'
]

findings = []

for root, dirs, files in os.walk(UI_DIR):
    for f in files:
        if f.endswith('.kt') and f != 'AppStrings.kt':
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8', errors='replace') as fp:
                content = fp.read()
            
            # Find all Text("...") where content is a raw string literal without AppStrings.t
            # Match Text("...") or Text(text = "...")
            for m in re.finditer(r'Text\s*\(\s*(?:text\s*=\s*)?"([^"]+)"', content):
                val = m.group(1)
                # Check if it has any ID word or purely english word that should be localized
                val_lower = val.lower()
                is_id = any(w in val_lower for w in id_words)
                # Check line context
                start_line = content[:m.start()].count('\n') + 1
                findings.append((f, start_line, val, is_id, 'Text'))
                
            # Also find confirmButton = { Text("...") } or similar
            for m in re.finditer(r'(?:title|label|placeholder|confirmButton|dismissButton)\s*=\s*(?:\{\s*Text\s*\(\s*(?:text\s*=\s*)?)?"([^"]+)"', content):
                val = m.group(1)
                val_lower = val.lower()
                is_id = any(w in val_lower for w in id_words)
                start_line = content[:m.start()].count('\n') + 1
                findings.append((f, start_line, val, is_id, 'Parameter'))

print(f"Total UI literal findings: {len(findings)}")
id_findings = [item for item in findings if item[3]]
print(f"Total UI Indonesian literal findings: {len(id_findings)}")

with open(r'evidence\localization_audit\targeted_ui_raw_strings.txt', 'w', encoding='utf-8') as out:
    for f, line, val, is_id, kind in findings:
        out.write(f"[{'ID' if is_id else 'EN/RAW'}] {f}:{line} [{kind}] -> \"{val}\"\n")

print("Saved to evidence/localization_audit/targeted_ui_raw_strings.txt")
