# ==============================================================================
# SISTEM PENJAGA MEMORI, GIT & DETEKTOR KEBOHONGAN (auto_sync.py)
# Dibuat khusus untuk Owner Non-Coder: Proteksi Anti-Fake-Pass & Risk Interrupt
# ==============================================================================

import os
import sys
import re
import subprocess
import datetime

# Pastikan output konsol Windows mendukung karakter UTF-8 / Emoji tanpa crash
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

try:
    import winsound
except ImportError:
    winsound = None

TARGET_EXTENSIONS = {
    ".py", ".luau", ".lua", ".js", ".jsx", ".ts", ".tsx",
    ".go", ".rs", ".java", ".cs", ".cpp", ".c", ".h", ".php"
}

IGNORE_DIRS = {
    ".git", ".agent", "node_modules", "venv", ".venv", "env",
    "__pycache__", "build", "dist", "out", "target", "bin",
    "obj", "scratch", "Downloaded Asset", "Artifact"
}

BANNED_PATTERNS = [
    (r"\bTODO\b", "TODO (Tugas belum selesai)"),
    (r"\bFIXME\b", "FIXME (Kode bermasalah belum diperbaiki)"),
    (r"\bplaceholder\b", "placeholder (Data/tampilan palsu)"),
    (r"\bdummy\b", "dummy (Fungsi/data tiruan)"),
    (r"\bmock_data\b", "mock_data (Bukan data nyata dari database/sistem)"),
    (r"\bnot implemented\b", "not implemented (Fungsi belum diimplementasikan)"),
    (r"\bpass\s*#", "pass # (Stub kosong yang ditutupi)"),
    (r"\braise\s+NotImplementedError\b", "NotImplementedError (Sengaja dilempar error belum siap)"),
    (r"\bthrow\s+new\s+Error\s*\(\s*['\"]Not implemented", "Throw Not implemented error"),
    (r"\berror\s*\(\s*['\"]Not implemented", "Luau error('Not implemented')")
]

def print_banner(text, border="="):
    line = border * 75
    print(f"\n{line}\n{text}\n{line}")

def run_git_cmd(args):
    try:
        res = subprocess.run(
            ["git"] + args,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace"
        )
        return res.returncode == 0, res.stdout.strip(), res.stderr.strip()
    except Exception as e:
        return False, "", str(e)

