package com.loudwhisper.rangedtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loudwhisper.rangedtimer.core.AppData
import com.loudwhisper.rangedtimer.core.CYCLE_DAYS
import com.loudwhisper.rangedtimer.core.CycleSummary
import com.loudwhisper.rangedtimer.core.LabelFilter
import com.loudwhisper.rangedtimer.core.Outcome
import com.loudwhisper.rangedtimer.core.PERIOD_WINDOWS
import com.loudwhisper.rangedtimer.core.Session
import com.loudwhisper.rangedtimer.core.cycles
import com.loudwhisper.rangedtimer.core.day
import com.loudwhisper.rangedtimer.core.dayTotals
import com.loudwhisper.rangedtimer.core.filterSessions
import com.loudwhisper.rangedtimer.core.periodStats
import com.loudwhisper.rangedtimer.core.trend
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(data: AppData) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var window by rememberSaveable { mutableIntStateOf(7) }
    var filter by rememberSaveable { mutableStateOf(LabelFilter.ALL) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (data.sessions.isEmpty()) {
            Text("No sessions yet.", style = MaterialTheme.typography.titleMedium)
            Text("Your history, averages and 90-day cycles appear here after your first session.")
            return@Column
        }

        val filterNames = listOf(
            LabelFilter.ALL to "All",
            LabelFilter.MEDITATION to "Meditation",
            LabelFilter.READING to "Reading",
            LabelFilter.WORKOUT to "Workout",
            LabelFilter.OTHER to data.settings.otherLabelName,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filterNames.forEach { (f, name) ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(name) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PERIOD_WINDOWS.forEach { w ->
                FilterChip(selected = window == w, onClick = { window = w }, label = { Text(periodName(w)) })
            }
        }

        val sessions = filterSessions(data.sessions, filter)
        if (sessions.isEmpty()) {
            val name = filterNames.first { it.first == filter }.second
            Text("No $name sessions yet.", style = MaterialTheme.typography.titleMedium)
            return@Column
        }

        val stats = periodStats(sessions, today, window, zone)!!
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${oneDecimal(stats.avgMinutesPerDay)} min/day", style = MaterialTheme.typography.headlineMedium)
                Text("Average per day, skipped days count as 0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Practiced on ${stats.daysMeditated} of ${plural(stats.daysCounted, "day")}")
                stats.avgSessionMinutes?.let { Text("Average session: ${whole(it)} min") }
                if (stats.partial) Text("Based on ${plural(stats.daysCounted, "day")} so far", color = MaterialTheme.colorScheme.onSurfaceVariant)
                stats.changeVsPrevious?.let { Text(changeSentence(it, window), fontWeight = FontWeight.SemiBold) }
            }
        }

        // Bars cover the whole period, so a short history still reads as days, not one wide block.
        val totals = dayTotals(sessions, zone)
        val firstDay = sessions.minOf { it.day(zone) }
        val from = today.minusDays(window - 1L)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
            .map { it to (totals[it] ?: 0L) }.toList()
        MinutesGraph(days, trend(totals, firstDay, from, today))

        Text("90-day cycles", style = MaterialTheme.typography.titleLarge)
        // Cycles start on the first session of any label, so every filter shares the same boundaries.
        val anchor = data.sessions.minOf { it.day(zone) }
        cycles(sessions, today, zone, anchor = anchor).forEach { CycleRow(it) }

        Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
        sessions.takeLast(30).asReversed().forEach { SessionRow(it) }
    }
}

@Composable
private fun CycleRow(c: CycleSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val title = if (c.isCurrent) "Cycle ${c.index}, day ${c.dayOfCycle} of $CYCLE_DAYS" else "Cycle ${c.index}"
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("${dateOf(c.start)} to ${dateOf(c.end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${oneDecimal(c.avgMinutesPerDay)} min/day, practiced on ${plural(c.daysMeditated, "day")}")
            val avg = c.avgSessionMinutes?.let { "average session ${whole(it)} min, " } ?: ""
            Text("${avg}${totalTime(c.totalHours)} in total")
        }
    }
}

@Composable
private fun SessionRow(s: Session) {
    val sat = minutes(s.actualSeconds)
    val picked = minutes(s.pickedSeconds)
    val detail = when (s.outcome) {
        Outcome.COMPLETED -> "$sat min"
        Outcome.ENDED_EARLY -> "Ended early: $sat of $picked min"
        Outcome.INTERRUPTED -> "Interrupted: $sat min recorded"
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(dateTimeOf(s.startEpochMillis), modifier = Modifier.weight(1f))
            Text(detail)
        }
        Text(
            s.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}
