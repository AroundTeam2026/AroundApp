// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** The provider name the mocked positions report; any name will do. */
private const val MOCK_PROVIDER = "e2e"

/** How often [whileReportingLocation] reports the position again. */
private const val REPORT_INTERVAL_MS = 500L

/**
 * Puts the device at [location] for the fused location provider, which the app reads, until
 * [stopMockingDeviceLocation]. The test and the app then agree on where the explorer is, whatever
 * fix the emulator has, or doesn't have yet.
 *
 * The fused provider takes mocked positions only from an app allowed to mock locations, which only
 * the shell can allow, so this allows the app first.
 */
suspend fun mockDeviceLocation(location: Location) {
  val instrumentation = InstrumentationRegistry.getInstrumentation()
  val app = instrumentation.targetContext
  // Reading the output to its end waits for the command to finish
  ParcelFileDescriptor.AutoCloseInputStream(
          instrumentation.uiAutomation.executeShellCommand(
              "appops set ${app.packageName} android:mock_location allow"
          )
      )
      .use { it.readBytes() }
  LocationServices.getFusedLocationProviderClient(app).setMockMode(true).await()
  reportLocation(location)
}

/**
 * Runs [block] while the device reports [location] again every [REPORT_INTERVAL_MS], once
 * [mockDeviceLocation] has put it there. A request for a fresh fix, such as the one sending a proof
 * makes, only takes a position reported after it, so the one [mockDeviceLocation] reported never
 * answers it.
 */
fun <T> whileReportingLocation(location: Location, block: () -> T): T = runBlocking {
  // Off this thread, which block keeps busy while it waits for the app
  val reporting =
      launch(Dispatchers.Default) {
        while (true) {
          reportLocation(location)
          delay(REPORT_INTERVAL_MS)
        }
      }
  try {
    block()
  } finally {
    reporting.cancel()
  }
}

/** Gives the device its real position back, after [mockDeviceLocation]. */
suspend fun stopMockingDeviceLocation() {
  val app = InstrumentationRegistry.getInstrumentation().targetContext
  LocationServices.getFusedLocationProviderClient(app).setMockMode(false).await()
}

/** Reports [location] as the device's current position, in mock mode. */
private suspend fun reportLocation(location: Location) {
  val app = InstrumentationRegistry.getInstrumentation().targetContext
  LocationServices.getFusedLocationProviderClient(app)
      .setMockLocation(
          android.location.Location(MOCK_PROVIDER).apply {
            latitude = location.lat
            longitude = location.lng
            // The provider ignores a position without these
            accuracy = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
          }
      )
      .await()
}
