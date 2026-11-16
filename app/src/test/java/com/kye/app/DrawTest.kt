package com.kye.app

import android.os.Looper
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Like ScreenTest, but the screens are really drawn (ScreenTest only lays them out), so a crash
 * while painting a screen shows up here before it reaches a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestKyeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DrawTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun close() { scenario?.close() }

    private fun waitForText(text: String) = rule.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun draw() {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
    }

    @Test fun homeAndSettingsDraw() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Know Your Expense")
        draw()
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings")
        draw()
        listOf("Ocean", "Wallpaper", "Violet").forEach {
            rule.onNodeWithText(it).performScrollTo().performClick()
            draw()
        }
        rule.onNodeWithText("Pure black in dark mode").performScrollTo()
        draw()
    }
}