def init_project(project_name="Proyek Tanpa Nama"):
    print_banner(f"🚀 INISIALISASI TEMPLATE PROYEK AMAN: {project_name}")
    
    checkpoint_content = f"""# CURRENT_CHECKPOINT.md
# STATUS TERAKHIR PROYEK: {project_name}

> [!NOTE]
> File ini adalah memori utama proyek Anda. Setiap kali membuka **New Chat** di Antigravity,
> AI wajib membaca file ini untuk mengetahui status terakhir tanpa halusinasi.

## 📌 Identitas Proyek
- **Nama Proyek:** {project_name}
- **Tanggal Dibuat:** {datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
- **Status Terakhir:** Inisialisasi Baru
- **Fitur Aktif Terakhir:** Setup Fondasi Awal

## 📜 Log Riwayat Selesai (Verified E2E)
- [{datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")}] Inisialisasi template proyek aman berhasil.
"""

    architecture_content = f"""# ARCHITECTURE_MAP.md
# PETA HUBUNGAN ANTAR FITUR & STRUKTUR SISTEM: {project_name}

> [!IMPORTANT]
> **PETA ANTI-TABRAKAN FITUR (UNTUK OWNER & AGEN):**
> Agen DILARANG mengedit kode apa pun sebelum membaca peta ini.
> Jika fitur baru menyentuh modul yang ditandai [KRITIS/JANGAN RUSAK], agen WAJIB memberi peringatan risiko!

## 1. Peta Modul Utama & Alur Data
```mermaid
flowchart TD
    UI[Antarmuka Pengguna / Layar] --> LOGIC[Otak Sistem / Logika Utama]
    LOGIC --> STORAGE[Penyimpanan / Database]
```

## 2. Hubungan Antar Fitur (Dependency Matrix)
| Fitur / Modul | Bergantung Pada | Dampak Jika Diubah | Status Keamanan |
| :--- | :--- | :--- | :--- |
| **Contoh: Login / Profil** | Database Akun | Semua fitur pengguna butuh ini | 🔒 KRITIS (Jangan Rusak) |
| **Contoh: Transaksi / Data** | Database Utama | Uang/item pemain bisa hilang jika bug | 🔒 KRITIS (Wajib Teliti) |

## 3. Zona Larangan Sentuh Sembarangan
1. File konfigurasi utama dan kredensial.
2. Skema database yang sudah menyimpan data pemain/pengguna aktif.
3. Fungsi inti yang sudah diverifikasi E2E.
"""

    roadmap_content = f"""# ROADMAP.md
# RENCANA TAHAPAN FITUR: {project_name}

> [!TIP]
> Kerjakan SATU per SATU fitur secara vertikal (End-to-End sampai tuntas) sebelum pindah ke fitur berikutnya.

## 🏁 Tahap 1: Fondasi Awal
- [x] Inisialisasi template proyek aman (`auto_sync.py`, `.antigravityrules`, `GEMINI.md`)
- [ ] Fitur Fondasi 1: (Isi nama fitur utama pertama)
- [ ] Fitur Fondasi 2: (Isi nama fitur utama kedua)

## 🚀 Tahap 2: Fitur Utama
- [ ] Fitur Lanjutan 1
- [ ] Fitur Lanjutan 2

## 🎯 Tahap 3: Penyempurnaan & Polish
- [ ] Optimasi & Audit Akhir
"""

    proof_template = """# END_TO_END_PROOF.md
# BUKTI PENGUJIAN LENGKAP END-TO-END (ANTI-BOHONG)

> [!WARNING]
> Agen WAJIB mengisi file ini dengan bukti nyata pengujian sebelum menjalankan `auto_sync.py save`.
> Jangan biarkan bagian ini kosong atau berisi teks contoh/dummy!

## 1. Identitas Fitur
- **Nama Fitur:** [Tulis nama fitur yang baru dikerjakan]
- **Waktu Pengujian:** [YYYY-MM-DD HH:MM:SS]
- **Tujuan Fitur:** [Penjelasan 1 kalimat apa yang diharapkan berjalan]

## 2. Empat Pilar Bukti End-to-End
### Pilar 1: Input / Pemicu Nyata
- *Bukti:* Tombol/UI/Event/Endpoint benar-benar ada dan bisa dipicu.
- *Detail pengujian:* [Tuliskan apa yang diklik / input apa yang dimasukkan]

### Pilar 2: Logika Proses Tanpa Fake/Dummy
- *Bukti:* Tidak ada fungsi kosong, TODO, atau mock. Semua kalkulasi/validasi berjalan nyata.
- *Detail pengujian:* [File dan fungsi mana yang memproses data secara nyata]

### Pilar 3: Penyimpanan / Output Nyata
- *Bukti:* Data benar-benar tersimpan ke database/storage/state dan tampil di layar.
- *Detail pengujian:* [Hasil konkret yang terlihat pada layar atau data yang terverifikasi]

### Pilar 4: Uji Regresi (Fitur Lama Tetap Aman)
- *Bukti:* Fitur lama yang terhubung tidak rusak akibat penambahan kode baru.
- *Detail pengujian:* [Sebutkan fitur lama yang dites ulang dan dipastikan tetap berjalan normal]

---
## 3. Log Output / Screenshot Bukti
```text
[Tempelkan log terminal, bukti console, atau ringkasan pengujian di sini]
```
"""

    files = [
        ("CURRENT_CHECKPOINT.md", checkpoint_content),
        ("ARCHITECTURE_MAP.md", architecture_content),
        ("ROADMAP.md", roadmap_content),
        ("END_TO_END_PROOF.md", proof_template)
    ]

    for fname, content in files:
        if not os.path.exists(fname):
            with open(fname, "w", encoding="utf-8") as f:
                f.write(content.strip() + "\n")
            print(f"  + Berhasil membuat file: {fname}")
        else:
            print(f"  ~ File sudah ada (tidak ditimpa): {fname}")

    print("\n✅ Proyek berhasil diinisialisasi dengan fondasi aman!")

