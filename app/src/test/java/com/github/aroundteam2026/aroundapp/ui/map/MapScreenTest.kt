// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.DEFAULT_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val zurich = Location(47.3769, 8.5417)
  private val repository = FakeLocationRepository(zurich)
  private val viewModel = MapViewModel(repository)
  private val state
    get() = viewModel.uiState.value

  /** Shows the screen; the permission dialog, if asked for, answers with [answer]. */
  private fun show(answer: Map<String, Boolean>? = null): PermissionDialog {
    val dialog = PermissionDialog(answer)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        MapScreen(viewModel)
      }
    }
    composeTestRule.waitForIdle()
    return dialog
  }

  @Test
  fun asksForPreciseAndApproximateLocationTogether() {
    val dialog = show()

    assertEquals(1, dialog.requests.size)
    assertEquals(
        setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
        dialog.requests.single().toSet(),
    )
  }

  @Test
  fun anAlreadyGrantedPermissionLocatesWithoutAsking() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

    val dialog = show()

    assertTrue(dialog.requests.isEmpty())
    assertEquals(1, repository.calls)
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun anApproximateLocationIsEnoughToLocate() {
    val dialog = show(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to true))

    assertEquals(1, dialog.requests.size)
    assertEquals(1, repository.calls)
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun aRefusalKeepsLausanneAndNeverLocates() {
    val dialog = show(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))

    assertEquals(1, dialog.requests.size)
    assertEquals(0, repository.calls)
    assertFalse(state.showsUserLocation)
    assertEquals(DEFAULT_CENTER.boundsWithin(NEARBY_RADIUS_METERS), state.areaToFrame)
  }

  /** Shows the app with this test's map on the Map tab, then opens it. */
  private fun showInApp(answer: Map<String, Boolean>): PermissionDialog {
    val dialog = PermissionDialog(answer)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        AroundApp(screen = { tab -> if (tab == Tab.MAP) MapScreen(viewModel) else Text(tab.route) })
      }
    }
    openTab(Tab.MAP)
    return dialog
  }

  private fun openTab(tab: Tab) {
    composeTestRule.onNodeWithTag(tab.tabTag).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun aRefusalIsNotAskedAgainWhenComingBackToTheMap() {
    val dialog = showInApp(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))

    openTab(Tab.PROFILE)
    openTab(Tab.MAP)

    assertEquals(1, dialog.requests.size)
    assertFalse(state.showsUserLocation)
  }

  // The permission is checked before the earlier refusal, so a later grant always counts
  @Test
  fun aPermissionGrantedInTheSettingsIsUsedOnReturn() {
    showInApp(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))
    openTab(Tab.PROFILE)

    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(ACCESS_COARSE_LOCATION)
    openTab(Tab.MAP)

    assertEquals(1, repository.calls)
    assertTrue(state.showsUserLocation)
  }

  @Test
  fun backEndsTheVisitSoTheNextVisitAsksAgain() {
    // Back pops the map with its state, unlike switching tabs
    val dialog = showInApp(mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false))

    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()
    openTab(Tab.MAP)

    assertEquals(2, dialog.requests.size)
  }

  @Test
  fun aDismissedDialogCountsAsARefusal() {
    // Android answers with no grants at all when the dialog is dismissed, e.g. on rotation
    val dialog = show(emptyMap())

    assertEquals(1, dialog.requests.size)
    assertEquals(0, repository.calls)
    assertFalse(state.showsUserLocation)
  }
}

/** Stands in for the system permission dialog: records requests and answers with [answer]. */
private class PermissionDialog(private val answer: Map<String, Boolean>?) :
    ActivityResultRegistryOwner {
  val requests = mutableListOf<Array<String>>()

  override val activityResultRegistry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
          @Suppress("UNCHECKED_CAST")
          requests += input as Array<String>
          answer?.let { dispatchResult(requestCode, it) }
        }
      }
}
