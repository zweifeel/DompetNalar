package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.IncomeGreenDark
import com.example.util.EntitlementManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDevPanelSheet(
    users: List<User>,
    onDismiss: () -> Unit,
    onUpgradeUser: (Long) -> Unit = {},
    onUpgradeUserWithDuration: (Long, Long) -> Unit = { id, _ -> onUpgradeUser(id) },
    onDowngradeUser: (Long) -> Unit
) {
    if (!com.example.BuildConfig.DEBUG) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<UserRole?>(null) }

    // State for Duration Selection & Confirmation Dialogs
    var userForDurationPicker by remember { mutableStateOf<User?>(null) }
    var selectedDurationDays by remember { mutableStateOf(30L) }
    var customDurationInput by remember { mutableStateOf("") }
    var isCustomDurationSelected by remember { mutableStateOf(false) }
    var customDurationError by remember { mutableStateOf<String?>(null) }

    var userPendingConfirmation by remember { mutableStateOf<Pair<User, Long>?>(null) }
    var userPendingRevoke by remember { mutableStateOf<User?>(null) }

    val filteredUsers = users.filter { user ->
        val query = searchQuery.trim()
        val matchQuery = query.isBlank() ||
                user.id.toString().contains(query) ||
                user.username.contains(query, ignoreCase = true) ||
                user.email.contains(query, ignoreCase = true)

        val matchRole = selectedRoleFilter == null || user.role == selectedRoleFilter
        matchQuery && matchRole
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("master_dev_panel_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
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
                            .size(38.dp)
                            .background(GoldAccent.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Panel Master Dev",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFD97706),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "SLEEPYKOALA",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Kelola ${users.size} akun, durasi membership & approval Smart Master 👑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = com.example.util.AppStrings.t("Tutup", "Close"))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar (ID, Nama, Email)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari User ID, nama, atau email pengguna...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_search_master_users")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Role Filter Chips & Result Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedRoleFilter == null,
                        onClick = { selectedRoleFilter = null },
                        label = { Text("Semua", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedRoleFilter == UserRole.FREE_MEMBER,
                        onClick = { selectedRoleFilter = UserRole.FREE_MEMBER },
                        label = { Text("Member", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedRoleFilter == UserRole.SMART_MASTER,
                        onClick = { selectedRoleFilter = UserRole.SMART_MASTER },
                        label = { Text("Smart Master", fontSize = 11.sp) }
                    )
                }
                Text(
                    text = "${filteredUsers.size} dari ${users.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User List / Empty State
            if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Tidak ada pengguna yang cocok dengan \"$searchQuery\"" else "Tidak ada pengguna pada kategori ini",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    items(filteredUsers, key = { it.id }) { user ->
                        UserManagementCard(
                            user = user,
                            onOpenDurationPicker = {
                                userForDurationPicker = user
                                isCustomDurationSelected = false
                                selectedDurationDays = 30L
                                customDurationError = null
                            },
                            onRevoke = { userPendingRevoke = user }
                        )
                    }
                }
            }
        }
    }

    // 1. DIALOG PEMILIHAN DURASI MEMBERSHIP
    if (userForDurationPicker != null) {
        val target = userForDurationPicker!!
        AlertDialog(
            onDismissRequest = { userForDurationPicker = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = GoldAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pilih Durasi Smart Master", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Target: [ID #${target.id}] ${target.username} (${target.email})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    HorizontalDivider()

                    Text("Preset Durasi:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val presets = listOf(7L, 14L, 30L, 90L, 180L, 365L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.take(3).forEach { days ->
                            FilterChip(
                                selected = !isCustomDurationSelected && selectedDurationDays == days,
                                onClick = {
                                    isCustomDurationSelected = false
                                    selectedDurationDays = days
                                    customDurationError = null
                                },
                                label = { Text("$days H", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.takeLast(3).forEach { days ->
                            FilterChip(
                                selected = !isCustomDurationSelected && selectedDurationDays == days,
                                onClick = {
                                    isCustomDurationSelected = false
                                    selectedDurationDays = days
                                    customDurationError = null
                                },
                                label = { Text("$days H", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    FilterChip(
                        selected = isCustomDurationSelected,
                        onClick = { isCustomDurationSelected = true },
                        label = { Text("Durasi Khusus (Kustom)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isCustomDurationSelected) {
                        OutlinedTextField(
                            value = customDurationInput,
                            onValueChange = {
                                customDurationInput = it.filter { char -> char.isDigit() }
                                customDurationError = null
                            },
                            label = { Text("Jumlah Hari (1 - 3650)") },
                            placeholder = { Text("Contoh: 45") },
                            isError = customDurationError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (customDurationError != null) {
                            Text(customDurationError!!, color = ExpenseRed, fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val daysToApply = if (isCustomDurationSelected) {
                            val validation = EntitlementManager.validateCustomDuration(customDurationInput)
                            if (validation.isFailure) {
                                customDurationError = validation.exceptionOrNull()?.message
                                return@Button
                            }
                            validation.getOrThrow()
                        } else {
                            selectedDurationDays
                        }
                        userPendingConfirmation = Pair(target, daysToApply)
                        userForDurationPicker = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreenDark)
                ) {
                    Text(com.example.util.AppStrings.t("Lanjutkan ke Konfirmasi", "Proceed to Confirmation"))
                }
            },
            dismissButton = {
                TextButton(onClick = { userForDurationPicker = null }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 2. DIALOG KONFIRMASI PEMBERIAN MEMBERSHIP
    if (userPendingConfirmation != null) {
        val (targetUser, days) = userPendingConfirmation!!
        val now = System.currentTimeMillis()
        val newExpiry = now + (days * 86400000L)
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))

        AlertDialog(
            onDismissRequest = { userPendingConfirmation = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreenDark) },
            title = { Text(com.example.util.AppStrings.t("Konfirmasi Pemberian Membership", "Confirm Membership Grant"), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Harap tinjau detail perubahan sebelum menyimpan:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Target Pengguna: [ID #${targetUser.id}] ${targetUser.username}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("• Email: ${targetUser.email}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Status Sebelumnya: ${targetUser.role.displayName}", fontSize = 11.sp)
                    Text("• Durasi Tambahan: $days Hari", fontWeight = FontWeight.Bold, color = IncomeGreenDark, fontSize = 12.sp)
                    Text("• Masa Berlaku Baru: ${sdf.format(Date(newExpiry))}", fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpgradeUserWithDuration(targetUser.id, days)
                        userPendingConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreenDark)
                ) {
                    Text(com.example.util.AppStrings.t("Konfirmasi & Simpan", "Confirm & Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { userPendingConfirmation = null }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }

    // 3. DIALOG KONFIRMASI PENCABUTAN MEMBERSHIP
    if (userPendingRevoke != null) {
        val targetUser = userPendingRevoke!!
        AlertDialog(
            onDismissRequest = { userPendingRevoke = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed) },
            title = { Text(com.example.util.AppStrings.t("Konfirmasi Cabut Membership", "Confirm Membership Revocation"), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin mencabut status Smart Master dari pengguna [ID #${targetUser.id}] ${targetUser.username} (${targetUser.email})? Akun ini akan dikembalikan ke status Free Member.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDowngradeUser(targetUser.id)
                        userPendingRevoke = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Ya, Cabut Membership")
                }
            },
            dismissButton = {
                TextButton(onClick = { userPendingRevoke = null }) {
                    Text(com.example.util.AppStrings.cancel)
                }
            }
        )
    }
}

@Composable
fun UserManagementCard(
    user: User,
    onOpenDurationPicker: () -> Unit,
    onRevoke: () -> Unit
) {
    val entitlementStatus = EntitlementManager.evaluate(user)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                when (user.role) {
                                    UserRole.MASTER_DEV -> Color(0xFFD97706)
                                    UserRole.SMART_MASTER -> GoldAccent
                                    UserRole.FREE_MEMBER -> FinSmartPrimary
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.username.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.username,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "#${user.id}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = user.email,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Role Badge
                Surface(
                    color = when (user.role) {
                        UserRole.MASTER_DEV -> Color(0xFFD97706).copy(alpha = 0.2f)
                        UserRole.SMART_MASTER -> GoldAccent.copy(alpha = 0.2f)
                        UserRole.FREE_MEMBER -> FinSmartPrimary.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = user.role.badgeText,
                        color = when (user.role) {
                            UserRole.MASTER_DEV -> Color(0xFFD97706)
                            UserRole.SMART_MASTER -> Color(0xFFD97706)
                            UserRole.FREE_MEMBER -> FinSmartPrimary
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expiry & Entitlement Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Masa Aktif: ${entitlementStatus.getFormattedExpiry()}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            if (user.role == UserRole.MASTER_DEV) {
                Text(
                    text = "👑 Akun Master Developer (Akses Penuh Tanpa Batas)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD97706)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenDurationPicker,
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeGreenDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_manage_membership_${user.id}")
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (user.role == UserRole.SMART_MASTER) "Perpanjang / Edit 👑" else "Beri Smart Master 👑",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (user.role == UserRole.SMART_MASTER) {
                        OutlinedButton(
                            onClick = onRevoke,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(0.7f)
                                .height(36.dp)
                                .testTag("btn_downgrade_member_${user.id}")
                        ) {
                            Text("Cabut", fontSize = 11.sp, color = ExpenseRed)
                        }
                    }
                }
            }
        }
    }
}
