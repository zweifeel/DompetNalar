package com.example.ui.viewmodel
import com.example.util.AppStrings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AppIconEdition
import com.example.data.model.FinancialPersonality
import com.example.data.model.User
import com.example.data.model.UserGamificationState
import com.example.data.model.UserRole
import com.example.data.repository.UserRepository
import com.example.BuildConfig
import com.example.util.GamificationManager
import com.example.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.session.SessionManager
import com.example.util.DriveAuthResult
import com.example.util.EntitlementManager
import com.example.util.GoogleDriveService
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.util.Random

sealed interface MaintenanceState {
    object Idle : MaintenanceState
    object InProgress : MaintenanceState
    object Success : MaintenanceState
    data class Failure(val message: String, val cause: Throwable? = null) : MaintenanceState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
    private val userRepository: UserRepository
    private val database: FinSmartDatabase

    val currentUser: StateFlow<User?> = SessionManager.currentUser

    companion object {
        const val PREF_KEY_GUEST_BANNER_DISMISSED_AT = "guest_banner_dismissed_at"
        const val GUEST_BANNER_COOLDOWN_MILLIS = 24L * 60 * 60 * 1000L // 24 jam cooldown
        const val GUEST_BANNER_INITIAL_DELAY_MILLIS = 2L * 60 * 1000L // 2 menit delay
        const val ROLLING_STREAK_REWARD_PERIOD_MILLIS = 168L * 3600000L // 7 hari = 168 jam rolling eligibility (P0)
        const val GRANT_SYNC_DEBOUNCE_MILLIS = 30_000L // 30 detik debounce pencegahan request berulang (Phase 4B.7)
    }

    private var lastGrantSyncTimestamp = 0L
    private var grantSyncJob: Job? = null
    private var firebaseAuthStateListener: FirebaseAuth.AuthStateListener? = null

    private val _isGuestReminderBannerVisible = MutableStateFlow(false)
    val isGuestReminderBannerVisible: StateFlow<Boolean> = _isGuestReminderBannerVisible.asStateFlow()

    private var guestBannerJob: Job? = null

    private val _isMasterDevPanelOpen = MutableStateFlow(false)
    val isMasterDevPanelOpen: StateFlow<Boolean> = _isMasterDevPanelOpen.asStateFlow()

    private val _isPaywallOpen = MutableStateFlow(false)
    val isPaywallOpen: StateFlow<Boolean> = _isPaywallOpen.asStateFlow()

    private val _isAuthModalOpen = MutableStateFlow(false)
    val isAuthModalOpen: StateFlow<Boolean> = _isAuthModalOpen.asStateFlow()

    private val _isDeleteAccountOpen = MutableStateFlow(false)
    val isDeleteAccountOpen: StateFlow<Boolean> = _isDeleteAccountOpen.asStateFlow()

    private val _isProminentDisclosureOpen = MutableStateFlow(false)
    val isProminentDisclosureOpen: StateFlow<Boolean> = _isProminentDisclosureOpen.asStateFlow()

    // OTP State for Email Registration
    private val _pendingOtpCode = MutableStateFlow<String?>(null)
    val pendingOtpCode: StateFlow<String?> = _pendingOtpCode.asStateFlow()

    private val _isInitialMasterSetupOpen = MutableStateFlow(false)
    val isInitialMasterSetupOpen: StateFlow<Boolean> = _isInitialMasterSetupOpen.asStateFlow()

    fun closeInitialMasterSetup() {
        _isInitialMasterSetupOpen.value = false
    }

    private val _isChangePasswordOpen = MutableStateFlow(false)
    val isChangePasswordOpen: StateFlow<Boolean> = _isChangePasswordOpen.asStateFlow()

    private val _changePasswordError = MutableStateFlow<String?>(null)
    val changePasswordError: StateFlow<String?> = _changePasswordError.asStateFlow()

    fun openChangePassword() {
        _changePasswordError.value = null
        _isChangePasswordOpen.value = true
    }

    fun closeChangePassword() {
        _changePasswordError.value = null
        _isChangePasswordOpen.value = false
    }

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isSuccessToast = MutableStateFlow<String?>(null)
    val isSuccessToast: StateFlow<String?> = _isSuccessToast.asStateFlow()

    fun clearSuccessToast() {
        _isSuccessToast.value = null
    }

    private val _isGoogleSignInLoading = MutableStateFlow(false)
    val isGoogleSignInLoading: StateFlow<Boolean> = _isGoogleSignInLoading.asStateFlow()

    private val _isDriveSyncing = MutableStateFlow(false)
    val isDriveSyncing: StateFlow<Boolean> = _isDriveSyncing.asStateFlow()

    private val _driveRecoveryIntent = MutableStateFlow<Intent?>(null)
    val driveRecoveryIntent: StateFlow<Intent?> = _driveRecoveryIntent.asStateFlow()

    fun clearDriveRecoveryIntent() {
        _driveRecoveryIntent.value = null
    }

    private val _isDriveConnected = MutableStateFlow(false)
    val isDriveConnected: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _driveEmail = MutableStateFlow("")
    val driveEmail: StateFlow<String?> = _driveEmail.asStateFlow()

    private val _driveLastBackup = MutableStateFlow("")
    val driveLastBackup: StateFlow<String> = _driveLastBackup.asStateFlow()

    private val _driveAutoSync = MutableStateFlow(false)
    val driveAutoSync: StateFlow<Boolean> = _driveAutoSync.asStateFlow()

    private val _driveAutoBackupInterval = MutableStateFlow(
        GoogleDriveService.getAutoBackupIntervalHours(application)
    )
    val driveAutoBackupInterval: StateFlow<Long> = _driveAutoBackupInterval.asStateFlow()

    private val _singleFeaturePassCredits = MutableStateFlow(prefs.getInt("single_feature_credits", 0))
    val singleFeaturePassCredits: StateFlow<Int> = _singleFeaturePassCredits.asStateFlow()

    private val _isGamificationHubOpen = MutableStateFlow(false)
    val isGamificationHubOpen: StateFlow<Boolean> = _isGamificationHubOpen.asStateFlow()

    private val _isPersonalityQuizOpen = MutableStateFlow(false)
    val isPersonalityQuizOpen: StateFlow<Boolean> = _isPersonalityQuizOpen.asStateFlow()

    private val _isDuressPinModalOpen = MutableStateFlow(false)
    val isDuressPinModalOpen: StateFlow<Boolean> = _isDuressPinModalOpen.asStateFlow()

    private val _isDebtPlannerOpen = MutableStateFlow(false)
    val isDebtPlannerOpen: StateFlow<Boolean> = _isDebtPlannerOpen.asStateFlow()

    private val _isAssetTrackerOpen = MutableStateFlow(false)
    val isAssetTrackerOpen: StateFlow<Boolean> = _isAssetTrackerOpen.asStateFlow()

    private val _isReceiptGeneratorOpen = MutableStateFlow(false)
    val isReceiptGeneratorOpen: StateFlow<Boolean> = _isReceiptGeneratorOpen.asStateFlow()

    private val _isNotificationCenterOpen = MutableStateFlow(false)
    val isNotificationCenterOpen: StateFlow<Boolean> = _isNotificationCenterOpen.asStateFlow()

    private val authSessionEpoch = java.util.concurrent.atomic.AtomicLong(0)

    private val _profilePhotoUri = MutableStateFlow(prefs.getString("user_profile_photo_uri", null))
    val profilePhotoUri: StateFlow<String?> = _profilePhotoUri.asStateFlow()

    fun updateProfilePhotoUri(uri: String?) {
        _profilePhotoUri.value = uri
        if (uri.isNullOrBlank()) {
            prefs.edit().remove("user_profile_photo_uri").apply()
        } else {
            prefs.edit().putString("user_profile_photo_uri", uri).apply()
        }
    }

    // FEAT-36 Shake Gesture Action ("OFF", "TRANSACTION", "STEALTH")
    private val _shakeGestureAction = MutableStateFlow(prefs.getString("shake_gesture_action", "OFF") ?: "OFF")
    val shakeGestureAction: StateFlow<String> = _shakeGestureAction.asStateFlow()

    fun updateShakeGestureAction(action: String) {
        _shakeGestureAction.value = action
        prefs.edit().putString("shake_gesture_action", action).apply()
        try {
            getApplication<Application>().getSharedPreferences("finsmart_prefs", Context.MODE_PRIVATE)
                .edit().putString("shake_gesture_action", action).apply()
        } catch (_: Exception) {}
    }

    // Rewarded Ads Live Countdown (Bug 2)
    private val _adRewardRemainingSeconds = MutableStateFlow(0L)
    val adRewardRemainingSeconds: StateFlow<Long> = _adRewardRemainingSeconds.asStateFlow()
    private var adRewardCountdownJob: Job? = null

    private fun getSanitizedInitialStreak(): Int {
        val initialized = prefs.getBoolean("gamify_streak_initialized", false)
        val rawStreak = prefs.getInt("gamify_streak", 1)
        val lastDateStr = prefs.getString("gamify_last_activity_date", null)
        if (!initialized && rawStreak == 7 && lastDateStr == null) {
            prefs.edit()
                .putInt("gamify_streak", 1)
                .putInt("gamify_best_streak", 1)
                .putBoolean("gamify_streak_initialized", true)
                .apply()
            return 1
        }
        return rawStreak.coerceAtLeast(1)
    }

    private fun getSanitizedInitialBestStreak(): Int {
        val streak = getSanitizedInitialStreak()
        val rawBest = prefs.getInt("gamify_best_streak", 1)
        return maxOf(streak, rawBest)
    }

    private fun getSanitizedInitialBadges(): Set<String> {
        val saved = prefs.getStringSet("gamify_badges", emptySet())?.toMutableSet() ?: mutableSetOf()
        val streak = getSanitizedInitialStreak()
        if (streak < 7 && saved.contains("rookie_planner") && !prefs.getBoolean("gamify_streak_initialized", false)) {
            saved.remove("rookie_planner")
        }
        return saved
    }

    private val _gamificationState = MutableStateFlow(
        UserGamificationState(
            level = prefs.getInt("gamify_level", 1),
            currentXp = prefs.getInt("gamify_xp", 20),
            xpForNextLevel = GamificationManager.calculateLevel(prefs.getInt("gamify_xp", 20)).second,
            dailyStreak = getSanitizedInitialStreak(),
            bestStreak = getSanitizedInitialBestStreak(),
            personality = try {
                FinancialPersonality.valueOf(prefs.getString("gamify_personality", "BALANCED") ?: "BALANCED")
            } catch (e: Exception) { FinancialPersonality.BALANCED },
            activeIconEdition = try {
                AppIconEdition.valueOf(prefs.getString("gamify_icon", "DEFAULT_CLASSIC") ?: "DEFAULT_CLASSIC")
            } catch (e: Exception) { AppIconEdition.DEFAULT_CLASSIC },
            customBackgroundUri = prefs.getString("gamify_bg_uri", null),
            customBackgroundOpacity = prefs.getFloat("gamify_bg_opacity", 0.3f),
            isNoSpendChallengeActive = prefs.getBoolean("gamify_nospend", false),
            mainPin = prefs.getString("security_main_pin", "123456") ?: "123456",
            duressPin = prefs.getString("security_duress_pin", "999999") ?: "999999",
            isBiometricEnabled = prefs.getBoolean("security_biometric", false),
            unlockedBadgeIds = getSanitizedInitialBadges()
        )
    )
    val gamificationState: StateFlow<UserGamificationState> = _gamificationState.asStateFlow()

    private val _maintenanceState = MutableStateFlow<MaintenanceState>(MaintenanceState.Idle)
    val maintenanceState: StateFlow<MaintenanceState> = _maintenanceState.asStateFlow()

