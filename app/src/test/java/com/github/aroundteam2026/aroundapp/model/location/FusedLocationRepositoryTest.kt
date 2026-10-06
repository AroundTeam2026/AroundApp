// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.lang.ref.WeakReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class FusedLocationRepositoryTest {

  private val app: Application = ApplicationProvider.getApplicationContext()
  private val client = mockk<FusedLocationProviderClient>()
  private val repository = FusedLocationRepository(app, client)

  private fun grant(vararg permissions: String) = shadowOf(app).grantPermissions(*permissions)

  private fun deviceAt(lat: Double, lng: Double) =
      android.location.Location("test").apply {
        latitude = lat
        longitude = lng
      }

  private fun clientReturns(location: android.location.Location?) {
    every { client.getCurrentLocation(any<Int>(), any()) } returns Tasks.forResult(location)
  }

  /** A repository built from a screen's context, and a weak reference to that context. */
  private fun buildFromAScreen(): Pair<LocationRepository, WeakReference<Context>> {
    val screen = ContextWrapper(app)
    return FusedLocationRepository(screen, client) to WeakReference(screen)
  }

  @Test
  fun aScreenThatBuiltItCanBeFreedWhileItKeepsWorking() = runTest {
    // The repository can outlive the screen, so keeping the screen's context would leak the screen
    // with all its views
    val (built, screen) = buildFromAScreen()

    assertTrue("The repository keeps the screen's context alive", screen.isCollected())
    grant(ACCESS_FINE_LOCATION)
    clientReturns(deviceAt(46.5197, 6.6323))
    assertEquals(Location(46.5197, 6.6323), built.currentLocation())
  }

  @Test
  fun returnsTheDevicePosition() = runTest {
    grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    clientReturns(deviceAt(46.5197, 6.6323))

    assertEquals(Location(46.5197, 6.6323), repository.currentLocation())
  }

  @Test
  fun anApproximateLocationIsEnough() = runTest {
    // Since Android 12 the user may grant only the coarse permission
    grant(ACCESS_COARSE_LOCATION)
    clientReturns(deviceAt(-33.8688, 151.2093))

    assertEquals(Location(-33.8688, 151.2093), repository.currentLocation())
  }

  @Test
  fun withoutPermissionItNeverAsksTheProvider() = runTest {
    // Asking would throw a SecurityException
    assertNull(repository.currentLocation())
    verify(exactly = 0) { client.getCurrentLocation(any<Int>(), any()) }
  }

  @Test
  fun asksForCityBlockAccuracyNotGps() = runTest {
    // Framing 5 km does not need GPS precision, which costs more battery and time
    grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    clientReturns(deviceAt(0.0, 0.0))

    repository.currentLocation()

    verify { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, any()) }
  }

  @Test
  fun returnsNullWhenNoFixIsAvailable() = runTest {
    // The provider gives null right after boot, or with location services just turned on
    grant(ACCESS_FINE_LOCATION)
    clientReturns(null)

    assertNull(repository.currentLocation())
  }

  @Test
  fun returnsNullWhenTheProviderFails() = runTest {
    grant(ACCESS_FINE_LOCATION)
    every { client.getCurrentLocation(any<Int>(), any()) } returns
        Tasks.forException(ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED)))

    assertNull(repository.currentLocation())
  }

  @Test
  fun returnsNullWhenThePermissionIsRevokedDuringTheRequest() = runTest {
    grant(ACCESS_FINE_LOCATION)
    every { client.getCurrentLocation(any<Int>(), any()) } throws SecurityException("revoked")

    assertNull(repository.currentLocation())
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun cancellingTheCallerCancelsTheLocationRequest() = runTest {
    // Leaving the map while it locates must not keep the location hardware running
    grant(ACCESS_FINE_LOCATION)
    val token = slot<CancellationToken>()
    every { client.getCurrentLocation(any<Int>(), capture(token)) } returns
        TaskCompletionSource<android.location.Location>().task

    val call = launch { repository.currentLocation() }
    runCurrent()
    assertFalse(token.captured.isCancellationRequested)

    call.cancel()
    runCurrent()
    assertTrue(token.captured.isCancellationRequested)
  }
}

/** Whether the garbage collector frees this reference's object; collection can take a few tries. */
private fun WeakReference<*>.isCollected(): Boolean {
  repeat(GC_ATTEMPTS) {
    if (get() == null) return true
    System.gc()
  }
  return get() == null
}

private const val GC_ATTEMPTS = 20
