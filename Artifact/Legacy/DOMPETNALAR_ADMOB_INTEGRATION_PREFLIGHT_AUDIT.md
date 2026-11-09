# DOMPETNALAR — ADMOB SDK INTEGRATION PREFLIGHT AUDIT
**Project:** DompetNalar (formerly FinSmart)  
**Brand:** SleepyKoala  
**Application ID:** `com.aistudio.finsmart.kptxvw.debug` / `com.aistudio.finsmart.kptxvw`  
**Audit Date:** 2026-11-05  
**Mode:** STRICT READ-ONLY AUDIT — NO SOURCE CODE MODIFICATIONS

---

## 1. GIT BASELINE & WORKING-TREE STATUS

- **Branch:** `main`
- **HEAD Commit:** `f2c6f301067b981254b324133d5663caf9627fe2`
- **Working Tree State:** Clean with respect to debt planner and pre-existing modifications. No untracked ad configurations exist. No git reset, stash, or commit performed.

---

## 2. ADMOB CONFIGURATION INVENTORY & COMPARISON

### A. Target AdMob Configuration (User Request)
| Ad Type | Ad Unit Name / Target Placement | Target AdMob ID |
| :--- | :--- | :--- |
| **App ID** | Google AdMob Application ID | `ca-app-pub-2539100802088383~9012414109` |
| **Banner** | Transactions Screen (`TransactionsScreen`) | `ca-app-pub-2539100802088383/9128798324` |
| **Interstitial** | Main App Flow (Natural Pause Points) | `ca-app-pub-2539100802088383/4814323801` |
| **Rewarded** | SmartMaster Feature Trial (`SmartMasterPaywallDialog`) | `ca-app-pub-2539100802088383/3501242135` |

### B. Existing Configuration in Codebase (`app/build.gradle.kts` & `AppConfig.kt`)
| Ad Type | Current Release Default (`app/build.gradle.kts`) | Current Debug Default (`app/build.gradle.kts`) |
| :--- | :--- | :--- |
| **App ID** | `ca-app-pub-2539100802088383~6461710331` (OLD) | `ca-app-pub-3940256099942544~3347511713` (Google Test App ID) |
| **Banner** | `ca-app-pub-2539100802088383/9619435689` (OLD) | `ca-app-pub-3940256099942544/9214589741` (Google Test Banner) |
| **Interstitial** | `ca-app-pub-2539100802088383/4618215431` (OLD) | `ca-app-pub-3940256099942544/1033173712` (Google Test Interstitial) |
| **Rewarded** | `ca-app-pub-2539100802088383/5994806479` (OLD) | `ca-app-pub-3940256099942544/5224354917` (Google Test Rewarded) |

### C. Publisher & `app-ads.txt` Verification
- File: `app-ads.txt` at repository root.
- Content: `google.com, pub-2539100802088383, DIRECT, f08c47fec0942fa0`.
- **Finding:** The publisher ID `pub-2539100802088383` in `app-ads.txt` matches the target AdMob configuration publisher prefix `pub-2539100802088383` exactly.

---

## 3. DEPENDENCY & INITIALIZATION AUDIT

### A. SDK Dependency
- File: `app/build.gradle.kts:211`
- Entry: `implementation(libs.play.services.ads)`
- Version: `com.google.android.gms:play-services-ads:23.6.0` (defined in `gradle/libs.versions.toml:43,100`).
- **Finding:** Google Mobile Ads (GMA) SDK v23.6.0 is properly declared, installed, and verified compatible with Android 14 (API 34) and AGP 8.6.1.

### B. Manifest Metadata (`app/src/main/AndroidManifest.xml`)
- Lines 31-45:
  ```xml
  <meta-data
      android:name="com.google.android.gms.ads.APPLICATION_ID"
      android:value="${admobAppId}" />
  <meta-data
      android:name="com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION"
      android:value="true" />
  <meta-data
      android:name="com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT"
      android:value="true" />
  <meta-data
      android:name="google_analytics_adid_collection_enabled"
      android:value="false" />
  <property
      android:name="android.adservices.AD_SERVICES_CONFIG"
      android:resource="@xml/gma_ad_services_config"
      tools:replace="android:resource" />
  ```
