package com.example.dhansutra.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhansutra.domain.model.CategoryBreakdown
import com.example.dhansutra.domain.model.MonthlyTrendPoint
import com.example.dhansutra.ui.theme.DhanSutraTheme
import com.example.dhansutra.util.CurrencyFormatter
import com.example.dhansutra.util.MonthYear
import java.time.YearMonth
import kotlin.math.max

/** Distinct, theme-independent slice colours (cycled when there are more categories than colours). */
private val sliceColors = listOf(
    Color(0xFF4C8DF6), Color(0xFFF2994A), Color(0xFF27AE60), Color(0xFFEB5757),
    Color(0xFF9B51E0), Color(0xFF2D9CDB), Color(0xFFF2C94C), Color(0xFF219653),
    Color(0xFFBB6BD9), Color(0xFF56CCF2),
)

fun sliceColor(index: Int): Color = sliceColors[index % sliceColors.size]

/** Percent of [part] in [total], 0 when total is 0. */
fun percentOf(part: Long, total: Long): Float = if (total <= 0) 0f else part * 100f / total

/** Donut chart with a legend listing name, amount and percentage per category. */
@Composable
fun CategoryDonutChart(data: List<CategoryBreakdown>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.amount }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(180.dp).padding(8.dp)) {
            val stroke = size.minDimension * 0.22f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total <= 0) {
                drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                data.forEachIndexed { i, slice ->
                    val sweep = 360f * slice.amount / total
                    drawArc(
                        color = sliceColor(i),
                        startAngle = start,
                        sweepAngle = max(sweep - if (data.size > 1) 1.5f else 0f, 0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke),
                    )
                    start += sweep
                }
            }
        }
        Text(CurrencyFormatter.format(total, false), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            data.forEachIndexed { i, slice ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.size(12.dp).background(sliceColor(i), CircleShape)) {}
                    Text(slice.name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.format(slice.amount, false), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "  %.0f%%".format(percentOf(slice.amount, total)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Line chart of monthly spending; handles empty/flat data and a single point. */
@Composable
fun SpendingTrendChart(points: List<MonthlyTrendPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val density = LocalDensity.current
    val textSizePx = with(density) { 11.sp.toPx() }

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val leftPad = 56.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val topPad = 12.dp.toPx()
        val rightPad = 12.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        val maxValue = max(points.maxOfOrNull { it.spent } ?: 0L, 1L)
        val niceMax = niceCeil(maxValue)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor
            textSize = textSizePx
        }

        // horizontal grid + y labels
        val steps = 4
        for (i in 0..steps) {
            val y = topPad + chartH * (1 - i / steps.toFloat())
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1.dp.toPx())
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                CurrencyFormatter.compact(niceMax * i / steps),
                leftPad - 6.dp.toPx(),
                y + textSizePx / 3,
                paint,
            )
        }

        if (points.isEmpty()) return@Canvas
        fun x(i: Int) = if (points.size == 1) leftPad + chartW / 2 else leftPad + chartW * i / (points.size - 1)
        fun y(v: Long) = topPad + chartH * (1 - v.toFloat() / niceMax)

        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.spent)) else path.lineTo(x(i), y(p.spent))
        }
        if (points.size > 1) {
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEachIndexed { i, p ->
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(i), y(p.spent)))
        }

        // x labels
        paint.textAlign = android.graphics.Paint.Align.CENTER
        points.forEachIndexed { i, p ->
            drawContext.canvas.nativeCanvas.drawText(
                MonthYear.shortLabel(p.month),
                x(i),
                size.height - 6.dp.toPx(),
                paint,
            )
        }
    }
}

/** Rounds [v] (paise) up to 1, 2, 2.5, 5 or 10 times a power of ten so axis labels are tidy. */
fun niceCeil(v: Long): Long {
    if (v <= 0) return 1
    var pow = 1L
    while (pow * 10 < v) pow *= 10
    for (m in longArrayOf(1, 2, 5, 10)) {
        if (pow * m >= v) return pow * m
    }
    return pow * 10
}

private val previewBreakdown = listOf(
    CategoryBreakdown(1, "Rent/EMI", 1_500_000),
    CategoryBreakdown(2, "Groceries", 650_000),
    CategoryBreakdown(3, "Dining & Swiggy", 520_000),
    CategoryBreakdown(4, "Utilities", 100_000),
)

private val previewTrend = listOf(
    MonthlyTrendPoint(YearMonth.of(2026, 6), 2_800_000, 0),
    MonthlyTrendPoint(YearMonth.of(2026, 7), 3_200_000, 0),
    MonthlyTrendPoint(YearMonth.of(2026, 8), 2_500_000, 0),
    MonthlyTrendPoint(YearMonth.of(2026, 9), 3_900_000, 0),
    MonthlyTrendPoint(YearMonth.of(2026, 10), 3_400_000, 0),
    MonthlyTrendPoint(YearMonth.of(2026, 11), 2_770_000, 0),
)

@Preview(showBackground = true)
@Composable
private fun DonutPreview() {
    DhanSutraTheme { CategoryDonutChart(previewBreakdown, Modifier.padding(16.dp)) }
}

@Preview(showBackground = true)
@Composable
private fun DonutEmptyPreview() {
    DhanSutraTheme { CategoryDonutChart(emptyList(), Modifier.padding(16.dp)) }
}

@Preview(showBackground = true)
@Composable
private fun TrendPreview() {
    DhanSutraTheme { SpendingTrendChart(previewTrend, Modifier.padding(16.dp)) }
}

@Preview(showBackground = true)
@Composable
private fun TrendSinglePointPreview() {
    DhanSutraTheme { SpendingTrendChart(previewTrend.takeLast(1), Modifier.padding(16.dp)) }
}
