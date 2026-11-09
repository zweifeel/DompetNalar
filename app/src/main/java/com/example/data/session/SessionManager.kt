package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.UserDao
import com.example.data.model.User
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DEF-03 Stage 3: Canonical session source of truth for FinSmart.
 *
 * Guarantees that:
 * 1. Only one active authenticated identity exists across repositories and ViewModels.
 * 2. Unauthenticated state is represented by `null`, never an arbitrary or fallback ID.
 * 3. Guest sessions obtain a genuine database-persisted ID, never a hardcoded constant.
 */
sealed interface SessionState {
    object Unauthenticated : SessionState
    data class Authenticated(val userId: Long, val user: User) : SessionState
}

object SessionManager {
    const val PREFS_NAME = "finsmart_auth_prefs"
    const val KEY_SAVED_USER_ID = "saved_user_id"

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _activeUserId = MutableStateFlow<Long?>(null)
    val activeUserId: StateFlow<Long?> = _activeUserId.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isStealthMode = MutableStateFlow(false)
    val isStealthMode: StateFlow<Boolean> = _isStealthMode.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun isStealthModeActive(): Boolean = _isStealthMode.value

    fun setStealthMode(enabled: Boolean) {
        _isStealthMode.value = enabled
    }

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun updateState(state: SessionState) {
        _sessionState.value = state
        when (state) {
            is SessionState.Authenticated -> {
                _activeUserId.value = state.userId
                _currentUser.value = state.user
            }
            SessionState.Unauthenticated -> {
                _activeUserId.value = null
                _currentUser.value = null
            }
        }
    }

    fun getActiveUserId(): Long? = _activeUserId.value

    fun requireActiveUserId(): Long {
        val id = _activeUserId.value
        if (id == null || id <= 0L) {
            throw IllegalStateException("Operation requires an active authenticated session.")
        }
        return id
    }

    fun isAuthenticated(): Boolean = _sessionState.value is SessionState.Authenticated

    fun login(user: User) {
        require(user.id > 0L) { "Cannot login with non-persisted user (id must be > 0)." }
        val previousUserId = _activeUserId.value
        if (previousUserId != null && previousUserId != user.id) {
            com.example.data.chatbot.parser.WalletDraftManager.clearAllDrafts()
            com.example.data.chatbot.parser.TransactionDraftManager.clearAllDrafts()
        }
        prefs?.edit()?.putLong(KEY_SAVED_USER_ID, user.id)?.apply()
        updateState(SessionState.Authenticated(user.id, user))
    }

    fun logout() {
        com.example.data.chatbot.parser.WalletDraftManager.clearAllDrafts()
        com.example.data.chatbot.parser.TransactionDraftManager.clearAllDrafts()
        prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
        setStealthMode(false)
        updateState(SessionState.Unauthenticated)
    }

    fun clearSession() {
        com.example.data.chatbot.parser.WalletDraftManager.clearAllDrafts()
        com.example.data.chatbot.parser.TransactionDraftManager.clearAllDrafts()
        prefs?.edit()?.clear()?.apply()
        setStealthMode(false)
        updateState(SessionState.Unauthenticated)
    }

    suspend fun restoreSession(
        userDao: UserDao,
        isDebug: Boolean = com.example.BuildConfig.DEBUG
    ): Boolean {
        val savedId = prefs?.getLong(KEY_SAVED_USER_ID, -1L) ?: -1L
        if (savedId <= 0L) {
            updateState(SessionState.Unauthenticated)
            return false
        }
        val user = userDao.getUserById(savedId)
        if (user == null || user.id <= 0L) {
            // User deleted or invalid in database
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            return false
        }

        // Phase 1G.6: Akun target developer (Lukas / lunachroc07@gmail.com) tidak boleh dipulihkan dalam sesi apa pun
        if (user.username.equals("Lukas", ignoreCase = true) || user.email.equals("lunachroc07@gmail.com", ignoreCase = true)) {
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            return false
        }

        // Pada build Release, sesi tersimpan dengan role MASTER_DEV tidak boleh dipulihkan secara otomatis
        // semata-mata dari database lokal atau SharedPreferences tanpa re-autentikasi resmi.
        if (!isDebug && user.role == UserRole.MASTER_DEV) {
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            return false
        }

        updateState(SessionState.Authenticated(user.id, user))
        return true
    }

    suspend fun startGuestSession(userDao: UserDao): User {
        val guestUser = User(
            username = "Tamu DompetNalar",
            email = "guest_${System.currentTimeMillis()}@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = 0L
        )
        val generatedId = com.example.data.local.DatabaseWriteCoordinator.getInstance().withWritePermit {
            userDao.insertUser(guestUser)
        }
        val persistedGuest = guestUser.copy(id = generatedId)
        login(persistedGuest)
        return persistedGuest
    }

    fun resetForTesting() {
        com.example.data.chatbot.parser.WalletDraftManager.clearAllDrafts()
        com.example.data.chatbot.parser.TransactionDraftManager.clearAllDrafts()
        setStealthMode(false)
        updateState(SessionState.Unauthenticated)
    }

    fun setSessionForTesting(userId: Long, user: User) {
        updateState(SessionState.Authenticated(userId, user))
    }
}
