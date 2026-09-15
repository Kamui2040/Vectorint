package io.github.kamui2040.vectorint.presentation.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import io.github.kamui2040.vectorint.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLanguageConfigurationTest {
    @Test
    fun `main activity handles locale changes without recreation`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val activityInfo =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )

        assertTrue(activityInfo.configChanges and ActivityInfo.CONFIG_LOCALE != 0)
        assertTrue(activityInfo.configChanges and ActivityInfo.CONFIG_LAYOUT_DIRECTION != 0)
    }
}
