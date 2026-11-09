# DOMPETNALAR — VERSION 1.0.13 FINAL RELEASE GATE AUDIT
**Project:** DompetNalar (formerly FinSmart)  
**Brand:** SleepyKoala  
**Target AAB:** `app/build/outputs/bundle/release/app-release.aab`  
**Expected Package:** `com.aistudio.finsmart.kptxvw`  
**Expected Version:** `1.0.13` (versionCode: `13`)  
**Audit Date:** 2026-11-05  
**Mode:** STRICT READ-ONLY AUDIT — NO SOURCE CODE MODIFICATIONS  

---

## 1. INDEPENDENT AAB ARTIFACT VERIFICATION

The release artifact `app/build/outputs/bundle/release/app-release.aab` was independently inspected and calculated on disk:

| Parameter | Report Specification | Actual Calculated Value | Match Status | Evidence / Tool |
| :--- | :--- | :--- | :--- | :--- |
| **Existence** | Exists | `True` | **MATCH** | File system check |
| **File Size** | `20394760 bytes` | `20394760 bytes` (19.45 MB) | **MATCH (Exact)** | `os.path.getsize` |
| **SHA-256** | `c24480792551...db05e` | `c2448079255130ad94476af05301b0418153cdc8b61de6b2ae36a0b53acdb05e` | **MATCH (Exact)** | Python SHA-256 stream |
| **Package ID** | `com.aistudio.finsmart.kptxvw` | `com.aistudio.finsmart.kptxvw` | **MATCH** | `base/manifest/AndroidManifest.xml` |
| **Version Code** | `13` | `13` (`0x0D`) | **MATCH** | Binary manifest attribute |
| **Version Name** | `"1.0.13"` | `"1.0.13"` | **MATCH** | Binary manifest attribute |
| **AdMob App ID** | `ca-app-pub-2539100802088383~9012414109` | `ca-app-pub-2539100802088383~9012414109` | **MATCH** | Manifest `meta-data` tag |

**Conclusion:** The release AAB matches the implementation report with 100% precision.

---

## 2. RELEASE SIGNING IDENTITY VERIFICATION

The signing certificate of `app/build/outputs/bundle/release/app-release.aab` was extracted using `keytool -printcert -jarfile` and compared directly with the existing known-good release bundle `DompetNalar-v1.0.8-versionCode8-release.aab`:

```text
Signer #1 Certificate:
Owner: L=gresik
Issuer: L=gresik
Valid from: Thu Oct 01 21:03:50 WIB 2026 until Mon Sep 25 21:03:50 WIB 2051
SHA-1 Fingerprint:   34:C0:4B:9D:B4:43:7E:FE:56:1B:46:B7:BF:18:7D:5C:28:B2:8D:5B
SHA-256 Fingerprint: 30:F0:D4:FF:58:83:04:5E:80:EC:DD:B8:16:87:42:A1:C0:E6:EE:64:18:A1:07:67:E5:6A:9A:7F:C2:9D:01:89
Signature Algorithm: SHA256withRSA (2048-bit key)
```

- **Comparison Result:** The certificate owner, issuer, validity period, SHA-1, and SHA-256 fingerprints are **100% identical** between `app-release.aab` (v1.0.13) and `DompetNalar-v1.0.8-versionCode8-release.aab` (v1.0.8).
- **Play Console Upload Key Status:** The bundle is verified to be signed with the identical local release key used for prior successful release builds. However, because direct live Google Play Developer API access is unavailable in this sandbox:
  `NOT VERIFIED — PLAY CONSOLE UPLOAD KEY EVIDENCE UNAVAILABLE` (relies on local known-good v1.0.8 bundle parity).

---

## 3. PUBLIC APP-ADS.TXT AVAILABILITY VERIFICATION

The exact public URL was queried using `curl.exe -i -s "https://sleepykoala.pages.dev/app-ads.txt"`:

- **Target URL:** `https://sleepykoala.pages.dev/app-ads.txt`
- **HTTP Response Status:** `HTTP/1.1 200 OK`
- **Redirects:** None (Direct 200 OK via Cloudflare CDN)
- **Content-Type:** `text/plain; charset=utf-8`
- **Authentication Required:** None (`Access-Control-Allow-Origin: *`)
- **Exact Body Content:**
  ```text
  google.com, pub-2539100802088383, DIRECT, f08c47fec0942fa0
  ```
- **Finding:** The public record is live, accessible worldwide without authentication, and matches the target AdMob publisher ID `pub-2539100802088383` exactly.

---

## 4. REWARDED UI & BILLING ISOLATION AUDIT

### A. Code Inspection
1. **In `SmartMasterPaywallDialog.kt`:**
   - Lines 572-575:
     ```kotlin
     onEarnedReward = {
         Toast.makeText(context, AppStrings.t("Iklan selesai ditonton! Terima kasih.", "Rewarded ad completed! Thank you."), Toast.LENGTH_LONG).show()
         onWatchRewardedAd()
     }
     ```
   - Lines 612-617:
     ```kotlin
     Text(
         text = "🎬 Tonton Iklan untuk Akses Premium 1 Jam",
         fontWeight = FontWeight.Bold,
         fontSize = 12.sp,
         color = Color(0xFF10B981)
     )
     ```
2. **In `FinSmartApp.kt`:**
   - Lines 782-785:
     ```kotlin
     onWatchRewardedAd = {
         // Rewarded-Billing Isolation:
         // Do not grant SmartMaster or Billing entitlement through rewarded ads.
         // Production reward policy remains an owner decision.
     }
     ```

