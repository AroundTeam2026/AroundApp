// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that the app gets the WorkManager queue, whose uploads outlive the app, and not a
 * stand-in; [ProofUploadQueueWorkManagerTest] tests what it does.
 */
@RunWith(AndroidJUnit4::class)
class ProofUploadQueueProviderTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun initializeWorkManager() {
    // Robolectric doesn't run the app's startup initializer, which sets WorkManager up
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
  }

  @Test
  fun servesTheWorkManagerQueue() {
    assertTrue(ProofUploadQueueProvider.queue(context) is ProofUploadQueueWorkManager)
  }
}
