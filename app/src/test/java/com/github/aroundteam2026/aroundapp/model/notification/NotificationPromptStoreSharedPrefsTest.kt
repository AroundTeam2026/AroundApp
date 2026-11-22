// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPromptStoreSharedPrefsTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /** The preferences file itself: what the next launch reads. */
  private val file =
      File(context.dataDir, "shared_prefs/${NotificationPromptStoreSharedPrefs.PREFS_NAME}.xml")

  private fun TestScope.store() =
      NotificationPromptStoreSharedPrefs(context, StandardTestDispatcher(testScheduler))

  @Test fun aNewPhoneHasNotAnswered() = runTest { assertFalse(store().wasAnswered()) }

  @Test
  fun anAnswerIsOnDiskWhenItReturns_andTheNextLaunchReadsIt() = runTest {
    store().markAnswered()

    assertTrue(file.readText().contains("answered"))
    assertTrue(store().wasAnswered())
  }

  @Test(expected = IOException::class)
  fun aFailedWriteIsReported() = runTest {
    val editor = mockk<SharedPreferences.Editor>()
    every { editor.putBoolean(any(), any()) } returns editor
    every { editor.commit() } returns false
    val failing = mockk<Context>()
    every { failing.getSharedPreferences(any(), any()) } returns
        mockk { every { edit() } returns editor }

    NotificationPromptStoreSharedPrefs(failing, StandardTestDispatcher(testScheduler))
        .markAnswered()
  }
}
