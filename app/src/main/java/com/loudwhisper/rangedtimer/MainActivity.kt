package com.loudwhisper.rangedtimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.loudwhisper.rangedtimer.alarm.AlertService
import com.loudwhisper.rangedtimer.alarm.Notifications
import com.loudwhisper.rangedtimer.alarm.SessionController
import com.loudwhisper.rangedtimer.core.AppData
import com.loudwhisper.rangedtimer.ui.HistoryScreen
import com.loudwhisper.rangedtimer.ui.HomeScreen
import com.loudwhisper.rangedtimer.ui.RangedTimerTheme
import com.loudwhisper.rangedtimer.ui.SettingsScreen
import com.loudwhisper.rangedtimer.ui.SetupStatus
import com.loudwhisper.rangedtimer.ui.setupStatus
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val store by lazy { SessionController.store(this) }
    private var data by mutableStateOf(AppData())
    private var alertRunning by mutableStateOf(false)
    private var resumed by mutableStateOf(false)
    private var setup by mutableStateOf(SetupStatus(notifications = true, exactAlarms = true, battery = true))

    private fun refresh() {
        data = store.load()
        alertRunning = AlertService.isRunning
        setup = setupStatus(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        refresh()

        setContent {
            RangedTimerTheme(data.settings.theme) {
                var tab by rememberSaveable { mutableIntStateOf(0) }

                // While the app is on screen, re-read the small data file so an alert
                // or a finished session shows up without reopening the app.
                LaunchedEffect(resumed) {
                    while (resumed) {
                        refresh()
                        delay(2000)
                    }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            listOf(
                                "Timer" to R.drawable.ic_tab_timer,
                                "History" to R.drawable.ic_tab_history,
                                "Settings" to R.drawable.ic_tab_settings,
                            ).forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(painterResource(icon), contentDescription = null) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    // Wide screens (tablets, unfolded phones) keep a comfortable column in the middle.
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
                        when (tab) {
                            0 -> HomeScreen(
                                data = data,
                                alertRunning = alertRunning,
                                setupComplete = setup.complete,
                                onRangeChange = { s, l ->
                                    data = store.update { it.copy(settings = it.settings.copy(shortestMinutes = s, longestMinutes = l)) }
                                },
                                onStart = { SessionController.start(this@MainActivity); refresh() },
                                onEndEarly = { SessionController.endEarly(this@MainActivity); refresh() },
                                onTest = { SessionController.testIn10s(this@MainActivity) },
                                onStopAlert = { AlertService.stop(this@MainActivity); alertRunning = false },
                                onOpenSettings = { tab = 2 },
                            )
                            1 -> HistoryScreen(data)
                            else -> SettingsScreen(
                                data = data,
                                status = setup,
                                onRingtone = { uri -> data = store.update { it.copy(settings = it.settings.copy(ringtoneUri = uri)) } },
                                onTheme = { t -> data = store.update { it.copy(settings = it.settings.copy(theme = t)) } },
                                onRecheck = { refresh() },
                                onExport = { uri ->
                                    contentResolver.openOutputStream(uri)?.use { store.exportTo(it) }
                                },
                                onImport = { text -> store.importFrom(text); refresh() },
                                onClearHistory = { store.clearHistory(); refresh() },
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SessionController.recoverOnOpen(this)
        refresh()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }
}
