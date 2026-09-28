#!/usr/bin/env python3
import os
import sys
import json
import time
import urllib.request
import urllib.error

def main():
    api_key = os.environ.get("GEMINI_API_KEY", "").strip().replace('"', '').replace("'", "")
    update_notes = os.environ.get("UPDATE_NOTES", "Pembaruan fitur OCR Gemini dan optimasi sistem FinSmart.")

    if not api_key or api_key == "MY_GEMINI_API_KEY":
        print("Peringatan: GEMINI_API_KEY tidak ditemukan atau masih bernilai default. Update PRD otomatis dilewati.")
        return

    script_dir = os.path.dirname(os.path.abspath(__file__))
    prd_path = os.path.join(script_dir, "..", "PRD.md")

    current_prd = ""
    if os.path.exists(prd_path):
        with open(prd_path, "r", encoding="utf-8") as f:
            current_prd = f.read()

    prompt = f"""
Kamu adalah seorang Technical Product Manager senior untuk aplikasi Android FinSmart.
Tugasmu adalah membuat/memperbarui dokumen PRD.md secara LENGKAP dan UTUH dari Bab 1 hingga Bab 14 berdasarkan catatan pembaruan berikut.

--- CATATAN PEMBARUAN / COMMIT TERBARU ---
{update_notes}

--- DOKUMEN PRD SAAT INI ---
{current_prd}

--- STRUKTUR WAJIB PRD (BAB 1 - 14) ---
Dokumen harus mencakup seluruh bab berikut secara detail:
1. Overview & Vision
2. Target Audience & User Personas
3. User Stories & Use Cases
4. Functional Requirements (Fitur Core, OCR Scan Struk Gemini 2.0 Flash, Split Bill, Mutasi, Anggaran, In-App Update)
5. Non-Functional Requirements (Performa, Keamanan, Offline First, Aksesibilitas)
6. System Architecture & Tech Stack (Android/Kotlin Jetpack Compose, Room DB, Retrofit, Gemini API)
7. UI/UX Design Guidelines & User Flow
8. Data Models & Database Schema
9. API & Third-Party Integration
10. Analytics & Telemetry Metrics
11. Security & Compliance (Data Privacy, Keystore, Encryption)
12. Release Strategy & Rollout Plan (In-App Self-Hosted Updates)
13. Risks, Assumptions & Dependencies
14. Appendix & Changelog (Sertakan Tabel Riwayat Perubahan)

--- INSTRUKSI SANGAT PENTING ---
1. TULISKAN SELURUH BAB 1 SAMPAI BAB 14 SECARA UTUH DAN DETAIL. JANGAN MENGHAPUS BAB APAPUN.
2. Integrasikan informasi dari Catatan Pembaruan ke bab yang relevan.
3. Kembalikan HANYA dokumen Markdown PRD tanpa kalimat pembuka atau penutup tambahan.
"""

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={api_key}"
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt}
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.2,
            "maxOutputTokens": 8192
        }
    }

    data = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}

    response_text = None
    delay_sec = 2

    for attempt in range(1, 4):
        try:
            print(f"Mengirim permintaan ke Gemini API (gemini-2.0-flash, percobaan {attempt}/3)...")
            req = urllib.request.Request(url, data=data, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=60) as res:
                body = res.read().decode("utf-8")
                res_json = json.loads(body)
                candidates = res_json.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        response_text = parts[0].get("text", "")
                        if response_text:
                            print("Berhasil mendapatkan respon dari Gemini 2.0 Flash!")
                            break
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="ignore")
            print(f"HTTP Error {e.code}: {e.reason} | {err_body}")
            if e.code in [429, 503, 500, 502, 504]:
                print(f"Server sibuk/kuota tercapai (HTTP {e.code}). Menunggu {delay_sec}s sebelum retry...")
                time.sleep(delay_sec)
                delay_sec *= 2
            else:
                break
        except Exception as e:
            print(f"Error jaringan: {e}")
            time.sleep(delay_sec)
            delay_sec *= 2

    if not response_text:
        print("Peringatan: Gagal memperbarui PRD.md via API. Mempertahankan versi PRD yang sudah ada.")
        return

    # Bersihkan markdown fence jika ada
    clean_md = response_text.strip()
    if clean_md.startswith("```markdown"):
        clean_md = clean_md[len("```markdown"):].strip()
    elif clean_md.startswith("```"):
        clean_md = clean_md[len("```"):].strip()
    if clean_md.endswith("```"):
        clean_md = clean_md[:-3].strip()

    with open(prd_path, "w", encoding="utf-8") as f:
        f.write(clean_md + "\n")

    print(f"PRD.md lengkap berhasil diperbarui di: {prd_path}")

if __name__ == "__main__":
    main()
