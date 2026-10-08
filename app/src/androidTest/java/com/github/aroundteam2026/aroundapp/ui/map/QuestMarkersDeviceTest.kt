// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.location.LocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.CameraPositionState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the quest pins on a device, where the Maps SDK draws them as the camera moves. */
@RunWith(AndroidJUnit4::class)
class QuestMarkersDeviceTest {

  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val here = Location(47.3769, 8.5417)
  private val cafe = quest("cafe", "Café Lumen", here)
  // About 3 km south: on screen too, clear of the café's pin
  private val bar = quest("bar", "Bar Nocturne", Location(47.3499, 8.5417))
  private val geneva = quest("geneva", "Atelier Genève", Location(46.2044, 6.1432))

  private val viewModel =
      MapViewModel(
          object : LocationRepository {
            override suspend fun currentLocation() = here
          },
          FakeQuestRepository(initialQuests = listOf(cafe, bar, geneva)),
          FakeVenueRepository(),
      )
  private val camera = CameraPositionState()

  private val state
    get() = composeTestRule.runOnUiThread { viewModel.uiState.value }

  @Before
  fun showTheMapAroundHere() {
    composeTestRule.setContent { MapScreen(viewModel, camera) }
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      composeTestRule.runOnUiThread { camera.position.target.isNear(here) && !camera.isMoving }
    }
    awaitPins("cafe", "bar")
  }

  private fun awaitPins(vararg venueIds: String) {
    composeTestRule.waitUntil(MAP_TIMEOUT_MILLIS) {
      state.pins.map { it.venueId }.toSet() == venueIds.toSet()
    }
  }

  @Test
  fun onlyVenuesOnScreenGetAPin() {
    // Checked in setUp too: Geneva is far out of the 5 km around here
    assertEquals(setOf("cafe", "bar"), state.pins.map { it.venueId }.toSet())
  }

  @Test
  fun panningToAnotherCityShowsItsVenues() {
    composeTestRule.runOnUiThread {
      camera.move(CameraUpdateFactory.newLatLngZoom(geneva.location.toLatLng(), 13f))
    }

    awaitPins("geneva")
  }

  private fun quest(venueId: String, venueName: String, location: Location) =
      Quest(
          id = "$venueId-quest",
          venueId = venueId,
          venueName = venueName,
          location = location,
          radiusMeters = 50,
          title = "Order the secret menu",
          description = "Description",
          requirements = "Requirements",
          proofType = ProofType.PHOTO,
          reward = Reward("Free coffee", terms = null, expiresAt = null),
          status = QuestStatus.ACTIVE,
          createdAt = 1_000L,
          updatedAt = 1_000L,
      )
}
