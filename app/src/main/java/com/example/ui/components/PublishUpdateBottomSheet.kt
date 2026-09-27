package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.MasterDevViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishUpdateBottomSheet(
    masterDevViewModel: MasterDevViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val targetCode by masterDevViewModel.targetVersionCode.collectAsState()
    val targetName by masterDevViewModel.targetVersionName.collectAsState()
    val changelog by masterDevViewModel.changelogText.collectAsState()
    val selectedApkUri by masterDevViewModel.selectedApkUri.collectAsState()
    val selectedApkFileName by masterDevViewModel.selectedApkFileName.collectAsState()
    val isPublishing by masterDevViewModel.isPublishing.collectAsState()
    val publishProgress by masterDevViewModel.publishProgress.collectAsState()

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                masterDevViewModel.setSelectedApkUri(uri)
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = {
            if (!isPublishing) onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("publish_update_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFD97706).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Publish Application Update",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "MasterDev Google Drive Release Pipeline",
                            fontSize = 11.sp,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!isPublishing) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subfolder Notice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = FinSmartPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Target Google Drive Folder:\nFinSmart/App Updates/ (version.json & FinSmart_v$targetName.apk)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Version Name & Code Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = targetName,
                    onValueChange = { masterDevViewModel.setTargetVersionName(it) },
                    label = { Text("Target Version Name") },
                    placeholder = { Text("Contoh: 1.0.4") },
                    singleLine = true,
                    enabled = !isPublishing,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("input_target_version_name")
                )

                OutlinedTextField(
                    value = targetCode.toString(),
                    onValueChange = {
                        val num = it.toIntOrNull() ?: 1
                        masterDevViewModel.setTargetVersionCode(num)
                    },
                    label = { Text("Version Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isPublishing,
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("input_target_version_code")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-line Changelog Text Area
            OutlinedTextField(
                value = changelog,
                onValueChange = { masterDevViewModel.setChangelogText(it) },
                label = { Text("Catatan Rilis / Changelog (1 baris = 1 poin)") },
                placeholder = { Text("• Fitur baru...\n• Perbaikan bug...") },
                minLines = 4,
                maxLines = 6,
                enabled = !isPublishing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_changelog_text")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // APK File Selector Button
            OutlinedButton(
                onClick = {
                    apkPickerLauncher.launch("*/*")
                },
                enabled = !isPublishing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_pick_apk_file"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (selectedApkUri != null) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (selectedApkUri != null) IncomeGreen else FinSmartPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedApkUri != null) {
                        "APK Dipilih: ${selectedApkFileName ?: "File Siap"}"
                    } else {
                        "Pilih File APK Release (.apk dari memori HP)"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicator during Transmission
            if (isPublishing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mengunggah ke Google Drive...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FinSmartPrimary
                        )
                        Text(
                            text = "${(publishProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FinSmartPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { publishProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFFD97706)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Publish Button
            Button(
                onClick = {
                    masterDevViewModel.publishUpdate(context) {
                        onDismiss()
                    }
                },
                enabled = !isPublishing && targetName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_publish_to_drive"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706)
                )
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPublishing) "Memproses Publikasi..." else "Publikasikan Pembaruan ke Google Drive",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
