// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes
import com.github.aroundteam2026.aroundapp.ui.navigation.Tab
import com.github.aroundteam2026.aroundapp.ui.navigation.pressBack
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewOnFakes
import com.github.aroundteam2026.aroundapp.ui.venuepage.VenuePageOnFakes
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The completion screen's route in the explorer's app: what opens it, what it receives, and that
 * closing it returns to the screen that opened it, once. A stand-in draws the screen, so nothing
 * reads Firestore.
 */
@RunWith(AndroidJUnit4::class)
class CompletionDestinationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  /** Closes the screen shown last, as its back arrow or a sent proof does. */
  private var close: () -> Unit = {}

  private fun launch() {
    compose.setContent {
      navController = rememberNavController()
      AroundApp(
          explorerUid = "explorer",
          onSignOut = {},
          navController = navController,
          venuePage = { venueId, onBack -> VenuePageOnFakes(venueId, onBack) },
          overview = { OverviewOnFakes(it) },
          completion = { reservationId, venueName, close ->
            this.close = close
            Column(Modifier.testTag(SCREEN)) {
              Text(reservationId, Modifier.testTag(RESERVATION))
              Text(venueName, Modifier.testTag(VENUE))
              Button(onClick = close, Modifier.testTag(CLOSE)) { Text("Close") }
            }
          },
      )
    }
    compose.waitForIdle()
  }

  private fun open(action: NavController.() -> Unit) {
    compose.runOnUiThread { navController.action() }
    compose.waitForIdle()
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag)

  @Test
  fun itOpensWithItsReservationAndVenue() {
    launch()
    open { navigateToCompletion("reservation-1", "Café Lumière") }

    node(RESERVATION).assertTextEquals("reservation-1")
    node(VENUE).assertTextEquals("Café Lumière")
  }

  // A slash or a "?" would otherwise split the id or cut the name, and match no route
  @Test
  fun itReceivesAnIdAndANameWithReservedCharacters() {
    launch()
    open { navigateToCompletion("a b/c?d", "Café & Co / 50%") }

    node(RESERVATION).assertTextEquals("a b/c?d")
    node(VENUE).assertTextEquals("Café & Co / 50%")
  }

  // "complete_quest/" matches no route: opening it would crash the app
  @Test
  fun aBlankReservationIdOpensNothingInsteadOfCrashing() {
    launch()
    open { navigateToCompletion(" ", "Café") }

    node(Tab.QUESTS.screenTag).assertIsDisplayed()
    node(SCREEN).assertDoesNotExist()
  }

  @Test
  fun theRouteRefusesABlankReservationId() {
    assertThrows(IllegalArgumentException::class.java) { Routes.completeQuest("", "Café") }
  }

  @Test
  fun closingReturnsToTheScreenThatOpenedIt() {
    launch()
    open { navigateToVenuePage("venue-1") }
    open { navigateToCompletion("reservation-1", "Café") }

    node(CLOSE).performClick()
    compose.waitForIdle()

    node(SCREEN).assertDoesNotExist()
    node(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
  }

  @Test
  fun systemBackReturnsToTheScreenThatOpenedIt() {
    launch()
    open { navigateToVenuePage("venue-1") }
    open { navigateToCompletion("reservation-1", "Café") }

    compose.pressBack()

    node(SCREEN).assertDoesNotExist()
    node(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
  }

  // A sent proof shows again as the screen leaves: a second close must not close the venue page too
  @Test
  fun closingTwiceClosesOnlyTheCompletionScreen() {
    launch()
    open { navigateToVenuePage("venue-1") }
    open { navigateToCompletion("reservation-1", "Café") }
    val closeIt = close

    compose.runOnUiThread {
      closeIt()
      closeIt()
    }
    compose.waitForIdle()

    node(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
  }

  // A fast double tap on "Complete quest" would otherwise stack the screen twice
  @Test
  fun aDoubleTapOpensTheScreenOnce() {
    launch()
    open { navigateToVenuePage("venue-1") }
    open {
      navigateToCompletion("reservation-1", "Café")
      navigateToCompletion("reservation-1", "Café")
    }

    compose.pressBack()

    node(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
    val route = compose.runOnUiThread { navController.currentDestination?.route }
    assertEquals(Routes.VENUE_PAGE, route)
  }

  private companion object {
    const val SCREEN = "completion_stand_in"
    const val RESERVATION = "completion_stand_in_reservation"
    const val VENUE = "completion_stand_in_venue"
    const val CLOSE = "completion_stand_in_close"
  }
}
