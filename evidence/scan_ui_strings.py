import os, re

UI_DIR = 'app/src/main/java/com/example/ui'

files_to_check = []
for root, dirs, files in os.walk(UI_DIR):
    for f in files:
        if f.endswith('.kt'):
            files_to_check.append(os.path.join(root, f))

id_words = [
    r'\bSimpan\b', r'\bBatal\b', r'\bHapus\b', r'\bTambah\b', r'\bUbah\b',
    r'\bPengaturan\b', r'\bKategori\b', r'\bDompet\b', r'\bTransaksi\b',
    r'\bMutasi\b', r'\bPemasukan\b', r'\bPengeluaran\b', r'\bSaldo\b',
    r'\bUtang\b', r'\bPiutang\b', r'\bCicilan\b', r'\bJatuh Tempo\b',
    r'\bTagihan\b', r'\bLunas\b', r'\bBelum Lunas\b', r'\bAnggaran\b',
    r'\bBulan\b', r'\bTahun\b', r'\bHari Ini\b', r'\bKemarin\b',
    r'\bLihat Semua\b', r'\bRiwayat\b', r'\bPilih\b', r'\bCatatan\b',
    r'\bNominal\b', r'\bKonfirmasi\b', r'\bKembali\b', r'\bBerhasil\b',
    r'\bGagal\b', r'\bInformasi\b', r'\bDeskripsi\b', r'\bTotal Saldo\b',
    r'\bKelola\b', r'\bAnalisis\b', r'\bAnalitik\b', r'\bSemua\b'
]

# Patterns for hardcoded Text("...") or Text(text = "...")
text_call_pattern = re.compile(r'Text\(\s*text\s*=\s*"([^"]+)"|Text\(\s*"([^"]+)"')

findings = []

for file_path in files_to_check:
    rel_path = os.path.relpath(file_path, UI_DIR)
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as fp:
        lines = fp.readlines()
        for i, line in enumerate(lines, 1):
            for match in text_call_pattern.finditer(line):
                text_content = match.group(1) or match.group(2)
                if text_content and len(text_content.strip()) > 1 and not text_content.startswith("$") and not text_content.startswith("Rp"):
                    has_id = any(re.search(w, text_content, re.I) for w in id_words)
                    findings.append((rel_path, i, text_content, has_id, line.strip()))

os.makedirs('evidence/localization_audit', exist_ok=True)
with open('evidence/localization_audit/hardcoded_findings.txt', 'w', encoding='utf-8') as out:
    out.write(f"Total files checked: {len(files_to_check)}\n")
    out.write(f"Total hardcoded Text candidates found: {len(findings)}\n")
    id_findings = [f for f in findings if f[3]]
    out.write(f"Hardcoded Indonesian Text items: {len(id_findings)}\n\n")
    for rel_path, line_no, text_content, _, raw in id_findings:
        out.write(f"{rel_path}:{line_no} -> \"{text_content}\"\n")

print(f"Report written to evidence/localization_audit/hardcoded_findings.txt with {len([f for f in findings if f[3]])} Indonesian items.")
