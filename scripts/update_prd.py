#!/usr/bin/env python3
"""
scripts/update_prd.py — Safe, Validated Documentation Pipeline for FinSmart PRD.md

Architecture Rules (FINSMART-ARCH-REV-001):
1. PRD.md is the controlled source-of-truth baseline.
2. Never blindly overwrite PRD.md with unvalidated LLM output.
3. Candidate output must pass strict structural, semantic, and security validation:
   - Non-empty and not truncated (minimum size check relative to baseline)
   - All 14 required chapter headings present
   - Full traceability matrix (FEAT-01 through FEAT-36) present
   - Architecture reference FINSMART-ARCH-REV-001 present
   - Zero positive inventory/stock/UMKM warehouse features (Negative Scope enforcement)
   - Zero credentials, API keys, or private secrets
4. Secrets must never be logged or exposed in HTTP error handlers or query parameters.
5. In case of validation failure, the existing PRD.md is preserved untouched.
"""

import os
import sys
import json
import time
import re
import shutil
import urllib.request
import urllib.error

# 14 Mandatory Chapters
REQUIRED_CHAPTERS = [
    "1. Overview & Vision",
    "2. Target Audience & User Personas",
    "3. User Stories & Use Cases",
    "4. Functional Requirements",
    "5. Non-Functional Requirements",
    "6. System Architecture & Tech Stack",
    "7. UI/UX Design Guidelines & User Flow",
    "8. Data Models & Database Schema",
    "9. API & Third-Party Integration",
    "10. Analytics & Telemetry Metrics",
    "11. Security & Compliance",
    "12. Release Strategy & Rollout Plan",
    "13. Risks, Assumptions & Dependencies",
    "14. Appendix & Changelog"
]

# Required Features FEAT-01 to FEAT-36
REQUIRED_FEATURES = [f"FEAT-{i:02d}" for i in range(1, 37)]

ARCH_REFERENCE = "FINSMART-ARCH-REV-001"

# Minimum document size in bytes to prevent catastrophic truncation
MIN_DOCUMENT_BYTES = 40000

# Prohibited inventory/warehouse terms in any context
FORBIDDEN_ANYWHERE = [
    "ProductDao",
    "InventoryViewModel",
    "inventory UMKM",
    "UMKM inventory",
]

# Prohibited inventory terms outside Chapter 1.3 Negative Scope
RESTRICTED_INVENTORY_SYMBOLS = [
    "ProductItem",
    "StockMovement",
    "InventoryScreen",
    "InventoryRepository",
    "stock management",
]

# Security patterns that must never appear in PRD.md
SECRET_PATTERNS = [
    (r"AIza[0-9A-Za-z-_]{35}", "Google/Gemini API Key"),
    (r"ghp_[0-9A-Za-z]{36}", "GitHub Personal Access Token"),
    (r"github_pat_[0-9A-Za-z_]{82}", "GitHub Fine-Grained Token"),
    (r"GEMINI_API_KEY\s*=\s*[\"']?(?!MY_GEMINI_API_KEY)[A-Za-z0-9_\-\.]{20,}[\"']?", "GEMINI_API_KEY definition"),
    (r"(?i)master[_\s]?pin\s*[:=]\s*[\"']?\d{4,8}[\"']?", "Master PIN plain-text assignment"),
    (r"(?i)password\s*[:=]\s*[\"'][^\"'\s]{6,}[\"']", "Hardcoded password"),
]


