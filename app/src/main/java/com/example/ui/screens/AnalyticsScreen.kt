package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.components.CashflowChartCard
import com.example.ui.components.FinSmartIcons
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.CashflowDataPoint
import com.example.ui.viewmodel.TimeframeFilter
import com.example.util.CurrencyFormatter
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    transactions: List<TransactionItem>,
    chartData: List<CashflowDataPoint>,
    selectedTimeframe: TimeframeFilter,
    onTimeframeSelected: (TimeframeFilter) -> Unit,
    accountMode: AccountMode,
    isPrivacyMode: Boolean,
    modifier: Modifier = Modifier
) {
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netSavings = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).coerceIn(0.0, 100.0) else 0.0

    // Health Score calculation
    val healthScore = when {
        totalIncome == 0.0 && totalExpense == 0.0 -> 75 // Neutral
        savingsRate >= 30 -> 95
        savingsRate >= 20 -> 85
        savingsRate >= 10 -> 75
        savingsRate >= 0 -> 60
        else -> 40
    }

    // Category breakdown
    val expenseCategories = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val donutAnim = remember { Animatable(0f) }
    LaunchedEffect(expenseCategories) {
        donutAnim.snapTo(0f)
        donutAnim.animateTo(1f, animationSpec = tween(durationMillis = 700))
    }

    val categoryColors = listOf(
        Color(0xFFEF4444),
        Color(0xFFF59E0B),
        Color(0xFFEC4899),
        Color(0xFF8B5CF6),
        Color(0xFF06B6D4),
        Color(0xFF3B82F6),
        Color(0xFF10B981),
        Color(0xFF64748B)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("analytics_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp
        )
    ) {
        item {
            Column {
                Text(
                    text = "Analitik & Kesehatan Finansial",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Laporan wawasan cerdas arus kas (${accountMode.displayName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // AI Cashflow Health Score Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Cashflow Health Score",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                healthScore >= 85 -> "Keuangan Sangat Sehat (Optimal)"
                                healthScore >= 70 -> "Keuangan Stabil & Terkendali"
                                else -> "Perlu Perhatian pada Pengeluaran"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (healthScore >= 70) IncomeGreen else ExpenseRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rasio tabungan: ${savingsRate.roundToInt()}% dari total pemasukan.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Circular Health Gauge
                    val gaugeTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(70.dp)
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            drawCircle(
                                color = gaugeTrackColor,
                                style = Stroke(width = 6.dp.toPx())
                            )
                            drawArc(
                                color = if (healthScore >= 70) IncomeGreen else ExpenseRed,
                                startAngle = -90f,
                                sweepAngle = (healthScore / 100f) * 360f,
                                useCenter = false,
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "$healthScore",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Cashflow Chart
        item {
            CashflowChartCard(
                dataPoints = chartData,
                selectedTimeframe = selectedTimeframe,
                onTimeframeSelected = onTimeframeSelected
            )
        }

        // Category Breakdown (Donut + List)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Proporsi Pengeluaran per Kategori",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (expenseCategories.isEmpty() || totalExpense == 0.0) {
                        Text(
                            text = "Belum ada data pengeluaran untuk dianalisis.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        // Donut Graphic
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(130.dp)) {
                                var currentAngle = -90f
                                expenseCategories.forEachIndexed { index, (_, amount) ->
                                    val sweep = ((amount / totalExpense) * 360f * donutAnim.value).toFloat()
                                    val color = categoryColors[index % categoryColors.size]
                                    drawArc(
                                        color = color,
                                        startAngle = currentAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 22.dp.toPx())
                                    )
                                    currentAngle += sweep
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (isPrivacyMode) "Rp •••" else CurrencyFormatter.formatCompactRupiah(totalExpense),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category List Items
                        expenseCategories.forEachIndexed { index, (catName, amount) ->
                            val color = categoryColors[index % categoryColors.size]
                            val percent = if (totalExpense > 0) ((amount / totalExpense) * 100).roundToInt() else 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(color, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = catName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "$percent%",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isPrivacyMode) "Rp •••" else CurrencyFormatter.formatRupiah(amount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
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
