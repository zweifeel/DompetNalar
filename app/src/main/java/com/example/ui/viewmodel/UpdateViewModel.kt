package com.example.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.util.AppVersionMetadata
import com.example.util.GoogleDriveUpdateManager
import com.example.util.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel : ViewModel() {

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _updateMetadata = MutableStateFlow<AppVersionMetadata?>(null)
    val updateMetadata: StateFlow<AppVersionMetadata?> = _updateMetadata.asStateFlow()

    private val _isUpdateModalVisible = MutableStateFlow(false)
    val isUpdateModalVisible: StateFlow<Boolean> = _isUpdateModalVisible.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun checkForUpdates(context: Context, showFeedbackIfLatest: Boolean = true) {
        viewModelScope.launch {
            _isChecking.value = true
            val result = GoogleDriveUpdateManager.checkVersionFromDrive(context)
            _isChecking.value = false

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateMetadata.value = result.metadata
                    _isUpdateModalVisible.value = true
                }
                is UpdateCheckResult.UpToDate -> {
                    _updateMetadata.value = result.metadata
                    if (showFeedbackIfLatest) {
                        Toast.makeText(
                            context,
                            "FinSmart Anda sudah menggunakan versi terbaru (v${AppConfig.APP_VERSION_NAME})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is UpdateCheckResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun downloadAndInstall(context: Context) {
        val metadata = _updateMetadata.value ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f

            val (success, message) = GoogleDriveUpdateManager.downloadAndInstallApk(
                context = context,
                metadata = metadata,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )

            _isDownloading.value = false
            _statusMessage.value = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            if (success) {
                _isUpdateModalVisible.value = false
            }
        }
    }

    fun dismissUpdateModal() {
        _isUpdateModalVisible.value = false
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