def validate_candidate(candidate_text: str, baseline_len: int = 0) -> tuple[bool, str]:
    """
    Deterministically validates candidate PRD markdown against all architectural,
    structural, and safety constraints.
    Returns (True, message) if valid, or (False, reason) if invalid.
    """
    if not candidate_text or not candidate_text.strip():
        return False, "Candidate PRD is empty or whitespace only."

    size_bytes = len(candidate_text.encode("utf-8"))

    # Size and truncation checks
    if size_bytes < MIN_DOCUMENT_BYTES:
        return False, f"Candidate PRD is too short ({size_bytes} bytes). Minimum required is {MIN_DOCUMENT_BYTES} bytes."

    if baseline_len > 0 and size_bytes < int(0.70 * baseline_len):
        return False, f"Candidate PRD appears truncated ({size_bytes} bytes vs baseline {baseline_len} bytes, ratio < 70%)."

    # Chapter headings check
    for ch in REQUIRED_CHAPTERS:
        pattern = r"##\s+" + re.escape(ch)
        if not re.search(pattern, candidate_text):
            return False, f"Missing required chapter heading: '## {ch}'"

    # Feature traceability check (FEAT-01 .. FEAT-36)
    for feat in REQUIRED_FEATURES:
        if feat not in candidate_text:
            return False, f"Missing required feature identifier: '{feat}'"

    # Architecture reference check
    if ARCH_REFERENCE not in candidate_text:
        return False, f"Missing master architecture baseline reference: '{ARCH_REFERENCE}'"

    # Forbidden terms check (nowhere in document)
    for term in FORBIDDEN_ANYWHERE:
        if term.lower() in candidate_text.lower():
            return False, f"Prohibited inventory/obsolete term detected: '{term}'"

    # Restricted inventory symbols: only permitted in Chapter 1 Negative Scope
    ch2_match = re.search(r"##\s+2\.\s+Target Audience", candidate_text)
    if ch2_match:
        body_after_ch1 = candidate_text[ch2_match.start():]
        for sym in RESTRICTED_INVENTORY_SYMBOLS:
            if sym in body_after_ch1:
                return False, f"Positive inventory symbol '{sym}' detected outside Chapter 1 Negative Scope."

    # Secret / credential detection (never log the secret value)
    for pattern, desc in SECRET_PATTERNS:
        if re.search(pattern, candidate_text):
            return False, f"Potential credential or secret pattern detected ({desc})."

    # Unclosed code fence check (detects truncation mid-code block)
    fence_count = len(re.findall(r"^```", candidate_text, re.MULTILINE))
    if fence_count % 2 != 0:
        return False, "Candidate PRD contains unclosed markdown code fences (possible mid-block truncation)."

    return True, "Candidate PRD passed all structural, functional, and safety validations."


def sanitize_message(msg: str, secret: str = "") -> str:
    """Removes secret strings and sensitive patterns from error messages."""
    if not msg:
        return ""
    if secret and secret in msg:
        msg = msg.replace(secret, "***REDACTED***")
    # Redact potential API keys or query params
    msg = re.sub(r"key=[A-Za-z0-9_\-\.]+", "key=***REDACTED***", msg)
    msg = re.sub(r"AIza[0-9A-Za-z-_]{35}", "***REDACTED_API_KEY***", msg)
    return msg


def generate_prd_candidate(api_key: str, update_notes: str, current_prd: str) -> tuple[bool, str, str]:
    """
    Calls Google Gemini 2.0 Flash REST API safely.
    API key is passed via HTTP header 'x-goog-api-key', NOT in the URL query string.
    Returns (success, candidate_text, message).
    """
    url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    prompt = f"""
Kamu adalah seorang Technical Product Manager senior untuk aplikasi Android FinSmart.
Tugasmu adalah memperbarui dokumen PRD.md berdasarkan catatan pembaruan berikut.

--- ATURAN ARSITEKTUR WAJIB (FINSMART-ARCH-REV-001) ---
1. TULISKAN SELURUH BAB 1 SAMPAI BAB 14 SECARA UTUH, DETAIL, DAN LENGKAP. DILARANG MERINGKAS ATAU MEMOTONG BAB APAPUN.
2. PERTAHANKAN seluruh ID fitur resmi FEAT-01 sampai FEAT-36 tanpa perubahan nomor atau makna.
3. FinSmart MURNI aplikasi keuangan pribadi offline-first. DILARANG KERAS menambahkan fitur inventaris, gudang, stok, ProductItem, StockMovement, InventoryScreen, atau kasir UMKM.
4. JANGAN menyertakan API key, password, token, atau master PIN rahasia dalam dokumen.
5. Kembalikan HANYA dokumen Markdown PRD tanpa kalimat pembuka atau penutup tambahan.

--- CATATAN PEMBARUAN / COMMIT TERBARU ---
{update_notes}

--- DOKUMEN PRD SAAT INI (BASELINE) ---
{current_prd}
"""

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
    headers = {
        "Content-Type": "application/json",
        "x-goog-api-key": api_key
    }

    response_text = None
    delay_sec = 2

    for attempt in range(1, 4):
        try:
            print(f"Mengirim permintaan ke Gemini API (percobaan {attempt}/3)...")
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
                            print("Berhasil menerima respon dari Gemini API.")
                            break
        except urllib.error.HTTPError as e:
            print(f"HTTP Error {e.code}: {e.reason}", file=sys.stderr)
            if e.code in [429, 500, 502, 503, 504]:
                print(f"Server sibuk (HTTP {e.code}). Menunggu {delay_sec}s sebelum retry...", file=sys.stderr)
                time.sleep(delay_sec)
                delay_sec *= 2
            else:
                break
        except Exception as e:
            sanitized = sanitize_message(str(e), api_key)
            print(f"Error jaringan: {sanitized}", file=sys.stderr)
            time.sleep(delay_sec)
            delay_sec *= 2

    if not response_text:
        return False, "", "Gagal mendapatkan respon valid dari Gemini API setelah 3 percobaan."

    clean_md = response_text.strip()
    if clean_md.startswith("```markdown"):
        clean_md = clean_md[len("```markdown"):].strip()
    elif clean_md.startswith("```"):
        clean_md = clean_md[len("```"):].strip()
    if clean_md.endswith("```"):
        clean_md = clean_md[:-3].strip()

    return True, clean_md, "Candidate berhasil diterima dari API."