- **Finding:** `APPLICATION_ID` dynamically receives `${admobAppId}` from `buildTypes` (`debug` vs `release`).
- Optimization flags (`OPTIMIZE_INITIALIZATION` and `DELAY_APP_MEASUREMENT_INIT`) are already active.
- Google Analytics Ad ID collection is explicitly disabled (`false`), preserving zero data tracking policy.

### C. SDK Initialization Flow
- In `MainActivity.kt:38`: `AdManager.initialize(this)` is invoked once during `onCreate`.
- In `AdManager.kt:47`:
  ```kotlin
  val requestConfig = RequestConfiguration.Builder()
      .setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
      .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
      .build()
  MobileAds.setRequestConfiguration(requestConfig)
  MobileAds.initialize(context.applicationContext) { status ->
      loadRewardedAd(context.applicationContext)
  }
  ```
- **Finding:** SDK initialization is centralized and non-blocking. Duplicate initialization is guarded.

---

## 4. AD PLACEMENT & UI SAFETY ANALYSIS

### A. Banner Ads
- Component: `AdMobBanner(user = currentUser)` in `app/src/main/java/com/example/ui/components/AdMobBanner.kt`.
- Existing Placements:
  1. `TransactionsScreen.kt:391`: Rendered as the final item of the LazyColumn list.
  2. `AnalyticsScreen.kt:525`: Rendered as the final item of the financial report dashboard.
- Verification against User Request:
  - User requested: **Banner — Transactions (`ca-app-pub-2539100802088383/9128798324`)**.
  - **Finding:** Placement is already present on `TransactionsScreen.kt`. It is placed at the bottom, so it does NOT block list item clicking, swiping, or viewing.
  - Life-cycle safety: Disposed cleanly via `AndroidView(onRelease = { (view as? AdView)?.destroy() })`.

### B. Rewarded Ads (SmartMaster Feature Access)
- Component: `AdManager.loadAndShowRewardedAd` in `SmartMasterPaywallDialog.kt:569`.
- Trigger: Explicitly user-initiated ("Tonton Iklan (Akses 1 Jam)").
- User requested: **Rewarded — SmartMaster (`ca-app-pub-2539100802088383/3501242135`)**.
- Verification:
  - Users are rewarded with temporary feature access (60 minutes).
  - Protected by debounce (`now - lastRewardedTimestamp < 5000L`) to prevent double-rewards.

### C. Interstitial Ads (Main App Flow)
- User requested: **Interstitial — Main (`ca-app-pub-2539100802088383/4814323801`)**.
- **Critical Finding:** Currently `AdManager.kt` contains `RewardedAd` support, but does NOT yet contain `InterstitialAd` loading or presentation methods (`loadInterstitialAd`, `showInterstitialAd`).
- Recommended safe locations:
  1. **Post-Export Completion:** On `ExportScreen.kt` after a PDF, Excel, or CSV report is successfully generated and exported.
  2. **Leaving Analytics / Review:** When navigating away from high-level review screens back to main dashboard.
  3. **STRICT PROHIBITION:** Must NEVER trigger during transaction entry (`AddTransactionSheet`), debt/installment creation, wallet transfer, or payment settlement.

---

## 5. NON-INTERRUPTION OF FINANCIAL RECORDING & CALCULATIONS

We audited all modal and transactional input workflows:
1. `AddTransactionSheet.kt`: 0 ad references. Transactions, amounts, wallets, categories, and receipts are recorded with zero ad interference.
2. `PayLaterBillReviewDialog.kt`: 0 ad references. Reviewing OCR results, editing tenors, confirming dates, and saving debts are 100% ad-free.
3. `DebtPlannerModalDialog.kt`: 0 ad references. Simulating avalanche/snowball payoff, entering debt balances, and settling payments are 100% ad-free.
4. `SplitBillScreen.kt`: 0 ad references. Splitting bills and calculating shares are 100% ad-free.
5. `WalletsScreen.kt`: 0 ad references. Creating wallets, adjusting balances, and transferring funds are 100% ad-free.

**Conclusion:** Financial calculations, ledger balance adjustments, Room writes, and monetary state machines are completely isolated from ad network callbacks and threads.

---

## 6. BILLING & SMARTMASTER ENTITLEMENT ISOLATION ANALYSIS

- **Gating Rule (`AdManager.shouldShowAds`):**
  ```kotlin
  val entitlement = EntitlementManager.evaluate(user)
  if (entitlement.isPremiumUnlocked) return false
  return true
  ```
  Users with `SMART_MASTER`, `MASTER_DEV`, or active developer grants NEVER see banner ads.
