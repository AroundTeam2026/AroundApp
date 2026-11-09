// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.connectivity

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityRepositoryProviderTest {

  @Test
  fun servesTheConnectivityManagerRepository() {
    val repository =
        ConnectivityRepositoryProvider.repository(ApplicationProvider.getApplicationContext())

    assertTrue(repository is ConnectivityManagerRepository)
  }
}
