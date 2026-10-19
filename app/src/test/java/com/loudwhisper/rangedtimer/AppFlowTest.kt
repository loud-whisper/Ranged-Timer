package com.loudwhisper.rangedtimer

import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loudwhisper.rangedtimer.alarm.AlertService
import com.loudwhisper.rangedtimer.alarm.SessionController
import com.loudwhisper.rangedtimer.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// Screen size of a Galaxy S23 Ultra, so every control is on screen as on the real phone.
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val alarms get() = ctx.getSystemService(AlarmManager::class.java)

    @Test
    fun startHidesTimeSchedulesExactAlarmAndEndEarlyReveals() {
        compose.onNodeWithText("Start").assertIsDisplayed().performClick()
        compose.onNodeWithText("Session in progress").assertIsDisplayed()

        val active = SessionController.store(ctx).load().activeSession!!
        assertTrue(active.pickedSeconds in 3600L..5400L)
        val alarm = shadowOf(alarms).peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm!!.type)
        assertEquals(active.startElapsedMillis + active.pickedSeconds * 1000, alarm.triggerAtTime)
        // The end time must never be published to the lock screen or status bar.
        assertNull(alarms.nextAlarmClock)

        // Keep the finger down; the button disappears once the 1.5 s hold completes.
        compose.onNodeWithText("Hold to end early").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1700)
        compose.waitForIdle()

        val data = SessionController.store(ctx).load()
        assertNull(data.activeSession)
        assertEquals(Outcome.ENDED_EARLY, data.sessions.single().outcome)
        assertNull(shadowOf(alarms).peekNextScheduledAlarm())
        compose.onNodeWithText("You sat 0 minutes.").assertIsDisplayed()
        compose.onNodeWithText("The timer had picked ${active.pickedSeconds.let { Math.round(it / 60.0) }}.").assertIsDisplayed()
    }

    @Test
    fun shortTapDoesNotEndSession() {
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Hold to end early").performClick()
        compose.waitForIdle()
        assertNotNull(SessionController.store(ctx).load().activeSession)
    }

    @Test
    fun alarmRecordsCompletedSessionAndStartsAlert() {
        val active = SessionController.start(ctx)!!
        SessionController.onAlarm(ctx, active.id, isTest = false)
        val data = SessionController.store(ctx).load()
        assertNull(data.activeSession)
        assertEquals(Outcome.COMPLETED, data.sessions.single().outcome)
        assertEquals(active.pickedSeconds, data.sessions.single().actualSeconds)
        val started = shadowOf(ctx as android.app.Application).nextStartedService
        assertEquals(AlertService::class.java.name, started.component!!.className)
    }

    @Test
    fun staleAlarmIsIgnored() {
        SessionController.start(ctx)!!
        SessionController.onAlarm(ctx, "some-old-session", isTest = false)
        val data = SessionController.store(ctx).load()
        assertNotNull(data.activeSession)
        assertTrue(data.sessions.isEmpty())
    }

    @Test
    fun testAlertNeverTouchesHistory() {
        SessionController.testIn10s(ctx)
        val alarm = shadowOf(alarms).peekNextScheduledAlarm()!!
        assertTrue(alarm.triggerAtTime - SystemClock.elapsedRealtime() in 9_000L..10_000L)
        SessionController.onAlarm(ctx, null, isTest = true)
        assertTrue(SessionController.store(ctx).load().sessions.isEmpty())
    }

    @Test
    fun historyAndSettingsScreensRender() {
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("No sessions yet.").assertIsDisplayed()

        val active = SessionController.start(ctx)!!
        SessionController.onAlarm(ctx, active.id, isTest = false)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("90-day cycles").assertExists()
        compose.onNodeWithText("Recent sessions").assertExists()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Setup check").assertIsDisplayed()
        compose.onNodeWithText("Alarms and reminders").assertIsDisplayed()
    }

    @Test
    fun invalidRangeDisablesStart() {
        compose.onNodeWithText("60").performTextReplacement("120")
        compose.onNodeWithText("Shortest must not be more than Longest.").assertIsDisplayed()
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test
    fun clearHistoryWarnsAndOnlyWipesAfterConfirm() {
        val active = SessionController.start(ctx)!!
        SessionController.onAlarm(ctx, active.id, isTest = false)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Settings").performClick()

        compose.onAllNodesWithText("Clear history")[1].performScrollTo().performClick()
        compose.onNodeWithText("Clearing history will wipe all records", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, SessionController.store(ctx).load().sessions.size)

        compose.onAllNodesWithText("Clear history")[1].performScrollTo().performClick()
        compose.onNodeWithText("Proceed with full history wipe").performClick()
        compose.waitForIdle()
        assertTrue(SessionController.store(ctx).load().sessions.isEmpty())
    }

    @Test
    fun timerContentSitsInTheMiddleOfTheScreen() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val top = compose.onNodeWithText("Set your range").fetchSemanticsNode().boundsInRoot.top
        val bottom = compose.onNodeWithText("Test in 10 seconds").fetchSemanticsNode().boundsInRoot.bottom
        val middle = (top + bottom) / 2
        // Allow for the bottom tab bar taking space below the content area.
        assertTrue("content middle $middle vs screen ${root.height}", middle in root.height * 0.35f..root.height * 0.55f)
    }
}
