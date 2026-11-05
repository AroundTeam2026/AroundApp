// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CompletionPermissionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val locationManager = app.getSystemService(LocationManager::class.java)
  private var submits = 0
  private var requested: Array<String>? = null
  private var refused = false
  private var servicesEnabled = false

  private fun show(grantFine: Boolean, locationEnabled: Boolean = true) {
    shadowOf(locationManager).setLocationEnabled(locationEnabled)
    shadowOf(app).denyPermissions(ACCESS_FINE_LOCATION)
    shadowOf(app).grantPermissions(ACCESS_COARSE_LOCATION)
    val results =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry =
              object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                  @Suppress("UNCHECKED_CAST")
                  requested = input as Array<String>
                  if (grantFine) shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION)
                  dispatchResult(
                      requestCode,
                      mapOf(ACCESS_FINE_LOCATION to grantFine, ACCESS_COARSE_LOCATION to true),
                  )
                }
              }
        }
    compose.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides results) {
        val permission = rememberCompletionPermission { submits++ }
        refused = permission.refused
        servicesEnabled = permission.servicesEnabled
        Column {
          Button(onClick = permission.submit) { Text("Submit") }
          Button(onClick = permission.request) { Text("Recover location") }
        }
      }
    }
  }

  @Test
  fun approximateOnlyGrantDoesNotSubmitAndRequestsBothPermissions() {
    show(grantFine = false)
    compose.onNodeWithText("Submit").performClick()
    compose.runOnIdle {
      assertEquals(setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), requested!!.toSet())
      assertEquals(0, submits)
      assertTrue(refused)
    }
  }

  @Test
  fun preciseGrantContinuesSubmission() {
    show(grantFine = true)
    compose.onNodeWithText("Submit").performClick()
    compose.runOnIdle {
      assertEquals(1, submits)
      assertFalse(refused)
    }
  }

  @Test
  fun existingPreciseGrantSubmitsWithoutAnotherPrompt() {
    show(grantFine = true)
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION)
    compose.onNodeWithText("Submit").performClick()
    compose.runOnIdle {
      assertEquals(1, submits)
      assertNull(requested)
    }
  }

  @Test
  fun recoveryWithPrecisePermissionAndServicesOffOpensLocationSettings() {
    show(grantFine = true, locationEnabled = false)
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION)
    compose.onNodeWithText("Recover location").performClick()
    compose.runOnIdle {
      assertEquals(
          Settings.ACTION_LOCATION_SOURCE_SETTINGS,
          shadowOf(app).nextStartedActivity.action,
      )
      assertNull(requested)
      assertEquals(0, submits)
      assertFalse(servicesEnabled)
    }
    // Returning from settings refreshes the recovery state without submitting on its own.
    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    shadowOf(locationManager).setLocationEnabled(true)
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    compose.runOnIdle {
      assertTrue(servicesEnabled)
      assertEquals(0, submits)
    }
    compose.onNodeWithText("Recover location").performClick()
    compose.runOnIdle {
      assertEquals(1, submits)
      assertNull(requested)
    }
  }

  @Test
  fun submissionWithPrecisePermissionAndServicesOffOpensLocationSettings() {
    show(grantFine = true, locationEnabled = false)
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION)
    compose.onNodeWithText("Submit").performClick()
    compose.runOnIdle {
      assertEquals(
          Settings.ACTION_LOCATION_SOURCE_SETTINGS,
          shadowOf(app).nextStartedActivity.action,
      )
      assertNull(requested)
      assertEquals(0, submits)
    }
  }

  @Test
  fun recoveryRequestsPermissionOnlyWhenItIsMissing() {
    show(grantFine = true)
    compose.onNodeWithText("Recover location").performClick()
    compose.runOnIdle {
      assertEquals(setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), requested!!.toSet())
      assertEquals(1, submits)
    }
  }

  @Test
  fun recoveryRechecksServicesChangedWhileTheScreenIsOpen() {
    show(grantFine = true)
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION)
    shadowOf(locationManager).setLocationEnabled(false)
    compose.onNodeWithText("Recover location").performClick()
    compose.runOnIdle {
      assertEquals(
          Settings.ACTION_LOCATION_SOURCE_SETTINGS,
          shadowOf(app).nextStartedActivity.action,
      )
      assertNull(requested)
      assertEquals(0, submits)
    }
  }

  @Test
  fun recoveryAfterPermissionRefusalOpensAppSettings() {
    show(grantFine = false)
    compose.onNodeWithText("Submit").performClick()
    compose.onNodeWithText("Recover location").performClick()
    compose.runOnIdle {
      val intent = shadowOf(app).nextStartedActivity
      assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
      assertEquals("package:${app.packageName}", intent.data.toString())
      assertEquals(0, submits)
    }
  }
}
