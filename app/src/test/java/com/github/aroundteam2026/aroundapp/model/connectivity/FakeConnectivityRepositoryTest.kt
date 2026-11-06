// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.connectivity

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeConnectivityRepositoryTest {

  @Test
  fun startsOnlineByDefault() = runTest {
    assertTrue(FakeConnectivityRepository().isOnline.first())
  }

  @Test
  fun startsInTheGivenState() = runTest {
    assertFalse(FakeConnectivityRepository(online = false).isOnline.first())
  }

  @Test
  fun reportsEachChange() = runTest {
    val repository = FakeConnectivityRepository()
    val seen = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.isOnline.collect { seen += it }
    }

    repository.online.value = false
    repository.online.value = true

    assertEquals(listOf(true, false, true), seen)
  }
}
