package com.example.ui.viewmodel

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
import com.example.util.GamificationManager
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Random

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
    private val userRepository: UserRepository
    private val database: FinSmartDatabase

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

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

    private val _isDriveConnected = MutableStateFlow(false)
    val isDriveConnected: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _driveEmail = MutableStateFlow("")
    val driveEmail: StateFlow<String?> = _driveEmail.asStateFlow()

    private val _driveLastBackup = MutableStateFlow("")
    val driveLastBackup: StateFlow<String> = _driveLastBackup.asStateFlow()

    private val _driveAutoSync = MutableStateFlow(false)
    val driveAutoSync: StateFlow<Boolean> = _driveAutoSync.asStateFlow()

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

    private val _profilePhotoUri = MutableStateFlow(prefs.getString("user_profile_photo_uri", null))
    val profilePhotoUri: StateFlow<String?> = _profilePhotoUri.asStateFlow()

    fun updateProfilePhotoUri(uri: String?) {
        _profilePhotoUri.value = uri
        prefs.edit().putString("user_profile_photo_uri", uri).apply()
    }

    private val _gamificationState = MutableStateFlow(
        UserGamificationState(
            level = prefs.getInt("gamify_level", 1),
            currentXp = prefs.getInt("gamify_xp", 20),
            xpForNextLevel = GamificationManager.calculateLevel(prefs.getInt("gamify_xp", 20)).second,
            dailyStreak = prefs.getInt("gamify_streak", 7),
            bestStreak = prefs.getInt("gamify_best_streak", 7),
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
            unlockedBadgeIds = prefs.getStringSet("gamify_badges", setOf("rookie_planner")) ?: setOf("rookie_planner")
        )
    )
    val gamificationState: StateFlow<UserGamificationState> = _gamificationState.asStateFlow()

    init {
        database = FinSmartDatabase.getDatabase(application, viewModelScope)
        userRepository = UserRepository(database.userDao())

        // Initialize Google Drive connection preferences
        _isDriveConnected.value = com.example.util.GoogleDriveService.isConnected(application)
        _driveEmail.value = com.example.util.GoogleDriveService.getConnectedEmail(application)
        _driveLastBackup.value = com.example.util.GoogleDriveService.getLastBackupTime(application)
        _driveAutoSync.value = com.example.util.GoogleDriveService.isAutoSyncEnabled(application)

        // Load saved session or default to Guest Mode (Bab 1 & 2)
        viewModelScope.launch {
            val savedUserId = prefs.getLong("saved_user_id", -1L)
            if (savedUserId != -1L) {
                val user = userRepository.getUserById(savedUserId)
                if (user != null) {
                    _currentUser.value = user
                    return@launch
                }
            }

            // Fresh install / guest user default (3-day free trial, non-MasterDev)
            val guestUser = User(
                username = "Tamu FinSmart",
                email = "guest@finsmart.local",
                role = UserRole.FREE_MEMBER,
                isGuest = true,
                trialStartDate = System.currentTimeMillis()
            )
            _currentUser.value = guestUser
        }

        // Silent startup migration of legacy plaintext passwords to PBKDF2
        viewModelScope.launch {
            PasswordHasher.migrateLegacyPasswords(userRepository)
        }
    }

    val allRegisteredUsers: StateFlow<List<User>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setInitialMasterPassword(newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            _authError.value = null
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

            val lukas = userRepository.getUserByIdentifier("Lukas")
            if (lukas == null || lukas.role != UserRole.MASTER_DEV || lukas.passwordHash.isNotEmpty()) {
                _authError.value = "Pengaturan awal kata sandi tidak diizinkan."
                return@launch
            }

            val secureHash = PasswordHasher.hashPassword(trimmedNew)
            val updated = lukas.copy(passwordHash = secureHash)
            userRepository.updateUser(updated)

            _currentUser.value = updated
            prefs.edit().putLong("saved_user_id", updated.id).apply()
            _isInitialMasterSetupOpen.value = false
            _isAuthModalOpen.value = false
            _isSuccessToast.value = "Kata sandi Master Developer berhasil dibuat! 👑"
        }
    }

    fun changePassword(currentPass: String, newPass: String, confirmPass: String) {
        viewModelScope.launch {
            _changePasswordError.value = null
            val user = _currentUser.value
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

            _currentUser.value = updatedUser
            _isChangePasswordOpen.value = false
            _isSuccessToast.value = "Kata sandi berhasil diperbarui! 🔒"
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            val trimmedId = identifier.trim()
            val trimmedPass = password.trim()

            if (trimmedId.isEmpty()) {
                _authError.value = "Nama pengguna atau email tidak boleh kosong."
                return@launch
            }

            // Normal Database Authentication Flow
            val user = userRepository.getUserByIdentifier(trimmedId)
            if (user != null) {
                // Fresh install Master Dev initial password setup check
                if (user.role == UserRole.MASTER_DEV && user.passwordHash.isEmpty()) {
                    _authError.value = "Akun Master Developer belum memiliki kata sandi. Silakan buat kata sandi utama."
                    _isInitialMasterSetupOpen.value = true
                    return@launch
                }

                // Strict check: reject blank passwords
                if (trimmedPass.isEmpty()) {
                    _authError.value = "Kata sandi tidak boleh kosong."
                    return@launch
                }

                if (PasswordHasher.verifyPassword(trimmedPass, user.passwordHash) || user.isGuest) {
                    _currentUser.value = user
                    prefs.edit().putLong("saved_user_id", user.id).apply()
                    _isAuthModalOpen.value = false
                    if (user.role == UserRole.MASTER_DEV) {
                        _isSuccessToast.value = "Selamat Datang Master Developer Lukas! 👑"
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

            val existing = userRepository.getUserByIdentifier(email.trim())
            if (existing != null) {
                _authError.value = "Email sudah terdaftar. Silakan login."
                return@launch
            }

            val newUser = User(
                username = username.trim().ifBlank { "User ${email.substringBefore("@")}" },
                email = email.trim(),
                passwordHash = PasswordHasher.hashPassword(password.trim()),
                role = UserRole.FREE_MEMBER,
                trialStartDate = System.currentTimeMillis()
            )
            val newId = userRepository.insertUser(newUser)
            val created = newUser.copy(id = newId)

            _currentUser.value = created
            prefs.edit().putLong("saved_user_id", newId).apply()
            _pendingOtpCode.value = null
            _isAuthModalOpen.value = false
            _isSuccessToast.value = "Akun berhasil dibuat! Selamat datang di FinSmart."
        }
    }

    fun loginWithGoogleMock() {
        viewModelScope.launch {
            val googleEmail = "google.user@gmail.com"
            var user = userRepository.getUserByIdentifier(googleEmail)
            if (user == null) {
                val newUser = User(
                    username = "Google User",
                    email = googleEmail,
                    role = UserRole.FREE_MEMBER,
                    trialStartDate = System.currentTimeMillis()
                )
                val id = userRepository.insertUser(newUser)
                user = newUser.copy(id = id)
            }
            _currentUser.value = user
            prefs.edit().putLong("saved_user_id", user.id).apply()
            _isAuthModalOpen.value = false
            _isSuccessToast.value = "Berhasil masuk via Akun Google"
        }
    }

    fun loginAsGuest() {
        val guest = User(
            id = 9999L,
            username = "Tamu (Guest)",
            email = "guest@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = System.currentTimeMillis()
        )
        _currentUser.value = guest
        prefs.edit().putLong("saved_user_id", 9999L).apply()
        _isAuthModalOpen.value = false
        _isSuccessToast.value = "Mode Tamu aktif (Trial 3 Hari)"
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().remove("saved_user_id").apply()
        _isAuthModalOpen.value = true
    }

    fun grantTemporaryRewardAccess(durationHours: Int = 1) {
        val user = _currentUser.value ?: return
        val rewardExpiry = System.currentTimeMillis() + (durationHours * 3600000L) // Presisi 1 jam / 3,600,000 ms
        val updatedUser = user.copy(
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = rewardExpiry
        )
        _currentUser.value = updatedUser

        // Juga menambahkan 1x Per-Action Pass Credit sebagai bonus cadangan
        addSingleFeaturePassCredit(1)

        viewModelScope.launch {
            if (!user.isGuest && user.id > 0) {
                userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, rewardExpiry)
            }
        }
        _isPaywallOpen.value = false
        _isSuccessToast.value = "Selamat! Temporary Smart Master Pass (1 Jam) & 1x Action Pass Aktif 🎉"
    }

    fun grantStreakMilestoneReward(durationHours: Int = 24, actionPassCredits: Int = 3) {
        val user = _currentUser.value ?: return
        val rewardExpiry = System.currentTimeMillis() + (durationHours * 3600000L) // 24 jam = 86,400,000 ms
        val updatedUser = user.copy(
            role = UserRole.SMART_MASTER,
            subscriptionExpiresAt = rewardExpiry
        )
        _currentUser.value = updatedUser

        addSingleFeaturePassCredit(actionPassCredits)

        viewModelScope.launch {
            if (!user.isGuest && user.id > 0) {
                userRepository.updateUserRole(user.id, UserRole.SMART_MASTER, rewardExpiry)
            }
        }
        _isSuccessToast.value = "🎉 Reward Streak 7 Hari Active: 24 Jam Smart Master Pass + 3x Action Pass Credits!"
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

    // Master Dev Approval Actions (Bab 2 & 3)
    fun upgradeUserToSmartMaster(userId: Long) {
        viewModelScope.launch {
            val oneYearLater = System.currentTimeMillis() + (365L * 86400000L)
            userRepository.updateUserRole(userId, UserRole.SMART_MASTER, oneYearLater)
            if (_currentUser.value?.id == userId) {
                _currentUser.value = _currentUser.value?.copy(role = UserRole.SMART_MASTER, subscriptionExpiresAt = oneYearLater)
            }
            _isSuccessToast.value = "Status akun berhasil di-approve menjadi Smart Master 👑"
        }
    }

    fun downgradeUserToMember(userId: Long) {
        viewModelScope.launch {
            userRepository.updateUserRole(userId, UserRole.FREE_MEMBER, null)
            if (_currentUser.value?.id == userId) {
                _currentUser.value = _currentUser.value?.copy(role = UserRole.FREE_MEMBER, subscriptionExpiresAt = null)
            }
            _isSuccessToast.value = "Status akun dikembalikan ke FinSmart Member"
        }
    }

    // Account Deletion (Bab 2 & Bab 13 Play Store Compliance)
    fun deleteAccountPermanently(passwordConfirmation: String, onDeleted: () -> Unit): Boolean {
        val user = _currentUser.value ?: return false
        val trimmedPass = passwordConfirmation.trim()

        // Check password matching (or permit if guest)
        if (!user.isGuest && trimmedPass.isEmpty()) {
            return false
        }

        val isPasswordValid = when {
            user.isGuest -> true
            else -> PasswordHasher.verifyPassword(trimmedPass, user.passwordHash)
        }

        if (!isPasswordValid) {
            return false
        }

        viewModelScope.launch {
            // Delete user record from database
            if (!user.isGuest && user.id > 0) {
                userRepository.deleteUser(user)
            }
            // Clear local preferences
            prefs.edit().clear().apply()
            _currentUser.value = null
            _isDeleteAccountOpen.value = false
            _isSuccessToast.value = "Akun dan seluruh data lokal berhasil dihapus secara permanen."
            onDeleted()
            _isAuthModalOpen.value = true
        }

        return true
    }

    fun togglePlayStoreBuildMode() {
        AppConfig.IS_PLAY_STORE_BUILD = !AppConfig.IS_PLAY_STORE_BUILD
        _isSuccessToast.value = if (AppConfig.IS_PLAY_STORE_BUILD) {
            "Mode Beralih ke: Google Play Store Release (In-App Billing)"
        } else {
            "Mode Beralih ke: Non-Play Store APK (DANA 085155076566 + Master Dev)"
        }
    }

    fun isFeatureUnlocked(): Boolean {
        val user = _currentUser.value ?: return false
        if (user.role == UserRole.MASTER_DEV) return true

        if (user.role == UserRole.SMART_MASTER) {
            val expiresAt = user.subscriptionExpiresAt
            if (expiresAt == null || expiresAt > System.currentTimeMillis()) {
                return true
            }
        }

        if (_singleFeaturePassCredits.value > 0) {
            return true
        }

        // 3-day trial logic
        val threeDaysMillis = 3L * 24 * 3600 * 1000L
        val elapsed = System.currentTimeMillis() - user.trialStartDate
        return elapsed <= threeDaysMillis
    }

    fun getTrialDaysRemaining(): Int {
        val user = _currentUser.value ?: return 0
        if (user.role == UserRole.MASTER_DEV || user.role == UserRole.SMART_MASTER) {
            return 999
        }
        val threeDaysMillis = 3L * 24 * 3600 * 1000L
        val elapsed = System.currentTimeMillis() - user.trialStartDate
        val remainingMillis = threeDaysMillis - elapsed
        return if (remainingMillis > 0) {
            (remainingMillis / (24 * 3600 * 1000L)).toInt() + 1
        } else {
            0
        }
    }

    fun openMasterDevPanel() {
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
        _isAuthModalOpen.value = true
    }

    fun closeAuthModal() {
        _isAuthModalOpen.value = false
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

    fun connectGoogleDrive(email: String) {
        com.example.util.GoogleDriveService.connect(getApplication(), email)
        _isDriveConnected.value = true
        _driveEmail.value = email
        _isSuccessToast.value = "Google Drive terhubung ke $email"
    }

    fun disconnectGoogleDrive() {
        com.example.util.GoogleDriveService.disconnect(getApplication())
        _isDriveConnected.value = false
        _driveEmail.value = ""
        _isSuccessToast.value = "Koneksi Google Drive diputus"
    }

    fun toggleAutoSync(enabled: Boolean) {
        com.example.util.GoogleDriveService.setAutoSyncEnabled(getApplication(), enabled)
        com.example.util.GoogleDriveService.scheduleWeeklySync(getApplication(), enabled)
        _driveAutoSync.value = enabled
        _isSuccessToast.value = if (enabled) "Backup otomatis mingguan aktif via Wi-Fi" else "Backup otomatis mingguan dimatikan"
    }

    fun triggerManualBackup() {
        viewModelScope.launch {
            _isSuccessToast.value = "Memulai pencadangan data ke Google Drive..."
            val result = com.example.util.GoogleDriveService.backupNow(getApplication(), database)
            _driveLastBackup.value = com.example.util.GoogleDriveService.getLastBackupTime(getApplication())
            _isSuccessToast.value = result.second
        }
    }

    fun triggerManualRestore() {
        viewModelScope.launch {
            _isSuccessToast.value = "Memulai pemulihan data..."
            val result = com.example.util.GoogleDriveService.restoreNow(getApplication(), database)
            _isSuccessToast.value = result.second
        }
    }

    fun triggerPortableBackup(password: String, onComplete: ((Boolean, String, File?) -> Unit)? = null) {
        viewModelScope.launch {
            _isSuccessToast.value = "Membuat cadangan portabel..."
            val result = com.example.util.GoogleDriveService.backupPortableNow(getApplication(), database, password)
            _isSuccessToast.value = result.second
            onComplete?.invoke(result.first, result.second, result.third)
        }
    }

    fun triggerPortableRestore(backupJson: String?, password: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isSuccessToast.value = "Mendekripsi dan memulihkan cadangan portabel..."
            val result = com.example.util.GoogleDriveService.restorePortableNow(getApplication(), database, backupJson, password)
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
            isMasterDev = _currentUser.value?.role == UserRole.MASTER_DEV
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
        _gamificationState.value = _gamificationState.value.copy(
            mainPin = mainPin,
            duressPin = duressPin,
            isBiometricEnabled = biometricEnabled
        )
        prefs.edit()
            .putString("security_main_pin", mainPin)
            .putString("security_duress_pin", duressPin)
            .putBoolean("security_biometric", biometricEnabled)
            .apply()
    }

    fun verifyPinAndUnlock(inputPin: String) {
        val state = _gamificationState.value
        if (inputPin == state.duressPin) {
            _gamificationState.value = state.copy(isStealthMode = true)
            _isSuccessToast.value = "⚠️ FinSmart Masuk Mode Stealth (Duress PIN Mode)"
        } else if (inputPin == state.mainPin) {
            _gamificationState.value = state.copy(isStealthMode = false)
            _isSuccessToast.value = "✓ Keamanan Diverifikasi! Mode Asli Aktif"
        } else {
            _isSuccessToast.value = "❌ PIN Salah!"
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
