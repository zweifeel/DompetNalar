package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinSmartPrimary
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.CashflowDataPoint
import com.example.ui.viewmodel.TimeframeFilter
import com.example.util.CurrencyFormatter
import kotlin.math.max

enum class ChartType {
    BAR,
    LINE
}

@Composable
fun CashflowChartCard(
    dataPoints: List<CashflowDataPoint>,
    selectedTimeframe: TimeframeFilter,
    onTimeframeSelected: (TimeframeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var chartType by remember { mutableStateOf(ChartType.BAR) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(dataPoints, chartType) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(durationMillis = 600))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cashflow_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title, Chart Mode & Timeframe Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Analisis Arus Kas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Grafik Pemasukan vs Pengeluaran",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Switch Bar vs Line chart
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { chartType = ChartType.BAR },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (chartType == ChartType.BAR) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Bar Chart",
                            tint = if (chartType == ChartType.BAR) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { chartType = ChartType.LINE },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (chartType == ChartType.LINE) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Line Chart",
                            tint = if (chartType == ChartType.LINE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timeframe Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TimeframeFilter.WEEKLY,
                    TimeframeFilter.MONTHLY,
                    TimeframeFilter.YEARLY
                ).forEach { filter ->
                    val isSelected = selectedTimeframe == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPointIndex = null
                            onTimeframeSelected(filter)
                        },
                        label = { Text(filter.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Tooltip Header if tapped
            if (selectedPointIndex != null && selectedPointIndex in dataPoints.indices) {
                val point = dataPoints[selectedPointIndex!!]
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = point.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Masuk: ${CurrencyFormatter.formatRupiah(point.income)}",
                                color = IncomeGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Keluar: ${CurrencyFormatter.formatRupiah(point.expense)}",
                                color = ExpenseRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(IncomeGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pemasukan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ExpenseRed, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pengeluaran", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas Chart Drawing
            val textMeasurer = rememberTextMeasurer()
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant

            if (dataPoints.isEmpty() || dataPoints.all { it.income == 0.0 && it.expense == 0.0 }) {
                // Empty state graphic inside chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada transaksi di periode ini.\nTekan + Pemasukan / + Pengeluaran untuk memulai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val maxVal = max(
                    1.0,
                    dataPoints.maxOf { max(it.income, it.expense) } * 1.25
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(dataPoints) {
                                detectTapGestures { offset ->
                                    val count = dataPoints.size
                                    if (count > 0) {
                                        val sectionWidth = size.width / count
                                        val index = (offset.x / sectionWidth).toInt().coerceIn(0, count - 1)
                                        selectedPointIndex = if (selectedPointIndex == index) null else index
                                    }
                                }
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height - 24.dp.toPx() // leave room for labels
                        val count = dataPoints.size
                        val stepX = canvasWidth / count.toFloat()

                        // Grid lines
                        val gridCount = 3
                        for (i in 0..gridCount) {
                            val y = canvasHeight * (1f - i / gridCount.toFloat())
                            drawLine(
                                color = outlineColor,
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }

                        if (chartType == ChartType.BAR) {
                            val barGroupWidth = stepX * 0.65f
                            val barWidth = barGroupWidth / 2.2f

                            dataPoints.forEachIndexed { i, dp ->
                                val centerX = i * stepX + stepX / 2f
                                val incHeight = ((dp.income / maxVal) * canvasHeight * animProgress.value).toFloat()
                                val expHeight = ((dp.expense / maxVal) * canvasHeight * animProgress.value).toFloat()

                                val incomeX = centerX - barWidth - 2.dp.toPx()
                                val expenseX = centerX + 2.dp.toPx()

                                // Income Bar
                                if (incHeight > 0) {
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            listOf(IncomeGreen, IncomeGreen.copy(alpha = 0.7f))
                                        ),
                                        topLeft = Offset(incomeX, canvasHeight - incHeight),
                                        size = Size(barWidth, incHeight),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                }

                                // Expense Bar
                                if (expHeight > 0) {
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            listOf(ExpenseRed, ExpenseRed.copy(alpha = 0.7f))
                                        ),
                                        topLeft = Offset(expenseX, canvasHeight - expHeight),
                                        size = Size(barWidth, expHeight),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                }

                                // Highlight tapped bar
                                if (selectedPointIndex == i) {
                                    drawRoundRect(
                                        color = FinSmartPrimary.copy(alpha = 0.15f),
                                        topLeft = Offset(centerX - stepX / 2f + 4.dp.toPx(), 0f),
                                        size = Size(stepX - 8.dp.toPx(), canvasHeight),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    )
                                }

                                // Label
                                val textLayout = textMeasurer.measure(
                                    text = dp.label,
                                    style = TextStyle(fontSize = 10.sp, color = textColor)
                                )
                                drawText(
                                    textLayoutResult = textLayout,
                                    topLeft = Offset(
                                        centerX - textLayout.size.width / 2f,
                                        canvasHeight + 4.dp.toPx()
                                    )
                                )
                            }
                        } else {
                            // LINE CHART
                            val incomePath = Path()
                            val expensePath = Path()

                            val incomeFillPath = Path()
                            val expenseFillPath = Path()

                            dataPoints.forEachIndexed { i, dp ->
                                val x = i * stepX + stepX / 2f
                                val yInc = (canvasHeight - (dp.income / maxVal) * canvasHeight * animProgress.value).toFloat()
                                val yExp = (canvasHeight - (dp.expense / maxVal) * canvasHeight * animProgress.value).toFloat()

                                if (i == 0) {
                                    incomePath.moveTo(x, yInc)
                                    expensePath.moveTo(x, yExp)

                                    incomeFillPath.moveTo(x, canvasHeight)
                                    incomeFillPath.lineTo(x, yInc)

                                    expenseFillPath.moveTo(x, canvasHeight)
                                    expenseFillPath.lineTo(x, yExp)
                                } else {
                                    incomePath.lineTo(x, yInc)
                                    expensePath.lineTo(x, yExp)

                                    incomeFillPath.lineTo(x, yInc)
                                    expenseFillPath.lineTo(x, yExp)
                                }

                                if (i == dataPoints.size - 1) {
                                    incomeFillPath.lineTo(x, canvasHeight)
                                    incomeFillPath.close()

                                    expenseFillPath.lineTo(x, canvasHeight)
                                    expenseFillPath.close()
                                }

                                // Label
                                val textLayout = textMeasurer.measure(
                                    text = dp.label,
                                    style = TextStyle(fontSize = 10.sp, color = textColor)
                                )
                                drawText(
                                    textLayoutResult = textLayout,
                                    topLeft = Offset(
                                        x - textLayout.size.width / 2f,
                                        canvasHeight + 4.dp.toPx()
                                    )
                                )
                            }

                            // Draw gradient fills
                            drawPath(
                                path = incomeFillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(IncomeGreen.copy(alpha = 0.25f), Color.Transparent)
                                )
                            )
                            drawPath(
                                path = expenseFillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(ExpenseRed.copy(alpha = 0.2f), Color.Transparent)
                                )
                            )

                            // Draw strokes
                            drawPath(
                                path = incomePath,
                                color = IncomeGreen,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawPath(
                                path = expensePath,
                                color = ExpenseRed,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw Points
                            dataPoints.forEachIndexed { i, dp ->
                                val x = i * stepX + stepX / 2f
                                val yInc = (canvasHeight - (dp.income / maxVal) * canvasHeight * animProgress.value).toFloat()
                                val yExp = (canvasHeight - (dp.expense / maxVal) * canvasHeight * animProgress.value).toFloat()

                                drawCircle(color = IncomeGreen, radius = 4.dp.toPx(), center = Offset(x, yInc))
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, yInc))

                                drawCircle(color = ExpenseRed, radius = 4.dp.toPx(), center = Offset(x, yExp))
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, yExp))
                            }
                        }
                    }
                }
            }
        }
    }
}
