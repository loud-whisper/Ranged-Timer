package com.loudwhisper.rangedtimer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loudwhisper.rangedtimer.alarm.Scheduler
import com.loudwhisper.rangedtimer.core.AppData
import com.loudwhisper.rangedtimer.core.Codec
import com.loudwhisper.rangedtimer.core.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SetupStatus(val notifications: Boolean, val exactAlarms: Boolean, val battery: Boolean) {
    val complete get() = notifications && exactAlarms && battery
}

fun setupStatus(ctx: Context) = SetupStatus(
    notifications = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    exactAlarms = Scheduler.canScheduleExact(ctx),
    battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
)

@Composable
fun SettingsScreen(
    data: AppData,
    status: SetupStatus,
    onRingtone: (String?) -> Unit,
    onTheme: (Theme) -> Unit,
    onRecheck: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val ctx = LocalContext.current
    var importCandidate by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        if (result.resultCode == android.app.Activity.RESULT_OK) onRingtone(uri?.toString())
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        onRecheck()
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            onExport(uri)
            message = "History exported."
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text != null && Codec.decode(text).isSuccess) importCandidate = text else message = "That file is not a Ranged Timer history file."
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Setup check", style = MaterialTheme.typography.titleLarge)
        Text(
            "All three must be ticked so the alert can go off on time with the screen off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SetupRow("Notifications", "So Stop appears on the lock screen.", status.notifications) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        SetupRow("Alarms and reminders", "So the alert fires at the exact second.", status.exactAlarms) {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
        SetupRow("Battery: Unrestricted", "So Samsung does not put the app to sleep.", status.battery) {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        }
        Text(
            "On Samsung, also check Settings, Battery, Background usage limits: Ranged Timer must not be in Sleeping apps or Deep sleeping apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Sound", style = MaterialTheme.typography.titleLarge)
        val ringtoneName = remember(data.settings.ringtoneUri) { ringtoneTitle(ctx, data.settings.ringtoneUri) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ringtone when sound is on")
                Text(ringtoneName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, data.settings.ringtoneUri?.let(Uri::parse))
                ringtonePicker.launch(intent)
            }) { Text("Choose") }
        }
        Text(
            "On Silent or Vibrate the phone only vibrates. Alerts stop when you tap Stop or after 3 minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Theme", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Theme.SYSTEM to "Follow phone", Theme.LIGHT to "Light", Theme.DARK to "Dark").forEach { (t, label) ->
                FilterChip(selected = data.settings.theme == t, onClick = { onTheme(t) }, label = { Text(label) })
            }
        }

        Text("History backup", style = MaterialTheme.typography.titleLarge)
        Text(
            "Your history lives only on this phone. Export a copy now and then, and keep it somewhere safe.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { exporter.launch("ranged-timer-history-${LocalDate.now()}.json") }) { Text("Export history") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Import history") }
        }
        message?.let { Text(it) }

        Text("Clear history", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            onClick = { confirmClear = true },
            enabled = data.sessions.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Clear history") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = {
                Text(
                    "Clearing history will wipe all records, with no way of getting them back. " +
                        "You can export your history first before clearing."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        confirmClear = false
                        message = "History cleared."
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Proceed with full history wipe") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    importCandidate?.let { text ->
        val incoming = Codec.decode(text).getOrNull()?.sessions.orEmpty()
        val zone = ZoneId.systemDefault()
        val range = if (incoming.isEmpty()) "no sessions" else {
            val first = Instant.ofEpochMilli(incoming.minOf { it.startEpochMillis }).atZone(zone).toLocalDate()
            val last = Instant.ofEpochMilli(incoming.maxOf { it.startEpochMillis }).atZone(zone).toLocalDate()
            "${incoming.size} sessions from ${dateOf(first)} to ${dateOf(last)}"
        }
        AlertDialog(
            onDismissRequest = { importCandidate = null },
            title = { Text("Replace history?") },
            text = {
                Text(
                    "The file has $range. It will replace the ${data.sessions.size} sessions on this phone. " +
                        "A copy of the current history is kept inside the app first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onImport(text)
                    importCandidate = null
                    message = "History imported."
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { importCandidate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetupRow(title: String, why: String, ok: Boolean, onFix: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ok) {
                Text("Done", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedButton(onClick = onFix) { Text("Allow") }
            }
        }
    }
}

private fun ringtoneTitle(ctx: Context, uri: String?): String {
    if (uri == null) return "Phone's default alarm sound"
    return runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) }.getOrNull() ?: "Chosen sound"
}
