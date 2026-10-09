package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FinSmartDatabase
import com.example.data.model.FeedbackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val feedbackDao = FinSmartDatabase.getDatabase(application, viewModelScope).feedbackDao()

    val allFeedbacks: StateFlow<List<FeedbackItem>> = feedbackDao.getAllFeedbacks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submissionSuccess = MutableStateFlow<String?>(null)
    val submissionSuccess: StateFlow<String?> = _submissionSuccess.asStateFlow()

    init {
        // Preload sample feedback for developer view if empty
        viewModelScope.launch {
            allFeedbacks.collect { list ->
                if (list.isEmpty()) {
                    feedbackDao.insertFeedback(
                        FeedbackItem(
                            userName = "Lukas",
                            userEmail = "lunachroc07@gmail.com",
                            rating = 5,
                            category = "Saran Fitur",
                            message = "Integrasi Google Drive dan Auto-Scan OCR AI berjalan sangat mulus di versi 1.0.4!",
                            timestamp = System.currentTimeMillis() - 3600000L
                        )
                    )
                    feedbackDao.insertFeedback(
                        FeedbackItem(
                            userName = "Siti Rahma",
                            userEmail = "siti.rahma@example.com",
                            rating = 5,
                            category = "Desain / UI",
                            message = "Desain kartu mutasi sekarang sangat rapi, tidak bertumpuk dengan status bar.",
                            timestamp = System.currentTimeMillis() - 7200000L
                        )
                    )
                    feedbackDao.insertFeedback(
                        FeedbackItem(
                            userName = "Budi Santoso",
                            userEmail = "budi.santoso@example.com",
                            rating = 4,
                            category = "Bug / Kendala",
                            message = "Mohon pastikan format ekspor laporan menyertakan logo dompet dan format IDR standar.",
                            timestamp = System.currentTimeMillis() - 14400000L
                        )
                    )
                }
            }
        }
    }

    fun submitFeedback(
        userName: String,
        userEmail: String,
        rating: Int,
        category: String,
        message: String,
        onSuccess: () -> Unit
    ) {
        if (message.isBlank()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val activeUserId = com.example.data.session.SessionManager.getActiveUserId()
                val item = FeedbackItem(
                    userName = userName.ifBlank { "Pengguna FinSmart" },
                    userEmail = userEmail.ifBlank { "user@finsmart.local" },
                    rating = rating.coerceIn(1, 5),
                    category = category,
                    message = message.trim(),
                    timestamp = System.currentTimeMillis(),
                    userId = activeUserId
                )
                feedbackDao.insertFeedback(item)
                _submissionSuccess.value = "Terima kasih! Masukan Anda berhasil dikirim ke pengembang."
                onSuccess()
            } catch (e: Exception) {
                _submissionSuccess.value = "Gagal mengirim masukan: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun deleteFeedback(id: String) {
        viewModelScope.launch {
            feedbackDao.deleteFeedbackByAdmin(id)
        }
    }

    fun clearMessage() {
        _submissionSuccess.value = null
    }
}
