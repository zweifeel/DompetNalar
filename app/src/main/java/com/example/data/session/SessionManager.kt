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

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
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
        prefs?.edit()?.putLong(KEY_SAVED_USER_ID, user.id)?.apply()
        updateState(SessionState.Authenticated(user.id, user))
    }

    fun logout() {
        prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
        updateState(SessionState.Unauthenticated)
    }

    fun clearSession() {
        prefs?.edit()?.clear()?.apply()
        updateState(SessionState.Unauthenticated)
    }

    suspend fun restoreSession(userDao: UserDao): Boolean {
        val savedId = prefs?.getLong(KEY_SAVED_USER_ID, -1L) ?: -1L
        if (savedId <= 0L) {
            updateState(SessionState.Unauthenticated)
            return false
        }
        val user = userDao.getUserById(savedId)
        return if (user != null && user.id > 0L) {
            updateState(SessionState.Authenticated(user.id, user))
            true
        } else {
            // User deleted or invalid in database
            prefs?.edit()?.remove(KEY_SAVED_USER_ID)?.apply()
            updateState(SessionState.Unauthenticated)
            false
        }
    }

    suspend fun startGuestSession(userDao: UserDao): User {
        val guestUser = User(
            username = "Tamu FinSmart",
            email = "guest_${System.currentTimeMillis()}@finsmart.local",
            role = UserRole.FREE_MEMBER,
            isGuest = true,
            trialStartDate = System.currentTimeMillis()
        )
        val generatedId = userDao.insertUser(guestUser)
        val persistedGuest = guestUser.copy(id = generatedId)
        login(persistedGuest)
        return persistedGuest
    }

    fun resetForTesting() {
        updateState(SessionState.Unauthenticated)
    }

    fun setSessionForTesting(userId: Long, user: User) {
        updateState(SessionState.Authenticated(userId, user))
    }
}
