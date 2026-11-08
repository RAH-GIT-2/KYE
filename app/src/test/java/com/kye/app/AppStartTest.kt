package com.kye.app

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real app class, with WorkManager set up for tests before the app schedules its jobs. */
class TestKyeApp : KyeApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this, Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        super.onCreate()
    }
}

/**
 * Starts KYE the way a phone does: app class, then the main screen. v1.7 closed itself on launch;
 * this catches that kind of crash on the laptop before an APK is built.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestKyeApp::class)
class AppStartTest {

    private fun settle() {
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test
    fun mainScreenOpensAndStaysOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            scenario.onActivity { assertFalse("main screen closed itself", it.isFinishing) }
        }
    }

    @Test
    fun widgetAndTileButtonsOpenTheApp() {
        listOf(MainActivity.ACTION_VOICE, MainActivity.ACTION_ADD, MainActivity.ACTION_ASK).forEach { action ->
            val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ACTION, action)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                settle()
                scenario.onActivity { assertFalse(action, it.isFinishing) }
            }
        }
    }

    @Test
    fun survivesRotation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            scenario.recreate()
            settle()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
