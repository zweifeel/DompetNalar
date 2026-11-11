package com.example.ui.screens
import com.example.util.AppStrings

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppConfig
import com.example.BuildConfig
import com.example.data.model.AccountMode
import com.example.data.model.AppIconEdition
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.session.SessionManager
import com.example.ui.components.ExportFormatManagementDialog
import com.example.ui.components.PublishUpdateBottomSheet
import com.example.ui.components.UpdateAvailableBottomSheet
import com.example.util.InstallSourceHelper
import androidx.compose.material.icons.filled.Tune
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.viewmodel.MasterDevViewModel
import com.example.ui.viewmodel.UpdateViewModel
import com.example.util.AppAccentColor
import com.example.util.AppCurrency
import com.example.util.AppIconManager
import com.example.util.AppLanguage
import com.example.util.AppThemeMode
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper
import com.example.util.ThemeEngine
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    currentUser: User?,
    accountMode: AccountMode,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onToggleAccountMode: () -> Unit,
    onOpenMasterDevPanel: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenAuthModal: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
    onOpenProminentDisclosure: () -> Unit,
    onTogglePlayStoreMode: () -> Unit,
    onLogout: () -> Unit,
    onOpenExportReport: () -> Unit,
    isDriveConnected: Boolean,
    driveEmail: String,
    driveLastBackup: String,
    driveAutoSync: Boolean,
    driveAutoBackupInterval: Long = 24L,
    onConnectDrive: (String) -> Unit,
    onDisconnectDrive: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onUpdateAutoBackupInterval: (Long) -> Unit = {},
    onTriggerBackup: () -> Unit,
    onTriggerRestore: () -> Unit,
    onTriggerPortableBackup: (String, (Boolean, String, File?) -> Unit) -> Unit = { _, _ -> },
    onTriggerPortableRestore: (String?, String, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onOpenGamificationHub: () -> Unit = {},
    onOpenPersonalityQuiz: () -> Unit = {},
    onOpenDuressPinModal: () -> Unit = {},
    onOpenDebtPlanner: () -> Unit = {},
    onOpenAssetTracker: () -> Unit = {},
    onOpenReceiptGenerator: () -> Unit = {},
    profilePhotoUri: String? = null,
    onAvatarClick: () -> Unit = {},
    dailyStreak: Int = 1,
    onOpenFeedback: () -> Unit = {},
    onOpenDevFeedbackViewer: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    shakeGestureAction: String = "OFF",
    onUpdateShakeGestureAction: (String) -> Unit = {},
    updateViewModel: UpdateViewModel = viewModel(),
    masterDevViewModel: MasterDevViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val role = currentUser?.role ?: UserRole.FREE_MEMBER

    val selectedLanguage by LocaleHelper.currentLanguage.collectAsState()
    val selectedCurrency by CurrencyFormatter.selectedCurrency.collectAsState()
    val currentThemeMode by ThemeEngine.currentThemeMode.collectAsState()
    val currentAccentColor by ThemeEngine.currentAccentColor.collectAsState()
    val customWallpaperUri by ThemeEngine.customWallpaperUri.collectAsState()
    val customWallpaperOpacity by ThemeEngine.customWallpaperOpacity.collectAsState()
    val activeIconEdition by AppIconManager.currentIconEdition.collectAsState()

    // App Update & MasterDev State
    val isCheckingUpdate by updateViewModel.isChecking.collectAsState()
    val isUpdateModalVisible by updateViewModel.isUpdateModalVisible.collectAsState()
    val updateMetadata by updateViewModel.updateMetadata.collectAsState()
    val isDownloadingUpdate by updateViewModel.isDownloading.collectAsState()
    val downloadUpdateProgress by updateViewModel.downloadProgress.collectAsState()
    val isPublishModalOpen by masterDevViewModel.isPublishModalOpen.collectAsState()

    var isLanguageDialogOpen by remember { mutableStateOf(false) }
    var isCurrencyDialogOpen by remember { mutableStateOf(false) }
    var isThemeDialogOpen by remember { mutableStateOf(false) }
    var isAccentDialogOpen by remember { mutableStateOf(false) }
    var isWallpaperDialogOpen by remember { mutableStateOf(false) }
    var isAppIconDialogOpen by remember { mutableStateOf(false) }
    var isShakeGestureDialogOpen by remember { mutableStateOf(false) }
    var isManageExportFormatsOpen by remember { mutableStateOf(false) }
    val isGooglePlayInstalled = remember { InstallSourceHelper.isInstalledFromGooglePlay(context) }

    var tempOpacity by remember(customWallpaperOpacity) { mutableStateOf(customWallpaperOpacity) }
    var isLogoutConfirmDialogOpen by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                    // Safe ignore jika content provider tidak mendukung persistable URI
                }
                ThemeEngine.setCustomWallpaperForUser(context, currentUser, uri.toString(), customWallpaperOpacity)
                Toast.makeText(context, if (LocaleHelper.isEnglish()) "Background successfully applied!" else "Latar belakang berhasil diterapkan!", Toast.LENGTH_SHORT).show()
                isWallpaperDialogOpen = true
            }
        }
    )

    var isBackupChoiceDialogOpen by remember { mutableStateOf(false) }
    var isPortableBackupDialogOpen by remember { mutableStateOf(false) }
    var isRestoreChoiceDialogOpen by remember { mutableStateOf(false) }
    var isPortableRestoreDialogOpen by remember { mutableStateOf(false) }

    var portableBackupPassword by remember { mutableStateOf("") }
    var portableBackupConfirmPassword by remember { mutableStateOf("") }
    var portableBackupPasswordVisible by remember { mutableStateOf(false) }
    var portableBackupConfirmVisible by remember { mutableStateOf(false) }
    var portableBackupError by remember { mutableStateOf<String?>(null) }
    var isCreatingPortableBackup by remember { mutableStateOf(false) }

    var portableRestorePassword by remember { mutableStateOf("") }
    var portableRestorePasswordVisible by remember { mutableStateOf(false) }
    var portableRestoreError by remember { mutableStateOf<String?>(null) }
    var isRestoringPortableBackup by remember { mutableStateOf(false) }
    var selectedRestoreFileName by remember { mutableStateOf<String?>(null) }
    var selectedRestoreFileContent by remember { mutableStateOf<String?>(null) }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                selectedRestoreFileName = uri.lastPathSegment ?: "backup.json"
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    selectedRestoreFileContent = content
                    portableRestoreError = null
                } catch (e: Exception) {
                    portableRestoreError = com.example.util.AppStrings.t("Gagal membaca file: ${e.message}", "Failed to read file: ${e.message}")
                }
            }
        }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp
        )
    ) {
        // Header
        item {
            Column {
                Text(
                    text = AppStrings.t("Pengaturan", "Settings"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = AppStrings.t("Kelola profil, keamanan, dan sinkronisasi cloud DompetNalar", "Manage DompetNalar profile, security, and cloud sync"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // CARD 1: Profile & Membership
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Smart Member Entitlement Status & Active Reactive Counter (Shared across profile row & status card)
                    val currentTime by produceState(initialValue = System.currentTimeMillis()) {
                        while (true) {
                            delay(1000L)
                            value = System.currentTimeMillis()
                        }
                    }
                    val entitlement = remember(currentUser, currentTime) {
                        com.example.util.EntitlementManager.evaluate(currentUser, now = currentTime)
                    }
                    val statusInfo = remember(entitlement, currentUser, currentTime) {
                        when {
                            currentUser?.role == UserRole.MASTER_DEV -> {
                                SmartMemberStatusUI(
                                    title = "Smart Member — Master Developer 👑",
                                    detail = "Akses Penuh Seumur Hidup (Permanen)",
                                    badge = "Permanen",
                                    color = Color(0xFFD97706),
                                    bgColor = Color(0xFFD97706).copy(alpha = 0.12f),
                                    icon = Icons.Default.AdminPanelSettings
                                )
                            }
                            currentUser?.isGuest == true -> {
                                SmartMemberStatusUI(
                                    title = "Smart Member — Tamu",
                                    detail = "Mode Tamu (Akses fitur terbatas)",
                                    badge = "Tamu",
                                    color = Color(0xFF64748B),
                                    bgColor = Color(0xFF64748B).copy(alpha = 0.10f),
                                    icon = Icons.Default.Shield
                                )
                            }
                            entitlement.isPremiumUnlocked && currentUser?.role == UserRole.SMART_MASTER -> {
                                val expiry = entitlement.expiresAt
                                val (detailText, badgeText) = if (expiry != null && expiry > currentTime) {
                                    val remainingMillis = (expiry - currentTime).coerceAtLeast(0L)
                                    val days = (remainingMillis / (24L * 3600 * 1000L)).toInt()
                                    val hours = ((remainingMillis % (24L * 3600 * 1000L)) / (3600 * 1000L)).toInt()
                                    val minutes = ((remainingMillis % (3600 * 1000L)) / (60 * 1000L)).toInt()
                                    val formattedDuration = when {
                                        days > 0 -> "$days hari $hours jam"
                                        hours > 0 -> "$hours jam $minutes mnt"
                                        else -> "${minutes.coerceAtLeast(1)} mnt"
                                    }
                                    Pair(
                                        "Sisa masa aktif: $formattedDuration (s/d ${entitlement.getFormattedExpiry()})",
                                        formattedDuration
                                    )
                                } else {
                                    Pair("Akses: Aktif (Tidak Terbatas)", "Aktif")
                                }
                                SmartMemberStatusUI(
                                    title = "Smart Member — Aktif 👑",
                                    detail = detailText,
                                    badge = badgeText,
                                    color = Color(0xFFD97706),
                                    bgColor = Color(0xFFD97706).copy(alpha = 0.12f),
                                    icon = Icons.Default.Diamond
                                )
                            }
                            entitlement.isExpired -> {
                                val detail = "Masa aktif telah berakhir. Perpanjang untuk membuka kembali fitur." 
                                SmartMemberStatusUI(
                                    title = "Smart Member — Kedaluwarsa",
                                    detail = detail,
                                    badge = "Kedaluwarsa",
                                    color = ExpenseRed,
                                    bgColor = ExpenseRed.copy(alpha = 0.12f),
                                    icon = Icons.Default.Lock
                                )
                            }
                            else -> {
                                SmartMemberStatusUI(
                                    title = "Smart Member — Bebas Biaya",
                                    detail = "Paket Bebas Biaya (Non-Premium)",
                                    badge = "Gratis",
                                    color = FinSmartPrimary,
                                    bgColor = FinSmartPrimary.copy(alpha = 0.10f),
                                    icon = Icons.Default.Shield
                                )
                            }
                        }
                    }

                    val displayedUsername = currentUser?.username?.takeIf { it.isNotBlank() }
                        ?: if (currentUser != null && !currentUser.isGuest && (currentUser.role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(currentUser.email))) AppStrings.t("Pengembang DompetNalar", "DompetNalar Developer")
                        else if (currentUser?.isGuest == true) AppStrings.t("Tamu DompetNalar", "DompetNalar Guest")
                        else AppStrings.t("Pengguna DompetNalar", "DompetNalar User")


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .clickable { onAvatarClick() }
                                .background(
                                    brush = Brush.linearGradient(
                                        when (role) {
                                            UserRole.MASTER_DEV -> listOf(Color(0xFFD97706), Color(0xFF92400E))
                                            UserRole.SMART_MASTER -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                            else -> listOf(FinSmartPrimary, Color(0xFF00897B))
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val shouldShowPhoto = currentUser != null && !currentUser.isGuest && !profilePhotoUri.isNullOrEmpty()
                            if (shouldShowPhoto) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = "Foto Profil",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = (displayedUsername.take(1).ifBlank { "D" }).uppercase(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = displayedUsername,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = when (role) {
                                    UserRole.MASTER_DEV -> Color(0xFFD97706)
                                    UserRole.SMART_MASTER -> Color(0xFFF59E0B).copy(alpha = 0.25f)
                                    UserRole.FREE_MEMBER -> FinSmartPrimary.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = role.resolveBadgeLabel(
                                        isGuest = currentUser?.isGuest == true,
                                        isEnglish = LocaleHelper.isEnglish()
                                    ),
                                    color = when (role) {
                                        UserRole.MASTER_DEV -> Color.White
                                        UserRole.SMART_MASTER -> Color(0xFFD97706)
                                        UserRole.FREE_MEMBER -> FinSmartPrimary
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (currentUser?.isGuest != true && !currentUser?.email.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentUser?.email ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dedicated Prominent Smart Member Status & Duration Counter Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_member_status_counter_box"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = statusInfo.bgColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(statusInfo.color.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = statusInfo.icon,
                                    contentDescription = null,
                                    tint = statusInfo.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = statusInfo.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusInfo.color
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = statusInfo.detail,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("smart_member_duration_counter")
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = statusInfo.color.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = statusInfo.badge,
                                    color = statusInfo.color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (BuildConfig.DEBUG && role == UserRole.MASTER_DEV) {
                        Button(
                            onClick = onOpenMasterDevPanel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_open_master_dev_panel_settings")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(com.example.util.AppStrings.t("Panel Kelola Akun Master Dev", "Master Dev Account Manager"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (role == UserRole.FREE_MEMBER) {
                        Button(
                            onClick = onOpenPaywall,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_upgrade_smart_master_settings")
                        ) {
                            Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(com.example.util.AppStrings.t("Upgrade ke Smart Master 👑 (Mulai Rp12.400/bln)", "Upgrade to Smart Master 👑 (From Rp12.400/mo)"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    if (currentUser != null && !currentUser.isGuest) {
                        SettingsRowItem(
                            icon = Icons.Default.SwitchAccount,
                            title = if (LocaleHelper.isEnglish()) "Switch Account" else "Ganti Akun",
                            subtitle = if (LocaleHelper.isEnglish()) "Log in with another account" else "Login dengan akun atau kredensial lain",
                            onClick = onOpenAuthModal
                        )
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        SettingsRowItem(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = if (LocaleHelper.isEnglish()) "Log Out" else "Keluar dari Akun",
                            subtitle = if (LocaleHelper.isEnglish()) "Sign out of current session" else "Keluar dari sesi akun saat ini",
                            onClick = { isLogoutConfirmDialogOpen = true }
                        )
                    } else {
                        SettingsRowItem(
                            icon = Icons.Default.Person,
                            title = if (LocaleHelper.isEnglish()) "Log In / Register" else "Masuk / Daftar Akun",
                            subtitle = if (LocaleHelper.isEnglish()) "Save and sync your financial data" else "Simpan dan sinkronkan data keuangan Anda",
                            onClick = onOpenAuthModal
                        )
                    }
                }
            }
        }

        // CARD 2: Preferences & Security
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = AppStrings.t("Preferensi & Keamanan", "Preferences & Security"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    SettingsRowToggle(
                        icon = Icons.Default.Person,
                        title = AppStrings.t("Mode Keuangan Aktif", "Active Financial Mode"),
                        subtitle = AppStrings.t("Beralih antara Mode ${accountMode.displayName}", "Switch between ${accountMode.displayName} Mode"),
                        checked = accountMode == AccountMode.BUSINESS,
                        onCheckedChange = { onToggleAccountMode() }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowToggle(
                        icon = Icons.Default.Visibility,
                        title = AppStrings.t("Mode Samarkan Saldo (Incognito)", "Hide Balance Mode (Incognito)"),
                        subtitle = AppStrings.t("Sembunyikan angka saldo di tempat umum", "Hide balance numbers in public spaces"),
                        checked = isPrivacyMode,
                        onCheckedChange = { onTogglePrivacy() }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.DarkMode,
                        title = AppStrings.t("Tema Tampilan", "Theme"),
                        subtitle = currentThemeMode.localizedTitle,
                        onClick = { isThemeDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.ColorLens,
                        title = AppStrings.t("Warna Aksen Aplikasi", "App Accent Color"),
                        subtitle = currentAccentColor.localizedDisplayName,
                        onClick = { isAccentDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    val isWallpaperUnlocked = role == UserRole.MASTER_DEV || role == UserRole.SMART_MASTER || com.example.util.EntitlementManager.evaluate(currentUser).isPremiumUnlocked

                    SettingsRowItem(
                        icon = Icons.Default.Wallpaper,
                        title = AppStrings.t("Latar Belakang Dasbor (Wallpaper)", "Dashboard Background (Wallpaper)"),
                        subtitle = if (!customWallpaperUri.isNullOrEmpty()) AppStrings.t("Custom Wallpaper Aktif (${(customWallpaperOpacity * 100).toInt()}%)", "Custom Wallpaper Active (${(customWallpaperOpacity * 100).toInt()}%)") else if (isWallpaperUnlocked) AppStrings.t("Bawaan Tema DompetNalar (Terbuka)", "DompetNalar Default Theme (Unlocked)") else AppStrings.t("Terkunci (Khusus Pengguna Smart Master 👑)", "Locked (Smart Master Only 👑)"),
                        onClick = { isWallpaperDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Apps,
                        title = AppStrings.t("Ikon Peluncur Dinamis (Homescreen)", "Dynamic Launcher Icon (Homescreen)"),
                        subtitle = activeIconEdition.localizedDisplayName,
                        onClick = { isAppIconDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Stars,
                        title = AppStrings.t("Gamifikasi, Daily Streak & Badges", "Gamification, Daily Streak & Badges"),
                        subtitle = AppStrings.t("Level XP, lencana digital & tantangan streak", "XP levels, digital badges & streak challenges"),
                        onClick = onOpenGamificationHub
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Person,
                        title = AppStrings.t("Tes Profil Finansial AI", "AI Financial Personality Quiz"),
                        subtitle = AppStrings.t("Ukur tipe kepribadian finansial (Saver/Investor/Impulse)", "Discover your financial personality type (Saver/Investor/Impulse)"),
                        onClick = onOpenPersonalityQuiz
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Language,
                        title = if (LocaleHelper.isEnglish()) "App Language" else "Bahasa Aplikasi",
                        subtitle = selectedLanguage.displayName,
                        onClick = { isLanguageDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Paid,
                        title = AppStrings.t("Mata Uang (Currency Selector)", "Currency (Currency Selector)"),
                        subtitle = "${selectedCurrency.displayName} • " + AppStrings.t("Format Global Aktif", "Active Global Format"),
                        onClick = { isCurrencyDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Security,
                        title = AppStrings.t("Duress PIN & Sistem Keamanan", "Duress PIN & Security Suite"),
                        subtitle = AppStrings.t("PIN Utama & PIN Darurat (Mode Stealth / Kamuflase)", "Primary PIN & Emergency PIN (Stealth / Camouflage Mode)"),
                        onClick = onOpenDuressPinModal
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Vibration,
                        title = AppStrings.t("Aksi Goyang HP (Shake Gesture)", "Shake Phone Action (Shake Gesture)"),
                        subtitle = when (shakeGestureAction) {
                            "TRANSACTION" -> AppStrings.t("Catat Cepat (Buka Lembar Transaksi)", "Quick Record (Open Transaction Sheet)")
                            "STEALTH" -> AppStrings.t("Mode Stealth Darurat (Samarkan Aplikasi)", "Emergency Stealth Mode (Camouflage App)")
                            else -> AppStrings.t("Nonaktif (Bawaan)", "Disabled (Default)")
                        },
                        onClick = { isShakeGestureDialogOpen = true }
                    )
                }
            }
        }

        // CARD 3: Backup & Cloud Sync
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = AppStrings.t("Google Drive & Sinkronisasi Cloud", "Google Drive & Cloud Sync"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDriveConnected) AppStrings.t("Terhubung: $driveEmail", "Connected: $driveEmail") else AppStrings.t("Folder: DompetNalar/Backup Data & App Updates", "Folder: DompetNalar/Backup Data & App Updates"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val isGuestUser = currentUser == null || currentUser.isGuest || currentUser.email == "guest@finsmart.local"

                    if (isGuestUser) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = AppStrings.t("Akses Cloud Sync Terbatas (Mode Tamu)", "Limited Cloud Sync Access (Guest Mode)"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = AppStrings.t("Silakan mendaftar atau masuk dengan Akun Google resmi untuk menggunakan fitur Backup & Restore Google Drive.", "Please register or sign in with an official Google Account to use Google Drive Backup & Restore."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenAuthModal,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                                ) {
                                    Text(com.example.util.AppStrings.t("Masuk / Daftar Akun Google", "Sign In / Register Google Account"), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    } else if (isDriveConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { isBackupChoiceDialogOpen = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_backup_now"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(com.example.util.AppStrings.t("Cadangkan", "Backup"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { isRestoreChoiceDialogOpen = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_restore_data"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(com.example.util.AppStrings.t("Pulihkan Data", "Restore Data"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }



                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AppStrings.t("Backup Otomatis Google Drive", "Google Drive Automatic Backup"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (driveAutoSync) AppStrings.t("Aktif (Latar belakang via WorkManager)", "Active (Background via WorkManager)") else AppStrings.t("Nonaktif", "Disabled"),
                                            fontSize = 10.sp,
                                            color = if (driveAutoSync) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = driveAutoSync,
                                        onCheckedChange = onToggleAutoSync,
                                        modifier = Modifier.testTag("switch_auto_sync")
                                    )
                                }

                                if (driveAutoSync) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = AppStrings.t("Interval Unggah:", "Upload Interval:"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3L, 6L, 12L, 24L).forEach { hours ->
                                            val isSelected = driveAutoBackupInterval == hours
                                            OutlinedButton(
                                                onClick = { onUpdateAutoBackupInterval(hours) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                                colors = if (isSelected) {
                                                    ButtonDefaults.outlinedButtonColors(
                                                        containerColor = FinSmartPrimary.copy(alpha = 0.15f),
                                                        contentColor = FinSmartPrimary
                                                    )
                                                } else {
                                                    ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSelected) FinSmartPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                )
                                            ) {
                                                Text(
                                                    text = if (LocaleHelper.isEnglish()) "${hours}h" else "${hours}j",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    val nextBackupTime = com.example.util.GoogleDriveService.getNextBackupTime(context)
                                    if (nextBackupTime != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = AppStrings.t("Perkiraan jadwal berikutnya: $nextBackupTime", "Estimated next schedule: $nextBackupTime"),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = AppStrings.t("Cadangan Terakhir:", "Last Backup:"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (driveLastBackup.isBlank()) AppStrings.t("Belum ada cadangan", "No backups yet") else driveLastBackup,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = onDisconnectDrive,
                                modifier = Modifier.testTag("btn_disconnect_drive"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = AppStrings.t("Putuskan", "Disconnect"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { onConnectDrive(currentUser?.email ?: "") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_connect_drive"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(com.example.util.AppStrings.t("Hubungkan ke Google Drive", "Connect to Google Drive"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // CARD 4: About & Account
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = AppStrings.t("Tentang & Akun", "About & Account"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )


                    SettingsRowItem(
                        icon = Icons.Default.Print,
                        title = AppStrings.t("Ekspor Laporan Keuangan", "Export Financial Report"),
                        subtitle = AppStrings.t("PDF formal atau Excel Spreadsheet (.csv)", "Formal PDF or Excel Spreadsheet (.csv)"),
                        onClick = onOpenExportReport
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Tune,
                        title = AppStrings.t("Kelola Format Ekspor", "Manage Export Formats"),
                        subtitle = AppStrings.t("Atur kolom kustom, pemisah CSV, dan format laporan", "Configure custom columns, CSV delimiter, and report format"),
                        onClick = { isManageExportFormatsOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Shield,
                        title = if (LocaleHelper.isEnglish()) "Privacy Policy & Disclosures" else "Kebijakan Privasi & GDPR",
                        subtitle = if (LocaleHelper.isEnglish()) "Local data transparency & device permissions" else "Transparansi data lokal & izin perangkat",
                        onClick = onOpenProminentDisclosure
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Feedback,
                        title = if (LocaleHelper.isEnglish()) "Feedback & Suggestions" else "Kritik & Saran",
                        subtitle = if (LocaleHelper.isEnglish()) "Send feedback to improve DompetNalar" else "Kirim masukan untuk perbaikan DompetNalar",
                        onClick = onOpenFeedback
                    )

                    if (role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(currentUser?.email)) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        SettingsRowItem(
                            icon = Icons.Default.AdminPanelSettings,
                            title = if (LocaleHelper.isEnglish()) "Feedback Cloud Viewer (Admin)" else "Viewer Feedback Cloud (Admin)",
                            subtitle = if (LocaleHelper.isEnglish()) "Inspect cloud feedback submissions" else "Pantau masukan pengguna di Firestore",
                            onClick = onOpenDevFeedbackViewer
                        )
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    if (currentUser != null && !currentUser.isGuest) {
                        SettingsRowItem(
                            icon = Icons.Default.Key,
                            title = AppStrings.t("Ubah Kata Sandi", "Change Password"),
                            subtitle = AppStrings.t("Ganti kata sandi akun ", "Change password for ") + currentUser.username,
                            onClick = onOpenChangePassword
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }


                    SettingsRowItem(
                        icon = Icons.Default.DeleteForever,
                        title = AppStrings.t("Hapus Akun & Data Permanen", "Delete Account & Data Permanently"),
                        subtitle = AppStrings.t("Hapus seluruh riwayat dan database lokal", "Delete all local history and database"),
                        titleColor = ExpenseRed,
                        iconTint = ExpenseRed,
                        onClick = onOpenDeleteAccount
                    )
                }
            }
        }

        // CARD 5: Developer Tools & Update Publisher (MasterDev only - strictly DEBUG only, hidden on Google Play and Release)
        if (BuildConfig.DEBUG && !isGooglePlayInstalled && (role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(currentUser?.email))) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFFD97706).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = AppStrings.t("Alat Pengembang & Rilis Pembaruan", "Developer Tools & Update Publisher"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                                Text(
                                    text = AppStrings.t("Pusat Kontrol Rilis Pembaruan Aplikasi ke Google Drive", "App Update Release Control Center to Google Drive"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { masterDevViewModel.openPublishModal() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_publish_update_masterdev"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.t("Rilis Pembaruan Aplikasi (MasterDev)", "Publish Application Update (MasterDev)"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onOpenMasterDevPanel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_open_master_dev_panel"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.t("Buka Master Dev User Manager", "Open Master Dev User Manager"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                    }
                }
            }
        }

        // Footer Info: © 2026 SleepyKoala
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${AppConfig.APP_NAME} Android • v${AppConfig.APP_VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "© ${AppConfig.COPYRIGHT_YEAR} ${AppConfig.APP_AUTHOR} • All Rights Reserved",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Shake Gesture Selector Dialog (FEAT-36)
    if (isShakeGestureDialogOpen) {
        AlertDialog(
            onDismissRequest = { isShakeGestureDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Aksi Goyang HP (Shake Gesture)", "Shake Phone Action (Shake Gesture)"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.t("Pilih aksi pintas saat perangkat digoyangkan secara mantap (> 2.5g):", "Choose shortcut action when device is shaken firmly (> 2.5g):"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val options = listOf(
                        Triple("OFF", AppStrings.t("Nonaktif", "Disabled"), AppStrings.t("Sensor mati, tidak ada aksi goyangan (Bawaan)", "Sensor off, no shake action (Default)")),
                        Triple("TRANSACTION", AppStrings.t("Catat Cepat", "Quick Record"), AppStrings.t("Buka lembar input transaksi baru", "Open new transaction input sheet")),
                        Triple("STEALTH", AppStrings.t("Mode Stealth Darurat", "Emergency Stealth Mode"), AppStrings.t("Samarkan antarmuka seketika ke mode stealth", "Immediately camouflage interface into stealth mode"))
                    )

                    options.forEach { (actionKey, title, desc) ->
                        val isSelected = shakeGestureAction == actionKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateShakeGestureAction(actionKey)
                                    isShakeGestureDialogOpen = false
                                    Toast.makeText(context, AppStrings.t("Aksi goyangan: $title", "Shake action: $title"), Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isShakeGestureDialogOpen = false }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 1. Theme Mode Dialog (Light, Dark, Auto-Time, System)
    if (isThemeDialogOpen) {
        AlertDialog(
            onDismissRequest = { isThemeDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Pilih Tema Tampilan", "Select Theme"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.t("Sesuaikan tampilan aplikasi dengan preferensi kenyamanan mata Anda:", "Customize the app appearance to your eye comfort preference:"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AppThemeMode.values().forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeEngine.setThemeMode(context, mode)
                                    isThemeDialogOpen = false
                                    Toast.makeText(context, AppStrings.t("Tema diubah: ${mode.localizedTitle}", "Theme changed: ${mode.localizedTitle}"), Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = mode.localizedTitle,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.localizedSubtitle,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isThemeDialogOpen = false }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 2. Accent Color Dialog
    if (isAccentDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAccentDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Warna Aksen Aplikasi", "App Accent Color"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.t("Pilih skema warna utama untuk tombol, highlight, dan indikator tab:", "Choose primary color scheme for buttons, highlights, and tab indicators:"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AppAccentColor.values().forEach { accent ->
                        val isSelected = currentAccentColor == accent
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) accent.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, accent.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeEngine.setAccentColor(context, accent)
                                    isAccentDialogOpen = false
                                    Toast.makeText(context, AppStrings.t("Aksen diubah: ${accent.localizedDisplayName}", "Accent changed: ${accent.localizedDisplayName}"), Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(accent.primary, CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = accent.localizedDisplayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = AppStrings.t("Terpilih", "Selected"),
                                        tint = accent.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isAccentDialogOpen = false }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 3. Wallpaper Dialog
    if (isWallpaperDialogOpen) {
        AlertDialog(
            onDismissRequest = { isWallpaperDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Latar Belakang Galeri", "Gallery Background"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                val isWallpaperUnlocked = role == UserRole.MASTER_DEV || role == UserRole.SMART_MASTER || com.example.util.EntitlementManager.evaluate(currentUser).isPremiumUnlocked

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = AppStrings.t("Pasang foto dari galeri HP sebagai latar belakang layar dasbor Anda:", "Set a photo from your device gallery as your dashboard background:"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isWallpaperUnlocked) {
                        Surface(
                            color = Color(0xFFD97706).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = AppStrings.t("🔒 Fitur Smart Master: Fitur ganti wallpaper dasbor ini terbuka otomatis bagi pengguna berstatus Smart Master 👑 (dapat diperoleh dari Reward Streak 30 Hari, Langganan, atau Hak Master Dev).", "🔒 Smart Master Feature: Changing dashboard wallpaper unlocks automatically for Smart Master 👑 users (obtainable from 30-Day Streak Reward, Subscription, or Master Dev Access)."),
                                fontSize = 11.sp,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            wallpaperPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        enabled = isWallpaperUnlocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isWallpaperUnlocked) AppStrings.t("Pilih Gambar dari Galeri", "Choose Image from Gallery") else AppStrings.t("Terkunci (Butuh Streak 30 Hari)", "Locked (Requires 30-Day Streak)"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!customWallpaperUri.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppStrings.t("Transparansi Gambar: ${(tempOpacity * 100).toInt()}%", "Image Opacity: ${(tempOpacity * 100).toInt()}%"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = tempOpacity,
                            onValueChange = {
                                tempOpacity = it
                                ThemeEngine.setCustomWallpaperForUser(context, currentUser, customWallpaperUri, it)
                            },
                            valueRange = 0.1f..0.85f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = {
                                ThemeEngine.setCustomWallpaperForUser(context, currentUser, null, 0.35f)
                                Toast.makeText(context, if (LocaleHelper.isEnglish()) "Background reset to default" else "Latar belakang dikembalikan ke default", Toast.LENGTH_SHORT).show()
                                isWallpaperDialogOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed)
                        ) {
                            Text(if (LocaleHelper.isEnglish()) "Remove Wallpaper / Reset Default" else "Hapus Wallpaper / Kembalikan Default", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isWallpaperDialogOpen = false }) {
                    Text(if (LocaleHelper.isEnglish()) "Done" else "Selesai")
                }
            }
        )
    }

    // Logout Confirmation Dialog (Yes/No requirement)
    if (isLogoutConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = { isLogoutConfirmDialogOpen = false },
            title = {
                Text(
                    text = if (LocaleHelper.isEnglish()) "Confirm Logout" else "Konfirmasi Keluar",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (LocaleHelper.isEnglish()) {
                        "Are you sure you want to log out? Your transaction history and financial records will remain safely saved."
                    } else {
                        "Apakah Anda yakin ingin keluar dari akun ini? Seluruh riwayat transaksi dan data finansial Anda tetap tersimpan dengan aman."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isLoggingOut) {
                            isLoggingOut = true
                            isLogoutConfirmDialogOpen = false
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (LocaleHelper.isEnglish()) "Yes" else "Ya")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { isLogoutConfirmDialogOpen = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (LocaleHelper.isEnglish()) "No" else "Tidak")
                }
            }
        )
    }

    // 4. Dynamic Launcher Icon Dialog
    if (isAppIconDialogOpen) {
        AlertDialog(
            onDismissRequest = { isAppIconDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.t("Ikon Peluncur Dinamis", "Dynamic Launcher Icon"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.t("Ubah ikon aplikasi di homescreen HP secara dinamis. Streak harian saat ini: ", "Dynamically change app home screen icon. Current daily streak: ") + "$dailyStreak " + AppStrings.t("Hari", "Days"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    AppIconEdition.values().forEach { edition ->
                        val isSelected = activeIconEdition == edition
                        val isUnlocked = AppIconManager.canUnlockIcon(edition, dailyStreak, role == UserRole.MASTER_DEV)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isUnlocked) {
                                    val success = AppIconManager.setAppIcon(
                                        context = context,
                                        edition = edition,
                                        currentStreak = dailyStreak,
                                        isMasterDev = role == UserRole.MASTER_DEV
                                    )
                                    if (success) {
                                        Toast.makeText(context, AppStrings.t("Ikon berhasil diubah ke ${edition.localizedDisplayName}", "Icon successfully changed to ${edition.localizedDisplayName}"), Toast.LENGTH_SHORT).show()
                                        isAppIconDialogOpen = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                when (edition) {
                                                    AppIconEdition.DEFAULT_CLASSIC -> Color(0xFF0B57D0)
                                                    AppIconEdition.GOLD_LUXURY -> Color(0xFFD97706)
                                                    AppIconEdition.DARK_OBSIDIAN -> Color(0xFF0F172A)
                                                    AppIconEdition.CYBERPUNK_NEON -> Color(0xFF7000FF)
                                                },
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!isUnlocked) {
                                            Icon(Icons.Default.Lock, contentDescription = AppStrings.t("Tergembok", "Locked"), tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = edition.localizedDisplayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = if (isUnlocked) AppStrings.t("Tersedia & Terbuka", "Available & Unlocked") else AppStrings.t("Tergembok (Perlu Streak ${edition.requiredStreak} Hari)", "Locked (Requires Streak of ${edition.requiredStreak} Days)"),
                                            fontSize = 10.sp,
                                            color = if (isUnlocked) MaterialTheme.colorScheme.primary else ExpenseRed
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = AppStrings.t("Aktif", "Active"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isAppIconDialogOpen = false }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 1. Language Selection Dialog
    if (isLanguageDialogOpen) {
        AlertDialog(
            onDismissRequest = { isLanguageDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (LocaleHelper.isEnglish()) "Select App Language" else "Pilih Bahasa Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) FinSmartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isLanguageDialogOpen = false
                                    LocaleHelper.setLanguage(context, lang)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = lang.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lang.subtitle,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isLanguageDialogOpen = false }) {
                    Text(if (LocaleHelper.isEnglish()) "Close" else "Tutup")
                }
            }
        )
    }

    // 2. Currency Selector Dialog
    if (isCurrencyDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCurrencyDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Paid, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.t("Pilih Mata Uang Global", "Select Global Currency"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentMode by CurrencyFormatter.currencyMode.collectAsState()
                    Text(if (LocaleHelper.isEnglish()) "Select currency display format:" else "Format angka & simbol mata uang akan otomatis diperbarui secara global di seluruh UI:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option: Follow Device Region
                    val isFollowDevice = currentMode == com.example.util.CurrencyMode.FOLLOW_DEVICE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFollowDevice) FinSmartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isFollowDevice) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                CurrencyFormatter.setMode(context, com.example.util.CurrencyMode.FOLLOW_DEVICE)
                                isCurrencyDialogOpen = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isFollowDevice) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isFollowDevice) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (LocaleHelper.isEnglish()) "Follow Device Region" else "Ikuti Region Perangkat",
                                    fontWeight = if (isFollowDevice) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (LocaleHelper.isEnglish()) "Automatically matches device locale region" else "Menyesuaikan otomatis dengan negara/wilayah perangkat",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    AppCurrency.values().forEach { curr ->
                        val isSelected = !isFollowDevice && selectedCurrency == curr
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) FinSmartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CurrencyFormatter.setCurrency(curr, context)
                                    isCurrencyDialogOpen = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) FinSmartPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = curr.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = AppStrings.t("Contoh: ${curr.prefix}50.000", "Example: ${curr.prefix}50,000"),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    color = FinSmartPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = curr.symbol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = FinSmartPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isCurrencyDialogOpen = false }) {
                    Text(AppStrings.t("Tutup", "Close"))
                }
            }
        )
    }

    // 3. Check Update Dialog
    // 4b. Export Format Management Dialog
    if (isManageExportFormatsOpen) {
        ExportFormatManagementDialog(
            onDismiss = { isManageExportFormatsOpen = false }
        )
    }

    // 5. Update Available Bottom Sheet (Drive Client Side Engine)
    if (isUpdateModalVisible && updateMetadata != null) {
        UpdateAvailableBottomSheet(
            metadata = updateMetadata!!,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadUpdateProgress,
            onDownloadAndInstall = {
                updateViewModel.downloadAndInstall(context)
            },
            onDismiss = {
                updateViewModel.dismissUpdateModal()
            }
        )
    }

    // 6. MasterDev One-Click Update Publisher Bottom Sheet
    if (isPublishModalOpen) {
        PublishUpdateBottomSheet(
            masterDevViewModel = masterDevViewModel,
            onDismiss = {
                masterDevViewModel.dismissPublishModal()
            }
        )
    }

    // 7. Backup Choice Dialog (Device Local vs Portable)
    if (isBackupChoiceDialogOpen) {
        AlertDialog(
            onDismissRequest = { isBackupChoiceDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Pilihan Pencadangan Data", "Data Backup Options"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.t("Pilih metode pencadangan yang sesuai dengan kebutuhan Anda:", "Choose the backup method that suits your needs:"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (SessionManager.isStealthModeActive()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().testTag("stealth_backup_warning")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    AppStrings.t("Mode Stealth Aktif: Operasi pencadangan dinonaktifkan demi perlindungan privasi data.", "Stealth Mode Active: Backup operation is disabled for data privacy protection."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Option A: Device Local Backup
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isBackupChoiceDialogOpen = false
                                onTriggerBackup()
                            }
                            .testTag("btn_choice_device_backup")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.example.util.AppStrings.t("Cadangan Perangkat (Otomatis)", "Device Backup (Automatic)"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    AppStrings.t("Menggunakan Android Keystore. Cepat, otomatis & tanpa perlu kata sandi.", "Uses Android Keystore. Fast, automatic & no password required."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option B: Portable Backup
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FinSmartPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isBackupChoiceDialogOpen = false
                                portableBackupPassword = ""
                                portableBackupConfirmPassword = ""
                                portableBackupError = null
                                isPortableBackupDialogOpen = true
                            }
                            .testTag("btn_choice_portable_backup")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(FinSmartPrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.example.util.AppStrings.t("Cadangan Portabel (Kata Sandi)", "Portable Backup (Password)"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FinSmartPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    AppStrings.t("Terenkripsi AES-256-GCM dengan kata sandi khusus. Dapat dipindahkan ke perangkat lain.", "AES-256-GCM encrypted with custom password. Can be transferred to another device."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isBackupChoiceDialogOpen = false }) {
                    Text(AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // 8. Portable Backup Dialog (Password Setup & Export)
    if (isPortableBackupDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (!isCreatingPortableBackup) {
                    isPortableBackupDialogOpen = false
                    portableBackupPassword = ""
                    portableBackupConfirmPassword = ""
                    portableBackupError = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Buat Cadangan Portabel", "Create Portable Backup"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.t("Tentukan kata sandi untuk melindungi cadangan Anda. Data dienkripsi secara independen tanpa bergantung pada kunci hardware perangkat ini.", "Set a password to protect your backup. Data is encrypted independently without relying on this device's hardware keys."),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = portableBackupPassword,
                        onValueChange = {
                            portableBackupPassword = it
                            portableBackupError = null
                        },
                        label = { Text(com.example.util.AppStrings.t("Kata Sandi Cadangan", "Backup Password")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Minimal 6 karakter", "Minimum 6 characters")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { portableBackupPasswordVisible = !portableBackupPasswordVisible }) {
                                Icon(
                                    imageVector = if (portableBackupPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (portableBackupPasswordVisible) "Sembunyikan" else "Tampilkan"
                                )
                            }
                        },
                        visualTransformation = if (portableBackupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_portable_backup_password")
                    )

                    OutlinedTextField(
                        value = portableBackupConfirmPassword,
                        onValueChange = {
                            portableBackupConfirmPassword = it
                            portableBackupError = null
                        },
                        label = { Text(com.example.util.AppStrings.t("Konfirmasi Kata Sandi", "Confirm Password")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Ketik ulang kata sandi", "Retype password")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { portableBackupConfirmVisible = !portableBackupConfirmVisible }) {
                                Icon(
                                    imageVector = if (portableBackupConfirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (portableBackupConfirmVisible) "Sembunyikan" else "Tampilkan"
                                )
                            }
                        },
                        visualTransformation = if (portableBackupConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_portable_backup_confirm_password")
                    )

                    if (!portableBackupError.isNullOrBlank()) {
                        Text(
                            text = portableBackupError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("text_portable_backup_error")
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = AppStrings.t("Catatan: Jangan lupakan kata sandi ini. DompetNalar tidak menyimpannya di server demi keamanan privasi Anda.", "Note: Do not forget this password. DompetNalar does not store it on servers for your privacy."),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = portableBackupPassword.trim()
                        val confirmPass = portableBackupConfirmPassword.trim()
                        when {
                            pass.isEmpty() -> {
                                portableBackupError = AppStrings.t("Kata sandi cadangan tidak boleh kosong.", "Backup password cannot be empty.")
                            }
                            pass.length < 6 -> {
                                portableBackupError = AppStrings.t("Kata sandi cadangan minimal 6 karakter.", "Backup password must be at least 6 characters.")
                            }
                            pass != confirmPass -> {
                                portableBackupError = AppStrings.t("Konfirmasi kata sandi tidak cocok.", "Password confirmation does not match.")
                            }
                            else -> {
                                isCreatingPortableBackup = true
                                portableBackupError = null
                                val capturedPassword = pass
                                // Clear password states from UI immediately
                                portableBackupPassword = ""
                                portableBackupConfirmPassword = ""
                                onTriggerPortableBackup(capturedPassword) { success, message, file ->
                                    isCreatingPortableBackup = false
                                    if (success) {
                                        isPortableBackupDialogOpen = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        if (file != null && file.exists()) {
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/json"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    putExtra(Intent.EXTRA_SUBJECT, "DompetNalar Portable Backup")
                                                    putExtra(Intent.EXTRA_TEXT, com.example.util.AppStrings.t("File cadangan portabel DompetNalar terenkripsi.", "Encrypted DompetNalar portable backup file."))
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, com.example.util.AppStrings.t("Bagikan / Simpan File Cadangan Portabel", "Share / Save Portable Backup File")))
                                            } catch (e: Exception) {
                                                Log.w("SettingsScreen", "Gagal memanggil share chooser: ${e.message}")
                                            }
                                        }
                                    } else {
                                        portableBackupError = message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isCreatingPortableBackup,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.testTag("btn_submit_portable_backup")
                ) {
                    Text(if (isCreatingPortableBackup) AppStrings.t("Memproses...", "Processing...") else AppStrings.t("Cadangkan & Ekspor", "Backup & Export"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isPortableBackupDialogOpen = false
                        portableBackupPassword = ""
                        portableBackupConfirmPassword = ""
                        portableBackupError = null
                    },
                    enabled = !isCreatingPortableBackup
                ) {
                    Text(AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // 9. Restore Choice Dialog (Device Local vs Portable)
    if (isRestoreChoiceDialogOpen) {
        AlertDialog(
            onDismissRequest = { isRestoreChoiceDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Pilihan Pemulihan Data", "Data Restore Options"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.t("Pilih sumber data cadangan yang ingin dipulihkan ke aplikasi ini:", "Select the backup data source you want to restore to this app:"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (SessionManager.isStealthModeActive()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().testTag("stealth_restore_warning")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    AppStrings.t("Mode Stealth Aktif: Operasi pemulihan dinonaktifkan demi perlindungan privasi data.", "Stealth Mode Active: Restore operation is disabled for data privacy protection."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Option A: Device Local Restore
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isRestoreChoiceDialogOpen = false
                                onTriggerRestore()
                            }
                            .testTag("btn_choice_device_restore")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.example.util.AppStrings.t("Pulihkan Cadangan Perangkat", "Restore Device Backup"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    AppStrings.t("Pulihkan dari cadangan lokal / Google Drive sandbox perangkat ini.", "Restore from local backup / Google Drive sandbox of this device."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option B: Portable Restore
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FinSmartPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isRestoreChoiceDialogOpen = false
                                portableRestorePassword = ""
                                portableRestoreError = null
                                selectedRestoreFileName = null
                                selectedRestoreFileContent = null
                                isPortableRestoreDialogOpen = true
                            }
                            .testTag("btn_choice_portable_restore")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(FinSmartPrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = FinSmartPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.example.util.AppStrings.t("Pulihkan Cadangan Portabel (Kata Sandi)", "Restore Portable Backup (Password)"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FinSmartPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    AppStrings.t("Pilih file cadangan (.json) dan dekripsi menggunakan kata sandi Anda.", "Select the backup file (.json) and decrypt using your password."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isRestoreChoiceDialogOpen = false }) {
                    Text(AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }

    // 10. Portable Restore Dialog (Password Input & Validation)
    if (isPortableRestoreDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoringPortableBackup) {
                    isPortableRestoreDialogOpen = false
                    portableRestorePassword = ""
                    portableRestoreError = null
                    selectedRestoreFileName = null
                    selectedRestoreFileContent = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(com.example.util.AppStrings.t("Pulihkan Cadangan Portabel", "Restore Portable Backup"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.t("Pilih file cadangan dan masukkan kata sandi yang digunakan saat pencadangan:", "Select the backup file and enter the password used during backup:"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // File picker indicator & action
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (selectedRestoreFileName != null) AppStrings.t("File Terpilih: $selectedRestoreFileName", "Selected File: $selectedRestoreFileName") else AppStrings.t("Sumber: dompetnalar_backup.json (Sandbox/Lokal)", "Source: dompetnalar_backup.json (Sandbox/Local)"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { backupPickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_pick_backup_file"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(com.example.util.AppStrings.t("Pilih File Cadangan Lain (.json)", "Select Another Backup File (.json)"), fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = portableRestorePassword,
                        onValueChange = {
                            portableRestorePassword = it
                            portableRestoreError = null
                        },
                        label = { Text(com.example.util.AppStrings.t("Kata Sandi Cadangan", "Backup Password")) },
                        placeholder = { Text(com.example.util.AppStrings.t("Masukkan kata sandi", "Enter password")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { portableRestorePasswordVisible = !portableRestorePasswordVisible }) {
                                Icon(
                                    imageVector = if (portableRestorePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (portableRestorePasswordVisible) "Sembunyikan" else "Tampilkan"
                                )
                            }
                        },
                        visualTransformation = if (portableRestorePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_portable_restore_password")
                    )

                    if (!portableRestoreError.isNullOrBlank()) {
                        Text(
                            text = portableRestoreError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("text_portable_restore_error")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = portableRestorePassword.trim()
                        if (pass.isEmpty()) {
                            portableRestoreError = AppStrings.t("Kata sandi pemulihan tidak boleh kosong.", "Recovery password cannot be empty.")
                            return@Button
                        }
                        isRestoringPortableBackup = true
                        portableRestoreError = null
                        val capturedPassword = pass
                        val capturedContent = selectedRestoreFileContent
                        // Clear password from memory/state
                        portableRestorePassword = ""
                        onTriggerPortableRestore(capturedContent, capturedPassword) { success, message ->
                            isRestoringPortableBackup = false
                            if (success) {
                                isPortableRestoreDialogOpen = false
                                selectedRestoreFileName = null
                                selectedRestoreFileContent = null
                                portableRestoreError = null
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            } else {
                                portableRestoreError = message
                            }
                        }
                    },
                    enabled = !isRestoringPortableBackup,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary),
                    modifier = Modifier.testTag("btn_submit_portable_restore")
                ) {
                    Text(if (isRestoringPortableBackup) AppStrings.t("Mendekripsi...", "Decrypting...") else AppStrings.t("Pulihkan Data", "Restore Data"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isPortableRestoreDialogOpen = false
                        portableRestorePassword = ""
                        portableRestoreError = null
                        selectedRestoreFileName = null
                        selectedRestoreFileContent = null
                    },
                    enabled = !isRestoringPortableBackup
                ) {
                    Text(AppStrings.t("Batal", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingsRowToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private data class SmartMemberStatusUI(
    val title: String,
    val detail: String,
    val badge: String,
    val color: Color,
    val bgColor: Color,
    val icon: ImageVector
)