    init {
        com.example.util.EntitlementRegistry.purgeTrialRecords(application)
        database = FinSmartDatabase.getDatabase(application, viewModelScope)
        userRepository = UserRepository(database.userDao())

        // Initialize Google Drive connection preferences
        _isDriveConnected.value = com.example.util.GoogleDriveService.isConnected(application)
        _driveEmail.value = com.example.util.GoogleDriveService.getConnectedEmail(application)
        _driveLastBackup.value = com.example.util.GoogleDriveService.getLastBackupTime(application)
        _driveAutoSync.value = com.example.util.GoogleDriveService.isAutoSyncEnabled(application)

        // Initialize canonical SessionManager and restore session or enter guest mode
        SessionManager.initialize(application)
        viewModelScope.launch {
            // Phase 1G.7: Controlled maintenance purge with explicit failure handling and startup barrier.
            // Executed strictly on Dispatchers.IO and completed BEFORE session is restored.
            val targetEmail = "lunachroc07@gmail.com"
            val targetUsername = "Lukas"
            val targetCandidate = try {
                database.userDao().getUserByEmail(targetEmail)
                    ?: database.userDao().getUserByIdentifier(targetUsername)
            } catch (_: Exception) {
                null
            }

            if (targetCandidate != null) {
                _maintenanceState.value = MaintenanceState.InProgress
                val purgeResult = withContext(Dispatchers.IO) {
                    FinSmartDatabase.purgeTargetDeveloperAccountAtomically(database)
                }

                if (purgeResult.isFailure) {
                    val error = purgeResult.exceptionOrNull()
                    val errorMsg = error?.message ?: "Controlled maintenance purge failed"
                    Log.e("AuthViewModel", "Target developer account maintenance purge failed: $errorMsg", error)
                    _maintenanceState.value = MaintenanceState.Failure(errorMsg, error)
                    _authError.value = errorMsg

                    // Safely block affected startup path: if saved session belongs to Lukas, wipe it immediately
                    val savedId = prefs.getLong("saved_user_id", -1L)
                    if (savedId > 0L) {
                        val savedUser = try { database.userDao().getUserById(savedId) } catch (_: Exception) { null }
                        if (savedUser != null && (savedUser.username.equals("Lukas", ignoreCase = true) || savedUser.email.equals("lunachroc07@gmail.com", ignoreCase = true))) {
                            prefs.edit().remove("saved_user_id").apply()
                            SessionManager.logout()
                        }
                    }
                    return@launch
                }
                _maintenanceState.value = MaintenanceState.Success
            } else {
                _maintenanceState.value = MaintenanceState.Success
            }

            val restored = SessionManager.restoreSession(database.userDao())
            if (!restored) {
                SessionManager.startGuestSession(database.userDao())
            } else {
                // Sesi non-guest berhasil dipulihkan: picu sinkronisasi Remote Grant dan User Registry (Phase 4B.7)
                val restoredUser = SessionManager.currentUser.value
                if (restoredUser != null && !restoredUser.isGuest && !restoredUser.email.isNullOrBlank()) {
                    triggerStartupSessionSync(restoredUser.email)
                }
            }
        }

        // Listener reaktif Firebase AuthState: sinkronkan grant saat kredensial cloud Firebase siap (Phase 4B.7)
        try {
            if (FirebaseApp.getApps(application).isNotEmpty()) {
                val auth = FirebaseAuth.getInstance()
                val listener = FirebaseAuth.AuthStateListener { fbAuth ->
                    val fbUser = fbAuth.currentUser
                    if (fbUser != null && !fbUser.isAnonymous) {
                        val localUser = currentUser.value
                        if (localUser != null && !localUser.isGuest && localUser.email.equals(fbUser.email, ignoreCase = true)) {
                            triggerStartupSessionSync(localUser.email, fbUser, force = false)
                        }
                    }
                }
                firebaseAuthStateListener = listener
                auth.addAuthStateListener(listener)
            }
        } catch (e: Exception) {
            Log.w("AuthViewModel", "FirebaseAuth AuthStateListener initialization skipped: ${e.message}")
        }

        // Silent startup migration of legacy plaintext passwords to PBKDF2
        viewModelScope.launch {
            PasswordHasher.migrateLegacyPasswords(userRepository)
        }

        // Migrate legacy plaintext PIN to PBKDF2 SHA-256 hash securely
        if (!prefs.contains("security_main_pin_hash")) {
            val legacyMain = prefs.getString("security_main_pin", "123456") ?: "123456"
            val legacyDuress = prefs.getString("security_duress_pin", "999999") ?: "999999"
            prefs.edit()
                .putString("security_main_pin_hash", PasswordHasher.hashPassword(legacyMain))
                .putString("security_duress_pin_hash", PasswordHasher.hashPassword(legacyDuress))
                .remove("security_main_pin")
                .remove("security_duress_pin")
                .apply()
        }


        // Monitor session state to schedule or hide Guest Reminder Banner and handle subscription expiry
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user?.isGuest == true) {
                    scheduleGuestBannerPrompt()
                } else {
                    guestBannerJob?.cancel()
                    _isGuestReminderBannerVisible.value = false
                }