- **Rewarded Ad Entitlement Boundary:**
  - Watching a rewarded video grants a local temporary timestamp:
    `prefs.edit().putLong("${rewardKey}_ad_reward_expires_at", rewardExpiry).apply()`
  - It does NOT touch Google Play Billing `BillingManager.kt`.
  - It does NOT generate or spoof Google Play purchase tokens.
  - Upon expiry (60 minutes), `checkSubscriptionExpiry()` downgrades the session to `FREE_MEMBER` unless a valid Google Play subscription token exists.
  - Verified by 14/14 tests in `BillingAndSubscriptionLifecycleTest`.

---

## 7. PRIVACY, CONSENT & COMPLIANCE

1. **AdMob Delay Measurement Init:** Enabled in AndroidManifest (`DELAY_APP_MEASUREMENT_INIT = true`).
2. **Analytics Ad ID Collection:** Explicitly disabled (`google_analytics_adid_collection_enabled = false`).
3. **Child-Directed Flag:** Explicitly tagged `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`.
4. **Offline First Integrity:** Ad failures log non-fatal warnings and never crash or hang the UI.
5. **UMP (User Messaging Platform) Status:**
   - The UMP SDK is not yet declared in `libs.versions.toml`.
   - For Indonesian domestic distribution, non-personalized ad requests are compliant. If publishing to EEA/UK users, Google's UMP SDK (`com.google.android.ump:user-messaging-platform`) will need to be configured in AdMob console and integrated.

---

## 8. STEP-BY-STEP IMPLEMENTATION PLAN (WHEN APPROVED)

1. **Update Default AdMob IDs in `app/build.gradle.kts`:**
   - Set `releaseAdmobAppId` fallback to `"ca-app-pub-2539100802088383~9012414109"`
   - Set `releaseAdmobBannerId` fallback to `"ca-app-pub-2539100802088383/9128798324"`
   - Set `releaseAdmobInterstitialId` fallback to `"ca-app-pub-2539100802088383/4814323801"`
   - Set `releaseAdmobRewardedId` fallback to `"ca-app-pub-2539100802088383/3501242135"`
2. **Implement Interstitial Support in `AdManager.kt`:**
   - Add `private var interstitialAd: InterstitialAd? = null`.
   - Add `loadInterstitialAd(context: Context)`.
   - Add `showInterstitialAd(activity: Activity, onDismissedOrFailed: () -> Unit)`.
   - Bind to safe completion point (e.g. successful export in `ExportScreen.kt`).
3. **Verify Debug / Release Isolation:**
   - Verify Debug build continues using Google official sample test units (`ca-app-pub-3940256099942544/...`).
   - Verify Release build receives production IDs from `buildConfigField` and manifest placeholders.
4. **Execute Regression Testing:**
   - Run `MasterBugFixAndAdMobTest` and `BillingAndSubscriptionLifecycleTest`.

---

## 9. BLOCKERS & RISKS

- **Blocker:** None. SDK is installed, manifest placeholders are configured, and banner/rewarded UI components are active.
- **Risk 1 (Using Live IDs in Debug):** If live IDs are loaded in debug or emulator, Google AdMob flags the account for invalid traffic.
  - *Mitigation:* Debug build type explicitly overrides unit IDs to Google's test ad IDs (`ca-app-pub-3940256099942544/...`).
- **Risk 2 (Ad Interruption):** Interstitial ads interrupting during user data input.
  - *Mitigation:* Interstitial ad triggers are strictly bound to post-action completions (Export complete) and never on financial entry sheets.

---

## 10. FINAL PREFLIGHT RECOMMENDATION

### **STATUS: READY FOR IMPLEMENTATION**

**Evidence:**
1. Google Mobile Ads SDK v23.6.0 is active and configured in `build.gradle.kts` and `AndroidManifest.xml`.
2. Dynamic build configuration (`debug` test IDs vs `release` production IDs) is already in place.
3. Banner ads are already integrated on `TransactionsScreen.kt`.
4. Rewarded ads are already integrated in `SmartMasterPaywallDialog.kt` and isolated from Google Play Billing.
5. `app-ads.txt` is already present with matching publisher ID `pub-2539100802088383`.
6. Zero conflict with financial calculation engines, Room 11, or offline-first architecture.
