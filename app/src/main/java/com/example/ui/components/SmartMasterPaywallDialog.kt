package com.example.ui.components
import com.example.util.AppStrings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.RewardedAdRewardManager
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import android.app.Activity
import com.example.util.AdManager
import com.example.util.BillingManager
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState

enum class PlanChoice {
    MONTHLY,
    YEARLY
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMasterPaywallDialog(
    currentUser: User? = null,
    onDismiss: () -> Unit,
    onRequestUpgrade: ((PlanChoice) -> Unit)? = null,
    onWatchRewardedAd: () -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPlan by remember { mutableStateOf(PlanChoice.YEARLY) }

    // Dynamic Google Play Billing prices
    val officialMonthlyPrice by BillingManager.monthlyPrice.collectAsState()
    val officialYearlyPrice by BillingManager.yearlyPrice.collectAsState()
    val billingConnection by BillingManager.connectionStatus.collectAsState()
    val isBillingProcessing by BillingManager.isProcessing.collectAsState()

    val displayMonthlyPrice = officialMonthlyPrice ?: "Rp19.000"
    val displayYearlyPrice = officialYearlyPrice ?: "Rp149.000"


    androidx.compose.runtime.LaunchedEffect(currentUser) {
        if (AdManager.shouldShowAds(currentUser)) {
            AdManager.loadRewardedAd(context, currentUser)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("smart_master_paywall_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SMART MASTER UPGRADE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Hero Title
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Diamond,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = AppStrings.t("Buka Akses DompetNalar Tanpa Batas 👑", "Unlock Unlimited DompetNalar Access 👑"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Upgrade ke Smart Master untuk menikmati seluruh fitur eksklusif AI, pemindaian nota otomatis, dan analitik finansial tanpa batas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Feature Highlights
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaywallFeatureRow("Input Transaksi via Chat AI & Voice Logging Tanpa Batas")
                PaywallFeatureRow(AppStrings.t("Scan & OCR Nota/Struk Otomatis via DompetNalar OCR", "Automatic Receipt Scan & OCR via DompetNalar OCR"))
                PaywallFeatureRow("AI Cashflow Health Score & Laporan Mendalam (PDF/Excel)")
                PaywallFeatureRow("Pengalaman 100% Bebas Iklan (Ad-Free)")
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Pricing Plans
            Text(
                text = AppStrings.t("Pilih Paket Berlangganan:", "Select Subscription Plan:"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. TAHUNAN (PALING POPULER & HEMAT)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { selectedPlan = PlanChoice.YEARLY }
                    .testTag("plan_yearly"),
                shape = RoundedCornerShape(16.dp),
                border = if (selectedPlan == PlanChoice.YEARLY) BorderStroke(2.dp, Color(0xFFD97706)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedPlan == PlanChoice.YEARLY) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.t("👑 Smart Master — Tahunan", "👑 Smart Master — Annual"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFD97706),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "HEMAT 45%",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = displayYearlyPrice,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF92400E)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "/ tahun",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 Hanya ~Rp12.400/bln. Paling hemat untuk kelola finansial setahun penuh!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB45309)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. BULANAN
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { selectedPlan = PlanChoice.MONTHLY }
                    .testTag("plan_monthly"),
                shape = RoundedCornerShape(16.dp),
                border = if (selectedPlan == PlanChoice.MONTHLY) BorderStroke(2.dp, FinSmartPrimary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedPlan == PlanChoice.MONTHLY) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.t("⭐ Smart Master — Bulanan", "⭐ Smart Master — Monthly"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = displayMonthlyPrice,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "/ bln",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.t("Cocok untuk mencoba seluruh fitur AI DompetNalar secara fleksibel per bulan.", "Perfect to try all DompetNalar AI features flexibly per month."),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

                // Google Play Official Billing Interface
                val targetProductId = when (selectedPlan) {
                    PlanChoice.MONTHLY -> BillingManager.PRODUCT_ID_MONTHLY
                    PlanChoice.YEARLY -> BillingManager.PRODUCT_ID_YEARLY
                }

                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            val result = BillingManager.launchPurchaseFlow(activity, targetProductId)
                            if (result.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                                val msg = result.debugMessage.ifBlank {
                                    if (result.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
                                        "Produk langganan sedang dipersiapkan di Google Play Console."
                                    } else {
                                        "Layanan Google Play belum siap (Kode: ${result.responseCode})."
                                    }
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Konteks aktivitas tidak valid untuk peluncuran billing.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_google_play_billing"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58)),
                    enabled = !isBillingProcessing
                ) {
                    if (isBillingProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.example.util.AppStrings.t("Memproses Transaksi...", "Processing Transaction..."), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Beli via Google Play",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Restore Purchases Button
                TextButton(
                    onClick = {
                        BillingManager.restorePurchases { count, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_restore_purchases"),
                    enabled = !isBillingProcessing
                ) {
                    if (isBillingProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Memeriksa Google Play...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pulihkan Pembelian (Restore Purchases)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

            // All active SmartMaster users (paid, permanent, developer granted, or active temporary)
            // are completely ad-free and must never see rewarded ad offers or prompts
            val isAdEligible = AdManager.shouldShowAds(currentUser)

            if (isAdEligible) {
                var isAdLoading by remember { mutableStateOf(false) }
                val isDailyClaimed = RewardedAdRewardManager.isDailyRewardClaimed(context, currentUser)

                Spacer(modifier = Modifier.height(16.dp))

                if (isDailyClaimed) {
                    // Notice: Today's SmartMaster reward has already been claimed
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = AppStrings.t(
                                    "Hadiah SmartMaster hari ini sudah digunakan. Anda masih dapat menonton iklan, tetapi tidak memperoleh hadiah tambahan.",
                                    "Today's SmartMaster reward has already been claimed. You can still watch ads, but no additional reward will be granted."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Badge: 1 SmartMaster reward available today (60 minutes)
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.t(
                                    "🎁 1 Hadiah SmartMaster Tersedia Hari Ini (60 Menit)",
                                    "🎁 1 SmartMaster Reward Available Today (60 Minutes)"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (isAdLoading) return@OutlinedButton
                        val activity = context.findActivity()
                        if (activity != null) {
                            AdManager.loadAndShowRewardedAd(
                                activity = activity,
                                user = currentUser,
                                onLoading = { isAdLoading = it },
                                onEarnedReward = {
                                    Toast.makeText(
                                        context,
                                        if (isDailyClaimed) {
                                            AppStrings.t(
                                                "Iklan selesai ditonton. Hadiah hari ini sudah digunakan sebelumnya.",
                                                "Ad finished. Today's reward was already claimed earlier."
                                            )
                                        } else {
                                            AppStrings.t("Iklan selesai ditonton! Terima kasih.", "Rewarded ad completed! Thank you.")
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onWatchRewardedAd()
                                },
                                onAdClosedOrFailed = { errorMsg ->
                                    if (errorMsg != null) {
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Iklan ditutup.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            Toast.makeText(context, "Iklan tidak dapat dimuat pada konteks ini.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_watch_rewarded_ad"),
                    enabled = !isAdLoading,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, if (isAdLoading) Color.Gray else Color(0xFF10B981))
                ) {
                    if (isAdLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Memuat Iklan Video...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                    } else {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDailyClaimed) {
                                AppStrings.t("🎬 Tonton Iklan (Tanpa Hadiah)", "🎬 Watch Ad (No Reward)")
                            } else {
                                AppStrings.t("🎬 Tonton Iklan untuk Akses SmartMaster 1 Jam", "🎬 Watch Ad for 1 Hour SmartMaster Access")
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaywallFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color(0xFFD97706), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
