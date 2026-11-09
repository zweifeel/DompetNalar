package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FinSmartDatabase
import com.example.data.model.FeedbackItem
import com.example.config.AppConfig
import com.example.config.DeveloperAccountConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FinSmartDatabase.getDatabase(application, viewModelScope)
    private val feedbackDao = database.feedbackDao()

    val allFeedbacks: StateFlow<List<FeedbackItem>> = feedbackDao.getAllFeedbacks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unsyncedFeedbacks: StateFlow<List<FeedbackItem>> = feedbackDao.getUnsyncedFeedbacks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRetryingSync = MutableStateFlow(false)
    val isRetryingSync: StateFlow<Boolean> = _isRetryingSync.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submissionSuccess = MutableStateFlow<String?>(null)
    val submissionSuccess: StateFlow<String?> = _submissionSuccess.asStateFlow()

    private val _syncedCloudIds = MutableStateFlow<Set<String>>(emptySet())
    val syncedCloudIds: StateFlow<Set<String>> = _syncedCloudIds.asStateFlow()

    private val _cloudFeedbacks = MutableStateFlow<List<FeedbackItem>>(emptyList())
    val cloudFeedbacks: StateFlow<List<FeedbackItem>> = _cloudFeedbacks.asStateFlow()

    private val _cloudFetchStatus = MutableStateFlow<String?>(null)
    val cloudFetchStatus: StateFlow<String?> = _cloudFetchStatus.asStateFlow()

    private val _isCloudLoading = MutableStateFlow(false)
    val isCloudLoading: StateFlow<Boolean> = _isCloudLoading.asStateFlow()

    private val _firebaseUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
    val firebaseUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = _firebaseUser.asStateFlow()

    private val _isFirebaseAuthActive = MutableStateFlow(false)
    val isFirebaseAuthActive: StateFlow<Boolean> = _isFirebaseAuthActive.asStateFlow()

    private val _currentFirebaseAuthEmail = MutableStateFlow<String?>(null)
    val currentFirebaseAuthEmail: StateFlow<String?> = _currentFirebaseAuthEmail.asStateFlow()

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    companion object {
        private const val TAG = "FeedbackViewModel"
        const val MAX_MESSAGE_LENGTH = 1000
        val ALLOWED_CATEGORIES = listOf("Bug / Kendala", "Saran Fitur", "Desain / UI", "Lainnya")
    }

    init {
        // Safe check for FirebaseApp and register AuthStateListener for reactive UI updates
        try {
            if (FirebaseApp.getApps(application).isNotEmpty()) {
                val auth = FirebaseAuth.getInstance()
                val current = auth.currentUser
                _firebaseUser.value = current
                _isFirebaseAuthActive.value = current != null
                _currentFirebaseAuthEmail.value = current?.email

                val listener = FirebaseAuth.AuthStateListener { fbAuth ->
                    val user = fbAuth.currentUser
                    val previousUid = _firebaseUser.value?.uid
                    _firebaseUser.value = user
                    val isActive = user != null
                    _isFirebaseAuthActive.value = isActive
                    _currentFirebaseAuthEmail.value = user?.email
                    Log.d(TAG, "FirebaseAuth state change: user=${user?.email}, isActive=$isActive")

                    // Automatically fetch cloud feedbacks and retry pending sync if user transitioned to signed in
                    if (isActive && previousUid != user?.uid) {
                        fetchCloudFeedbacks()
                        retrySyncPendingFeedbacks()
                    }
                }
                authStateListener = listener
                auth.addAuthStateListener(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth state listener initialization skipped: ${e.message}")
        }

    }

    override fun onCleared() {
        super.onCleared()
        try {
            authStateListener?.let { listener ->
                FirebaseAuth.getInstance().removeAuthStateListener(listener)
            }
        } catch (_: Exception) {}
    }

    @androidx.annotation.VisibleForTesting
    fun setFirebaseUserForTesting(email: String?, uid: String? = "test-uid") {
        val isActive = !email.isNullOrBlank()
        _isFirebaseAuthActive.value = isActive
        _currentFirebaseAuthEmail.value = email
    }

    fun fetchCloudFeedbacks() {
        val isFirebaseReady = try {
            FirebaseApp.getApps(getApplication<Application>()).isNotEmpty()
        } catch (e: Exception) {
            false
        }
        if (!isFirebaseReady) {
            _cloudFetchStatus.value = "FirebaseApp belum terkonfigurasi (google-services.json belum dimuat)."
            return
        }

        val fbAuthUser = _firebaseUser.value ?: try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fbAuthUser == null) {
            _cloudFetchStatus.value = "Sesi Lokal Aktif (Belum Ada Token Firebase Auth). Masuk dengan Akun Google Developer (${DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS.joinToString(", ")}) untuk mengakses Firestore Cloud."
            return
        }

        viewModelScope.launch {
            _isCloudLoading.value = true
            _cloudFetchStatus.value = null
            try {
                withContext(Dispatchers.IO) {
                    val firestore = FirebaseFirestore.getInstance()
                    val snapshot = withTimeout(15000L) {
                        try {
                            firestore.collection("feedbacks")
                                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                .limit(50)
                                .get()
                                .awaitTask()
                        } catch (e: Exception) {
                            val emsg = e.message ?: ""
                            if (emsg.contains("index", ignoreCase = true) || emsg.contains("FAILED_PRECONDITION", ignoreCase = true)) {
                                Log.w(TAG, "OrderBy createdAt query failed ($emsg), falling back to un-ordered query...")
                                firestore.collection("feedbacks")
                                    .limit(50)
                                    .get()
                                    .awaitTask()
                            } else {
                                throw e
                            }
                        }
                    }

                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            val feedbackId = doc.getString("feedbackId") ?: doc.id
                            val userName = doc.getString("userName") ?: "Pengguna DompetNalar"
                            val userEmail = doc.getString("userEmail") ?: ""
                            val rating = doc.getLong("rating")?.toInt() ?: 5
                            val category = doc.getString("category") ?: "Lainnya"
                            val message = doc.getString("message") ?: ""
                            val createdAtTs = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                            val userId = doc.getLong("userId")
                            FeedbackItem(
                                id = feedbackId,
                                userName = userName,
                                userEmail = userEmail,
                                rating = rating,
                                category = category,
                                message = message,
                                timestamp = createdAtTs,
                                userId = userId
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val sortedItems = items.sortedByDescending { it.timestamp }
                    _cloudFeedbacks.value = sortedItems
                    _syncedCloudIds.value = _syncedCloudIds.value + sortedItems.map { it.id }.toSet()
                    if (sortedItems.isEmpty()) {
                        _cloudFetchStatus.value = "Cloud Firestore terhubung (Akun: ${fbAuthUser.email}). Belum ada feedback dari pengguna di cloud."
                    } else {
                        _cloudFetchStatus.value = "Berhasil memuat ${sortedItems.size} feedback dari Cloud Firestore (Akun: ${fbAuthUser.email})."
                    }
                    Log.i(TAG, "Berhasil memuat ${items.size} feedback dari Firestore.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memuat feedback dari Firestore: ${e.message}", e)
                val msg = e.localizedMessage ?: e.message ?: "Kendala jaringan"
                val isPermissionDenied = msg.contains("PERMISSION_DENIED", ignoreCase = true)
                val isUnauthenticated = msg.contains("UNAUTHENTICATED", ignoreCase = true)
                val isNetwork = msg.contains("UNAVAILABLE", ignoreCase = true) ||
                        msg.contains("network", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("offline", ignoreCase = true)

                if (isPermissionDenied) {
                    val authEmail = _currentFirebaseAuthEmail.value ?: try { FirebaseAuth.getInstance().currentUser?.email } catch (_: Exception) { null }
                    if (authEmail.isNullOrBlank()) {
                        _cloudFetchStatus.value = "Akses Cloud Ditolak (PERMISSION_DENIED): Belum ada sesi Firebase Auth aktif. Masuk dengan Akun Google Developer (${DeveloperAccountConfig.TRUSTED_DEVELOPER_EMAILS.joinToString(", ")})."
                    } else if (DeveloperAccountConfig.isTrustedDeveloperEmail(authEmail)) {
                        _cloudFetchStatus.value = "Akses Cloud Ditolak (PERMISSION_DENIED): Akun terverifikasi '$authEmail' ditolak oleh aturan Firestore di Cloud. Pastikan file firestore.rules lokal sudah dipublikasikan ke Firebase Console (Project: finsmart-47fdd) dengan menambahkan '$authEmail' ke fungsi isAdmin()."
                    } else {
                        _cloudFetchStatus.value = "Akses Cloud Ditolak: Akun '$authEmail' bukan administrator yang berwenang."
                    }
                } else if (isUnauthenticated) {
                    _cloudFetchStatus.value = "Sesi Tidak Terotentikasi (UNAUTHENTICATED): Sesi Firebase Auth tidak valid atau telah berakhir. Silakan hubungkan ulang akun Google."
                } else if (isNetwork) {
                    _cloudFetchStatus.value = "Gagal terhubung ke Cloud Firestore: Periksa koneksi internet perangkat Anda."
                } else {
                    _cloudFetchStatus.value = "Gagal memuat feedback cloud: $msg"
                }
            } finally {
                _isCloudLoading.value = false
            }
        }
    }

    fun submitFeedback(
        userName: String,
        userEmail: String,
        rating: Int,
        category: String,
        message: String,
        onResult: (isCloudSynced: Boolean) -> Unit = {},
        onError: ((String) -> Unit)? = null
    ) {
        val cleanMessage = message.trim().take(MAX_MESSAGE_LENGTH)
        if (cleanMessage.isBlank()) return

        val validatedCategory = if (category in ALLOWED_CATEGORIES) category else "Lainnya"
        val validatedRating = rating.coerceIn(1, 5)

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val activeUserId = com.example.data.session.SessionManager.getActiveUserId()
                // Validasi Foreign Key ke database Room untuk mencegah constraint crash
                val validUserId = if (activeUserId != null && activeUserId > 0L) {
                    try {
                        val user = FinSmartDatabase.getDatabase(getApplication(), viewModelScope).userDao().getUserById(activeUserId)
                        if (user != null) activeUserId else null
                    } catch (e: Exception) {
                        null
                    }
                } else null

                val item = FeedbackItem(
                    userName = userName.ifBlank { "Pengguna DompetNalar" },
                    userEmail = userEmail.ifBlank { "user@finsmart.local" },
                    rating = validatedRating,
                    category = validatedCategory,
                    message = cleanMessage,
                    timestamp = System.currentTimeMillis(),
                    userId = validUserId,
                    isSynced = false
                )

                // 1. Always persist locally for offline developer access and history
                database.writeCoordinator.withWritePermit {
                    feedbackDao.insertFeedback(item)
                }

                // 2. Submit to Firebase Cloud Firestore if initialized
                val isFirebaseReady = try {
                    FirebaseApp.getApps(getApplication<Application>()).isNotEmpty()
                } catch (e: Exception) {
                    false
                }

                var isCloudSynced = false
                if (isFirebaseReady) {
                    withContext(Dispatchers.IO) {
                        try {
                            val firestore = FirebaseFirestore.getInstance()
                            val cloudPayload = hashMapOf<String, Any>(
                                "feedbackId" to item.id,
                                "userId" to (item.userId ?: 0L),
                                "userName" to item.userName,
                                "userEmail" to item.userEmail,
                                "rating" to item.rating,
                                "category" to item.category,
                                "message" to item.message,
                                "appVersion" to AppConfig.APP_VERSION_NAME,
                                "createdAt" to FieldValue.serverTimestamp()
                            )

                            withTimeout(15000L) {
                                firestore.collection("feedbacks")
                                    .document(item.id)
                                    .set(cloudPayload)
                                    .awaitTask()
                            }
                            Log.i(TAG, "Feedback ${item.id} successfully written to Firestore.")
                            database.writeCoordinator.withWritePermit { feedbackDao.markFeedbackAsSynced(item.id) }
                            _syncedCloudIds.value = _syncedCloudIds.value + item.id
                            _submissionSuccess.value = "Feedback berhasil dikirim dan tersinkronisasi."
                            isCloudSynced = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Gagal sinkronisasi ke Firestore: ${e.message}", e)
                            _submissionSuccess.value = "Feedback tersimpan di perangkat, tetapi belum berhasil dikirim ke server. Silakan coba sinkronisasi kembali."
                            isCloudSynced = false
                        }
                    }
                } else {
                    Log.i(TAG, "FirebaseApp belum terkonfigurasi. Masukan tersimpan di lokal.")
                    _submissionSuccess.value = "Feedback tersimpan di perangkat, tetapi belum berhasil dikirim ke server. Silakan coba sinkronisasi kembali."
                    isCloudSynced = false
                }

                onResult(isCloudSynced)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memproses feedback", e)
                val errMsg = "Feedback gagal disimpan. Silakan coba lagi."
                _submissionSuccess.value = errMsg
                onError?.invoke(errMsg)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /**
     * Backward-compatible overload for callers that pass a simple unit callback.
     * Invokes onSuccess ONLY when cloud sync succeeded, preventing false success confirmations.
     */
    fun submitFeedback(
        userName: String,
        userEmail: String,
        rating: Int,
        category: String,
        message: String,
        onSuccess: () -> Unit
    ) {
        submitFeedback(
            userName = userName,
            userEmail = userEmail,
            rating = rating,
            category = category,
            message = message,
            onResult = { isSynced ->
                if (isSynced) {
                    onSuccess()
                }
            },
            onError = null
        )
    }

    fun retrySyncPendingFeedbacks(onComplete: ((synced: Int, failed: Int) -> Unit)? = null) {
        val isFirebaseReady = try {
            FirebaseApp.getApps(getApplication<Application>()).isNotEmpty()
        } catch (e: Exception) {
            false
        }
        if (!isFirebaseReady) {
            onComplete?.invoke(0, 0)
            return
        }

        viewModelScope.launch {
            _isRetryingSync.value = true
            var syncedCount = 0
            var failedCount = 0
            try {
                val pendingList = withContext(Dispatchers.IO) {
                    feedbackDao.getUnsyncedFeedbacksList()
                }
                if (pendingList.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val firestore = FirebaseFirestore.getInstance()
                        for (item in pendingList) {
                            try {
                                val cloudPayload = hashMapOf<String, Any>(
                                    "feedbackId" to item.id,
                                    "userId" to (item.userId ?: 0L),
                                    "userName" to item.userName,
                                    "userEmail" to item.userEmail,
                                    "rating" to item.rating,
                                    "category" to item.category,
                                    "message" to item.message,
                                    "appVersion" to AppConfig.APP_VERSION_NAME,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )

                                withTimeout(15000L) {
                                    firestore.collection("feedbacks")
                                        .document(item.id)
                                        .set(cloudPayload)
                                        .awaitTask()
                                }
                                database.writeCoordinator.withWritePermit { feedbackDao.markFeedbackAsSynced(item.id) }
                                _syncedCloudIds.value = _syncedCloudIds.value + item.id
                                syncedCount++
                                Log.i(TAG, "Retry sync for feedback ${item.id} succeeded.")
                            } catch (e: Exception) {
                                failedCount++
                                Log.w(TAG, "Retry sync for feedback ${item.id} failed: ${e.message}")
                            }
                        }
                    }
                }
                onComplete?.invoke(syncedCount, failedCount)
            } catch (e: Exception) {
                Log.e(TAG, "Error during retrySyncPendingFeedbacks", e)
                onComplete?.invoke(syncedCount, failedCount)
            } finally {
                _isRetryingSync.value = false
            }
        }
    }

    fun deleteFeedback(id: String) {
        viewModelScope.launch {
            database.writeCoordinator.withWritePermit {
                feedbackDao.deleteFeedbackByAdmin(id)
            }
            _cloudFeedbacks.value = _cloudFeedbacks.value.filter { it.id != id }
            _syncedCloudIds.value = _syncedCloudIds.value - id
        }
    }

    fun clearMessage() {
        _submissionSuccess.value = null
    }
}

/**
 * Extension helper to await Google Play / Firebase Task as a suspendable coroutine.
 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { exception -> cont.resumeWithException(exception) }
        addOnCanceledListener { cont.cancel() }
    }
