package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppConfig
import com.example.data.model.AccountMode
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent

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
    modifier: Modifier = Modifier
) {
    val role = currentUser?.role ?: UserRole.FREE_MEMBER

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
                    text = "Pengaturan & Akun",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Kelola preferensi FinSmart dan status keanggotaan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Profile Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
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
                            Text(
                                text = (currentUser?.username?.take(1) ?: "L").uppercase(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser?.username ?: "Lukas",
                                    fontSize = 16.sp,
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
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentUser?.email ?: "lunachroc07@gmail.com",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Role-Specific Action Banner
                    Spacer(modifier = Modifier.height(14.dp))
                    if (role == UserRole.MASTER_DEV) {
                        Button(
                            onClick = onOpenMasterDevPanel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_open_master_dev_panel_settings")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buka Panel Kelola Akun Master Dev", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else if (role == UserRole.FREE_MEMBER) {
                        Button(
                            onClick = onOpenPaywall,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_upgrade_smart_master_settings")
                        ) {
                            Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upgrade ke Smart Master 👑 (Mulai Rp12.400/bln)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section: Preferensi Keuangan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Preferensi & Tampilan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Mode Akun Toggle
                    SettingsRowToggle(
                        icon = Icons.Default.Person,
                        title = "Mode Keuangan Aktif",
                        subtitle = "Beralih antara Mode ${accountMode.displayName}",
                        checked = accountMode == AccountMode.BUSINESS,
                        onCheckedChange = { onToggleAccountMode() }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Privacy Mode Toggle
                    SettingsRowToggle(
                        icon = Icons.Default.Visibility,
                        title = "Mode Samarkan Saldo (Incognito)",
                        subtitle = "Sembunyikan angka saldo di tempat umum",
                        checked = isPrivacyMode,
                        onCheckedChange = { onTogglePrivacy() }
                    )
                }
            }
        }

        // Section: Dual-Mode Architecture (Bab 13)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Arsitektur Dual-Mode (Bab 13)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SettingsRowToggle(
                        icon = Icons.Default.Shop,
                        title = "Mode Rilis Play Store",
                        subtitle = if (AppConfig.IS_PLAY_STORE_BUILD) "Aktif: Google Play Billing & In-App Updates" else "Non-Play Store: DANA 085155076566 & Google Drive APK",
                        checked = AppConfig.IS_PLAY_STORE_BUILD,
                        onCheckedChange = { onTogglePlayStoreMode() }
                    )
                }
            }
        }

        // Section: Akun, Privasi & Keamanan (Bab 2, 13 & 14)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Autentikasi & Privasi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SettingsRowItem(
                        icon = Icons.Default.SwitchAccount,
                        title = "Ganti Akun / Masuk",
                        subtitle = "Login dengan akun email atau kredensial lain",
                        onClick = onOpenAuthModal
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.Shield,
                        title = "Kebijakan Privasi & GDPR",
                        subtitle = "Transparansi izin Auto-Read, Kamera, & Mic",
                        onClick = onOpenProminentDisclosure
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Keluar dari Akun",
                        subtitle = "Sesi login tetap tersimpan hingga Anda logout",
                        onClick = onLogout
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Delete Account permanently (Bab 2 & Bab 13)
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

        // Section: Google Drive Ready & Cloud Security
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Sistem & Sinkronisasi Cloud",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SettingsRowItem(
                        icon = Icons.Default.CloudSync,
                        title = "Google Drive Sync (Hierarki Folder)",
                        subtitle = "Folder: FinSmart/Backup Data & App Updates"
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsRowItem(
                        icon = Icons.Default.Security,
                        title = "Keamanan Data (Enkripsi AES-256)",
                        subtitle = "Data tersimpan 100% lokal & privat di perangkat"
                    )
                }
            }
        }

        // Footer Info: Bab 1 requirement "© 2026 Lukas beserta nomor versi aplikasi saat ini"
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${AppConfig.APP_NAME} Android • v${AppConfig.APP_VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "© ${AppConfig.COPYRIGHT_YEAR} ${AppConfig.APP_AUTHOR}. All Rights Reserved.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = "Dual-Mode Architecture (APK Non-Play Store & Play Store Ready)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
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
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = Color.Unspecified,
    iconTint: Color = FinSmartPrimary,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (iconTint == ExpenseRed) ExpenseRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
                )
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}
