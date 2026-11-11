// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where the device is during the journey, and where the venue and its quest are: EPFL. */
private val HERE = Location(46.5191, 6.5668)

/**
 * An explorer's first visit, through the app as it ships on the Auth and Firestore emulators: they
 * create an account, find a venue's quest saved in Firestore among the map's nearby quests, view
 * that venue's page and accept the quest there, then see it waiting for the venue on the Quests
 * tab. The venue then signs in on the same device and finds the request on its dashboard.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerJourneyTest : TestCase() {
  @get:Rule val e2e = E2eRule()
  private val composeTestRule = e2e.composeTestRule

  private val id = UUID.randomUUID()
  private val venueEmail = "venue-$id@around.test"
  private val venueName = "Journey venue $id"
  private val questTitle = "Journey quest $id"

  /** The seeded venue's id, its owner's uid. */
  private lateinit var venueId: String
  /** The seeded quest's id, which Firestore assigns. */
  private lateinit var questId: String
  /** The id of the reservation the explorer makes, which Firestore assigns. */
  private lateinit var reservationId: String

  /**
   * Puts the device at [HERE] and saves a venue and its active quest there, as a fresh venue
   * account would: the nearby quests are those within 5 km of the explorer, so the app must find
   * the explorer where the test put the quest, whatever fix the emulator has.
   */
  @Before
  fun seedQuest(): Unit = runBlocking {
    withTimeout(SETUP_TIMEOUT_MS) {
      mockDeviceLocation(HERE)
      val seeded =
          seedVenueAndQuest(seedApp(), venueEmail, venueName, questTitle, HERE, ProofType.PHOTO)
      venueId = seeded.venueId
      questId = seeded.questId
    }
  }

  @After fun stopMockingLocation() = runBlocking { stopMockingDeviceLocation() }

  @Test
  fun aNewExplorerFindsTheSeededQuestAndOpensItsVenuesPage() = run {
    step("Register a new explorer") { composeTestRule.createAccount() }
    step("Find the quest in the map's nearby list") {
      composeTestRule.findTheQuestNearby(questTitle)
    }
    step("View the venue's page, which lists the quest") {
      composeTestRule.viewTheVenuesPage(venueName, questTitle)
    }
  }

  @Test
  fun anExplorerAcceptsTheSeededQuestAndItsVenueSeesTheRequest() = run {
    step("Register a new explorer and open the venue's page") {
      composeTestRule.createAccount()
      composeTestRule.findTheQuestNearby(questTitle)
      composeTestRule.viewTheVenuesPage(venueName, questTitle)
    }
    step("Pick the quest and accept it") {
      val row = hasTestTag(C.Tag.VENUE_QUEST_ROW) and hasText(questTitle)
      composeTestRule.onNode(row).performClick().assertIsSelected()
      composeTestRule.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertIsEnabled().performClick()
    }
    step("The bar says the venue has yet to approve") {
      val accepted =
          InstrumentationRegistry.getInstrumentation()
              .targetContext
              .getString(R.string.accept_quest_accepted)
      composeTestRule.waitFor(
          hasTestTag(C.Tag.ACCEPT_QUEST_BUTTON) and hasAnyDescendant(hasText(accepted))
      )
      composeTestRule.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON).assertIsNotEnabled()
    }
    step("The server keeps one pending reservation for the quest") {
      val explorerUid = Firebase.auth.currentUser!!.uid
      val reservations = runBlocking {
        withTimeout(SETUP_TIMEOUT_MS) {
          // The app writes without waiting for the server; a write the rules refuse is rolled
          // back, so it never shows up below and the test times out
          Firebase.firestore.waitForPendingWrites().await()
          ReservationRepositoryFirestore(Firebase.firestore).observeForExplorer(explorerUid).first {
            it.isNotEmpty()
          }
        }
      }
      val reservation = reservations.single()
      assertEquals(questId, reservation.questId)
      assertEquals(venueId, reservation.venueId)
      assertEquals(ReservationStatus.PENDING, reservation.status)
      reservationId = reservation.id
    }
    step("The Quests tab lists the quest, waiting for the venue") {
      composeTestRule.seeTheQuestsCard(reservationId, questTitle, R.string.overview_status_waiting)
    }
    step("Its card opens the quest's details, whose back arrow returns to the Quests tab") {
      composeTestRule.onNodeWithTag("${C.Tag.OVERVIEW_ROW}_$reservationId").performClick()
      composeTestRule.waitFor(hasTestTag(C.Tag.QUEST_DETAIL_SCREEN))
      composeTestRule.onNodeWithTag(C.Tag.VENUE_PAGE_BACK).performClick()
      composeTestRule.waitFor(hasTestTag("${C.Tag.OVERVIEW_ROW}_$reservationId"))
    }
    step("The explorer signs out") { composeTestRule.signOut() }
    step("The quest's venue signs in and its dashboard lists the request") {
      composeTestRule.signIn(venueEmail, landsOn = C.Tag.VENUE_DASHBOARD_SCREEN)
      // Only this reservation's row, whatever else the dashboard lists
      val inItsRow = hasAnyAncestor(hasTestTag("${C.Tag.DASHBOARD_ROW}_$reservationId"))
      composeTestRule.waitFor(
          hasTestTag(C.Tag.DASHBOARD_ROW_TITLE) and hasText(questTitle) and inItsRow
      )
      // Only a request still waiting for an answer offers to approve it
      composeTestRule.waitFor(hasTestTag(C.Tag.DASHBOARD_ROW_APPROVE) and inItsRow)
      // The party comes from its memberships, which the venue may read: the explorer alone
      val solo =
          InstrumentationRegistry.getInstrumentation()
              .targetContext
              .getString(R.string.dashboard_party_solo)
      composeTestRule.waitFor(hasTestTag(C.Tag.DASHBOARD_ROW_PARTY) and hasText(solo) and inItsRow)
    }
  }
}
