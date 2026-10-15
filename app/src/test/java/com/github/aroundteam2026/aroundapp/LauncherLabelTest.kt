// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherLabelTest {
  @Test
  fun launcherShowsTheAppName() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageManager = context.packageManager
    // Launchers show the activity's label, which overrides the application's when set
    val launcher =
        packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

    assertEquals(
        context.getString(R.string.app_name),
        launcher.loadLabel(packageManager).toString(),
    )
  }
}
