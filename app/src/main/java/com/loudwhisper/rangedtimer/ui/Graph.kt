package com.loudwhisper.rangedtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.loudwhisper.rangedtimer.core.graphScale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val barDayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * One bar per day (minutes) with the trailing 7-day average drawn on top, over dashed
 * guide lines every 30 minutes. Tapping a bar shows that day's minutes above the graph.
 * [days] runs oldest to newest; [trend] gives the average for some of those days, by date.
 */
@Composable
fun MinutesGraph(days: List<Pair<LocalDate, Long>>, trend: List<Pair<LocalDate, Double>>) {
    val barColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val selectedColor = MaterialTheme.colorScheme.secondary
    val lineColor = MaterialTheme.colorScheme.primary
    // Text grey at half strength: visible on OLED black and on the light off-white, still behind the bars.
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val maxMinutes = max(days.maxOfOrNull { it.second / 60.0 } ?: 0.0, trend.maxOfOrNull { it.second } ?: 0.0)
    val scale = graphScale(maxMinutes)
    val top = scale.topMinutes.toDouble()
    // A new period or filter clears the selection.
    var selected by remember(days.firstOrNull()?.first, days.size) { mutableStateOf<Int?>(null) }

    Column {
        val pick = selected?.let(days::getOrNull)
        Text(
            if (pick == null) "Tap a bar to see that day's time."
            else "${pick.first.format(barDayFormat)}: ${minutes(pick.second)} min",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (pick == null) FontWeight.Normal else FontWeight.SemiBold,
            color = if (pick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .testTag("minutesGraph")
                .pointerInput(days) {
                    detectTapGestures { tap ->
                        // Same layout maths as the drawing below: the left gutter holds the minute labels.
                        val gutter = 32.dp.toPx()
                        val slot = (size.width - gutter) / days.size.coerceAtLeast(1)
                        val i = ((tap.x - gutter) / slot).toInt()
                        if (tap.x >= gutter && i in days.indices) selected = if (selected == i) null else i
                    }
                },
        ) {
            val gutter = 32.dp.toPx()
            val headroom = 8.dp.toPx() // keeps the top label from being cut off
            val plotHeight = size.height - headroom
            fun yOf(minutes: Double) = (headroom + plotHeight * (1 - minutes / top)).toFloat()
            val n = days.size.coerceAtLeast(1)
            val slot = (size.width - gutter) / n
            val barWidth = (slot * 0.7f).coerceIn(1f, 24.dp.toPx())

            drawLine(gridColor, Offset(gutter, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            scale.lineMinutes.forEach { m ->
                val y = yOf(m.toDouble())
                drawLine(gridColor, Offset(gutter, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val label = measurer.measure("$m", labelStyle)
                drawText(label, topLeft = Offset(gutter - 6.dp.toPx() - label.size.width, y - label.size.height / 2f))
            }
            days.forEachIndexed { i, (_, seconds) ->
                val h = (seconds / 60.0 / top * plotHeight).toFloat()
                if (h > 0f) {
                    drawRoundRect(
                        color = if (i == selected) selectedColor else barColor,
                        topLeft = Offset(gutter + i * slot + (slot - barWidth) / 2, size.height - h),
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
                    val x = gutter + i * slot + slot / 2
                    val y = yOf(m)
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 32.dp)) {
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