def safe_update_prd(candidate_text: str, prd_path: str, verbose: bool = True) -> bool:
    """
    Saves candidate to a temporary file, executes deterministic validation,
    and replaces the existing PRD.md only if all checks pass.
    """
    candidate_path = prd_path + ".candidate"
    backup_path = prd_path + ".bak"

    baseline_len = os.path.getsize(prd_path) if os.path.exists(prd_path) else 0

    # 1. Write candidate to temporary file
    try:
        with open(candidate_path, "w", encoding="utf-8") as f:
            f.write(candidate_text + "\n")
    except Exception as e:
        if verbose:
            print(f"Error menulis file kandidat: {e}", file=sys.stderr)
        return False

    # 2. Validate candidate
    is_valid, reason = validate_candidate(candidate_text, baseline_len)
    if not is_valid:
        if verbose:
            print(f"VALIDASI PRD GAGAL: {reason}", file=sys.stderr)
            print("PRD.md asli tetap dipertahankan tanpa perubahan.", file=sys.stderr)
        if os.path.exists(candidate_path):
            os.remove(candidate_path)
        return False

    # 3. Create backup of current PRD if it exists
    if os.path.exists(prd_path):
        try:
            shutil.copy2(prd_path, backup_path)
        except Exception as e:
            if verbose:
                print(f"Peringatan: Gagal membuat backup {backup_path}: {e}", file=sys.stderr)

    # 4. Atomically replace PRD.md with validated candidate
    try:
        os.replace(candidate_path, prd_path)
        if verbose:
            print(f"SUKSES: PRD.md berhasil diperbarui dan tervalidasi di: {prd_path}")
        if os.path.exists(backup_path):
            os.remove(backup_path)
        return True
    except Exception as e:
        if verbose:
            print(f"Error saat mengganti file PRD.md: {e}", file=sys.stderr)
        # Attempt recovery from backup if replace failed
        if os.path.exists(backup_path) and not os.path.exists(prd_path):
            shutil.copy2(backup_path, prd_path)
        return False