def show_status():
    print_banner("📋 STATUS CHECKPOINT & PETA ARSITEKTUR AKTIF")
    
    if os.path.exists("CURRENT_CHECKPOINT.md"):
        print("\n>>> [MEMORI AKTIF] CURRENT_CHECKPOINT.md:")
        with open("CURRENT_CHECKPOINT.md", "r", encoding="utf-8") as f:
            print(f.read())
    else:
        print("\n⚠️ File CURRENT_CHECKPOINT.md belum ada. Jalankan 'python auto_sync.py init' terlebih dahulu.")

    if os.path.exists("ARCHITECTURE_MAP.md"):
        print("\n>>> [PETA ARSITEKTUR] ARCHITECTURE_MAP.md:")
        with open("ARCHITECTURE_MAP.md", "r", encoding="utf-8") as f:
            print(f.read())
    else:
        print("\n⚠️ File ARCHITECTURE_MAP.md belum ada.")

def run_audit(silent_if_pass=False):
    if not silent_if_pass:
        print_banner("🔍 DETEKTOR FAKE-PASS: MEMINDAI KODE PROYEK...")

    violations = []
    
    # Skrip ini dan file template/docs dikecualikan dari scanning kata kunci ban
    script_self = os.path.abspath(__file__)
    
    for root, dirs, files in os.walk("."):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
        
        for file in files:
            ext = os.path.splitext(file)[1].lower()
            if ext not in TARGET_EXTENSIONS:
                continue
                
            file_path = os.path.join(root, file)
            if os.path.abspath(file_path) == script_self:
                continue
                
            try:
                with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                    for line_idx, line in enumerate(f, start=1):
                        for pattern, desc in BANNED_PATTERNS:
                            if re.search(pattern, line, re.IGNORECASE):
                                violations.append((file_path, line_idx, line.strip(), desc))
            except Exception as e:
                pass

    if violations:
        print("\n" + "!" * 75)
        print("❌ AUDIT GAGAL! DETEKTOR KEBOHONGAN MENEMUKAN KODE BELUM SELESAI / MASIH DUMMY!")
        print("Dilarang klaim 'Selesai/PASS'! Kerjakan fitur sampai tuntas End-to-End.")
        print("!" * 75)
        for fpath, lnum, lcontent, desc in violations[:15]:
            print(f"\n📁 File   : {fpath} (Baris {lnum})")
            print(f"⚠️ Alasan : {desc}")
            print(f"📝 Potongan Kode: {lcontent}")
        
        if len(violations) > 15:
            print(f"\n... dan masih ada {len(violations) - 15} temuan lainnya!")
            
        print("\n🛑 PROSES PENYIMPANAN DIBATALKAN KARENA KODE MASIH MENTAH / FAKE-PASS.")
        return False

    if not silent_if_pass:
        print("\n✅ AUDIT BERHASIL! Kode bersih dari kata kunci TODO, Dummy, Placeholder, dan Mock.")
    return True

