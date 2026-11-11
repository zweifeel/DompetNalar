package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.FeedbackViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevFeedbackViewerScreen(
    currentUser: User? = null,
    onNavigateBack: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    feedbackViewModel: FeedbackViewModel = viewModel(),
    isGoogleSignInLoading: Boolean = false,
    googleSignInErrorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val feedbacks by feedbackViewModel.allFeedbacks.collectAsState()
    val cloudFeedbacks by feedbackViewModel.cloudFeedbacks.collectAsState()
    val cloudFetchStatus by feedbackViewModel.cloudFetchStatus.collectAsState()
    val isCloudLoading by feedbackViewModel.isCloudLoading.collectAsState()
    val syncedCloudIds by feedbackViewModel.syncedCloudIds.collectAsState()
    val unsyncedFeedbacks by feedbackViewModel.unsyncedFeedbacks.collectAsState()
    val isRetryingSync by feedbackViewModel.isRetryingSync.collectAsState()
    val isFirebaseAuthActive by feedbackViewModel.isFirebaseAuthActive.collectAsState()
    val currentFirebaseAuthEmail by feedbackViewModel.currentFirebaseAuthEmail.collectAsState()

    LaunchedEffect(Unit) {
        feedbackViewModel.fetchCloudFeedbacks()
    }

    // Authorization Verification: Hanya peran MASTER_DEV terverifikasi atau email developer tepercaya
    val isAuthorized = currentUser?.role == UserRole.MASTER_DEV ||
            com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(currentUser?.email)

    if (!isAuthorized) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Akses Ditolak", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(com.example.util.AppStrings.t("Akses Terbatas: Master Developer", "Restricted Access: Master Developer"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = com.example.util.AppStrings.t(
                                "Layar ini adalah alat internal pengembang. Anda harus masuk dengan kredensial Master Developer yang terotorisasi untuk melihat umpan balik pengguna.",
                                "This screen is an internal developer tool. You must sign in with authorized Master Developer credentials to view user feedback."
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text(com.example.util.AppStrings.t("Kembali ke Pengaturan", "Back to Settings"))
                        }
                    }
                }
            }
        }
        return
    }

    // Merge Room local feedbacks with remote Firestore feedbacks without duplicates
    val combinedFeedbacks = remember(feedbacks, cloudFeedbacks) {
        val localIds = feedbacks.map { it.id }.toSet()
        val cloudOnly = cloudFeedbacks.filter { it.id !in localIds }
        (feedbacks + cloudOnly).sortedByDescending { it.timestamp }
    }

    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    val filteredFeedbacks = if (selectedCategoryFilter == null) {
        combinedFeedbacks
    } else {
        combinedFeedbacks.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    // Group feedbacks by User / Email
    val groupedFeedbacks = filteredFeedbacks.groupBy { "${it.userName} • ${it.userEmail}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val devUsername = currentUser?.username?.takeIf { it.isNotBlank() }
                        ?: if (currentUser != null && !currentUser.isGuest && (currentUser.role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(currentUser.email))) com.example.util.AppStrings.t("Pengembang DompetNalar", "DompetNalar Developer")
                        else com.example.util.AppStrings.t("Pengguna DompetNalar", "DompetNalar User")
                    val devEmail = currentUser?.email?.takeIf { it.isNotBlank() } ?: com.example.util.AppStrings.t("Tidak Ada Email", "No Email")

                    Column {
                        Text(
                            text = "Developer Feedback Viewer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        val cloudText = if (isFirebaseAuthActive) {
                            " • Cloud: ${currentFirebaseAuthEmail ?: "Aktif"}"
                        } else {
                            " • Cloud: Offline"
                        }
                        Text(
                            text = "${combinedFeedbacks.size} Masukan • Admin Lokal: $devUsername$cloudText",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_dev_feedback")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { feedbackViewModel.fetchCloudFeedbacks() },
                        enabled = !isCloudLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang Cloud")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("dev_feedback_viewer_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            val devUsername = currentUser?.username?.takeIf { it.isNotBlank() }
                ?: if (currentUser != null && !currentUser.isGuest && (currentUser.role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(currentUser.email))) com.example.util.AppStrings.t("Pengembang DompetNalar", "DompetNalar Developer")
                else com.example.util.AppStrings.t("Pengguna DompetNalar", "DompetNalar User")
            val devEmail = currentUser?.email?.takeIf { it.isNotBlank() } ?: com.example.util.AppStrings.t("Tidak Ada Email", "No Email")

            // Prominent Developer Credentials Card (Local vs Cloud Differentiation)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Lokal (Room SQLite): $devUsername • $devEmail",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isFirebaseAuthActive) Color(0xFF10B981) else Color(0xFF6B7280),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFirebaseAuthActive) {
                                com.example.util.AppStrings.t("Sesi Cloud: Terautentikasi via Firebase Auth ($currentFirebaseAuthEmail)", "Cloud Session: Authenticated via Firebase Auth ($currentFirebaseAuthEmail)")
                            } else {
                                com.example.util.AppStrings.t("Sesi Cloud: Belum Terautentikasi (Offline / Hanya Cache Lokal Room)", "Cloud Session: Unauthenticated (Offline / Room Local Cache Only)")
                            },
                            fontSize = 11.sp,
                            color = if (isFirebaseAuthActive) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Google Sign-In prompt if Cloud session is not active
            if (!isFirebaseAuthActive) {
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Akses Cloud Firestore Memerlukan Google Sign-In",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = com.example.util.AppStrings.t("Aturan keamanan Firebase Cloud mewajibkan token Google yang terdaftar di Developer Whitelist. Sesi lokal hanya dapat membaca cache offline lokal.", "Firebase Cloud security rules require a Google token registered in the Developer Whitelist. Local session can only read offline cache."),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!googleSignInErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠️ $googleSignInErrorMessage",
                                fontSize = 11.sp,
                                color = ExpenseRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onGoogleSignIn,
                            enabled = !isGoogleSignInLoading && !isCloudLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_connect_google_dev"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FinSmartPrimary)
                        ) {
                            if (isGoogleSignInLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Menghubungkan Akun Google...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hubungkan Akun Google Developer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Truthful Architecture & Sync Status Banner
            val isDenied = cloudFetchStatus?.contains("Ditolak", ignoreCase = true) == true ||
                    cloudFetchStatus?.contains("Gagal", ignoreCase = true) == true
            Surface(
                color = if (isDenied) ExpenseRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDenied) Icons.Default.Lock else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isDenied) ExpenseRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = cloudFetchStatus
                                ?: "ℹ️ Status Penyimpanan: Backend Firebase Cloud Firestore aktif. Masukan disimpan secara offline-first di database Room lokal dan disinkronisasikan saat daring dengan sesi Google terotorisasi.",
                            fontSize = 11.sp,
                            color = if (isDenied) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isCloudLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = FinSmartPrimary
                        )
                    } else if (isDenied) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { feedbackViewModel.fetchCloudFeedbacks() },
                            modifier = Modifier
                                .align(Alignment.End)
                                .height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Coba Lagi", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Unsynced Feedbacks Retry Banner
            if (unsyncedFeedbacks.isNotEmpty()) {
                Surface(
                    color = Color(0xFF3B82F6).copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = com.example.util.AppStrings.t("Menunggu Sinkronisasi Cloud (${unsyncedFeedbacks.size} Masukan)", "Pending Cloud Sync (${unsyncedFeedbacks.size} Feedbacks)"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = com.example.util.AppStrings.t("Tersimpan di Room SQLite lokal perangkat.", "Stored in local device Room SQLite."),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                feedbackViewModel.retrySyncPendingFeedbacks { synced, failed ->
                                    val msg = com.example.util.AppStrings.t("Sinkronisasi: $synced berhasil dikirim, $failed gagal.", "Sync: $synced sent successfully, $failed failed.")
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isRetryingSync,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            if (isRetryingSync) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(com.example.util.AppStrings.t("Sinkronkan", "Sync"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Category Filter Chips
            val catLabels = mapOf(
                "Bug / Kendala" to com.example.util.AppStrings.t("Bug / Kendala", "Bug / Issue"),
                "Saran Fitur" to com.example.util.AppStrings.t("Saran Fitur", "Feature Suggestion"),
                "Desain / UI" to com.example.util.AppStrings.t("Desain / UI", "Design / UI")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                    label = { Text("${com.example.util.AppStrings.t("Semua", "All")} (${combinedFeedbacks.size})", fontSize = 11.sp) }
                )
                listOf("Bug / Kendala", "Saran Fitur", "Desain / UI").forEach { cat ->
                    val count = combinedFeedbacks.count { it.category.equals(cat, ignoreCase = true) }
                    val displayCat = catLabels[cat] ?: cat
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                        label = { Text("$displayCat ($count)", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredFeedbacks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Feedback,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedCategoryFilter == null) com.example.util.AppStrings.t("Belum ada masukan dari pengguna.", "No user feedback yet.") else "${com.example.util.AppStrings.t("Tidak ada masukan pada kategori", "No feedback in category")} \"${catLabels[selectedCategoryFilter] ?: selectedCategoryFilter}\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    groupedFeedbacks.forEach { (userHeader, userFeedbackList) ->
                        item(key = userHeader) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_user_feedback_group"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // User Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(FinSmartPrimary.copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = FinSmartPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                val parts = userHeader.split(" • ")
                                                Text(
                                                    text = parts.getOrNull(0) ?: "Pengguna",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = parts.getOrNull(1) ?: "-",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${userFeedbackList.size} Masukan",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )

                                    // Feedback Items for this User
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        userFeedbackList.forEach { feedback ->
                                            val isCloud = feedback.isSynced || syncedCloudIds.contains(feedback.id) || cloudFeedbacks.any { it.id == feedback.id }
                                            FeedbackItemRow(
                                                feedback = feedback,
                                                isCloudSynced = isCloud,
                                                onCopy = {
                                                    clipboardManager.setText(
                                                        AnnotatedString(
                                                            "[DompetNalar Feedback]\n${com.example.util.AppStrings.t("Dari", "From")}: ${feedback.userName} (${feedback.userEmail})\n${com.example.util.AppStrings.t("Kategori", "Category")}: ${feedback.category}\nRating: ${feedback.rating}/5\n${com.example.util.AppStrings.t("Pesan", "Message")}: ${feedback.message}"
                                                        )
                                                    )
                                                    Toast.makeText(context, com.example.util.AppStrings.t("Masukan disalin ke papan klip!", "Feedback copied to clipboard!"), Toast.LENGTH_SHORT).show()
                                                },
                                                onDelete = {
                                                    feedbackViewModel.deleteFeedback(feedback.id)
                                                    Toast.makeText(context, com.example.util.AppStrings.t("Masukan berhasil dihapus.", "Feedback deleted successfully."), Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedbackItemRow(
    feedback: com.example.data.model.FeedbackItem,
    isCloudSynced: Boolean = false,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", com.example.util.LocaleHelper.getActiveLocale()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Category & Storage Status & Star Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val catDisplay = when (feedback.category) {
                        "Bug / Kendala" -> com.example.util.AppStrings.t("Bug / Kendala", "Bug / Issue")
                        "Saran Fitur" -> com.example.util.AppStrings.t("Saran Fitur", "Feature Suggestion")
                        "Desain / UI" -> com.example.util.AppStrings.t("Desain / UI", "Design / UI")
                        else -> feedback.category
                    }
                    Surface(
                        color = when (feedback.category) {
                            "Bug / Kendala" -> ExpenseRed.copy(alpha = 0.15f)
                            "Saran Fitur" -> FinSmartPrimary.copy(alpha = 0.15f)
                            "Desain / UI" -> GoldAccent.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = catDisplay,
                            color = when (feedback.category) {
                                "Bug / Kendala" -> ExpenseRed
                                "Saran Fitur" -> FinSmartPrimary
                                "Desain / UI" -> Color(0xFFD97706)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Truthful Storage Status Badge
                    val (storageText, storageColor, storageBg) = when {
                        isCloudSynced -> Triple(com.example.util.AppStrings.t("Tersimpan di Cloud", "Stored in Cloud"), FinSmartPrimary, FinSmartPrimary.copy(alpha = 0.15f))
                        feedback.userName in listOf("Siti Rahma") -> Triple(com.example.util.AppStrings.t("Tersimpan Lokal", "Stored Locally"), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        else -> Triple(com.example.util.AppStrings.t("Menunggu Sinkronisasi", "Pending Sync"), Color(0xFFD97706), Color(0xFFD97706).copy(alpha = 0.15f))
                    }
                    Surface(
                        color = storageBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = storageText,
                            color = storageColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Star Rating
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(feedback.rating) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${feedback.rating}/5",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message Body
            Text(
                text = feedback.message,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer (Timestamp & Action Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(feedback.timestamp)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Salin",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = com.example.util.AppStrings.t("Hapus", "Delete"),
                            tint = ExpenseRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
