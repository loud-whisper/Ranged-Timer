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
        shot("1-timer")
        compose.onNodeWithText("Start").performClick()
        shot("2-in-session")
        compose.onNodeWithText("Settings").performClick()
        shot("3-settings")
        compose.onNodeWithText("History").performClick()
        shot("4-history")
    }
}
