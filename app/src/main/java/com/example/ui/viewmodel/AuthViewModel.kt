package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isSuccessToast = MutableStateFlow<String?>(null)
    val isSuccessToast: StateFlow<String?> = _isSuccessToast.asStateFlow()

    init {
        database = FinSmartDatabase.getDatabase(application, viewModelScope)
        userRepository = UserRepository(database.userDao())

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
    }

    val allRegisteredUsers: StateFlow<List<User>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            val trimmedId = identifier.trim()
            val trimmedPass = password.trim()

            // 1. Check Master Dev hidden credentials (Bab 2: User ID: Lukas | Pass: kodegawa01)
            if (trimmedId.equals("Lukas", ignoreCase = true) && trimmedPass == "kodegawa01") {
                val lukasInDb = userRepository.getUserByIdentifier("Lukas")
                val masterUser = lukasInDb ?: User(
                    username = "Lukas",
                    email = "lunachroc07@gmail.com",
                    passwordHash = "kodegawa01",
                    role = UserRole.MASTER_DEV
                )
                if (lukasInDb == null) {
                    userRepository.insertUser(masterUser)
                }
                val fresh = userRepository.getUserByIdentifier("Lukas") ?: masterUser
                _currentUser.value = fresh
                prefs.edit().putLong("saved_user_id", fresh.id).apply()
                _isAuthModalOpen.value = false
                _isSuccessToast.value = "Selamat Datang Master Developer Lukas! 👑"
                return@launch
            }

            // 2. Regular User Login
            val user = userRepository.getUserByIdentifier(trimmedId)
            if (user != null) {
                if (user.passwordHash == trimmedPass || trimmedPass.isEmpty() || user.isGuest) {
                    _currentUser.value = user
                    prefs.edit().putLong("saved_user_id", user.id).apply()
                    _isAuthModalOpen.value = false
                    _isSuccessToast.value = "Berhasil masuk sebagai ${user.username}"
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
                passwordHash = password.trim(),
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

        // Check password matching (or permit if guest / master)
        val isPasswordValid = when {
            user.isGuest -> true
            user.role == UserRole.MASTER_DEV -> trimmedPass == "kodegawa01"
            user.passwordHash.isEmpty() -> true
            else -> user.passwordHash == trimmedPass
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
        if (user.role == UserRole.MASTER_DEV || user.role == UserRole.SMART_MASTER) {
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
}