def validate_e2e_proof():
    proof_path = "END_TO_END_PROOF.md"
    if not os.path.exists(proof_path):
        print(f"\n❌ AUDIT GAGAL: File {proof_path} tidak ditemukan!")
        print("Agen wajib mengisi file END_TO_END_PROOF.md sebelum menyimpan progres.")
        return False
        
    with open(proof_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Cek apakah pilar-pilar penting masih berupa template mentah
    mandatory_tokens = [
        "Pilar 1: Input / Pemicu",
        "Pilar 2: Logika Proses",
        "Pilar 3: Penyimpanan / Output",
        "Pilar 4: Uji Regresi"
    ]
    
    for token in mandatory_tokens:
        if token not in content:
            print(f"\n❌ AUDIT GAGAL: File {proof_path} tidak memuat '{token}'.")
            return False

    if "[Tulis nama fitur" in content or "[Tempelkan log terminal" in content:
        print(f"\n❌ AUDIT GAGAL: File {proof_path} masih memuat teks template bawaan.")
        print("Agen wajib menuliskan bukti konkret hasil pengujian nyata, bukan template!")
        return False

    return True

def save_progress(summary):
    print_banner(f"💾 MENJALANKAN PROSES SIMPAN AMAN: {summary}")
    
    # 1. Jalankan audit kode
    if not run_audit():
        sys.exit(1)

    # 2. Periksa bukti End-to-End
    if not validate_e2e_proof():
        sys.exit(1)

    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 3. Catat ke CURRENT_CHECKPOINT.md
    if os.path.exists("CURRENT_CHECKPOINT.md"):
        with open("CURRENT_CHECKPOINT.md", "r", encoding="utf-8") as f:
            cp_data = f.read()
        
        log_entry = f"- [{now_str}] Verified E2E: {summary}\n"
        
        # Ganti status terakhir jika ada
        cp_data = re.sub(r"- \*\*Status Terakhir:\*\* .*", f"- **Status Terakhir:** Siap Dilanjutkan ({summary})", cp_data)
        cp_data = re.sub(r"- \*\*Fitur Aktif Terakhir:\*\* .*", f"- **Fitur Aktif Terakhir:** {summary}", cp_data)
        
        if "## 📜 Log Riwayat Selesai (Verified E2E)" in cp_data:
            parts = cp_data.split("## 📜 Log Riwayat Selesai (Verified E2E)")
            cp_data = parts[0] + "## 📜 Log Riwayat Selesai (Verified E2E)\n" + log_entry + parts[1].lstrip("\n")
        else:
            cp_data += "\n## 📜 Log Riwayat Selesai (Verified E2E)\n" + log_entry

        with open("CURRENT_CHECKPOINT.md", "w", encoding="utf-8") as f:
            f.write(cp_data)
        print("  + Status tersimpan di CURRENT_CHECKPOINT.md")

    # 4. Jalankan Git
    print("\n📦 Menjalankan Git Commit & Push...")
    ok_add, _, err = run_git_cmd(["add", "."])
    if not ok_add:
        print(f"⚠️ Git add mengalami kendala: {err}")
    
    commit_msg = f"Verified E2E: {summary}"
    ok_commit, out_c, err_c = run_git_cmd(["commit", "-m", commit_msg])
    if ok_commit:
        print(f"  + Git commit berhasil: {commit_msg}")
    else:
        print(f"  ~ Git commit info: {out_c or err_c}")

    # Cek branch aktif
    _, current_branch, _ = run_git_cmd(["branch", "--show-current"])
    if not current_branch:
        current_branch = "main"

    ok_push, out_p, err_p = run_git_cmd(["push", "origin", current_branch])
    if ok_push:
        print(f"  + Git push ke origin/{current_branch} berhasil!")
    else:
        print(f"  ~ Catatan Git Push: {err_p or out_p} (Jika remote belum diset, commit lokal tetap aman).")

    print("\n" + "=" * 75)
    print("✅ LOLOS AUDIT END-TO-END & TERSIMPAN!")
    print("👉 Silakan buka Percakapan Baru (New Chat) untuk mencegah halusinasi.")
    print("=" * 75 + "\n")

def need_decision(explanation):
    print_banner("🚨 ALARM KEPUTUSAN OWNER DIAKTIFKAN! PERLU PERHATIAN KHUSUS 🚨")
    
    # 1. Bunyikan Alarm Windows
    if winsound:
        try:
            for _ in range(3):
                winsound.MessageBeep(winsound.MB_ICONEXCLAMATION)
                winsound.Beep(1200, 250)
                winsound.Beep(900, 200)
        except Exception:
            try:
                winsound.MessageBeep(winsound.MB_ICONHAND)
            except Exception:
                pass
    
    # 2. Tulis file BUTUH_KEPUTUSAN_OWNER.txt dalam bahasa awam
    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    notepad_file = "BUTUH_KEPUTUSAN_OWNER.txt"
    
    content = f"""================================================================================
🚨 PERHATIAN OWNER: AGEN MEMERLUKAN KEPUTUSAN ANDA SEBELUM MELANJUTKAN 🚨
Waktu Deteksi: {now_str}
================================================================================

Halo Owner! Agen Antigravity menghentikan proses sementara karena menemukan
potensi risiko teknis, benturan antar fitur, atau kendala arsitektur penting.

--------------------------------------------------------------------------------
1. APA MASALAH / POTENSI TABRAKANNYA? (Bahasa Awam)
--------------------------------------------------------------------------------
{explanation}

--------------------------------------------------------------------------------
2. APA RISIKO BURUKNYA JIKA LANGSUNG DIPAKSAKAN?
--------------------------------------------------------------------------------
- Fitur lama yang sebelumnya sudah berjalan normal bisa rusak (regresi/error).
- Alur data atau database berisiko berantakan sehingga data tidak sinkron.
- Perbaikan di kemudian hari akan jauh lebih sulit dan memakan waktu lama.

--------------------------------------------------------------------------------
3. PILIHAN KEPUTUSAN UNTUK OWNER:
--------------------------------------------------------------------------------
[PILIHAN A] (Rekomendasi Aman):
  - Solusi: Pisahkan modul baru ini secara bertahap tanpa merombak sistem lama.
  - Kelebihan: Aman dari risiko merusak fitur yang sudah jalan.
  - Kekurangan: Butuh 1 langkah pengujian tambahan.

[PILIHAN B] (Rombak Langsung):
  - Solusi: Mengubah sistem lama agar langsung mengikuti alur fitur baru.
  - Kelebihan: Struktur langsung seragam.
  - Kekurangan: Risiko fitur lama mogok sangat tinggi.

--------------------------------------------------------------------------------
4. BAGAIMANA CARA ANDA MENJAWAB?
--------------------------------------------------------------------------------
Cukup ketik pilihan Anda (Pilihan A atau Pilihan B) di jendela chat Antigravity,
atau jelaskan instruksi tambahan yang Anda inginkan. Agen akan menunggu arahan
Anda dan TIDAK AKAN mengedit kode sebelum Anda memberikan persetujuan!
================================================================================
"""
    with open(notepad_file, "w", encoding="utf-8") as f:
        f.write(content)
        
    print(f"📄 File penjelasan telah dibuat: {notepad_file}")
    print("🖥️ Membuka Notepad di layar Anda sekarang juga...")

    # 3. Buka Notepad di layar Windows
    try:
        subprocess.Popen(["notepad.exe", notepad_file])
    except Exception as e:
        print(f"⚠️ Gagal membuka notepad.exe otomatis: {e}. Silakan buka file '{notepad_file}' secara manual.")

    print("\n" + "#" * 75)
    print("🛑 KERJA AGEN DIHENTIKAN SEMENTARA.")
    print("Tunggu hingga Owner membaca Notepad dan memberikan instruksi di chat.")
    print("#" * 75 + "\n")

def print_help():
    print("""
Penggunaan Skrip Keamanan auto_sync.py:
---------------------------------------------------------------------------
1. python auto_sync.py init "Nama Proyek"
   -> Membuat file checkpoint, roadmap, dan peta arsitektur awal.

2. python auto_sync.py status
   -> Menampilkan ringkasan memori proyek saat buka New Chat di Antigravity.

3. python auto_sync.py audit
   -> Detektor Fake-Pass: Memeriksa kode dari kata-kata TODO, dummy, placeholder.

4. python auto_sync.py save "Nama Fitur Selesai"
   -> Menjalankan audit, cek bukti E2E, mencatat checkpoint, dan git commit.

5. python auto_sync.py need-decision "Penjelasan masalah dalam bahasa awam"
   -> Membunyikan alarm Windows, menulis penjelasan risiko, dan membuka Notepad.
---------------------------------------------------------------------------
""")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print_help()
        sys.exit(0)

    cmd = sys.argv[1].lower()

    if cmd == "init":
        pname = sys.argv[2] if len(sys.argv) > 2 else "Proyek Saya"
        init_project(pname)
    elif cmd == "status":
        show_status()
    elif cmd == "audit":
        if not run_audit():
            sys.exit(1)
    elif cmd == "save":
        if len(sys.argv) < 3:
            print("❌ Harap sertakan ringkasan fitur! Contoh: python auto_sync.py save \"Sistem Login Pemain\"")
            sys.exit(1)
        save_progress(sys.argv[2])
    elif cmd in ("need-decision", "need_decision"):
        if len(sys.argv) < 3:
            print("❌ Harap sertakan penjelasan risiko! Contoh: python auto_sync.py need-decision \"Fitur ini akan mengubah tabel database utama\"")
            sys.exit(1)
        need_decision(sys.argv[2])
    else:
        print(f"❌ Perintah '{cmd}' tidak dikenali.")
        print_help()
        sys.exit(1)
