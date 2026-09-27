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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.ui.components.AdMobBanner
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

enum class CategoryChartMode {
    DONUT,
    BAR,
    LINE
}

@Composable
fun AnalyticsScreen(
    transactions: List<TransactionItem>,
    chartData: List<CashflowDataPoint>,
    selectedTimeframe: TimeframeFilter,
    onTimeframeSelected: (TimeframeFilter) -> Unit,
    accountMode: AccountMode,
    isPrivacyMode: Boolean,
    currentUser: User? = null,
    modifier: Modifier = Modifier
) {
    var categoryChartMode by remember { mutableStateOf(CategoryChartMode.DONUT) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Proporsi Pengeluaran",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Mode Selector: Donut, Bar, Line
                        Row(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(2.dp)
                        ) {
                            IconButton(
                                onClick = { categoryChartMode = CategoryChartMode.DONUT },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (categoryChartMode == CategoryChartMode.DONUT) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("btn_category_chart_donut")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = "Donut / Pie Chart",
                                    tint = if (categoryChartMode == CategoryChartMode.DONUT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { categoryChartMode = CategoryChartMode.BAR },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (categoryChartMode == CategoryChartMode.BAR) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("btn_category_chart_bar")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Bar Chart",
                                    tint = if (categoryChartMode == CategoryChartMode.BAR) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { categoryChartMode = CategoryChartMode.LINE },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (categoryChartMode == CategoryChartMode.LINE) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("btn_category_chart_line")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = "Line Chart",
                                    tint = if (categoryChartMode == CategoryChartMode.LINE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    if (expenseCategories.isEmpty() || totalExpense == 0.0) {
                        Text(
                            text = "Belum ada data pengeluaran untuk dianalisis.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        when (categoryChartMode) {
                            CategoryChartMode.DONUT -> {
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
                            }
                            CategoryChartMode.BAR -> {
                                // Horizontal Category Bars
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    expenseCategories.take(5).forEachIndexed { index, (catName, amount) ->
                                        val color = categoryColors[index % categoryColors.size]
                                        val fraction = if (totalExpense > 0) ((amount / totalExpense).toFloat() * donutAnim.value).coerceIn(0f, 1f) else 0f
                                        val percent = if (totalExpense > 0) ((amount / totalExpense) * 100).roundToInt() else 0
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(catName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                Text("$percent% • ${CurrencyFormatter.formatRupiah(amount)}", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction)
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(color)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            CategoryChartMode.LINE -> {
                                // Line / Trend chart of Category Distribution
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val width = size.width
                                        val height = size.height - 20.dp.toPx()
                                        val count = expenseCategories.size
                                        if (count > 0) {
                                            val maxCat = expenseCategories.maxOf { it.second }.coerceAtLeast(1.0)
                                            val stepX = if (count > 1) width / (count - 1).toFloat() else width / 2f

                                            val linePath = Path()
                                            val areaPath = Path()

                                            expenseCategories.forEachIndexed { i, (_, amt) ->
                                                val x = if (count > 1) i * stepX else width / 2f
                                                val y = height - (amt / maxCat * height * donutAnim.value).toFloat()
                                                if (i == 0) {
                                                    linePath.moveTo(x, y)
                                                    areaPath.moveTo(x, height)
                                                    areaPath.lineTo(x, y)
                                                } else {
                                                    val prevX = (i - 1) * stepX
                                                    val prevAmt = expenseCategories[i - 1].second
                                                    val prevY = height - (prevAmt / maxCat * height * donutAnim.value).toFloat()
                                                    val cx = (prevX + x) / 2f
                                                    linePath.cubicTo(cx, prevY, cx, y, x, y)
                                                    areaPath.cubicTo(cx, prevY, cx, y, x, y)
                                                }
                                                if (i == count - 1) {
                                                    areaPath.lineTo(x, height)
                                                    areaPath.close()
                                                }
                                            }

                                            drawPath(
                                                path = areaPath,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(FinSmartPrimary.copy(alpha = 0.3f), Color.Transparent)
                                                )
                                            )
                                            drawPath(
                                                path = linePath,
                                                color = FinSmartPrimary,
                                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                            )

                                            // Draw nodes
                                            expenseCategories.forEachIndexed { i, (_, amt) ->
                                                val x = if (count > 1) i * stepX else width / 2f
                                                val y = height - (amt / maxCat * height * donutAnim.value).toFloat()
                                                val color = categoryColors[i % categoryColors.size]
                                                drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(x, y))
                                                drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
                                            }
                                        }
                                    }
                                }
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

        // AdMob Banner Ad at bottom for Free users
        item {
            AdMobBanner(
                user = currentUser,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}
