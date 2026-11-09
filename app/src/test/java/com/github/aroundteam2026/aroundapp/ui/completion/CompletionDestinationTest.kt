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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.TEST_EXPLORER_NAME
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.Proof
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes
import com.github.aroundteam2026.aroundapp.ui.navigation.Tab
import com.github.aroundteam2026.aroundapp.ui.navigation.pressBack
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewOnFakes
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.venuepage.APPROVED_RESERVATION_ID
import com.github.aroundteam2026.aroundapp.ui.venuepage.FAKE_EXPLORER_UID
import com.github.aroundteam2026.aroundapp.ui.venuepage.FAKE_SLOT_START
import com.github.aroundteam2026.aroundapp.ui.venuepage.VenuePageOnFakes
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import kotlinx.coroutines.runBlocking
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

  /** How many times the app's [Completion] closed, in [showCompletion]. */
  private var closes = 0

  /** Opens the app; with [approvedQuestId], the venue pages offer that quest, approved. */
  private fun launch(approvedQuestId: String? = null) {
    compose.setContent {
      navController = rememberNavController()
      AroundApp(
          explorerUid = EXPLORER,
          onSignOut = {},
          navController = navController,
          venuePage = { venueId, questId, onBack, onSubmitProof ->
            VenuePageOnFakes(venueId, onBack, onSubmitProof, approvedQuestId, questId)
          },
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

  // The app's own wiring: the venue page's button must open this route, not a stand-in callback
  @Test
  fun submitProofOnTheQuestDetailsOpensItsReservationAndVenue() {
    val quest = testQuest()
    launch(approvedQuestId = quest.id)
    open { navigateToVenuePage("venue-1") }

    val details = compose.activity.getString(R.string.venue_quest_details, quest.title)
    compose.onNodeWithContentDescription(details).performClick()
    node(C.Tag.SUBMIT_PROOF_BUTTON).performClick()
    compose.waitForIdle()

    node(RESERVATION).assertTextEquals(APPROVED_RESERVATION_ID)
    node(VENUE).assertTextEquals(testVenue().name)
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

  // A fast double tap on "Submit proof" would otherwise stack the screen twice
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

  /**
   * Shows [Completion] for an approved reservation, on fakes, counting its closes in [closes]. With
   * [alreadySent], the explorer's proof of it is stored before the screen opens.
   */
  private fun showCompletion(alreadySent: Boolean = false) {
    val reservations = FakeReservationRepository()
    val completions = FakeCompletionRepository()
    val venue = testVenue()
    val reservationId = runBlocking {
      val id =
          reservations
              .createReservation(
                  newPendingReservation("quest", venue.id, TEST_EXPLORER_NAME, FAKE_SLOT_START),
                  EXPLORER,
              )
              .getOrThrow()
      reservations.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
      if (alreadySent) {
        completions.submit(id, venue.id, EXPLORER, Proof.Text("By the window")).getOrThrow()
      }
      id
    }
    val factory = viewModelFactory {
      initializer {
        CompletionViewModel(
            reservationId,
            EXPLORER,
            reservations,
            FakeQuestRepository(initialQuests = listOf(testQuest())),
            FakeVenueRepository(listOf(venue)),
            FakeLocationRepository(venue.location),
            completions,
            FakeProofPhotoRepository(),
        )
      }
    }
    compose.setContent {
      AroundAppTheme { Completion(venue.name, close = { closes++ }, factory = factory) }
    }
    compose.waitForIdle()
  }

  @Test
  fun theAppsScreenShowsTheFormAndItsBackArrowClosesIt() {
    showCompletion()

    node(C.Tag.COMPLETION_SCREEN).assertIsDisplayed()
    compose
        .onNodeWithContentDescription(compose.activity.getString(R.string.completion_back))
        .performClick()

    assertEquals(1, closes)
  }

  // Opened again after the proof was sent, the screen has nothing left to do
  @Test
  fun anAlreadySentProofClosesTheAppsScreen() {
    showCompletion(alreadySent = true)

    compose.waitUntil { closes == 1 }
  }

  private companion object {
    const val EXPLORER = FAKE_EXPLORER_UID
    const val SCREEN = "completion_stand_in"
    const val RESERVATION = "completion_stand_in_reservation"
    const val VENUE = "completion_stand_in_venue"
    const val CLOSE = "completion_stand_in_close"
  }
}
