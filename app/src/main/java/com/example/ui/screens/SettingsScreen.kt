package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.SystemUpdate
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
import com.example.data.model.AccountMode
import com.example.data.model.AppIconEdition
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.components.ChangelogBottomSheet
import com.example.ui.components.PublishUpdateBottomSheet
import com.example.ui.components.UpdateAvailableBottomSheet
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
    onConnectDrive: (String) -> Unit,
    onDisconnectDrive: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onTriggerBackup: () -> Unit,
    onTriggerRestore: () -> Unit,
    onOpenGamificationHub: () -> Unit = {},
    onOpenPersonalityQuiz: () -> Unit = {},
    onOpenDuressPinModal: () -> Unit = {},
    onOpenDebtPlanner: () -> Unit = {},
    onOpenAssetTracker: () -> Unit = {},
    onOpenReceiptGenerator: () -> Unit = {},
    profilePhotoUri: String? = null,
    onAvatarClick: () -> Unit = {},
    dailyStreak: Int = 1,
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
    var isCheckUpdateDialogOpen by remember { mutableStateOf(false) }
    var isChangelogOpen by remember { mutableStateOf(false) }

    var tempOpacity by remember(customWallpaperOpacity) { mutableStateOf(customWallpaperOpacity) }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                ThemeEngine.setCustomWallpaper(context, uri.toString(), customWallpaperOpacity)
                Toast.makeText(context, "Latar belakang berhasil diterapkan!", Toast.LENGTH_SHORT).show()
                isWallpaperDialogOpen = true
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
                    text = "Pengaturan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Kelola profil, keamanan, dan sinkronisasi cloud FinSmart",
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
                            if (!profilePhotoUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = "Foto Profil",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = (currentUser?.username?.take(1) ?: "L").uppercase(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser?.username ?: "Lukas",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = when (role) {
                                        UserRole.MASTER_DEV -> Color(0xFFD97706)
                                        UserRole.SMART_MASTER -> Color(0xFFF59E0B).copy(alpha = 0.25f)
                                        UserRole.FREE_MEMBER -> FinSmartPrimary.copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = role.badgeText,
                                        color = when (role) {
                                            UserRole.MASTER_DEV -> Color.White
                                            UserRole.SMART_MASTER -> Color(0xFFD97706)
                                            UserRole.FREE_MEMBER -> FinSmartPrimary
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentUser?.email ?: "lunachroc07@gmail.com",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (role == UserRole.MASTER_DEV) {
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
                            Text("Panel Kelola Akun Master Dev", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            Text("Upgrade ke Smart Master 👑 (Mulai Rp12.400/bln)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.SwitchAccount,
                        title = "Ganti Akun / Masuk",
                        subtitle = "Login dengan email atau kredensial lain",
                        onClick = onOpenAuthModal
                    )
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
                        text = "Preferences & Security",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    SettingsRowToggle(
                        icon = Icons.Default.Person,
                        title = "Mode Keuangan Aktif",
                        subtitle = "Beralih antara Mode ${accountMode.displayName}",
                        checked = accountMode == AccountMode.BUSINESS,
                        onCheckedChange = { onToggleAccountMode() }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowToggle(
                        icon = Icons.Default.Visibility,
                        title = "Mode Samarkan Saldo (Incognito)",
                        subtitle = "Sembunyikan angka saldo di tempat umum",
                        checked = isPrivacyMode,
                        onCheckedChange = { onTogglePrivacy() }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.DarkMode,
                        title = "Tema Tampilan",
                        subtitle = currentThemeMode.title,
                        onClick = { isThemeDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.ColorLens,
                        title = "Warna Aksen Aplikasi",
                        subtitle = currentAccentColor.displayName,
                        onClick = { isAccentDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Wallpaper,
                        title = "Latar Belakang Dasbor (Wallpaper)",
                        subtitle = if (!customWallpaperUri.isNullOrEmpty()) "Custom Wallpaper Aktif (${(customWallpaperOpacity * 100).toInt()}%)" else "Bawaan Tema FinSmart",
                        onClick = { isWallpaperDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Apps,
                        title = "Dynamic Launcher Icon (Homescreen)",
                        subtitle = activeIconEdition.displayName,
                        onClick = { isAppIconDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Stars,
                        title = "Gamifikasi, Daily Streak & Badges",
                        subtitle = "Level XP, lencana digital & custom background",
                        onClick = onOpenGamificationHub
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Person,
                        title = "Tes Profil Finansial AI",
                        subtitle = "Ukur tipe kepribadian finansial (Saver/Investor/Impulse)",
                        onClick = onOpenPersonalityQuiz
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Language,
                        title = "Bahasa Aplikasi",
                        subtitle = selectedLanguage.displayName,
                        onClick = { isLanguageDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Paid,
                        title = "Mata Uang (Currency Selector)",
                        subtitle = "${selectedCurrency.displayName} • Format Global Aktif",
                        onClick = { isCurrencyDialogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Security,
                        title = "Duress PIN & Security Suite",
                        subtitle = "PIN Utama & PIN Darurat (Mode Stealth / Dummy)",
                        onClick = onOpenDuressPinModal
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
                                text = "Google Drive & Cloud Sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDriveConnected) "Terhubung: $driveEmail" else "Folder: FinSmart/Backup Data & App Updates",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isDriveConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onTriggerBackup,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_backup_now"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cadangkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onTriggerRestore,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_restore_data"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Pulihkan Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Backup Otomatis Mingguan", fontSize = 12.sp)
                            Switch(
                                checked = driveAutoSync,
                                onCheckedChange = onToggleAutoSync,
                                modifier = Modifier.testTag("switch_auto_sync")
                            )
                        }
                    } else {
                        Button(
                            onClick = { onConnectDrive(currentUser?.email ?: "lunachroc07@gmail.com") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_connect_drive"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Hubungkan ke Google Drive", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        text = "About & Account",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    SettingsRowItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "Cek Pembaruan Versi",
                        subtitle = if (isCheckingUpdate) "Memeriksa ke Google Drive..." else "FinSmart v${AppConfig.APP_VERSION_NAME} (Build ${AppConfig.APP_VERSION_CODE}) • Cek Pembaruan Drive",
                        onClick = {
                            updateViewModel.checkForUpdates(context, showFeedbackIfLatest = true)
                        }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Description,
                        title = "Catatan Rilis / Changelog (v1.0.3)",
                        subtitle = "Lihat pembaruan fitur & log perubahan terbaru",
                        onClick = { isChangelogOpen = true }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Print,
                        title = "Ekspor Laporan Keuangan",
                        subtitle = "PDF formal atau Excel Spreadsheet (.csv)",
                        onClick = onOpenExportReport
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.Shield,
                        title = "Kebijakan Privasi & GDPR",
                        subtitle = "Transparansi data lokal & izin perangkat",
                        onClick = onOpenProminentDisclosure
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Keluar dari Akun",
                        subtitle = "Keluar dari sesi saat ini",
                        onClick = onLogout
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsRowItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Hapus Akun & Data Permanen",
                        subtitle = "Hapus seluruh riwayat dan database lokal",
                        titleColor = ExpenseRed,
                        iconTint = ExpenseRed,
                        onClick = onOpenDeleteAccount
                    )
                }
            }
        }

        // CARD 5: Developer Tools & Update Publisher (MasterDev / Lukas only)
        if (role == UserRole.MASTER_DEV || currentUser?.username.equals("Lukas", ignoreCase = true) || currentUser?.email.equals("lunachroc07@gmail.com", ignoreCase = true)) {
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
                                    text = "Developer Tools & Update Publisher",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                                Text(
                                    text = "Pusat Kontrol Rilis Pembaruan Aplikasi ke Google Drive",
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
                            Text("Publish Application Update (MasterDev)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            Text("Buka Master Dev User Manager", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Footer Info: © 2026 Lukas
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
                    text = "© 2026 Lukas • All Rights Reserved",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    // 1. Theme Mode Dialog (Light, Dark, Auto-Time, System)
    if (isThemeDialogOpen) {
        AlertDialog(
            onDismissRequest = { isThemeDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pilih Tema Tampilan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sesuaikan tampilan aplikasi dengan preferensi kenyamanan mata Anda:",
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
                                    Toast.makeText(context, "Tema diubah: ${mode.title}", Toast.LENGTH_SHORT).show()
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
                                        text = mode.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.subtitle,
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
                    Text("Tutup")
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
                    Text("Warna Aksen Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih skema warna utama untuk tombol, highlight, dan indikator tab:",
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
                                    Toast.makeText(context, "Aksen diubah: ${accent.displayName}", Toast.LENGTH_SHORT).show()
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
                                        text = accent.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
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
                    Text("Tutup")
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
                    Text("Latar Belakang Galeri", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pasang foto dari galeri HP sebagai latar belakang layar dasbor Anda:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            wallpaperPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Gambar dari Galeri", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!customWallpaperUri.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transparansi Gambar: ${(tempOpacity * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = tempOpacity,
                            onValueChange = {
                                tempOpacity = it
                                ThemeEngine.setCustomWallpaper(context, customWallpaperUri, it)
                            },
                            valueRange = 0.1f..0.85f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = {
                                ThemeEngine.clearCustomWallpaper(context)
                                Toast.makeText(context, "Latar belakang dikembalikan ke default", Toast.LENGTH_SHORT).show()
                                isWallpaperDialogOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed)
                        ) {
                            Text("Hapus Wallpaper / Kembalikan Default", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isWallpaperDialogOpen = false }) {
                    Text("Selesai")
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
                    Text("Dynamic Launcher Icon", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ubah ikon aplikasi di homescreen HP secara dinamis. Streak harian saat ini: $dailyStreak Hari",
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
                                        Toast.makeText(context, "Ikon berhasil diubah ke ${edition.displayName}", Toast.LENGTH_SHORT).show()
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
                                            Icon(Icons.Default.Lock, contentDescription = "Tergembok", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = edition.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = if (isUnlocked) "Tersedia & Terbuka" else "Tergembok (Perlu Streak ${edition.requiredStreak} Hari)",
                                            fontSize = 10.sp,
                                            color = if (isUnlocked) MaterialTheme.colorScheme.primary else ExpenseRed
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Aktif",
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
                    Text("Tutup")
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
                    Text("Pilih Bahasa Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.values().forEach { lang ->
                        val isSelected = selectedLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) FinSmartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LocaleHelper.setLanguage(lang)
                                    isLanguageDialogOpen = false
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
                    Text("Tutup")
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
                    Text("Pilih Mata Uang Global", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Format angka & simbol mata uang akan otomatis diperbarui secara global di seluruh UI:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    AppCurrency.values().forEach { curr ->
                        val isSelected = selectedCurrency == curr
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) FinSmartPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, FinSmartPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CurrencyFormatter.setCurrency(curr)
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
                                            text = "Contoh: ${curr.prefix}50.000",
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
                    Text("Tutup")
                }
            }
        )
    }

    // 3. Check Update Dialog
    if (isCheckUpdateDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCheckUpdateDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = FinSmartPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pembaruan Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text("Versi Terpasang: v${AppConfig.APP_VERSION_NAME} (Build ${AppConfig.APP_VERSION_CODE})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: Aplikasi Anda sudah menggunakan versi rilis terbaru!\n\nFolder Google Drive: FinSmart/App Updates\nServer Build: GitHub Actions CI/CD Automated Workflow",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCheckUpdateDialogOpen = false
                        isChangelogOpen = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                ) {
                    Text("Lihat Changelog")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCheckUpdateDialogOpen = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // 4. Changelog Bottom Sheet
    if (isChangelogOpen) {
        ChangelogBottomSheet(
            onDismiss = { isChangelogOpen = false }
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
