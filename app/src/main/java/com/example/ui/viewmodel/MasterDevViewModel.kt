package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.GoogleDrivePublisherEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MasterDevViewModel : ViewModel() {

    private val _targetVersionCode = MutableStateFlow(4)
    val targetVersionCode: StateFlow<Int> = _targetVersionCode.asStateFlow()

    private val _targetVersionName = MutableStateFlow("1.0.4")
    val targetVersionName: StateFlow<String> = _targetVersionName.asStateFlow()

    private val _changelogText = MutableStateFlow(
        "• Pembaruan performa engine sinkronisasi Google Drive\n" +
        "• Peningkatan akurasi AI Date Recognition pada transaksi\n" +
        "• Dukungan multi-currency & format angka lokal baru\n" +
        "• Pilihan tema tampilan & background kustom galeri"
    )
    val changelogText: StateFlow<String> = _changelogText.asStateFlow()

    private val _selectedApkUri = MutableStateFlow<Uri?>(null)
    val selectedApkUri: StateFlow<Uri?> = _selectedApkUri.asStateFlow()

    private val _selectedApkFileName = MutableStateFlow<String?>(null)
    val selectedApkFileName: StateFlow<String?> = _selectedApkFileName.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _publishProgress = MutableStateFlow(0f)
    val publishProgress: StateFlow<Float> = _publishProgress.asStateFlow()

    private val _isPublishModalOpen = MutableStateFlow(false)
    val isPublishModalOpen: StateFlow<Boolean> = _isPublishModalOpen.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun openPublishModal() {
        _isPublishModalOpen.value = true
    }

    fun dismissPublishModal() {
        if (!_isPublishing.value) {
            _isPublishModalOpen.value = false
        }
    }

    fun setTargetVersionCode(code: Int) {
        _targetVersionCode.value = code
    }

    fun setTargetVersionName(name: String) {
        _targetVersionName.value = name
    }

    fun setChangelogText(text: String) {
        _changelogText.value = text
    }

    fun setSelectedApkUri(uri: Uri?, fileName: String? = null) {
        _selectedApkUri.value = uri
        _selectedApkFileName.value = fileName ?: uri?.lastPathSegment
    }

    fun publishUpdate(context: Context, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishProgress.value = 0f

            val changelogLines = _changelogText.value
                .split("\n")
                .map { it.trim().removePrefix("•").removePrefix("-").trim() }
                .filter { it.isNotBlank() }

            val (success, message) = GoogleDrivePublisherEngine.publishApplicationUpdate(
                context = context,
                targetVersionCode = _targetVersionCode.value,
                targetVersionName = _targetVersionName.value,
                changelog = changelogLines,
                apkUri = _selectedApkUri.value,
                onProgress = { progress ->
                    _publishProgress.value = progress
                }
            )

            _isPublishing.value = false
            _feedbackMessage.value = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            if (success) {
                _isPublishModalOpen.value = false
                onSuccess()
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }
}
