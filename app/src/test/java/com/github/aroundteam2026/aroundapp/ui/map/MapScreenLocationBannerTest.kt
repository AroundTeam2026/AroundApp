// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Tests the banner that says the location is off, and its "Turn on" button. */
@RunWith(AndroidJUnit4::class)
class MapScreenLocationBannerTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app = ApplicationProvider.getApplicationContext<Application>()
  private val viewModel =
      MapViewModel(
          FakeLocationRepository(Location(46.5220, 6.6330)),
          FakeQuestRepository(),
          FakeVenueRepository(),
      )
  private val refused = mapOf(ACCESS_FINE_LOCATION to false, ACCESS_COARSE_LOCATION to false)
  private val granted = mapOf(ACCESS_FINE_LOCATION to true, ACCESS_COARSE_LOCATION to true)

  /** Shows the map, whose permission requests [dialog] answers. */
  private fun show(dialog: PermissionDialog): PermissionDialog {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialog) {
        MapScreen(viewModel, onOpenVenue = { _, _ -> })
      }
    }
    composeTestRule.waitForIdle()
    return dialog
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** Whether Android would still show its dialog, rather than refuse at once. */
  private fun androidWouldAsk(ask: Boolean) =
      listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION).forEach {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(it, ask)
      }

  private fun turnOn() {
    node(C.Tag.LOCATION_TURN_ON).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun aRefusedLocationShowsTheBanner() {
    show(PermissionDialog(refused))

    node(C.Tag.LOCATION_BANNER).assertIsDisplayed()
    composeTestRule.onNodeWithText(app.getString(R.string.map_location_off)).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(app.getString(R.string.map_location_off_fallback))
        .assertIsDisplayed()
  }

  @Test
  fun theTurnOnButtonIsFortyFourDpTall() {
    show(PermissionDialog(refused))

    val button = node(C.Tag.LOCATION_TURN_ON).getBoundsInRoot()
    assertDpEquals("button height", 44.dp, button.bottom - button.top)
  }

  @Test
  fun aGrantedLocationShowsNoBanner() {
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

    show(PermissionDialog(null))

    node(C.Tag.LOCATION_BANNER).assertDoesNotExist()
  }

  @Test
  fun turnOnAsksAgainWhileAndroidStillWould() {
    val dialog = show(PermissionDialog(refused))
    androidWouldAsk(true)
    // This time the explorer allows it
    dialog.answer = granted
    shadowOf(app).grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

    turnOn()

    assertEquals(2, dialog.requests.size)
    assertNull(shadowOf(composeTestRule.activity).nextStartedActivity)
    assertTrue(viewModel.uiState.value.showsUserLocation)
    node(C.Tag.LOCATION_BANNER).assertDoesNotExist()
  }

  @Test
  fun turnOnOpensTheAppsSettingsWhenAndroidWontAsk() {
    val dialog = show(PermissionDialog(refused))
    androidWouldAsk(false)

    turnOn()

    assertEquals("no request Android would refuse at once", 1, dialog.requests.size)
    val settings = shadowOf(composeTestRule.activity).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.action)
    assertEquals("package:${app.packageName}", settings.dataString)
  }
}