                // Auto-sync and downgrade expired subscriptions / sanitize guest ad rewards
                checkAndSanitizeEntitlement()
            }
        }

        // Initialize Google Play Billing Client
        try {
            com.example.util.BillingManager.initialize(application) { productId, purchaseToken, orderId, purchaseTime, isAutoRenewing, isExplicitPurchase ->
                applyGooglePlayPurchase(productId, purchaseToken, orderId, purchaseTime, isAutoRenewing, isExplicitPurchase)
            }
            com.example.util.BillingManager.onSubscriptionsSynced = { activeSubs, isConnected ->
                handleGooglePlaySubscriptionsSynced(activeSubs, isConnected)
            }
        } catch (e: Exception) {
            Log.w("AuthViewModel", "BillingManager init warning: ${e.message}")
        }
    }

    val allRegisteredUsers: StateFlow<List<User>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setInitialMasterPassword(newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            _authError.value = null
            if (!BuildConfig.DEBUG) {
                _authError.value = "Pengaturan awal kata sandi developer hanya diizinkan pada build Debug."
                return@launch
            }
            val trimmedNew = newPassword.trim()
            val trimmedConfirm = confirmPassword.trim()

            if (trimmedNew.isEmpty()) {
                _authError.value = "Kata sandi tidak boleh kosong."
                return@launch
            }
            if (trimmedNew.length < 6) {
                _authError.value = "Kata sandi minimal 6 karakter."
                return@launch
            }
            if (trimmedNew != trimmedConfirm) {
                _authError.value = "Konfirmasi kata sandi tidak cocok."
                return@launch
            }

            if (!BuildConfig.DEBUG) {
                _authError.value = "Pengaturan awal kata sandi tidak diizinkan pada build release."
                return@launch
            }

            var masterDev = userRepository.getUserByEmail(com.example.config.DeveloperAccountConfig.CONTACT_DEV_EMAIL)
                ?: userRepository.getUserByEmail(com.example.config.DeveloperAccountConfig.OWNER_PROJECT_EMAIL)
            if (masterDev == null || masterDev.role != UserRole.MASTER_DEV || masterDev.passwordHash.isNotEmpty()) {
                _authError.value = "Pengaturan awal kata sandi tidak diizinkan."
                return@launch
            }

            val secureHash = PasswordHasher.hashPassword(trimmedNew)
            val updated = masterDev.copy(passwordHash = secureHash)
            userRepository.updateUser(updated)

            SessionManager.login(updated)
            _isInitialMasterSetupOpen.value = false
            _isAuthModalOpen.value = false
            _isSuccessToast.value = "Kata sandi Master Developer berhasil dibuat! 👑"
        }
    }

    fun changePassword(currentPass: String, newPass: String, confirmPass: String) {
        viewModelScope.launch {
            _changePasswordError.value = null
            val user = currentUser.value
            if (user == null || user.isGuest) {
                _changePasswordError.value = "Pengguna tamu tidak dapat mengubah kata sandi."
                return@launch
            }
            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                _changePasswordError.value = "Semua bidang kata sandi wajib diisi."
                return@launch
            }
            if (!PasswordHasher.verifyPassword(currentPass, user.passwordHash)) {
                _changePasswordError.value = "Kata sandi saat ini salah."
                return@launch
            }
            if (newPass.length < 6) {
                _changePasswordError.value = "Kata sandi baru minimal 6 karakter."
                return@launch
            }
            if (newPass != confirmPass) {
                _changePasswordError.value = "Konfirmasi kata sandi baru tidak cocok."
                return@launch
            }
            if (newPass == currentPass) {
                _changePasswordError.value = "Kata sandi baru tidak boleh sama dengan kata sandi saat ini."
                return@launch
            }

            val newHash = PasswordHasher.hashPassword(newPass)
            val updatedUser = user.copy(passwordHash = newHash)
            userRepository.updateUser(updatedUser)

            SessionManager.login(updatedUser)
            _isChangePasswordOpen.value = false
            _isSuccessToast.value = "Kata sandi berhasil diperbarui! 🔒"
        }
    }

    fun login(identifier: String, password: String, isDebug: Boolean = BuildConfig.DEBUG) {
        viewModelScope.launch {
            _authError.value = null
            val trimmedId = identifier.trim()
            val trimmedPass = password.trim()

            if (trimmedId.isEmpty()) {
                _authError.value = "Nama pengguna atau email tidak boleh kosong."
                return@launch
            }

            // Normal Database Authentication Flow
            var user = userRepository.getUserByIdentifier(trimmedId)
            if (user == null && com.example.config.DeveloperAccountConfig.isMasterDevIdentifier(trimmedId)) {
                if (!isDebug) {
                    _authError.value = "Akses developer pada rilis publik memerlukan autentikasi resmi Google Sign-In."
                    return@launch
                }
            }
            if (user == null && com.example.config.ReviewerAccountConfig.isReviewerIdentifier(trimmedId)) {
                FinSmartDatabase.ensureReviewerAccount(database)
                user = userRepository.getUserByIdentifier(trimmedId)
            }
            if (user != null) {
                // Ensure trusted developer identities always possess verified MASTER_DEV role in DEBUG builds only
                if (isDebug && com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(user.email) && user.role != UserRole.MASTER_DEV) {
                    userRepository.updateUserRole(user.id, UserRole.MASTER_DEV, null)
                    user = user.copy(role = UserRole.MASTER_DEV, subscriptionExpiresAt = null)
                }

                // Fresh install Master Dev initial password setup check
                if (user.role == UserRole.MASTER_DEV && user.passwordHash.isEmpty()) {
                    if (isDebug) {
                        _authError.value = "Akun Master Developer belum memiliki kata sandi. Silakan buat kata sandi utama."
                        _isInitialMasterSetupOpen.value = true
                        return@launch
                    } else {
                        // Pada build Release, akun tanpa password tidak boleh diklaim secara lokal
                        _authError.value = "Akses developer pada rilis publik memerlukan autentikasi resmi Google Sign-In."
                        return@launch
                    }
                }

                // Pada build Release, login akun Master Dev dengan kredensial lokal ditolak;
                // Wajib menggunakan autentikasi resmi Google Sign-In.
                if (!isDebug && user.role == UserRole.MASTER_DEV) {
                    _authError.value = "Akses developer pada rilis publik memerlukan autentikasi resmi Google Sign-In."
                    return@launch
                }

                // Strict check: reject blank passwords
                if (trimmedPass.isEmpty()) {
                    _authError.value = "Kata sandi tidak boleh kosong."
                    return@launch
                }

                if (PasswordHasher.verifyPassword(trimmedPass, user.passwordHash) || (user.isGuest && user.role != UserRole.MASTER_DEV)) {
                    SessionManager.login(user)
                    _isAuthModalOpen.value = false
                    if (user.role == UserRole.MASTER_DEV) {
                        _isSuccessToast.value = "Selamat Datang Master Developer ${com.example.config.AppConfig.APP_AUTHOR}! 👑"
                    } else {
                        _isSuccessToast.value = "Berhasil masuk sebagai ${user.username}"
                    }
                } else {
                    _authError.value = "Kata sandi salah. Silakan coba lagi."
                }
            } else {
                _authError.value = "Akun tidak ditemukan. Silakan lakukan registrasi."
            }
        }
    }

    fun generateOtpForRegistration(email: String) {
        val otp = String.format("%06d", Random().nextInt(999999))
        _pendingOtpCode.value = otp
    }

    fun registerWithOtp(username: String, email: String, password: String, enteredOtp: String) {
        viewModelScope.launch {
            _authError.value = null
            if (enteredOtp != _pendingOtpCode.value) {
                _authError.value = "Kode OTP tidak valid atau salah."
                return@launch
            }

            val trimmedEmail = email.trim()
            val trimmedUsername = username.trim()
            if (com.example.config.ReviewerAccountConfig.isReviewerIdentifier(trimmedEmail) ||
                com.example.config.ReviewerAccountConfig.isReviewerIdentifier(trimmedUsername) ||
                com.example.config.DeveloperAccountConfig.isMasterDevIdentifier(trimmedEmail) ||
                com.example.config.DeveloperAccountConfig.isMasterDevIdentifier(trimmedUsername)
            ) {
                _authError.value = "Identitas akun ini telah dicadangkan untuk peninjauan atau administrasi sistem."
                return@launch
            }

            val existingEmail = userRepository.getUserByIdentifier(trimmedEmail)
            if (existingEmail != null) {
                _authError.value = "Email sudah terdaftar. Silakan login."
                return@launch
            }

            if (trimmedUsername.isNotBlank()) {
                val existingUsername = userRepository.getUserByIdentifier(trimmedUsername)
                if (existingUsername != null) {
                    _authError.value = "Nama pengguna '$trimmedUsername' sudah terdaftar. Silakan gunakan nama pengguna lain."
                    return@launch
                }
            }

            val previousGuestUserId = currentUser.value?.takeIf { it.isGuest }?.id

            val newUser = User(
                username = username.trim().ifBlank { "User ${email.substringBefore("@")}" },
                email = email.trim(),
                passwordHash = PasswordHasher.hashPassword(password.trim()),
                role = UserRole.FREE_MEMBER,
                isGuest = false,
                trialStartDate = 0L
            )
            val newId = userRepository.insertUser(newUser)
            val created = newUser.copy(id = newId)

            // P0: Atomically migrate existing guest transactions, wallets, and financial records to new registered account
            if (previousGuestUserId != null && previousGuestUserId > 0L && previousGuestUserId != newId) {
                try {
                    com.example.data.repository.FinanceRepository(database.walletDao(), database)
                        .reassignUserData(previousGuestUserId, newId)
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Failed to reassign guest data to registered user: ${e.message}")
                }
            } else {
                try {
                    com.example.data.repository.FinanceRepository(database.walletDao(), database)
                        .provisionDefaultWalletsIfEmpty(newId)
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Failed to provision default wallets: ${e.message}")
                }
            }

            SessionManager.login(created)
            _pendingOtpCode.value = null
            _isAuthModalOpen.value = false
            _isSuccessToast.value = AppStrings.t("Akun berhasil dibuat! Selamat datang di DompetNalar.", "Account created successfully! Welcome to DompetNalar.")
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Real Google Sign-In using Android Credential Manager and Google Identity Services.
     * Uses Web Application Client ID as serverClientId to obtain Google ID Token.
     */
    fun signInWithGoogle(
        activityContext: Context,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        openModalOnError: Boolean = (onError == null)
    ) {
        val sessionEpoch = authSessionEpoch.get()
        viewModelScope.launch {
            _isGoogleSignInLoading.value = true
            _authError.value = null
            try {
                // Safely resolve Activity from context hierarchy to satisfy Credential Manager UI requirements
                val targetActivity = activityContext.findActivity() ?: activityContext
                val credentialManager = CredentialManager.create(targetActivity)

                // 1. Explicit Sign-In with Google Option (Branded button flow)
                // CATATAN KEAMANAN & ARSITEKTUR: GetSignInWithGoogleOption TIDAK BOLEH digabungkan
                // dengan opsi lain dalam satu GetCredentialRequest karena melanggar kontrak Credential Manager API.
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                    serverClientId = AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID
                ).build()

                val primaryRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val result = try {
                    credentialManager.getCredential(
                        context = targetActivity,
                        request = primaryRequest
                    )
                } catch (e: GetCredentialCancellationException) {
                    throw e // Tangani pembatalan pengguna langsung tanpa fallback
                } catch (e: NoCredentialException) {
                    throw e // Tangani ketiadaan akun langsung tanpa fallback
                } catch (e: Exception) {
                    // Jika perangkat atau Play Services belum mendukung GetSignInWithGoogleOption,
                    // coba fallback berurutan terpisah dengan GetGoogleIdOption (HANYA jika bukan pembatalan)
                    Log.w("AuthViewModel", "Opsi utama Google Sign-In tidak dapat diproses (${e.message}), mencoba fallback GetGoogleIdOption...")
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(AppConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID)
                        .setAutoSelectEnabled(false)
                        .build()

                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    credentialManager.getCredential(
                        context = targetActivity,
                        request = fallbackRequest
                    )
                }

                val credential = result.credential
                val googleIdTokenCredential = try {
                    GoogleIdTokenCredential.createFrom(credential.data)
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Gagal memproses ID Token Credential: ${e.message}", e)
                    null
                }

                if (googleIdTokenCredential == null) {
                    val err = "Gagal memproses kredensial ID Token dari respon akun Google."
                    _authError.value = err
                    if (openModalOnError) _isAuthModalOpen.value = true
                    onError?.invoke(err)
                    return@launch
                }

                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: "Pengguna Google"
                val photoUri = googleIdTokenCredential.profilePictureUri?.toString()

                if (email.isBlank()) {
                    val err = "Identitas akun Google tidak valid (alamat email kosong)."
                    _authError.value = err
                    if (openModalOnError) _isAuthModalOpen.value = true
                    onError?.invoke(err)
                    return@launch
                }

                val isTrustedDev = BuildConfig.DEBUG && com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(email)

                // Sinkronisasi akun dengan basis data lokal Room FinSmart (strict email matching)
                var user = userRepository.getUserByEmail(email)
                if (user == null) {
                    val newUser = User(
                        username = displayName,
                        email = email,
                        role = if (isTrustedDev) UserRole.MASTER_DEV else UserRole.FREE_MEMBER,
                        isGuest = false,
                        trialStartDate = 0L,
                        subscriptionExpiresAt = null
                    )
                    val id = userRepository.insertUser(newUser)
                    user = newUser.copy(id = id)
                } else {
                    if (isTrustedDev && (user.role != UserRole.MASTER_DEV || user.subscriptionExpiresAt != null)) {
                        val upgradedDev = user.copy(
                            role = UserRole.MASTER_DEV,
                            subscriptionExpiresAt = null,
                            trialStartDate = 0L,
                            isGuest = false
                        )
                        userRepository.updateUser(upgradedDev)
                        user = upgradedDev
                    } else if (user.trialStartDate > 0L) {
                        val cleanedUser = user.copy(
                            trialStartDate = 0L,
                            isGuest = false
                        )
                        userRepository.updateUser(cleanedUser)
                        user = cleanedUser
                    }
                }

                if (sessionEpoch != authSessionEpoch.get()) {
                    Log.i("AuthViewModel", "Google Sign-In response discarded because session epoch changed (user logged out).")
                    return@launch
                }

                if (!photoUri.isNullOrBlank()) {
                    updateProfilePhotoUri(photoUri)
                }

                val previousGuestUserId = currentUser.value?.takeIf { it.isGuest }?.id

                // P0: Atomically migrate existing guest transactions, wallets, and financial records to Google account
                if (previousGuestUserId != null && previousGuestUserId > 0L && previousGuestUserId != user.id) {
                    try {
                        com.example.data.repository.FinanceRepository(database.walletDao(), database)
                            .reassignUserData(previousGuestUserId, user.id)
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Failed to reassign guest data to Google user: ${e.message}")
                    }
                } else {
                    // Provision default wallets for newly registered Google user if empty (P0)
                    try {
                        com.example.data.repository.FinanceRepository(database.walletDao(), database).provisionDefaultWalletsIfEmpty(user.id)
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Failed to provision default wallets: ${e.message}")
                    }
                }

                // 2. Pertukaran Kredensial Resmi ke Firebase Authentication (request.auth alignment)
                val idToken = googleIdTokenCredential.idToken
                val isFirebaseReady = try {
                    FirebaseApp.getApps(getApplication<Application>()).isNotEmpty()
                } catch (e: Exception) {
                    false
                }

                var isFirebaseAuthSuccessful = false
                var firebaseAuthErrorMessage: String? = null
                if (isFirebaseReady && !idToken.isNullOrBlank()) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                        withContext(Dispatchers.IO) {
                            val authResult = suspendCancellableCoroutine { cont ->
                                FirebaseAuth.getInstance().signInWithCredential(authCredential)
                                    .addOnSuccessListener { cont.resume(it) }
                                    .addOnFailureListener { cont.resumeWithException(it) }
                            }
                            val fbUser = authResult.user
                            isFirebaseAuthSuccessful = fbUser != null
                            Log.i("AuthViewModel", "Firebase Auth exchange successful: uid=${fbUser?.uid}, email=${fbUser?.email}")
                        }
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Firebase Auth credential exchange failed: ${e.message}", e)
                        firebaseAuthErrorMessage = e.localizedMessage ?: e.message ?: "Pertukaran kredensial Firebase Auth gagal"
                    }
                }

                SessionManager.login(user)
                _isAuthModalOpen.value = false

                // Sinkronisasi otomatis User Registry & Remote Tester Grant (Phase 4B)
                val activeFbUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                if (activeFbUser != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        com.example.util.UserRegistryManager.syncUserRegistry(getApplication(), activeFbUser)
                    }
                }
                syncTesterGrant(email, force = true)

                if (isFirebaseReady && !idToken.isNullOrBlank() && !isFirebaseAuthSuccessful) {
                    val safeErrorMsg = "Sesi lokal aktif, namun autentikasi Firebase Cloud gagal: ${firebaseAuthErrorMessage ?: "Layanan Firebase tidak dapat memvalidasi token"}"
                    _authError.value = safeErrorMsg
                    _isSuccessToast.value = "Masuk lokal berhasil (Cloud belum terhubung)"
                    onError?.invoke(safeErrorMsg)
                } else {
                    _isSuccessToast.value = if (isTrustedDev) {
                        if (isFirebaseAuthSuccessful) {
                            "Selamat Datang Master Developer 👑 ($email - Cloud Aktif)"
                        } else {
                            "Selamat Datang Master Developer 👑 ($email)"
                        }
                    } else {
                        "Berhasil masuk sebagai $displayName ($email)"
                    }
                    onSuccess?.invoke()
                }
            } catch (e: GetCredentialCancellationException) {
                // Pengguna membatalkan dialog akun; tangani dengan ramah tanpa error palsu
                Log.i("AuthViewModel", "Google Sign-In dibatalkan oleh pengguna.")
                _isAuthModalOpen.value = true
                onCancel?.invoke()
            } catch (e: NoCredentialException) {
                val msg = "Tidak ada akun Google yang dipilih atau aktif di perangkat ini. Silakan pastikan akun Google sudah login di pengaturan HP Anda."
                _authError.value = msg
                if (openModalOnError) _isAuthModalOpen.value = true
                onError?.invoke(msg)
            } catch (e: GetCredentialException) {
                Log.e("AuthViewModel", "GetCredentialException [${e.type}]: ${e.message}", e)
                val rawMsg = e.message ?: ""
                val is16OrMismatch = rawMsg.contains("16") ||
                        rawMsg.contains("matching credential", ignoreCase = true) ||
                        rawMsg.contains("Caller not authorized", ignoreCase = true) ||
                        rawMsg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                        rawMsg.contains("10")
                val isNetwork = rawMsg.contains("network", ignoreCase = true) ||
                        rawMsg.contains("timeout", ignoreCase = true) ||
                        rawMsg.contains("connect", ignoreCase = true) ||
                        rawMsg.contains("offline", ignoreCase = true) ||
                        rawMsg.contains("unreachable", ignoreCase = true)
                val isConfigConflict = rawMsg.contains("Cannot be combined", ignoreCase = true) ||
                        rawMsg.contains("GetSignInWithGoogleOption", ignoreCase = true)

                val msg = when {
                    isConfigConflict -> {
                        "Konfigurasi opsi Google Sign-In tidak valid. Silakan hubungi pengembang aplikasi.\n" +
                        "[Detail Teknis: ${rawMsg.ifBlank { e.type }}]"
                    }
                    is16OrMismatch -> {
                        "Google Sign-In belum terhubung di Google Cloud Console.\n" +
                        "Penyebab utama: Sidik jari sertifikat APK (SHA-1) atau Nama Paket (com.aistudio.finsmart.kptxvw) belum didaftarkan pada Android OAuth Client di Google Cloud Console, atau akun penguji belum ditambahkan ke Test Users.\n" +
                        "[Detail Teknis: ${rawMsg.ifBlank { e.type }}]"
                    }
                    isNetwork -> {
                        "Koneksi jaringan terputus saat menghubungi server Google. Periksa koneksi internet perangkat Anda.\n" +
                        "[Detail Teknis: ${rawMsg.ifBlank { e.type }}]"
                    }
                    else -> {
                        "Gagal autentikasi Google: ${rawMsg.ifBlank { "Layanan Google Play Services menolak otorisasi." }}\n" +
                        "[Detail Teknis: ${e.type}]"
                    }
                }
                _authError.value = msg
                if (openModalOnError) _isAuthModalOpen.value = true
                onError?.invoke(msg)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Kesalahan Google Sign-In tidak terduga", e)
                val rawMsg = e.message ?: ""
                val msg = when {
                    rawMsg.contains("Cannot be combined", ignoreCase = true) -> {
                        "Konfigurasi opsi Google Sign-In tidak valid. Silakan hubungi pengembang aplikasi."
                    }
                    rawMsg.contains("network", ignoreCase = true) ||
                    rawMsg.contains("timeout", ignoreCase = true) ||
                    rawMsg.contains("connect", ignoreCase = true) ||
                    rawMsg.contains("offline", ignoreCase = true) -> {
                        "Koneksi jaringan terputus saat menghubungi server Google. Periksa koneksi internet perangkat Anda."
                    }
                    else -> {
                        "Gagal masuk dengan Google: ${rawMsg.ifBlank { "Otorisasi akun ditolak atau tidak tersedia" }}"
                    }
                }
                _authError.value = msg
                if (openModalOnError) _isAuthModalOpen.value = true
                onError?.invoke(msg)
            } finally {
                _isGoogleSignInLoading.value = false
            }
        }
    }

    /**
     * Fallback mock login for offline sandbox testing environments only.
     */
    fun loginWithGoogleMock() {
        viewModelScope.launch {
            val googleEmail = "google.user@gmail.com"
            var user = userRepository.getUserByIdentifier(googleEmail)
            if (user == null) {
                val newUser = User(
                    username = "Google User (Mock)",
                    email = googleEmail,
                    role = UserRole.FREE_MEMBER,
                    isGuest = false,
                    trialStartDate = 0L
                )
                val id = userRepository.insertUser(newUser)
                user = newUser.copy(id = id)
            } else if (user.trialStartDate != 0L) {
                val activatedUser = user.copy(
                    trialStartDate = 0L,
                    isGuest = false
                )
                userRepository.updateUser(activatedUser)
                user = activatedUser
            }
            val previousGuestUserId = currentUser.value?.takeIf { it.isGuest }?.id
            if (previousGuestUserId != null && previousGuestUserId > 0L && previousGuestUserId != user.id) {
                try {
                    com.example.data.repository.FinanceRepository(database.walletDao(), database)
                        .reassignUserData(previousGuestUserId, user.id)
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Failed to reassign guest data to mock Google user: ${e.message}")
                }
            } else {
                try {
                    com.example.data.repository.FinanceRepository(database.walletDao(), database).provisionDefaultWalletsIfEmpty(user.id)
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Failed to provision default wallets: ${e.message}")
                }
            }
            SessionManager.login(user)
            _isAuthModalOpen.value = false
            _isSuccessToast.value = "Berhasil masuk via Akun Google (Mock)"
        }
    }

    fun loginAsGuest() {
        authSessionEpoch.incrementAndGet()
        updateProfilePhotoUri(null)
        try {
            coil.Coil.imageLoader(getApplication<Application>()).memoryCache?.clear()
        } catch (_: Exception) {}
        viewModelScope.launch {
            SessionManager.startGuestSession(database.userDao())
            _singleFeaturePassCredits.value = 0
            _isAuthModalOpen.value = false
            _isSuccessToast.value = "Mode Tamu aktif"
        }
    }

    fun logout(context: Context? = null, onComplete: (() -> Unit)? = null) {
        authSessionEpoch.incrementAndGet()
        updateProfilePhotoUri(null)
        try {
            val targetContext = context ?: getApplication<Application>()
            coil.Coil.imageLoader(targetContext).memoryCache?.clear()
        } catch (e: Exception) {
            Log.w("AuthViewModel", "Coil memoryCache clear note: ${e.message}")
        }
        viewModelScope.launch {
            try {
                if (FirebaseApp.getApps(getApplication<Application>()).isNotEmpty()) {
                    FirebaseAuth.getInstance().signOut()
                }
            } catch (e: Exception) {
                Log.w("AuthViewModel", "Firebase signOut note: ${e.message}")
            }

            // Clear Android Credential Manager state
            try {
                val targetContext = context ?: getApplication<Application>()
                val targetActivity = targetContext.findActivity() ?: targetContext
                val credentialManager = CredentialManager.create(targetActivity)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                Log.i("AuthViewModel", "CredentialManager clearCredentialState succeeded.")
            } catch (e: Exception) {
                Log.w("AuthViewModel", "CredentialManager clearCredentialState note: ${e.message}")
            }

            // Disconnect and reset Google Drive session & cancel auto sync worker
            try {
                val targetContext = context ?: getApplication<Application>()
                GoogleDriveService.disconnect(targetContext)
                GoogleDriveService.cancelAutoSync(targetContext)
            } catch (e: Exception) {
                Log.w("AuthViewModel", "GoogleDriveService disconnect on logout: ${e.message}")
            }
            _isDriveConnected.value = false
            _driveEmail.value = ""
            _driveAutoSync.value = false

            _singleFeaturePassCredits.value = 0
            prefs.edit().remove("single_feature_credits").apply()
            adRewardCountdownJob?.cancel()
            adRewardCountdownJob = null
            _adRewardRemainingSeconds.value = 0L
            SessionManager.logout()
            _isAuthModalOpen.value = true
            _isSuccessToast.value = "Anda berhasil keluar dari akun."
            onComplete?.invoke()
        }
    }

    private var lastRewardedTimestamp = 0L

    fun startAdRewardCountdown(expiryMillis: Long) {
        adRewardCountdownJob?.cancel()
        val now = System.currentTimeMillis()
        val initialRemaining = ((expiryMillis - now) / 1000L).coerceAtLeast(0L)
        _adRewardRemainingSeconds.value = initialRemaining
        if (initialRemaining <= 0L) {
            checkAndSanitizeEntitlement(now)
            return
        }
        adRewardCountdownJob = viewModelScope.launch {
            while (true) {
                val currentNow = System.currentTimeMillis()
                val remaining = (expiryMillis - currentNow) / 1000L
                if (remaining <= 0L) {
                    _adRewardRemainingSeconds.value = 0L
                    checkAndSanitizeEntitlement(currentNow)
                    break
                } else {
                    _adRewardRemainingSeconds.value = remaining
                }
                delay(1000L)
            }
        }
    }

    fun downgradeExpiredSubscription(user: User) {
        val downgraded = user.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null)
        SessionManager.login(downgraded)
        viewModelScope.launch {
            if (user.id > 0L) {
                userRepository.updateUserRole(user.id, UserRole.FREE_MEMBER, null)
            }
        }
        val rewardKey = if (user.isGuest) "guest" else "user_${user.id}"
        prefs.edit()
            .remove("${rewardKey}_ad_reward_granted_at")
            .remove("${rewardKey}_ad_reward_expires_at")
            .remove("guest_ad_reward_granted_at")
            .remove("guest_ad_reward_expires_at")
            .apply()
        _adRewardRemainingSeconds.value = 0L
        adRewardCountdownJob?.cancel()
        adRewardCountdownJob = null
    }

    fun checkAndSanitizeEntitlement(now: Long = System.currentTimeMillis()) {
        val user = currentUser.value ?: return

        // KEAMANAN RELEASE DEVELOPER (Phase 4B):
        if (!BuildConfig.DEBUG && user.role == UserRole.MASTER_DEV) {
            Log.w("AuthViewModel", "Akun lokal dengan role MASTER_DEV terdeteksi di build Release. Mendowngrade ke FREE_MEMBER.")
            downgradeExpiredSubscription(user)
            return
        }

        // Audit & enforce 60-minute limit on Rewarded Ads for BOTH Guest and Authenticated users (BUG-05)
        val rewardKey = if (user.isGuest) "guest" else "user_${user.id}"
        val grantedAt = prefs.getLong("${rewardKey}_ad_reward_granted_at", if (user.isGuest) prefs.getLong("guest_ad_reward_granted_at", 0L) else 0L)
        val savedExpiry = prefs.getLong("${rewardKey}_ad_reward_expires_at", if (user.isGuest) prefs.getLong("guest_ad_reward_expires_at", 0L) else (user.subscriptionExpiresAt ?: 0L))

        if (grantedAt > 0L || (user.role == UserRole.SMART_MASTER && user.subscriptionExpiresAt != null && user.subscriptionExpiresAt > now && (user.subscriptionExpiresAt - now) <= 7200000L)) {
            val maxAllowedExpiry = if (grantedAt > 0L) {
                val activeMgrExpiry = com.example.util.RewardedAdRewardManager.getActiveRewardExpiry(getApplication(), user, now)
                if (activeMgrExpiry > grantedAt + 3600000L) activeMgrExpiry else (grantedAt + 3600000L)
            } else (user.subscriptionExpiresAt ?: (now + 3600000L))
            val clampedExpiry = if (savedExpiry > maxAllowedExpiry || savedExpiry <= 0L) maxAllowedExpiry else savedExpiry

            if (now >= clampedExpiry) {
                // Expired! Clear prefs and downgrade
                prefs.edit()
                    .remove("${rewardKey}_ad_reward_granted_at")
                    .remove("${rewardKey}_ad_reward_expires_at")
                    .remove("guest_ad_reward_granted_at")
                    .remove("guest_ad_reward_expires_at")
                    .apply()
                _adRewardRemainingSeconds.value = 0L
                adRewardCountdownJob?.cancel()
                adRewardCountdownJob = null
                if (user.role == UserRole.SMART_MASTER) {
                    downgradeExpiredSubscription(user)
                }
            } else {
                // Active reward: update ticker and synchronize user state
                val remainingSec = (clampedExpiry - now) / 1000L
                _adRewardRemainingSeconds.value = remainingSec
                if (user.role != UserRole.SMART_MASTER || user.subscriptionExpiresAt != clampedExpiry) {
                    val updated = user.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = clampedExpiry)
                    SessionManager.login(updated)
                    if (user.id > 0L) {
                        viewModelScope.launch { userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, clampedExpiry) }
                    }
                }
                if (adRewardCountdownJob == null || adRewardCountdownJob?.isActive != true) {
                    startAdRewardCountdown(clampedExpiry)
                }
            }
        } else if (user.role == UserRole.SMART_MASTER && user.subscriptionExpiresAt != null && user.subscriptionExpiresAt <= now) {
            downgradeExpiredSubscription(user)
        }
    }

    fun grantTemporaryRewardAccess(durationHours: Int = 1, expectedUserId: Long? = null) {
        val now = System.currentTimeMillis()
        if (now - lastRewardedTimestamp < 5000L) {
            Log.w("AuthViewModel", "Duplicate reward callback ignored (debounce 5s)")
            return
        }
        lastRewardedTimestamp = now

        val user = currentUser.value ?: return

        // 1. Account switching defense: ensure the callback belongs to the intended active user
        if (expectedUserId != null && user.id != expectedUserId) {
            Log.w("AuthViewModel", "Account mismatch during reward grant! Expected $expectedUserId, active is ${user.id}. Aborted.")
            return
        }

        // 2. Paid / Permanent SmartMaster protection: active paid/granted subscribers can never receive temporary rewards
        if (EntitlementManager.isPaidOrPermanentSmartMaster(getApplication(), user, now)) {
            Log.w("AuthViewModel", "grantTemporaryRewardAccess rejected: user ${user.id} has active paid or permanent SmartMaster.")
            _isPaywallOpen.value = false
            return
        }

        val claimResult = com.example.util.RewardedAdRewardManager.processEarnedReward(
            getApplication<Application>(),
            user,
            now
        )

        if (!claimResult.isGranted) {
            _isPaywallOpen.value = false
            _isSuccessToast.value = claimResult.message
            return
        }

        val rewardExpiry = claimResult.newExpiryMillis
        val updatedUser = user.copy(
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = rewardExpiry
        )
        SessionManager.login(updatedUser)

        val rewardKey = com.example.util.RewardedAdRewardManager.getUserStorageKey(user)
        prefs.edit()
            .putLong("${rewardKey}_ad_reward_granted_at", now)
            .putLong("${rewardKey}_ad_reward_expires_at", rewardExpiry)
            .apply()

        // Start countdown ticker for BOTH guest and authenticated users!
        startAdRewardCountdown(rewardExpiry)

        viewModelScope.launch {
            if (user.id > 0L) {
                userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, rewardExpiry)
            }
        }
        _isPaywallOpen.value = false
        _isSuccessToast.value = claimResult.message
    }

    /**
     * Catat aktivitas harian valid untuk perhitungan streak yang akurat dan konsisten (Bug 7).
     * Hari pertama aktivitas menghasilkan streak 1.
     * Aktivitas berulang pada hari kalender yang sama tidak menambah streak.
     * Aktivitas pada hari esoknya menambah streak (+1).
     * Hari terlewat me-reset streak kembali ke 1.
     */
    fun recordDailyActivity(now: Long = System.currentTimeMillis()): Int {
        val lastDateStr = prefs.getString("gamify_last_activity_date", null)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayStr = sdf.format(java.util.Date(now))

        if (lastDateStr == todayStr) {
            return _gamificationState.value.dailyStreak
        }

        val currentStreak = _gamificationState.value.dailyStreak
        val newStreak = if (lastDateStr == null) {
            1
        } else {
            val lastDate = try {
                sdf.parse(lastDateStr)
            } catch (_: Exception) {
                null
            }
            if (lastDate != null) {
                val cal = java.util.Calendar.getInstance()
                cal.time = lastDate
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                val expectedNextDayStr = sdf.format(cal.time)
                if (expectedNextDayStr == todayStr) {
                    (currentStreak + 1).coerceAtLeast(2)
                } else {
                    1
                }
            } else {
                1
            }
        }

        val currentBest = _gamificationState.value.bestStreak
        val newBest = maxOf(currentBest, newStreak)

        val currentBadges = _gamificationState.value.unlockedBadgeIds.toMutableSet()
        if (newStreak >= 7) currentBadges.add("rookie_planner")
        if (newStreak >= 30) currentBadges.add("budget_master")

        _gamificationState.value = _gamificationState.value.copy(
            dailyStreak = newStreak,
            bestStreak = newBest,
            unlockedBadgeIds = currentBadges
        )

        prefs.edit()
            .putString("gamify_last_activity_date", todayStr)
            .putInt("gamify_streak", newStreak)
            .putInt("gamify_best_streak", newBest)
            .putStringSet("gamify_badges", currentBadges)
            .putBoolean("gamify_streak_initialized", true)
            .apply()

        return newStreak
    }

    private var isClaimingStreakReward = false

    /**
     * Recheck streak milestone eligibility strictly against persisted timestamp and user ID.
     * Prevents duplicate claims within rolling 168 hours (7 days) and validates active streak >= 7.
     */
    fun canClaimStreakReward(user: User? = currentUser.value): Boolean {
        if (user == null) return false
        val streak = _gamificationState.value.dailyStreak
        if (streak < 7) return false
        val key = if (user.isGuest || user.id <= 0L) "streak_reward_last_claimed_guest" else "streak_reward_last_claimed_${user.id}"
        val lastClaimed = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        if (lastClaimed > now) return false // Anti-tamper future clock safeguard
        return (now - lastClaimed) >= ROLLING_STREAK_REWARD_PERIOD_MILLIS
    }

    fun getStreakRewardRemainingHours(user: User? = currentUser.value): Long {
        if (user == null) return 0L
        val key = if (user.isGuest || user.id <= 0L) "streak_reward_last_claimed_guest" else "streak_reward_last_claimed_${user.id}"
        val lastClaimed = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        val elapsed = now - lastClaimed
        return if (elapsed < ROLLING_STREAK_REWARD_PERIOD_MILLIS) {
            val remainingMillis = ROLLING_STREAK_REWARD_PERIOD_MILLIS - elapsed
            (remainingMillis / 3600000L).coerceAtLeast(1L)
        } else {
            0L
        }
    }

    /**
     * Streak 7 hari memberikan Smart Member Pass 5 jam + 3x Action Pass Credits
     */
    fun grantStreakMilestoneReward(durationHours: Int = 5, actionPassCredits: Int = 3) {
        val user = currentUser.value
        if (user == null) {
            _authError.value = "Sesi pengguna tidak aktif."
            return
        }

        // Strict 168-hour rolling eligibility check
        if (!canClaimStreakReward(user)) {
            val remainingHours = getStreakRewardRemainingHours(user)
            val streak = _gamificationState.value.dailyStreak
            val msg = if (streak < 7) {
                "Streak harian Anda belum mencapai 7 hari (saat ini: $streak hari)."
            } else {
                "Reward streak 7 hari sudah diklaim. Tersedia kembali dalam ~$remainingHours jam."
            }
            _authError.value = msg
            return
        }

        // Re-entrancy / double-tap prevention
        if (isClaimingStreakReward) return
        isClaimingStreakReward = true

        try {
            val now = System.currentTimeMillis()
            val key = if (user.isGuest || user.id <= 0L) "streak_reward_last_claimed_guest" else "streak_reward_last_claimed_${user.id}"
            prefs.edit().putLong(key, now).apply()

            if (user.role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(user.email)) {
                addSingleFeaturePassCredit(actionPassCredits)
                _isSuccessToast.value = "Hak Master Dev dipertahankan! Ditambahkan $actionPassCredits Action Pass Credits 👑"
                return
            }

            val rewardDurationMillis = durationHours * 3600000L
            val currentExpiry = user.subscriptionExpiresAt ?: 0L
            val rewardExpiry = if (currentExpiry > now + rewardDurationMillis) currentExpiry else (now + rewardDurationMillis)
            val updatedUser = user.copy(
                role = UserRole.SMART_MASTER,
                subscriptionExpiresAt = rewardExpiry
            )
            SessionManager.login(updatedUser)

            addSingleFeaturePassCredit(actionPassCredits)

            viewModelScope.launch {
                if (!user.isGuest && user.id > 0) {
                    userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, rewardExpiry)
                }
            }
            _isSuccessToast.value = "🎉 Reward Streak 7 Hari Aktif: $durationHours Jam Smart Master Pass + ${actionPassCredits}x Action Pass Credits!"
        } finally {
            isClaimingStreakReward = false
        }
    }

    /**
     * 30-Day Streak Milestone Check:
     * Requires non-guest user, daily streak >= 30, and rolling 30-day (720h) cooldown.
     */
    fun canClaim30DayStreakReward(user: User? = currentUser.value): Boolean {
        if (user == null || user.isGuest) return false
        val streak = _gamificationState.value.dailyStreak
        if (streak < 30) return false
        val key = "streak_30d_last_claimed_${user.id}"
        val lastClaimed = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        if (lastClaimed > now) return false
        val periodMillis = 30L * 24 * 3600 * 1000L
        return (now - lastClaimed) >= periodMillis
    }

    fun get30DayStreakRewardRemainingHours(user: User? = currentUser.value): Long {
        if (user == null || user.isGuest) return 0L
        val key = "streak_30d_last_claimed_${user.id}"
        val lastClaimed = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        val elapsed = now - lastClaimed
        val periodMillis = 30L * 24 * 3600 * 1000L
        return if (elapsed < periodMillis) {
            val remainingMillis = periodMillis - elapsed
            (remainingMillis / 3600000L).coerceAtLeast(1L)
        } else {
            0L
        }
    }

    /**
     * Streak 30 hari memberikan Akses Smart Member Gratis selama 24 jam (Bug 8)
     */
    fun grant30DayStreakMilestoneReward(durationHours: Int = 24) {
        val user = currentUser.value
        if (user == null || user.isGuest) {
            _authError.value = "Mode Tamu tidak berhak mengklaim reward Smart Member. Silakan masuk dengan Akun Google."
            return
        }

        if (!canClaim30DayStreakReward(user)) {
            val remainingHours = get30DayStreakRewardRemainingHours(user)
            val streak = _gamificationState.value.dailyStreak
            val msg = if (streak < 30) {
                "Streak harian Anda belum mencapai 30 hari (saat ini: $streak hari)."
            } else {
                "Reward streak 30 hari sudah diklaim. Tersedia kembali dalam ~$remainingHours jam."
            }
            _authError.value = msg
            return
        }

        if (isClaimingStreakReward) return
        isClaimingStreakReward = true

        try {
            val now = System.currentTimeMillis()
            val key = "streak_30d_last_claimed_${user.id}"
            prefs.edit().putLong(key, now).apply()

            if (user.role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(user.email)) {
                _isSuccessToast.value = "Hak Master Dev dipertahankan seumur hidup! 👑"
                return
            }

            val rewardDurationMillis = durationHours * 3600000L
            val currentExpiry = user.subscriptionExpiresAt ?: 0L
            val rewardExpiry = if (currentExpiry > now + rewardDurationMillis) currentExpiry else (now + rewardDurationMillis)
            val updatedUser = user.copy(
                role = UserRole.SMART_MASTER,
                subscriptionExpiresAt = rewardExpiry
            )
            SessionManager.login(updatedUser)

            viewModelScope.launch {
                if (!user.isGuest && user.id > 0) {
                    userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, rewardExpiry)
                }
            }
            _isSuccessToast.value = "🎉 Selamat! Reward Streak 30 Hari Aktif: Akses Smart Member Gratis Selama $durationHours Jam!"
        } finally {
            isClaimingStreakReward = false
        }
    }

    /**
     * Menerapkan pembelian resmi Google Play ke pengguna aktif.
     * Mengatur hak akses SMART_MASTER dan masa aktif subscription sesuai produk yang dibeli.
     * Bersifat idempoten dan membedakan pembelian baru eksplisit dari sinkronisasi latar belakang.
     */
    fun applyGooglePlayPurchase(
        productId: String,
        purchaseToken: String,
        orderId: String?,
        purchaseTime: Long = 0L,
        isAutoRenewing: Boolean = true,
        isExplicitPurchase: Boolean = true
    ) {
        viewModelScope.launch {
            val user = currentUser.value
            if (user == null || user.isGuest || user.id <= 0L) {
                _authError.value = "Pembelian Google Play memerlukan login akun (bukan Mode Tamu)."
                return@launch
            }

            val now = System.currentTimeMillis()
            val durationDays = when (productId) {
                com.example.util.BillingManager.PRODUCT_ID_MONTHLY -> 30L
                com.example.util.BillingManager.PRODUCT_ID_YEARLY -> 365L
                com.example.util.BillingManager.PRODUCT_ID_LIFETIME -> 3650L // 10 tahun / lifetime
                else -> 30L
            }
            val durationMillis = durationDays * 86400000L

            val calculatedExpiry = if (purchaseTime > 0L) {
                purchaseTime + durationMillis
            } else {
                now + durationMillis
            }

            // Validasi kedaluwarsa: Jika pembelian tidak auto-renewing dan calculatedExpiry <= now, akses berbayar ditolak
            if (!isAutoRenewing && purchaseTime > 0L && calculatedExpiry <= now) {
                Log.w("AuthViewModel", "Google Play purchase $orderId ($productId) telah kedaluwarsa pada $calculatedExpiry (purchaseTime: $purchaseTime). Akses ditolak.")
                if (user.role == UserRole.SMART_MASTER) {
                    userRepository.updateUserRole(user.id, UserRole.FREE_MEMBER, null)
                    SessionManager.login(user.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null))
                }
                _authError.value = "Status langganan Google Play untuk akun ini telah kedaluwarsa."
                return@launch
            }

            val effectiveExpiry = if (isAutoRenewing) {
                maxOf(now + durationMillis, calculatedExpiry)
            } else {
                calculatedExpiry
            }

            val lastProcessedToken = prefs.getString("billing_last_token_user_${user.id}", null)
            val isNewTransaction = lastProcessedToken != purchaseToken

            userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, effectiveExpiry)
            SessionManager.login(user.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = effectiveExpiry))

            prefs.edit()
                .putString("billing_purchase_source_user_${user.id}", "PLAY_STORE_BILLING")
                .putString("billing_last_token_user_${user.id}", purchaseToken)
                .putString("billing_last_product_user_${user.id}", productId)
                .putLong("billing_purchase_time_user_${user.id}", purchaseTime)
                .putBoolean("billing_auto_renewing_user_${user.id}", isAutoRenewing)
                .apply()

            _isPaywallOpen.value = false

            // Hanya tampilkan toast sukses untuk pembelian baru secara eksplisit, bukan pada startup/silent sync/duplicate callback
            if (isExplicitPurchase && isNewTransaction) {
                _isSuccessToast.value = "Pembelian Google Play Berhasil! Status Smart Master 👑 Aktif."
            }
            Log.i("AuthViewModel", "Google Play purchase applied for user ${user.id}, product $productId, order $orderId, isExplicit=$isExplicitPurchase, isNew=$isNewTransaction")
        }
    }

    /**
     * Menangani sinkronisasi status subscription aktif dari Google Play Billing.
     * Mengoreksi status entitlement saat langganan dibatalkan/direfund oleh Google Play,
     * sambil melindungi pengguna dari pemutusan prematur akibat kegagalan koneksi sesaat.
     */
    fun handleGooglePlaySubscriptionsSynced(
        activePurchases: List<com.android.billingclient.api.Purchase>,
        isConnected: Boolean
    ) {
        if (!isConnected) {
            Log.d("AuthViewModel", "Google Play Billing disconnected or query failed. Preserving current subscription status.")
            return
        }

        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            if (user.isGuest || user.id <= 0L) return@launch

            // Master Dev tidak pernah didowngrade oleh sinkronisasi subscription billing
            if (isDeveloperAuthorized()) return@launch

            val billingSource = prefs.getString("billing_purchase_source_user_${user.id}", null)
            val now = System.currentTimeMillis()

            // Jika status Smart Master berasal dari Google Play Billing
            if (user.role == UserRole.SMART_MASTER && billingSource == "PLAY_STORE_BILLING") {
                if (activePurchases.isEmpty()) {
                    // Google Play mengonfirmasi 0 langganan aktif: langganan dibatalkan & direfund, revoked, atau expired
                    Log.i("AuthViewModel", "Google Play mengonfirmasi 0 langganan aktif untuk user ${user.id}. Mendowngrade ke FREE_MEMBER.")
                    userRepository.updateUserRole(user.id, UserRole.FREE_MEMBER, null)
                    SessionManager.login(user.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null))
                    prefs.edit()
                        .remove("billing_purchase_source_user_${user.id}")
                        .remove("billing_last_token_user_${user.id}")
                        .remove("billing_last_product_user_${user.id}")
                        .remove("billing_purchase_time_user_${user.id}")
                        .remove("billing_auto_renewing_user_${user.id}")
                        .apply()
                } else {
                    // Ada pembelian aktif di Google Play. Periksa auto-renewal dan masa berlaku
                    val purchase = activePurchases.firstOrNull()
                    if (purchase != null) {
                        val productId = purchase.products.firstOrNull() ?: com.example.util.BillingManager.PRODUCT_ID_MONTHLY
                        val durationDays = when (productId) {
                            com.example.util.BillingManager.PRODUCT_ID_MONTHLY -> 30L
                            com.example.util.BillingManager.PRODUCT_ID_YEARLY -> 365L
                            else -> 30L
                        }
                        val durationMillis = durationDays * 86400000L
                        val purchaseTime = purchase.purchaseTime
                        val calculatedExpiry = if (purchaseTime > 0L) purchaseTime + durationMillis else now + durationMillis

                        if (!purchase.isAutoRenewing && purchaseTime > 0L && calculatedExpiry <= now) {
                            Log.w("AuthViewModel", "Langganan Google Play dibatalkan dan masa berlaku telah habis pada $calculatedExpiry untuk user ${user.id}. Mendowngrade.")
                            userRepository.updateUserRole(user.id, UserRole.FREE_MEMBER, null)
                            SessionManager.login(user.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null))
                            prefs.edit()
                                .remove("billing_purchase_source_user_${user.id}")
                                .remove("billing_last_token_user_${user.id}")
                                .apply()
                        } else {
                            val effectiveExpiry = if (purchase.isAutoRenewing) maxOf(now + durationMillis, calculatedExpiry) else calculatedExpiry
                            if (user.subscriptionExpiresAt != effectiveExpiry) {
                                userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, effectiveExpiry)
                                SessionManager.login(user.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = effectiveExpiry))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Memvalidasi identitas akun dan memicu sinkronisasi Remote Grant serta User Registry
     * saat sesi aplikasi dipulihkan atau saat kredensial Firebase Auth siap (Phase 4B.7).
     */
    fun triggerStartupSessionSync(
        email: String,
        fbUser: com.google.firebase.auth.FirebaseUser? = null,
        force: Boolean = false
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return

        val activeFbUser = fbUser ?: try {
            if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                FirebaseAuth.getInstance().currentUser
            } else null
        } catch (_: Exception) { null }

        if (activeFbUser != null && !activeFbUser.isAnonymous) {
            viewModelScope.launch(Dispatchers.IO) {
                com.example.util.UserRegistryManager.syncUserRegistry(getApplication(), activeFbUser)
            }
        }
        syncTesterGrant(cleanEmail, force = force)
    }

    /**
     * Dipanggil saat aplikasi kembali ke foreground (ON_RESUME) untuk memeriksa pembaruan grant
     * dari Web Admin secara efisien tanpa polling terus-menerus (Phase 4B.7).
     */
    fun checkAndSyncTesterGrantOnResume() {
        val user = currentUser.value ?: return
        if (user.isGuest || user.email.isBlank()) return
        val activeFbUser = try {
            if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                FirebaseAuth.getInstance().currentUser
            } else null
        } catch (_: Exception) { null }

        if (activeFbUser != null && !activeFbUser.isAnonymous) {
            triggerStartupSessionSync(user.email, activeFbUser, force = false)
        } else {
            syncTesterGrant(user.email, force = false)
        }
    }

    /**
     * Menyinkronkan Remote Smart Master Grant untuk email tester (Phase 4B.7).
     *
     * Dilengkapi perlindungan debounce (30 detik) dan in-flight concurrency guard
     * untuk mencegah request Firestore berulang yang tidak perlu.
     */
    fun syncTesterGrant(
        email: String,
        force: Boolean = false,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
            onFinished?.invoke(false)
            return
        }

        val now = System.currentTimeMillis()
        if (!force && (now - lastGrantSyncTimestamp in 0..GRANT_SYNC_DEBOUNCE_MILLIS)) {
            Log.d("AuthViewModel", "Sinkronisasi grant tester untuk $cleanEmail ter-debounce (${(now - lastGrantSyncTimestamp) / 1000}s lalu).")
            onFinished?.invoke(false)
            return
        }

        if (grantSyncJob?.isActive == true) {
            Log.d("AuthViewModel", "Sinkronisasi grant tester sedang berlangsung, mengabaikan request bersamaan.")
            onFinished?.invoke(false)
            return
        }

        grantSyncJob = viewModelScope.launch(Dispatchers.IO) {
            lastGrantSyncTimestamp = System.currentTimeMillis()
            val result = com.example.util.TesterGrantManager.fetchRemoteGrant(cleanEmail)
            if (result.isSuccess) {
                val grant = result.getOrNull()
                val verifyNow = System.currentTimeMillis()
                if (grant != null && com.example.util.TesterGrantManager.isGrantActive(grant, verifyNow)) {
                    // Simpan cache grant lokal
                    com.example.util.TesterGrantManager.saveCachedGrant(getApplication(), grant)
                    applyTesterGrant(grant)
                    withContext(Dispatchers.Main) { onFinished?.invoke(true) }
                } else {
                    // Grant tidak ditemukan / dicabut / kedaluwarsa
                    com.example.util.TesterGrantManager.clearCachedGrant(getApplication(), cleanEmail)
                    removeTesterGrantIfActive()
                    withContext(Dispatchers.Main) { onFinished?.invoke(false) }
                }
            } else {
                // Gangguan jaringan / PERMISSION_DENIED / Firestore gagal diakses
                val error = result.exceptionOrNull()
                val safeErrName = error?.javaClass?.simpleName ?: "Exception"
                val safeErrMsg = error?.message?.take(200) ?: "Unknown network/Firestore failure"
                Log.w("AuthViewModel", "Gagal memuat grant remote [$safeErrName]: $safeErrMsg")

                // Periksa offline grace period (maksimum 72 jam)
                val verifyNow = System.currentTimeMillis()
                val cached = com.example.util.TesterGrantManager.getCachedGrant(getApplication(), cleanEmail)
                if (cached != null && com.example.util.TesterGrantManager.isOfflineGraceValid(cached, verifyNow)) {
                    Log.i("AuthViewModel", "Offline grace period aktif untuk grant tester: ${cached.email}")
                    applyTesterGrant(cached)
                } else {
                    Log.w("AuthViewModel", "Offline grace period tidak berlaku/habis untuk grant: $cleanEmail")
                    removeTesterGrantIfActive()
                }
                // Firestore gagal diakses: jangan anggap sinkronisasi remote berhasil
                withContext(Dispatchers.Main) { onFinished?.invoke(false) }
            }
        }
    }

    private suspend fun applyTesterGrant(grant: com.example.util.TesterGrant) {
        val user = currentUser.value ?: return
        if (user.isGuest || user.id <= 0L) return

        // DILARANG merusak status Play Store Billing yang sah
        val billingSource = prefs.getString("billing_purchase_source_user_${user.id}", null)
        val isPlayBillingActive = billingSource == "PLAY_STORE_BILLING" &&
                (user.subscriptionExpiresAt == null || user.subscriptionExpiresAt > System.currentTimeMillis())

        if (isPlayBillingActive) {
            Log.i("AuthViewModel", "Pengguna memiliki langganan Play Store aktif. Grant remote disimpan sebagai cadangan tanpa menimpa billing.")
            return
        }

        // Terapkan role SMART_MASTER dengan sumber DEVELOPER_GRANT
        val updatedUser = user.copy(
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = grant.expiresAt
        )
        userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, grant.expiresAt)
        withContext(Dispatchers.Main) {
            SessionManager.login(updatedUser)
            prefs.edit()
                .putString("billing_purchase_source_user_${user.id}", "DEVELOPER_GRANT")
                .apply()
            _isPaywallOpen.value = false
        }
    }

    private suspend fun removeTesterGrantIfActive() {
        val user = currentUser.value ?: return
        if (user.isGuest || user.id <= 0L) return

        val billingSource = prefs.getString("billing_purchase_source_user_${user.id}", null)
        // Hanya downgrade jika sumber SMART_MASTER adalah DEVELOPER_GRANT
        if (user.role == UserRole.SMART_MASTER && billingSource == "DEVELOPER_GRANT") {
            Log.i("AuthViewModel", "Grant remote tester tidak lagi aktif. Mendowngrade ke FREE_MEMBER.")
            val downgraded = user.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null)
            userRepository.updateUserRole(user.id, UserRole.FREE_MEMBER, null)
            withContext(Dispatchers.Main) {
                SessionManager.login(downgraded)
                prefs.edit()
                    .remove("billing_purchase_source_user_${user.id}")
                    .apply()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            firebaseAuthStateListener?.let { listener ->
                if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                    FirebaseAuth.getInstance().removeAuthStateListener(listener)
                }
            }
        } catch (_: Exception) {}
        grantSyncJob?.cancel()
    }

    fun addSingleFeaturePassCredit(count: Int = 1) {
        val newCredits = _singleFeaturePassCredits.value + count
        _singleFeaturePassCredits.value = newCredits
        prefs.edit().putInt("single_feature_credits", newCredits).apply()
    }

    fun consumeSingleFeaturePassCredit(): Boolean {
        if (_singleFeaturePassCredits.value > 0) {
            val newCredits = _singleFeaturePassCredits.value - 1
            _singleFeaturePassCredits.value = newCredits
            prefs.edit().putInt("single_feature_credits", newCredits).apply()
            _isSuccessToast.value = "1x Per-Action Pass digunakan (Sisa pass: $newCredits)"
            return true
        }
        return false
    }

    // Master Dev Authorization & Audit Trail
    fun isDeveloperAuthorized(isDebug: Boolean = BuildConfig.DEBUG): Boolean {
        return EntitlementManager.isDeveloperAuthorized(currentUser.value, isDebug)
    }

    private fun logMasterDevAction(actor: String, targetId: Long, action: String, days: Long, expiry: Long?) {
        val currentLogs = prefs.getString("master_dev_audit_trail", "") ?: ""
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val newEntry = "[$timestamp] Aktor: $actor -> Target #$targetId: $action ($days hari, Expiry: $expiry)"
        val updatedLogs = if (currentLogs.isBlank()) newEntry else "$newEntry\n$currentLogs"
        prefs.edit().putString("master_dev_audit_trail", updatedLogs).apply()
    }

    fun upgradeUserWithDuration(userId: Long, durationDays: Long, reason: String = "Pemberian MasterDev") {
        if (!isDeveloperAuthorized()) {
            _authError.value = "Akses ditolak: Operasi ini membutuhkan hak istimewa Master Developer."
            return
        }
        val validation = EntitlementManager.validateCustomDuration(durationDays.toString())
        if (validation.isFailure) {
            _authError.value = validation.exceptionOrNull()?.message
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expiry = now + (durationDays * 86400000L)
            userRepository.updateUserRole(userId, UserRole.SMART_MASTER, expiry)
            val current = currentUser.value
            if (current?.id == userId) {
                SessionManager.login(current.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = expiry))
            }
            logMasterDevAction(actor = current?.email ?: "MasterDev", targetId = userId, action = "UPGRADE", days = durationDays, expiry = expiry)
            _isSuccessToast.value = "Berhasil: User #$userId di-upgrade ke Smart Master 👑 ($durationDays hari)"
        }
    }

    // Master Dev Approval Actions (Bab 2 & 3)
    fun upgradeUserToSmartMaster(userId: Long) {
        upgradeUserWithDuration(userId, 365L)
    }

    fun downgradeUserToMember(userId: Long) {
        if (!isDeveloperAuthorized()) {
            _authError.value = "Akses ditolak: Operasi ini membutuhkan hak istimewa Master Developer."
            return
        }
        viewModelScope.launch {
            userRepository.updateUserRole(userId, UserRole.FREE_MEMBER, null)
            val current = currentUser.value
            if (current?.id == userId) {
                SessionManager.login(current.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null))
            }
            logMasterDevAction(actor = current?.email ?: "MasterDev", targetId = userId, action = "DOWNGRADE", days = 0, expiry = null)
            _isSuccessToast.value = "Status akun #$userId dikembalikan ke DompetNalar Member"
        }
    }

    // Account Deletion (Bab 2 & Bab 13 Play Store Compliance)
    fun deleteAccountPermanently(
        passwordConfirmation: String,
        onError: ((String) -> Unit)? = null,
        onDeleted: () -> Unit
    ): Boolean {
        val user = currentUser.value ?: run {
            val msg = "Tidak ada sesi pengguna aktif."
            _authError.value = msg
            onError?.invoke(msg)
            return false
        }
        val requiresPassword = !user.isGuest && !user.passwordHash.isNullOrBlank()
        val trimmedPass = passwordConfirmation.trim()

        // Check password matching for password-based non-guest accounts
        if (requiresPassword) {
            if (trimmedPass.isEmpty()) {
                val msg = "Kata sandi wajib diisi untuk konfirmasi keamanan."
                _authError.value = msg
                onError?.invoke(msg)
                return false
            }
            val isPasswordValid = PasswordHasher.verifyPassword(trimmedPass, user.passwordHash)
            if (!isPasswordValid) {
                val msg = "Kata sandi salah. Penghapusan akun dibatalkan."
                _authError.value = msg
                onError?.invoke(msg)
                return false
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Hapus akun Firebase Auth di cloud terlebih dahulu jika akun terdaftar (bukan tamu)
            if (!user.isGuest && FirebaseApp.getApps(getApplication<Application>()).isNotEmpty()) {
                val fbUser = try {
                    FirebaseAuth.getInstance().currentUser
                } catch (e: Exception) {
                    null
                }
                if (fbUser != null) {
                    try {
                        suspendCancellableCoroutine<Void?> { cont ->
                            fbUser.delete()
                                .addOnSuccessListener { cont.resume(it) }
                                .addOnFailureListener { exc -> cont.resumeWithException(exc) }
                                .addOnCanceledListener { cont.cancel() }
                        }
                        Log.i("AuthViewModel", "Firebase Auth account successfully deleted from cloud.")
                    } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                        val errMsg = "Demi keamanan, penghapusan akun cloud memerlukan login terbaru. Silakan keluar dan login kembali sebelum menghapus akun."
                        Log.w("AuthViewModel", errMsg, e)
                        withContext(Dispatchers.Main) {
                            _authError.value = errMsg
                            onError?.invoke(errMsg)
                        }
                        return@launch
                    } catch (e: Exception) {
                        val errMsg = "Gagal menghapus akun di server cloud: ${e.localizedMessage ?: "Kesalahan jaringan"}. Penghapusan data lokal dibatalkan demi keamanan."
                        Log.e("AuthViewModel", errMsg, e)
                        withContext(Dispatchers.Main) {
                            _authError.value = errMsg
                            onError?.invoke(errMsg)
                        }
                        return@launch
                    }
                }
            }

            // 1. Atomic Room user account deletion & cascade (DEF-03 Stage 3)
            FinSmartDatabase.deleteUserAccountInTransaction(database, user)

            // 2. Cancel scheduled background tasks for this user
            try {
                androidx.work.WorkManager.getInstance(getApplication()).cancelAllWorkByTag("user_${user.id}")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Gagal membatalkan WorkManager tasks", e)
            }

            // 3. Disconnect Google Drive & clear drive preferences for this user
            try {
                com.example.util.GoogleDriveService.disconnect(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Gagal disconnect Google Drive", e)
            }

            // 4. Invalidate profile photo, cache, and session preferences
            authSessionEpoch.incrementAndGet()
            updateProfilePhotoUri(null)
            try {
                val targetContext = getApplication<Application>()
                coil.Coil.imageLoader(targetContext).memoryCache?.clear()
            } catch (_: Exception) {}

            prefs.edit().remove("saved_user_id").remove("last_logged_in_user").apply()
            try {
                getApplication<Application>().getSharedPreferences("finsmart_prefs", Context.MODE_PRIVATE)
                    .edit().remove("saved_user_id").apply()
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Gagal membersihkan finsmart_prefs", e)
            }

            // 5. Reset in-memory state flows on Main thread
            withContext(Dispatchers.Main) {
                SessionManager.logout()
                _singleFeaturePassCredits.value = 0
                _shakeGestureAction.value = "OFF"
                _isDriveConnected.value = false
                _driveEmail.value = ""
                _driveLastBackup.value = ""
                _driveAutoSync.value = false
                _gamificationState.value = UserGamificationState(
                    level = 1,
                    currentXp = 0,
                    xpForNextLevel = 100,
                    dailyStreak = 0,
                    bestStreak = 0,
                    personality = FinancialPersonality.BALANCED,
                    activeIconEdition = AppIconEdition.DEFAULT_CLASSIC,
                    customBackgroundUri = null,
                    customBackgroundOpacity = 0.3f,
                    isNoSpendChallengeActive = false,
                    mainPin = "123456",
                    duressPin = "999999",
                    isBiometricEnabled = false,
                    unlockedBadgeIds = setOf("rookie_planner")
                )
                _isDeleteAccountOpen.value = false
                _isSuccessToast.value = "Akun dan seluruh data lokal berhasil dihapus secara permanen."
                onDeleted()
                _isAuthModalOpen.value = true
            }
        }

        return true
    }

    fun togglePlayStoreBuildMode() {
        _isSuccessToast.value = if (AppConfig.IS_PLAY_STORE_BUILD) {
            "Konfigurasi Build: Google Play Store Release (IS_PLAY_STORE_BUILD = true)"
        } else {
            "Konfigurasi Build: Non-Play Store APK (IS_PLAY_STORE_BUILD = false)"
        }
    }

    fun isFeatureUnlocked(): Boolean {
        val now = System.currentTimeMillis()
        val user = currentUser.value
        if (user != null && user.role == UserRole.SMART_MASTER) {
            if (user.isGuest) {
                val grantedAt = prefs.getLong("guest_ad_reward_granted_at", 0L)
                val expiresAt = prefs.getLong("guest_ad_reward_expires_at", 0L)
                val maxAllowed = if (grantedAt > 0L) grantedAt + 3600000L else 0L
                val clamped = if (expiresAt > maxAllowed || expiresAt <= 0L) maxAllowed else expiresAt
                if (clamped <= now) {
                    downgradeExpiredSubscription(user)
                    return false
                }
            } else if (user.subscriptionExpiresAt != null && user.subscriptionExpiresAt <= now) {
                downgradeExpiredSubscription(user)
                return false
            }
        }
        return EntitlementManager.evaluate(currentUser.value, _singleFeaturePassCredits.value, now).isPremiumUnlocked
    }

    fun getTrialDaysRemaining(): Int {
        return 0
    }

    fun openMasterDevPanel() {
        if (!BuildConfig.DEBUG || !isDeveloperAuthorized()) return
        _isMasterDevPanelOpen.value = true
    }

    fun closeMasterDevPanel() {
        _isMasterDevPanelOpen.value = false
    }

    fun openPaywall() {
        _isPaywallOpen.value = true
    }

    fun closePaywall() {
        _isPaywallOpen.value = false
    }

    fun openAuthModal() {
        _authError.value = null
        _isAuthModalOpen.value = true
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun closeAuthModal() {
        _authError.value = null
        _isAuthModalOpen.value = false
        // Jika tidak ada user aktif saat modal ditutup, pastikan masuk ke Mode Tamu agar aplikasi tidak berada dalam kondisi null session
        if (SessionManager.getActiveUserId() == null || currentUser.value == null) {
            loginAsGuest()
        }
    }

    fun openDeleteAccount() {
        _isDeleteAccountOpen.value = true
    }

    fun closeDeleteAccount() {
        _isDeleteAccountOpen.value = false
    }

    fun openProminentDisclosure() {
        _isProminentDisclosureOpen.value = true
    }

    fun closeProminentDisclosure() {
        _isProminentDisclosureOpen.value = false
    }

    fun clearToast() {
        _isSuccessToast.value = null
    }

    // Guest Reminder Banner Logic
    fun isGuestReminderEligible(currentTime: Long = System.currentTimeMillis()): Boolean {
        val user = currentUser.value
        if (user == null || !user.isGuest) return false
        val lastDismissed = prefs.getLong(PREF_KEY_GUEST_BANNER_DISMISSED_AT, 0L)
        if (lastDismissed > 0L) {
            val elapsed = currentTime - lastDismissed
            if (elapsed in 0 until GUEST_BANNER_COOLDOWN_MILLIS) {
                return false
            }
        }
        return true
    }

    fun scheduleGuestBannerPrompt(
        delayMillis: Long = GUEST_BANNER_INITIAL_DELAY_MILLIS,
        currentTime: Long = System.currentTimeMillis()
    ) {
        guestBannerJob?.cancel()
        _isGuestReminderBannerVisible.value = false
        val user = currentUser.value
        if (user?.isGuest != true) return
        if (!isGuestReminderEligible(currentTime)) return

        guestBannerJob = viewModelScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            if (isGuestReminderEligible()) {
                _isGuestReminderBannerVisible.value = true
            }
        }
    }

    fun dismissGuestReminderBanner(dismissTime: Long = System.currentTimeMillis()) {
        guestBannerJob?.cancel()
        prefs.edit().putLong(PREF_KEY_GUEST_BANNER_DISMISSED_AT, dismissTime).apply()
        _isGuestReminderBannerVisible.value = false
    }

    fun triggerGuestBannerForTesting() {
        if (isGuestReminderEligible()) {
            _isGuestReminderBannerVisible.value = true
        }
    }

    fun connectGoogleDrive(email: String, activityContext: Context? = null) {
        viewModelScope.launch {
            _authError.value = null
            val targetEmail = email.trim().ifBlank { currentUser.value?.email ?: "" }
            if (targetEmail.isBlank() || targetEmail.endsWith("@finsmart.local")) {
                _authError.value = "Alamat email Google tidak valid. Masuk dengan akun Google terlebih dahulu."
                return@launch
            }

            _isDriveSyncing.value = true
            val context = activityContext ?: getApplication()
            val authResult = GoogleDriveService.getDriveAccessToken(context, targetEmail)
            when (authResult) {
                is DriveAuthResult.Success -> {
                    GoogleDriveService.connect(getApplication(), targetEmail)
                    _isDriveConnected.value = true
                    _driveEmail.value = targetEmail
                    _isSuccessToast.value = "Google Drive terhubung ke $targetEmail"
                }
                is DriveAuthResult.NeedsUserRecoverableAuth -> {
                    _driveRecoveryIntent.value = authResult.recoveryIntent
                    // DO NOT mark connected until the user actually consents via recovery activity
                    _isDriveConnected.value = false
                    _driveEmail.value = ""
                    _isSuccessToast.value = "Izin Google Drive memerlukan persetujuan akun Google. Silakan berikan izin pada dialog."
                }
                is DriveAuthResult.Error -> {
                    _isDriveConnected.value = false
                    _driveEmail.value = ""
                    _authError.value = "Gagal menghubungkan Google Drive: ${authResult.message}"
                }
            }
            _isDriveSyncing.value = false
        }
    }

    fun disconnectGoogleDrive() {
        GoogleDriveService.disconnect(getApplication())
        _isDriveConnected.value = false
        _driveEmail.value = ""
        _isSuccessToast.value = "Koneksi Google Drive diputus"
    }

    fun updateAutoBackupInterval(hours: Long) {
        GoogleDriveService.setAutoBackupIntervalHours(getApplication(), hours)
        _driveAutoBackupInterval.value = hours
        if (_driveAutoSync.value) {
            GoogleDriveService.scheduleAutoBackup(getApplication(), true, hours)
        }
        _isSuccessToast.value = "Interval backup otomatis diatur ke $hours jam"
    }

    fun toggleAutoSync(enabled: Boolean) {
        val interval = _driveAutoBackupInterval.value
        GoogleDriveService.setAutoSyncEnabled(getApplication(), enabled)
        GoogleDriveService.scheduleAutoBackup(getApplication(), enabled, interval)
        _driveAutoSync.value = enabled
        _isSuccessToast.value = if (enabled) "Backup otomatis Google Drive aktif (setiap $interval jam)" else "Backup otomatis Google Drive dinonaktifkan"
    }

    fun triggerManualBackup() {
        viewModelScope.launch {
            val isStealth = try {
                SessionManager.isStealthModeActive()
            } catch (e: Exception) {
                true
            }
            if (isStealth) {
                _isSuccessToast.value = "Pencadangan ditolak: Mode Stealth sedang aktif untuk melindungi privasi data."
                _isDriveSyncing.value = false
                return@launch
            }
            _isDriveSyncing.value = true
            _isSuccessToast.value = "Memulai pencadangan data..."
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                _isSuccessToast.value = "Gagal mencadangkan: Sesi pengguna tidak aktif."
                _isDriveSyncing.value = false
                return@launch
            }
            val result = GoogleDriveService.backupNow(getApplication(), database, userId)
            _driveLastBackup.value = GoogleDriveService.getLastBackupTime(getApplication())
            _isSuccessToast.value = result.second
            _isDriveSyncing.value = false
        }
    }

    fun triggerManualRestore() {
        viewModelScope.launch {
            val isStealth = try {
                SessionManager.isStealthModeActive()
            } catch (e: Exception) {
                true
            }
            if (isStealth) {
                _isSuccessToast.value = "Pemulihan data ditolak: Mode Stealth sedang aktif untuk melindungi privasi data."
                _isDriveSyncing.value = false
                return@launch
            }
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                _isSuccessToast.value = "Gagal memulihkan: Sesi pengguna tidak aktif."
                return@launch
            }
            _isDriveSyncing.value = true
            _isSuccessToast.value = "Memulai pemulihan data..."
            val result = GoogleDriveService.restoreNow(getApplication(), database, userId)
            _isSuccessToast.value = result.second
            _isDriveSyncing.value = false
        }
    }

    fun triggerPortableBackup(password: String, onComplete: ((Boolean, String, File?) -> Unit)? = null) {
        viewModelScope.launch {
            val isStealth = try {
                SessionManager.isStealthModeActive()
            } catch (e: Exception) {
                true
            }
            if (isStealth) {
                val msg = "Pencadangan portabel ditolak: Mode Stealth sedang aktif untuk melindungi privasi data."
                _isSuccessToast.value = msg
                onComplete?.invoke(false, msg, null)
                return@launch
            }
            _isSuccessToast.value = "Membuat cadangan portabel..."
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                _isSuccessToast.value = "Gagal mencadangkan: Sesi pengguna tidak aktif."
                onComplete?.invoke(false, "Sesi pengguna tidak aktif.", null)
                return@launch
            }
            val result = com.example.util.GoogleDriveService.backupPortableNow(getApplication(), database, password, userId)
            _isSuccessToast.value = result.second
            onComplete?.invoke(result.first, result.second, result.third)
        }
    }

    fun triggerPortableRestore(backupJson: String?, password: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val isStealth = try {
                SessionManager.isStealthModeActive()
            } catch (e: Exception) {
                true
            }
            if (isStealth) {
                val msg = "Pemulihan portabel ditolak: Mode Stealth sedang aktif untuk melindungi privasi data."
                _isSuccessToast.value = msg
                onComplete?.invoke(false, msg)
                return@launch
            }
            val userId = SessionManager.getActiveUserId()
            if (userId == null || userId <= 0L) {
                _isSuccessToast.value = "Gagal memulihkan: Sesi pengguna tidak aktif."
                onComplete?.invoke(false, "Sesi pengguna tidak aktif.")
                return@launch
            }
            _isSuccessToast.value = "Mendekripsi dan memulihkan cadangan portabel..."
            val result = com.example.util.GoogleDriveService.restorePortableNow(getApplication(), database, backupJson, password, userId)
            _isSuccessToast.value = result.second
            onComplete?.invoke(result.first, result.second)
        }
    }

    // Gamification & Security Controls (Bab 6 & 10)
    fun openGamificationHub() { _isGamificationHubOpen.value = true }
    fun closeGamificationHub() { _isGamificationHubOpen.value = false }

    fun openPersonalityQuiz() { _isPersonalityQuizOpen.value = true }
    fun closePersonalityQuiz() { _isPersonalityQuizOpen.value = false }

    fun openDuressPinModal() { _isDuressPinModalOpen.value = true }
    fun closeDuressPinModal() { _isDuressPinModalOpen.value = false }

    fun openDebtPlanner() { _isDebtPlannerOpen.value = true }
    fun closeDebtPlanner() { _isDebtPlannerOpen.value = false }

    fun openAssetTracker() { _isAssetTrackerOpen.value = true }
    fun closeAssetTracker() { _isAssetTrackerOpen.value = false }

    fun openReceiptGenerator() { _isReceiptGeneratorOpen.value = true }
    fun closeReceiptGenerator() { _isReceiptGeneratorOpen.value = false }

    fun openNotificationCenter() { _isNotificationCenterOpen.value = true }
    fun closeNotificationCenter() { _isNotificationCenterOpen.value = false }

    fun updatePersonality(personality: FinancialPersonality) {
        _gamificationState.value = _gamificationState.value.copy(personality = personality)
        prefs.edit().putString("gamify_personality", personality.name).apply()
        _isSuccessToast.value = "Profil Finansial diubah ke: ${personality.title}"
    }

    fun selectAppIconEdition(icon: AppIconEdition) {
        _gamificationState.value = _gamificationState.value.copy(activeIconEdition = icon)
        prefs.edit().putString("gamify_icon", icon.name).apply()
        com.example.util.AppIconManager.setAppIcon(
            context = getApplication(),
            edition = icon,
            currentStreak = _gamificationState.value.dailyStreak,
            isMasterDev = currentUser.value?.role == UserRole.MASTER_DEV
        )
        _isSuccessToast.value = "Dynamic App Icon diubah ke: ${icon.displayName}"
    }

    fun updateCustomBackground(uri: String?, opacity: Float) {
        _gamificationState.value = _gamificationState.value.copy(
            customBackgroundUri = uri,
            customBackgroundOpacity = opacity
        )
        prefs.edit()
            .putString("gamify_bg_uri", uri)
            .putFloat("gamify_bg_opacity", opacity)
            .apply()
        com.example.util.ThemeEngine.setCustomWallpaper(getApplication(), uri, opacity)
    }

    fun toggleNoSpendChallenge(enabled: Boolean) {
        _gamificationState.value = _gamificationState.value.copy(isNoSpendChallengeActive = enabled)
        prefs.edit().putBoolean("gamify_nospend", enabled).apply()
        _isSuccessToast.value = if (enabled) "No-Spend Challenge Aktif! Pertahankan 24 jam untuk Multiplier 2x!" else "No-Spend Challenge Dinonaktifkan"
    }

    fun saveSecurityPins(mainPin: String, duressPin: String, biometricEnabled: Boolean) {
        if (mainPin == duressPin || mainPin.length < 4 || duressPin.length < 4) {
            _isSuccessToast.value = "PIN Utama dan Duress PIN harus berbeda dan valid (minimal 4 digit)."
            return
        }
        val mainHash = PasswordHasher.hashPassword(mainPin)
        val duressHash = PasswordHasher.hashPassword(duressPin)
        _gamificationState.value = _gamificationState.value.copy(
            mainPin = "",
            duressPin = "",
            isBiometricEnabled = biometricEnabled
        )
        prefs.edit()
            .putString("security_main_pin_hash", mainHash)
            .putString("security_duress_pin_hash", duressHash)
            .remove("security_main_pin")
            .remove("security_duress_pin")
            .putBoolean("security_biometric", biometricEnabled)
            .apply()
    }

    fun verifyPinAndUnlock(inputPin: String) {
        val mainHash = prefs.getString("security_main_pin_hash", null)
            ?: prefs.getString("security_main_pin", null)?.let { PasswordHasher.hashPassword(it) }
            ?: PasswordHasher.hashPassword("123456")
        val duressHash = prefs.getString("security_duress_pin_hash", null)
            ?: prefs.getString("security_duress_pin", null)?.let { PasswordHasher.hashPassword(it) }
            ?: PasswordHasher.hashPassword("999999")

        val state = _gamificationState.value
        if (PasswordHasher.verifyPassword(inputPin, duressHash)) {
            SessionManager.setStealthMode(true)
            _gamificationState.value = state.copy(isStealthMode = true)
            _isSuccessToast.value = "⚠️ DompetNalar Masuk Mode Stealth (Duress PIN Mode)"
        } else if (PasswordHasher.verifyPassword(inputPin, mainHash)) {
            SessionManager.setStealthMode(false)
            _gamificationState.value = state.copy(isStealthMode = false)
            _isSuccessToast.value = "✓ Keamanan Diverifikasi! Mode Asli Aktif"
        } else {
            _isSuccessToast.value = "❌ PIN Salah!"
        }
    }

    /**
     * Activates Stealth Mode directly (used by FEAT-36 Shake Gesture).
     */
    fun activateStealthMode() {
        SessionManager.setStealthMode(true)
        val state = _gamificationState.value
        if (!state.isStealthMode) {
            _gamificationState.value = state.copy(isStealthMode = true)
            _isSuccessToast.value = "⚠️ DompetNalar Masuk Mode Stealth"
        }
    }

    fun awardXp(amount: Int) {
        val current = _gamificationState.value
        val newXp = current.currentXp + (amount * current.streakMultiplier)
        val (newLevel, nextReq) = GamificationManager.calculateLevel(newXp)
        _gamificationState.value = current.copy(
            level = newLevel,
            currentXp = newXp,
            xpForNextLevel = nextReq
        )
        prefs.edit()
            .putInt("gamify_level", newLevel)
            .putInt("gamify_xp", newXp)
            .apply()
    }
}