### B. Audit Findings
- **Billing Isolation:** **VERIFIED SECURE.** No Google Play Billing purchase token, subscription state, or Room entitlement write is triggered by watching a rewarded ad.
- **Critical UI/UX Discrepancy:**
  The user-facing button label visibly states:
  `"🎬 Tonton Iklan untuk Akses Premium 1 Jam"`
  However, `FinSmartApp.kt` intentionally does **not** grant temporary premium access upon ad completion.
  - **Risk:** Under Google Play's Developer Program Policy for Rewarded Ads, providing a button that explicitly offers a specific reward ("Akses Premium 1 Jam") without actually delivering that reward post-watch could be flagged by a Google Play policy reviewer or cause user dissatisfaction.
  - **Recommendation:** Before publishing to open production tracks, either update the button text to a neutral non-premium reward label (or hide the button) until an approved non-premium reward is defined by the owner.

---

## 5. UMP CONSENT AUDIT

Inspected `ConsentManager.kt`, `MainActivity.kt`, `AdManager.kt`, and `AdMobBanner.kt`:

1. **Consent Information Update:** `consentInformation.requestConsentInfoUpdate` is called on app launch with `tagForUnderAgeOfConsent = false` (`ConsentManager.kt:66-72`).
2. **Form Presentation:** `UserMessagingPlatform.loadAndShowConsentFormIfRequired` is executed upon update success (`ConsentManager.kt:75`).
3. **`canRequestAds()` Gate:**
   - `AdManager.loadInterstitialAd`: guarded by `if (!canRequestAds(appContext)) return` (`AdManager.kt:125`).
   - `AdManager.loadRewardedAd`: guarded by `if (!canRequestAds(appContext)) return` (`AdManager.kt:289`).
   - `AdMobBanner`: guarded by `if (!ConsentManager.canRequestAds(currentContext)) return` (`AdMobBanner.kt:29`).
4. **Privacy Options Support:** `isPrivacyOptionsRequired` and `showPrivacyOptionsForm` are implemented and call official UMP methods (`ConsentManager.kt:41,49`).
5. **Offline & Network Failure Behavior:** In `requestConsentInfoUpdate` failure callback, `ConsentManager` falls back to checking `consentInformation.canRequestAds()` and never crashes or assumes consent (`ConsentManager.kt:89-94`).
6. **Financial Operations Isolation:** Consent checks and forms do not touch ledger models, DAOs, or accounting balance computations.

---

## 6. AD PLACEMENT & PREMIUM EXEMPTION AUDIT

1. **Banner Placement (`TransactionsScreen.kt:390-397`):** Rendered as the final item of the scrollable `LazyColumn`. Does not float or obscure list rows, action buttons, or system bars.
2. **Post-Export Interstitial Placement (`ExportScreen.kt:672-674, 725-727`):** Interstitial is invoked only upon confirmed completion (`savedFile != null` for Download; after `Intent.createChooser` for Share). Never invoked on export errors.
3. **10-Minute Frequency Cap (`AdManager.kt:239, 268-272`):**
   - Timestamp is recorded inside `onAdShowedFullScreenContent` (actual display confirmation, not dismissal).
   - Frequency cap condition `System.currentTimeMillis() - lastShown < 10 * 60 * 1000L` blocks rapid repeat impressions.
4. **Premium Exemption (`AdManager.kt:89-92`):** `shouldShowAds(user)` checks `EntitlementManager.evaluate(user).isPremiumUnlocked`. Users with `SMART_MASTER` or `MASTER_DEV` never receive regular banner or interstitial ads.

---

## 7. REGRESSION EVIDENCE SUMMARY

- **Targeted Unit Tests:** **45/45 Passed (100%)**
  - `AdMobIntegrationSecurityTest`: 4/4 Passed
  - `AdManagerInterstitialTest`: 5/5 Passed
  - `ConsentManagerTest`: 2/2 Passed
  - `MasterBugFixAndAdMobTest`: 23/23 Passed
  - `DebtDueDateStatusTest`: 10/10 Passed
  - `DebtPlannerResetTest`: 1/1 Passed
- **Full Unit Test Suite (820+ tests):** `NOT VERIFIED IN THIS RUN` (full suite was not re-executed to avoid Windows SQLite temporary directory file locking).
- **Physical Device Ad Testing:** `NOT VERIFIED ON PHYSICAL DEVICE` (verified via JVM unit tests, Robolectric test runner, and APK/AAB bytecode static audit).

---

## 8. FINAL DECISION

**DECISION:**
### `CONDITIONALLY READY — EXPLICIT LIMITATIONS`

### Justification & Limitations:
1. **Ready Elements:**
   - Release AAB (`app-release.aab`) exists, is non-zero (19.45 MB), exactly matches the expected SHA-256 (`c2448079255130ad94476af05301b0418153cdc8b61de6b2ae36a0b53acdb05e`), package ID (`com.aistudio.finsmart.kptxvw`), and version (`1.0.13` / versionCode `13`).
   - Signing certificate is 100% identical to the known-good v1.0.8 release AAB.
   - Public `app-ads.txt` is verified live with HTTP 200 OK at `https://sleepykoala.pages.dev/app-ads.txt`.
   - AdMob production IDs and UMP consent gates are embedded and verified.
2. **Explicit Limitations to Acknowledge Prior to Production Rollout:**
   - **UI Discrepancy:** The button text in `SmartMasterPaywallDialog.kt` still reads `"🎬 Tonton Iklan untuk Akses Premium 1 Jam"`, while `FinSmartApp.kt` does not grant premium access post-watch to preserve Billing policy isolation.
   - **Play Console Keystore Evidence:** Matching against Google Play Console's web portal upload key fingerprint cannot be directly verified without access to Play Console API/credentials.
   - **Device Ad Verification:** Live banner/interstitial serving must be observed on a physical test device via Internal Testing before promotion to production.