def run_self_tests() -> bool:
    """
    Runs deterministic validation test suite (Tests A through I)
    using controlled local test fixtures.
    """
    print("=" * 60)
    print("MENJALANKAN SELF-TEST VALIDATOR PRD (TEST A - TEST I)")
    print("=" * 60)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    prd_path = os.path.join(script_dir, "..", "PRD.md")

    if not os.path.exists(prd_path):
        print("ERROR: PRD.md baseline tidak ditemukan untuk pengujian.", file=sys.stderr)
        return False

    with open(prd_path, "r", encoding="utf-8") as f:
        valid_prd = f.read()

    baseline_len = len(valid_prd.encode("utf-8"))
    all_passed = True

    # TEST A: Valid current PRD candidate -> PASS
    ok, msg = validate_candidate(valid_prd, baseline_len)
    if ok:
        print("[PASS] TEST A: Valid current PRD candidate passed validation.")
    else:
        print(f"[FAIL] TEST A: Expected PASS but got FAIL: {msg}", file=sys.stderr)
        all_passed = False

    # TEST B: Empty candidate -> FAIL
    ok, msg = validate_candidate("", baseline_len)
    if not ok and "empty" in msg.lower():
        print(f"[PASS] TEST B: Empty candidate correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST B: Expected FAIL for empty candidate but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST C: Truncated PRD (below 40,000 bytes) -> FAIL
    truncated_prd = valid_prd[:30000]
    ok, msg = validate_candidate(truncated_prd, baseline_len)
    if not ok and ("short" in msg.lower() or "truncated" in msg.lower()):
        print(f"[PASS] TEST C: Truncated candidate correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST C: Expected FAIL for truncated candidate but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST D: Missing one required chapter (e.g. Chapter 11 Security & Compliance) -> FAIL
    missing_ch_prd = re.sub(r"##\s+11\.\s+Security & Compliance", "## 11. Redacted Chapter", valid_prd)
    ok, msg = validate_candidate(missing_ch_prd, baseline_len)
    if not ok and "11. Security & Compliance" in msg:
        print(f"[PASS] TEST D: Candidate missing Chapter 11 correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST D: Expected FAIL for missing chapter but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST E: Missing FEAT-01 -> FAIL
    missing_feat01_prd = valid_prd.replace("FEAT-01", "FEAT-XX")
    ok, msg = validate_candidate(missing_feat01_prd, baseline_len)
    if not ok and "FEAT-01" in msg:
        print(f"[PASS] TEST E: Candidate missing FEAT-01 correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST E: Expected FAIL for missing FEAT-01 but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST F: Missing FEAT-36 -> FAIL
    missing_feat36_prd = valid_prd.replace("FEAT-36", "FEAT-ZZ")
    ok, msg = validate_candidate(missing_feat36_prd, baseline_len)
    if not ok and "FEAT-36" in msg:
        print(f"[PASS] TEST F: Candidate missing FEAT-36 correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST F: Expected FAIL for missing FEAT-36 but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST G: Candidate containing a Gemini API key pattern -> FAIL
    # Dummy pattern used for testing detection only (not a real key)
    secret_candidate = valid_prd + "\n`AIzaSy" + "A" * 33 + "`\n"
    ok, msg = validate_candidate(secret_candidate, baseline_len)
    if not ok and "credential" in msg.lower():
        print(f"[PASS] TEST G: Candidate with secret pattern correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST G: Expected FAIL for secret pattern but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST H: Candidate containing positive inventory feature outside Chapter 1 -> FAIL
    inventory_candidate = valid_prd + "\n### FEAT-99: Warehouse Stock Management\nEntitas ProductItem dan StockMovement aktif.\n"
    ok, msg = validate_candidate(inventory_candidate, baseline_len)
    if not ok and "inventory" in msg.lower():
        print(f"[PASS] TEST H: Candidate with positive inventory outside Ch1 correctly rejected ({msg}).")
    else:
        print(f"[FAIL] TEST H: Expected FAIL for positive inventory but got: {ok}, {msg}", file=sys.stderr)
        all_passed = False

    # TEST I: Simulation of safe update failure -> PRD.md remains untouched
    original_size = os.path.getsize(prd_path)
    update_result = safe_update_prd("Invalid small content", prd_path, verbose=False)
    current_size = os.path.getsize(prd_path)
    if not update_result and current_size == original_size:
        print(f"[PASS] TEST I: Safe update rejection verified; PRD.md remains intact ({current_size} bytes).")
    else:
        print(f"[FAIL] TEST I: Expected safe update to fail and preserve PRD.md.", file=sys.stderr)
        all_passed = False

    print("=" * 60)
    if all_passed:
        print("SEMUA PENGUJIAN SELF-TEST (A-I) BERHASIL 100% (PASS)")
    else:
        print("BEBERAPA PENGUJIAN SELF-TEST GAGAL", file=sys.stderr)
    print("=" * 60)
    return all_passed


def main():
    if "--test" in sys.argv:
        success = run_self_tests()
        sys.exit(0 if success else 1)

    if "--validate" in sys.argv:
        target_idx = sys.argv.index("--validate") + 1
        target_file = sys.argv[target_idx] if target_idx < len(sys.argv) else "PRD.md"
        if not os.path.exists(target_file):
            print(f"File tidak ditemukan: {target_file}", file=sys.stderr)
            sys.exit(1)
        with open(target_file, "r", encoding="utf-8") as f:
            content = f.read()
        valid, reason = validate_candidate(content)
        print(f"Validasi {target_file}: {'PASS' if valid else 'FAIL'} — {reason}")
        sys.exit(0 if valid else 1)

    # Standard run
    api_key = os.environ.get("GEMINI_API_KEY", "").strip().replace('"', '').replace("'", "")
    update_notes = os.environ.get("UPDATE_NOTES", "Pembaruan fitur OCR Gemini dan optimasi sistem FinSmart.")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    prd_path = os.path.join(script_dir, "..", "PRD.md")

    if not api_key or api_key == "MY_GEMINI_API_KEY":
        print("Pemberitahuan: GEMINI_API_KEY tidak ditemukan atau bernilai default. Pembaruan PRD otomatis dilewati (baseline aman).")
        sys.exit(0)

    current_prd = ""
    if os.path.exists(prd_path):
        with open(prd_path, "r", encoding="utf-8") as f:
            current_prd = f.read()

    # Generate candidate via Gemini
    success, candidate, msg = generate_prd_candidate(api_key, update_notes, current_prd)
    if not success:
        print(f"Peringatan: {msg}", file=sys.stderr)
        print("PRD.md baseline dipertahankan tanpa perubahan.", file=sys.stderr)
        # Non-zero exit code because a real update was requested and failed
        sys.exit(1)

    # Validate and safe update
    updated = safe_update_prd(candidate, prd_path)
    if not updated:
        sys.exit(1)

    sys.exit(0)


if __name__ == "__main__":
    main()
