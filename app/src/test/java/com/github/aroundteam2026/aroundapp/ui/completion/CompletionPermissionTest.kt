// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
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
  private var submits = 0
  private var requested: Array<String>? = null
  private var refused = false

  private fun show(grantFine: Boolean) {
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
        Button(onClick = permission.submit) { Text("Submit") }
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
}
