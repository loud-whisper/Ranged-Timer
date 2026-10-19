package com.loudwhisper.rangedtimer.preview

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loudwhisper.rangedtimer.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Writes screenshots for a visual check. Runs only when -Dscreenshots.dir is set:
 * ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots.dir=/tmp/rt-shots
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-night-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = System.getProperty("screenshots.dir") ?: return
        File(dir).mkdirs()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun capture() {
        assumeTrue(System.getProperty("screenshots.dir") != null)
        // Sample history: ten days of mixed labels so the History screen has something to show.
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val labels = listOf("Meditation", "Meditation", "Reading", "Workout", "Meditation")
        val sample = (0L..9L).filter { it != 3L && it != 7L }.mapIndexed { i, back ->
            val minutes = 55L + (i * 7) % 30
            com.loudwhisper.rangedtimer.core.Session(
                id = "s$i",
                startEpochMillis = today.minusDays(back).atTime(6, 30).atZone(zone).toInstant().toEpochMilli(),
                pickedSeconds = minutes * 60, actualSeconds = minutes * 60,
                outcome = com.loudwhisper.rangedtimer.core.Outcome.COMPLETED,
                label = labels[i % labels.size],
            )
        }
        val store = com.loudwhisper.rangedtimer.alarm.SessionController.store(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        store.save(store.load().copy(
            sessions = sample,
            settings = store.load().settings.copy(
                labelChoice = com.loudwhisper.rangedtimer.core.LabelChoice.OTHER,
                otherLabelName = "HIIT",
                recentCustomLabels = listOf("HIIT", "Cardio", "Guitar"),
            ),
        ))
        compose.activityRule.scenario.recreate()
        shot("1-timer")
        compose.onNodeWithText("Start").performClick()
        shot("2-in-session")
        compose.onNodeWithText("Settings").performClick()
        shot("3-settings")
        compose.onNodeWithText("History").performClick()
        shot("4-history")
    }
}
