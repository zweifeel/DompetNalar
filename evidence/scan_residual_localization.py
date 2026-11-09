import os
import re

UI_DIR = r'app\src\main\java\com\example'

id_indicators = [
    r'\bSimpan\b', r'\bBatal\b', r'\bHapus\b', r'\bTambah\b', r'\bUbah\b', r'\bKembali\b',
    r'\bKeluar\b', r'\bMasuk\b', r'\bPengeluaran\b', r'\bPemasukan\b', r'\bDompet\b',
    r'\bKategori\b', r'\bTransaksi\b', r'\bCatatan\b', r'\bDeskripsi\b', r'\bNominal\b',
    r'\bJumlah\b', r'\bSaldo\b', r'\bTanggal\b', r'\bWaktu\b', r'\bBulan\b', r'\bTahun\b',
    r'\bHari\b', r'\bMinggu\b', r'\bTotal\b', r'\bSisa\b', r'\bAnggaran\b', r'\bHutang\b',
    r'\bPiutang\b', r'\bAset\b', r'\bCicilan\b', r'\bStatus\b', r'\bBerhasil\b', r'\bGagal\b',
    r'\bKonfirmasi\b', r'\bPeringatan\b', r'\bApakah anda yakin\b', r'\bApakah Anda yakin\b',
    r'\bSilakan\b', r'\bHarap\b', r'\bTidak ada\b', r'\bBelum ada\b', r'\bPilih\b',
    r'\bSemua\b', r'\bRiwayat\b', r'\bLaporan\b', r'\bPengaturan\b', r'\bAkun\b',
    r'\bTentang\b', r'\bBantuan\b', r'\bBeli\b', r'\bBayar\b', r'\bLunas\b', r'\bJatuh Tempo\b'
]

compiled_id = [re.compile(p, re.IGNORECASE) for p in id_indicators]

findings = []

for root, dirs, files in os.walk(UI_DIR):
    for f in files:
        if f.endswith('.kt'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8', errors='replace') as fp:
                lines = fp.readlines()
            for idx, line in enumerate(lines, 1):
                if 'AppStrings.kt' in f or line.strip().startswith('//') or line.strip().startswith('*'):
                    continue
                # Extract double quoted strings
                quotes = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line)
                for q in quotes:
                    # If this quote is followed or preceded by AppStrings.t( or AppStrings.tr(
                    if 'AppStrings.t(' in line and ('"' + q + '"' in line):
                        # Could be the ID string in AppStrings.t("ID", "EN")
                        # Check if it's the first param
                        idx_call = line.find('AppStrings.t(')
                        idx_q = line.find('"' + q + '"')
                        # If inside AppStrings.t call, skip
                        if idx_q >= idx_call:
                            continue
                    for cp in compiled_id:
                        if cp.search(q):
                            findings.append((path, idx, q.strip(), line.strip()))
                            break

print(f"Total candidate hardcoded ID strings found: {len(findings)}")
output_lines = []
for path, idx, q, line in findings:
    rel_path = os.path.relpath(path, UI_DIR)
    output_lines.append(f"{rel_path}:{idx} -> \"{q}\"\n  Line: {line}\n")

with open(r'evidence\localization_audit\residual_id_scan.txt', 'w', encoding='utf-8') as out:
    out.writelines(output_lines)

print("Saved to evidence/localization_audit/residual_id_scan.txt")
