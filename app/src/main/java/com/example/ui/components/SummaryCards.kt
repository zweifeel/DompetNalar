package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountMode
import com.example.data.model.TransactionType
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedDark
import com.example.ui.theme.ExpenseRedLight
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenDark
import com.example.ui.theme.IncomeGreenLight
import com.example.util.AppStrings
import com.example.ui.viewmodel.CurrencyBalanceSummary
import com.example.util.CurrencyFormatter
import com.example.util.LocaleHelper

@Composable
fun MainBalanceCard(
    totalBalance: Double?,
    totalIncome: Double?,
    totalExpense: Double?,
    accountMode: AccountMode,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onToggleAccountMode: () -> Unit,
    onAddIncomeClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onTransferClick: () -> Unit,
    unassignedWalletsCount: Int = 0,
    onResolveUnassignedWallets: () -> Unit = {},
    multiCurrencyBalances: List<CurrencyBalanceSummary> = emptyList(),
    activeDashboardCurrency: String? = null,
    aggregationError: String? = null,
    onSelectCurrency: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_balance_card"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (accountMode == AccountMode.PERSONAL) {
                            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                        } else {
                            listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA))
                        }
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                // Header: Mode Switcher & Privacy Eye
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Account Mode Chip
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleAccountMode() }
                            .testTag("account_mode_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (accountMode == AccountMode.PERSONAL) Icons.Default.Person else Icons.Default.BusinessCenter,
                                contentDescription = "Mode Akun",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (accountMode == AccountMode.PERSONAL) AppStrings.personalMode else AppStrings.businessMode,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Privacy eye toggle
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("privacy_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPrivacyMode) AppStrings.showBalance else AppStrings.hideBalance,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // DEC-PROD-03: Multi-Currency Portfolio presentation
                val hasMultipleCurrencies = multiCurrencyBalances.size > 1
                if (hasMultipleCurrencies) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (LocaleHelper.isEnglish()) "Multi-Currency Portfolio" else "Portofolio Multi-Mata Uang",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${multiCurrencyBalances.size} " + AppStrings.t("Mata Uang", "Currencies"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("multi_currency_portfolio_section"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        multiCurrencyBalances.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectCurrency(item.currencyCode) }
                                    .testTag("currency_balance_row_${item.currencyCode}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (activeDashboardCurrency == item.currencyCode) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = item.currencyCode,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${item.walletCount} ${if (LocaleHelper.isEnglish()) "wallet" else "dompet"})",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = if (isPrivacyMode) "${item.currencyCode} •••••••"
                                    else CurrencyFormatter.formatCurrency(item.totalBalance, item.currencyCode),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("balance_text_${item.currencyCode}")
                                )
                            }
                        }
                    }
                } else if (aggregationError != null) {
                    Text(
                        text = AppStrings.totalBalance,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = aggregationError,
                        color = Color(0xFFFFB4AB),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("aggregation_error_text")
                    )
                } else if (activeDashboardCurrency.isNullOrBlank() && multiCurrencyBalances.isEmpty()) {
                    Text(
                        text = AppStrings.totalBalance,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onResolveUnassignedWallets() }
                            .testTag("unassigned_currency_prompt")
                    ) {
                        Text(
                            text = if (LocaleHelper.isEnglish()) "Assign wallet currency" else "Pilih / Tetapkan Mata Uang",
                            color = Color(0xFFFFD8E4),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = if (LocaleHelper.isEnglish()) "Assign Currency" else "Tetapkan Mata Uang",
                            tint = Color(0xFFFFD8E4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Single currency or fallback (BUG-09: Consistent currency display)
                    Text(
                        text = AppStrings.totalBalance,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val singleSummary = if (multiCurrencyBalances.size == 1) multiCurrencyBalances.firstOrNull() else null
                    val activeCurrencyCode = activeDashboardCurrency ?: singleSummary?.currencyCode
                    val displayBalance = if (activeCurrencyCode != null) (singleSummary?.totalBalance ?: totalBalance) else null
                    val formattedBalance = if (activeCurrencyCode.isNullOrBlank() || displayBalance == null) {
                        if (LocaleHelper.isEnglish()) "Select Currency Scope" else "Pilih Scope Mata Uang"
                    } else if (isPrivacyMode) {
                        "${activeCurrencyCode} •••••••"
                    } else {
                        CurrencyFormatter.formatCurrency(displayBalance, activeCurrencyCode)
                    }
                    Text(
                        text = formattedBalance,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("total_balance_text")
                    )
                }

                val singleSummary = if (multiCurrencyBalances.size == 1) multiCurrencyBalances.firstOrNull() else null
                val activeScopeCurrency = activeDashboardCurrency ?: singleSummary?.currencyCode

                Spacer(modifier = Modifier.height(18.dp))

                // Income & Expense Breakdown Row (BUG-09: Scoped to matching activeScopeCurrency)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Income Pill
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(IncomeGreen.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = AppStrings.t("Pemasukan", "Income"),
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = AppStrings.income,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMode) "$activeScopeCurrency •••" else if (totalIncome == null || activeScopeCurrency.isNullOrBlank()) "-" else CurrencyFormatter.formatCompactCurrency(totalIncome, activeScopeCurrency),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Expense Pill
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(ExpenseRed.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = AppStrings.expense,
                                    tint = ExpenseRed,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = AppStrings.expense,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMode) "$activeScopeCurrency •••" else if (totalExpense == null || activeScopeCurrency.isNullOrBlank()) "-" else CurrencyFormatter.formatCompactCurrency(totalExpense, activeScopeCurrency),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (unassignedWalletsCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResolveUnassignedWallets() }
                            .testTag("banner_unassigned_wallets")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (LocaleHelper.isEnglish()) "$unassignedWalletsCount wallet(s) have unassigned currency. Tap to set." else "Ada $unassignedWalletsCount dompet belum ditetapkan mata uangnya. Ketuk untuk atur.",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButtons(
    onAddIncomeClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onTransferClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // + Pemasukan Button
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onAddIncomeClick() }
                .testTag("btn_add_income"),
            color = IncomeGreen.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(IncomeGreen.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "+ " + AppStrings.income,
                        tint = IncomeGreenDark,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = AppStrings.income,
                    color = IncomeGreenDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // - Pengeluaran Button
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onAddExpenseClick() }
                .testTag("btn_add_expense"),
            color = ExpenseRed.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(ExpenseRed.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "- " + AppStrings.expense,
                        tint = ExpenseRedDark,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = AppStrings.expense,
                    color = ExpenseRedDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Transfer Button
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onTransferClick() }
                .testTag("btn_transfer"),
            color = FinSmartPrimary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(FinSmartPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = AppStrings.transfer,
                        tint = FinSmartPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = AppStrings.transfer,
                    color = FinSmartPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
