package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.*

/**
 * FirstRunOnboardingDialog
 *
 * Pop-up pengaturan awal instalasi (Fase 2) untuk memilih bahasa antarmuka
 * dan mata uang utama saat aplikasi dibuka pertama kali.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstRunOnboardingDialog(
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedLanguage by remember { mutableStateOf(AppLanguage.SYSTEM) }
    var selectedCurrencyMode by remember { mutableStateOf(CurrencyMode.FOLLOW_DEVICE) }
    var selectedCurrency by remember { mutableStateOf(AppCurrency.IDR) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    val detectedDeviceCurrency = remember { CurrencyFormatter.resolveDeviceCurrency() }

    Dialog(
        onDismissRequest = { /* Modal wajib diselesaikan sebelum masuk dasbor */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("first_run_onboarding_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Brand Header & Welcome Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = AppStrings.t("Selamat Datang di DompetNalar", "Welcome to DompetNalar"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = AppStrings.t(
                        "Atur preferensi bahasa antarmuka dan mata uang utama Anda untuk memulai pembukuan yang rapi.",
                        "Set your display language and primary currency preferences to start tidy bookkeeping."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 1: BAHASA ANTARMUKA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.t("1. Bahasa Antarmuka", "1. Display Language"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 1.1: Ikuti Sistem HP
                LanguageOptionCard(
                    title = AppLanguage.SYSTEM.displayName,
                    subtitle = AppLanguage.SYSTEM.subtitle,
                    isSelected = selectedLanguage == AppLanguage.SYSTEM,
                    tag = "opt_lang_system",
                    onClick = { selectedLanguage = AppLanguage.SYSTEM }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1.2: Bahasa Indonesia
                LanguageOptionCard(
                    title = "Bahasa Indonesia",
                    subtitle = "Gunakan Bahasa Indonesia secara penuh",
                    isSelected = selectedLanguage == AppLanguage.INDONESIAN,
                    tag = "opt_lang_id",
                    onClick = { selectedLanguage = AppLanguage.INDONESIAN }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1.3: English
                LanguageOptionCard(
                    title = "English",
                    subtitle = "Use English throughout the application",
                    isSelected = selectedLanguage == AppLanguage.ENGLISH,
                    tag = "opt_lang_en",
                    onClick = { selectedLanguage = AppLanguage.ENGLISH }
                )

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 2: MATA UANG UTAMA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.t("2. Mata Uang Utama", "2. Primary Currency"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2.1: Ikuti Wilayah HP
                CurrencyModeCard(
                    title = AppStrings.t("Ikuti Wilayah HP (Rekomendasi)", "Follow Device Region (Recommended)"),
                    subtitle = AppStrings.t(
                        "Terdeteksi: ${detectedDeviceCurrency.code} (${detectedDeviceCurrency.symbol}) - ${detectedDeviceCurrency.displayName}",
                        "Detected: ${detectedDeviceCurrency.code} (${detectedDeviceCurrency.symbol}) - ${detectedDeviceCurrency.displayName}"
                    ),
                    isSelected = selectedCurrencyMode == CurrencyMode.FOLLOW_DEVICE,
                    tag = "opt_curr_follow_device",
                    onClick = { selectedCurrencyMode = CurrencyMode.FOLLOW_DEVICE }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2.2: Pilih Manual
                CurrencyModeCard(
                    title = AppStrings.t("Pilih Mata Uang Tertentu", "Select Specific Currency"),
                    subtitle = if (selectedCurrencyMode == CurrencyMode.MANUAL) {
                        "${selectedCurrency.code} (${selectedCurrency.symbol}) - ${selectedCurrency.displayName}"
                    } else {
                        AppStrings.t("Pilih IDR, USD, EUR, SGD, MYR, dll.", "Select IDR, USD, EUR, SGD, MYR, etc.")
                    },
                    isSelected = selectedCurrencyMode == CurrencyMode.MANUAL,
                    tag = "opt_curr_manual",
                    onClick = { selectedCurrencyMode = CurrencyMode.MANUAL }
                )

                if (selectedCurrencyMode == CurrencyMode.MANUAL) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Popular Currency Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AppCurrency.IDR,
                            AppCurrency.USD,
                            AppCurrency.EUR,
                            AppCurrency.SGD,
                            AppCurrency.MYR,
                            AppCurrency.JPY
                        ).forEach { curr ->
                            FilterChip(
                                selected = selectedCurrency == curr,
                                onClick = { selectedCurrency = curr },
                                label = { Text("${curr.code} (${curr.symbol})", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Dropdown for more currencies
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { currencyDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${AppStrings.t("Mata Uang Lainnya: ", "Other Currencies: ")}${selectedCurrency.code} (${selectedCurrency.symbol})",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = currencyDropdownExpanded,
                            onDismissRequest = { currencyDropdownExpanded = false }
                        ) {
                            AppCurrency.entries.forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text("${curr.code} (${curr.symbol}) - ${curr.displayName}", fontSize = 13.sp) },
                                    onClick = {
                                        selectedCurrency = curr
                                        currencyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ACTION BUTTON: SIMPAN & MULAI
                Button(
                    onClick = {
                        // 1. Simpan status onboarding terlebih dahulu ke SharedPreferences
                        OnboardingManager.completeOnboarding(context)

                        // 2. Terapkan preferensi mata uang
                        if (selectedCurrencyMode == CurrencyMode.FOLLOW_DEVICE) {
                            CurrencyFormatter.setMode(context, CurrencyMode.FOLLOW_DEVICE)
                        } else {
                            CurrencyFormatter.setCurrency(selectedCurrency, context)
                        }

                        // 3. Terapkan bahasa (reaktif in-memory tanpa crash)
                        LocaleHelper.setLanguage(context, selectedLanguage)

                        // 4. Selesaikan dialog onboarding
                        onCompleted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_save_onboarding"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = AppStrings.t("Simpan & Mulai", "Save & Get Started"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrencyModeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
