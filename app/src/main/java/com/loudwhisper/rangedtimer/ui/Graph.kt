package com.loudwhisper.rangedtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/**
 * One bar per day (minutes) with the trailing 7-day average drawn on top.
 * [days] runs oldest to newest; [trend] gives the average for some of those days, by date.
 */
@Composable
fun MinutesGraph(days: List<Pair<LocalDate, Long>>, trend: List<Pair<LocalDate, Double>>) {
    val barColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val maxMinutes = max(days.maxOfOrNull { it.second / 60.0 } ?: 0.0, trend.maxOfOrNull { it.second } ?: 0.0)
    // Round the scale up to a friendly number so the top label is readable.
    val top = max(30.0, ceil(maxMinutes / 30.0) * 30.0)

    Column {
        Text("${whole(top)} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val n = days.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.7f).coerceIn(1f, 24.dp.toPx())
            listOf(0.0, 0.5, 1.0).forEach { f ->
                val y = (size.height * (1 - f)).toFloat()
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            days.forEachIndexed { i, (_, seconds) ->
                val h = (seconds / 60.0 / top * size.height).toFloat()
                if (h > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 4, barWidth / 4),
                    )
                }
            }
            val index = days.withIndex().associate { (i, d) -> d.first to i }
            val points = trend.mapNotNull { (date, m) -> index[date]?.let { it to m } }
            if (points.size >= 2) {
                val path = Path()
                points.forEachIndexed { k, (i, m) ->
                    val x = i * slot + slot / 2
                    val y = (size.height - m / top * size.height).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Text(dateOf(days.first().first), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(dateOf(days.last().first), style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            "Bars: minutes each day. Line: 7-day average.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
