package com.loudwhisper.rangedtimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.loudwhisper.rangedtimer.core.AppData
import com.loudwhisper.rangedtimer.core.LabelChoice
import com.loudwhisper.rangedtimer.core.Settings
import com.loudwhisper.rangedtimer.core.Outcome
import com.loudwhisper.rangedtimer.core.Session
import com.loudwhisper.rangedtimer.core.day
import com.loudwhisper.rangedtimer.core.dayTotals
import com.loudwhisper.rangedtimer.core.validateRange
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    data: AppData,
    alertRunning: Boolean,
    setupComplete: Boolean,
    onRangeChange: (Int, Int) -> Unit,
    onLabelChoice: (LabelChoice) -> Unit,
    onStart: () -> Unit,
    onEndEarly: () -> Unit,
    onStopAlert: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val active = data.activeSession
    var shortestText by remember { mutableStateOf(data.settings.shortestMinutes.toString()) }
    var longestText by remember { mutableStateOf(data.settings.longestMinutes.toString()) }

    val shortest = shortestText.toIntOrNull()
    val longest = longestText.toIntOrNull()
    val rangeError = if (shortest == null || longest == null) "Enter whole minutes." else validateRange(shortest, longest)

    // Centred on any screen size: the column is at least as tall as the screen,
    // so short content sits in the middle and long content still scrolls.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val screenHeight = maxHeight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = screenHeight)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        if (alertRunning) {
            Button(
                onClick = onStopAlert,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(24.dp),
            ) { Text("Stop alert", style = MaterialTheme.typography.headlineSmall) }
        }

        if (!setupComplete) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Setup needed", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text("Some phone settings could stop the alert from going off on time.")
                    TextButton(onClick = onOpenSettings) { Text("Open setup check") }
                }
            }
        }

        if (active == null) {
            LabelChips(data.settings, onLabelChoice)
            Text("Set your range", style = MaterialTheme.typography.titleLarge)
            Text(
                "The timer picks a moment between these two lengths and keeps it secret.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MinutesField("Shortest", shortestText, Modifier.weight(1f)) {
                    shortestText = it
                    val s = it.toIntOrNull(); val l = longestText.toIntOrNull()
                    if (s != null && l != null && validateRange(s, l) == null) onRangeChange(s, l)
                }
                MinutesField("Longest", longestText, Modifier.weight(1f)) {
                    longestText = it
                    val s = shortestText.toIntOrNull(); val l = it.toIntOrNull()
                    if (s != null && l != null && validateRange(s, l) == null) onRangeChange(s, l)
                }
            }
            if (rangeError != null) {
                Text(rangeError, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onStart,
                enabled = rangeError == null,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(24.dp),
            ) { Text("Start", style = MaterialTheme.typography.headlineSmall) }
        } else {
            Text(
                "${active.label} in progress",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                "Started ${timeOf(active.startEpochMillis)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            HoldToEndButton(onEndEarly)
        }

        LastSessionCard(data)
    }
    }
}

@Composable
private fun MinutesField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text("$label (min)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Must be held for 1.5 seconds, so a bump in a pocket cannot end the session. */
@Composable
private fun HoldToEndButton(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(durationMillis = 1500, easing = LinearEasing))
                        onEnd()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (job.isActive) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
        )
        Text(
            "Hold to end early",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelChips(settings: Settings, onChoice: (LabelChoice) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            LabelChoice.MEDITATION to "Meditation",
            LabelChoice.READING to "Reading",
            LabelChoice.WORKOUT to "Workout",
            LabelChoice.OTHER to settings.otherLabelName,
        ).forEach { (choice, name) ->
            FilterChip(selected = settings.labelChoice == choice, onClick = { onChoice(choice) }, label = { Text(name) })
        }
    }
}

@Composable
private fun LastSessionCard(data: AppData) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val last: Session = data.sessions.lastOrNull() ?: return
    if (last.day(zone) != today || data.activeSession != null) return
    val sameLabel = data.sessions.filter { it.label == last.label }
    val todayMinutes = minutes(dayTotals(sameLabel, zone)[today] ?: 0L)
    val sat = minutes(last.actualSeconds)
    val picked = minutes(last.pickedSeconds)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(last.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (last.outcome) {
                Outcome.COMPLETED -> {
                    Text("Today: $todayMinutes minutes", style = MaterialTheme.typography.headlineSmall)
                    if (todayMinutes != sat) Text("This session: $sat minutes")
                }
                Outcome.ENDED_EARLY -> {
                    Text("Ended early after $sat minutes.", style = MaterialTheme.typography.headlineSmall)
                    Text("The timer had picked $picked.")
                    if (todayMinutes != sat) Text("Today: $todayMinutes minutes")
                }
                Outcome.INTERRUPTED -> {
                    Text("Session interrupted", style = MaterialTheme.typography.headlineSmall)
                    Text("The phone restarted or the alert was missed. Recorded $sat minutes.")
                }
            }
        }
    }
}
