package com.example.ui.viewmodel

import com.example.util.AppStrings
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.util.AppVersionMetadata
import com.example.util.DriveUpdateRepository
import com.example.util.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel : ViewModel() {

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

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

    fun checkForUpdates(context: Context, metadataUrl: String? = null, showFeedbackIfLatest: Boolean = true) {
        viewModelScope.launch {
            _isChecking.value = true
            _updateStatus.value = UpdateStatus.Checking

            val status = DriveUpdateRepository.checkForUpdates(context, metadataUrl)
            _updateStatus.value = status
            _isChecking.value = false

            when (status) {
                is UpdateStatus.UpdateAvailable -> {
                    _updateMetadata.value = status.metadata
                    _isUpdateModalVisible.value = true
                }
                is UpdateStatus.UpToDate -> {
                    _updateMetadata.value = status.metadata
                    if (showFeedbackIfLatest) {
                        Toast.makeText(
                            context,
                            AppStrings.t(
                                "DompetNalar sudah versi terbaru (v${status.metadata.version_name})",
                                "DompetNalar is already on the latest version (v${status.metadata.version_name})"
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is UpdateStatus.Error -> {
                    if (showFeedbackIfLatest) {
                        Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                    }
                }
                else -> {}
            }
        }
    }

    fun downloadAndInstall(context: Context) {
        val metadata = _updateMetadata.value ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            _updateStatus.value = UpdateStatus.Downloading(0f)

            val (success, message) = DriveUpdateRepository.downloadAndInstallApk(
                context = context,
                apkUrl = metadata.download_url,
                versionName = metadata.version_name,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                    _updateStatus.value = UpdateStatus.Downloading(progress)
                }
            )

            _isDownloading.value = false
            _statusMessage.value = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            if (success) {
                _isUpdateModalVisible.value = false
            } else {
                _updateStatus.value = UpdateStatus.Error(message)
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
