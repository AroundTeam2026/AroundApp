// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRow
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRowStatus
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the Join a party screen: what each state of [JoinPartyUiState] shows, what the explorer's
 * taps and typing call, and the screen over a real [JoinPartyViewModel] on fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class JoinPartyScreenTest {
  @get:Rule val compose = createComposeRule()

  private val app = ApplicationProvider.getApplicationContext<Application>()

  private var state by mutableStateOf(JoinPartyUiState())
  private val typed = mutableListOf<String>()
  private var submits = 0
  private var backs = 0

  private fun show() {
    compose.setContent {
      AroundAppTheme {
        JoinPartyContent(
            state,
            onCodeChange = { typed += it },
            onSubmit = { submits++ },
            onBack = { backs++ },
        )
      }
    }
  }

  private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

  private fun refused(error: JoinPartyUiState.Error) =
      JoinPartyUiState(code = "7KQ M24", lookup = JoinPartyUiState.Lookup.Refused(error))

  private fun found(join: JoinPartyUiState.Join = JoinPartyUiState.Join.IDLE) =
      JoinPartyUiState("7KQ M42", JoinPartyUiState.Lookup.Found(party()), join)

  private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)
  private val hasNoError = SemanticsMatcher.keyNotDefined(SemanticsProperties.Error)

  @Test
  fun showsTheTitleTheExplanationTheLabelAndTheButtonAndTheBackArrowGoesBack() {
    show()

    compose.onNodeWithText(text(R.string.join_party_title)).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.join_party_body)).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.join_party_code_label)).assertIsDisplayed()
    compose
        .onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT)
        .assertTextContains(text(R.string.join_party_join))
    compose.onNodeWithContentDescription(text(R.string.join_party_back)).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun theFieldShowsTheCodeAndReportsWhatIsTyped() {
    show()
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).performTextInput("7kq")
    assertEquals(listOf("7kq"), typed)

    state = JoinPartyUiState(code = "7KQ M42")

    compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).assertTextContains("7KQ M42")
  }

  @Test
  fun eachRefusalShowsItsMessageAndMarksTheField() {
    // A full party with more joined than it takes, so swapped numbers would show
    val messages =
        mapOf(
            JoinPartyUiState.Error.NotFound to text(R.string.join_party_not_found),
            JoinPartyUiState.Error.Full(joined = 5, capacity = 4) to
                "This party is full: 5 of 4 joined.",
            JoinPartyUiState.Error.Ended to text(R.string.join_party_ended),
            JoinPartyUiState.Error.Closed to text(R.string.join_party_closed),
            JoinPartyUiState.Error.AlreadyJoined to text(R.string.join_party_already_joined),
            JoinPartyUiState.Error.Left to text(R.string.join_party_left),
            JoinPartyUiState.Error.Offline to text(R.string.join_party_offline),
            JoinPartyUiState.Error.Failed to text(R.string.join_party_failed),
        )
    state = refused(JoinPartyUiState.Error.NotFound)
    show()

    for ((error, message) in messages) {
      state = refused(error)
      compose.waitForIdle()

      compose.onNodeWithText(message).assertIsDisplayed()
      compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).assert(hasError)
      compose.onNodeWithTag(C.Tag.JOIN_PARTY_FOUND).assertDoesNotExist()
    }
  }

  @Test
  fun aFoundPartyShowsItsCardThatCannotBeTappedAndNoError() {
    state = found()
    show()

    compose.onNodeWithText(text(R.string.join_party_you_are_joining)).assertIsDisplayed()
    compose.onNodeWithText("Find the hidden fox").assertIsDisplayed()
    compose.onNodeWithTag("${C.Tag.OVERVIEW_ROW}_reservation-1").assertHasNoClickAction()
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).assert(hasNoError)
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_ERROR).assertDoesNotExist()
  }

  @Test
  fun whileLookingItShowsProgress() {
    state = JoinPartyUiState(code = "7KQ M42", lookup = JoinPartyUiState.Lookup.Looking)
    show()

    compose.onNodeWithTag(C.Tag.JOIN_PARTY_LOOKING).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.join_party_looking)).assertIsDisplayed()
  }

  @Test
  fun theButtonIsOffWithNothingTypedAndSubmitsAsDoesTheKeyboardsDoneKey() {
    show()
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT).assertIsNotEnabled()

    state = JoinPartyUiState(code = "7KQ")
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT).assertIsEnabled().performClick()
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).performImeAction()

    assertEquals(2, submits)
  }

  @Test
  fun whileJoiningTheButtonSaysJoiningAndTheFieldAndBackAreOff() {
    state = found(JoinPartyUiState.Join.JOINING)
    show()

    compose
        .onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT)
        .assertTextContains(text(R.string.join_party_joining))
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).assertIsNotEnabled()
    compose.onNodeWithContentDescription(text(R.string.join_party_back)).assertIsNotEnabled()
  }

  @Test
  fun typingACodeFindsItsPartyAndTappingJoinJoinsItAndSaysSo() {
    val questRepository = FakeQuestRepository(initialQuests = listOf(testQuest("fox", "venue-1")))
    val reservations = FakeReservationRepository(quests = questRepository, newCode = { "7KQM42" })
    runBlocking {
      reservations.createReservation(newPendingReservation("fox", "venue-1", "Ada", 0L), "friend")
    }
    val viewModel =
        JoinPartyViewModel(
            "explorer",
            reservations,
            questRepository,
            FakeVenueRepository(listOf(testVenue(id = "venue-1", name = "Café Lumen"))),
            reservations.party,
            FakeConnectivityRepository(),
        )
    var joined = 0
    compose.setContent {
      AroundAppTheme { JoinPartyScreen(viewModel, onBack = {}, onJoined = { joined++ }) }
    }

    compose.onNodeWithTag(C.Tag.JOIN_PARTY_CODE).performTextInput("7kq m42")
    compose.waitUntil {
      compose.onAllNodes(hasTestTag(C.Tag.JOIN_PARTY_FOUND)).fetchSemanticsNodes().size == 1
    }
    assertEquals(0, joined)
    compose.onNodeWithTag(C.Tag.JOIN_PARTY_SUBMIT).performClick()
    compose.waitUntil { joined == 1 }

    assertEquals(1, joined)
  }

  private fun party() =
      OverviewRow(
          reservationId = "reservation-1",
          questId = "fox",
          venueId = "venue-1",
          questTitle = "Find the hidden fox",
          reward = null,
          venueName = "Café Lumen",
          address = null,
          avatar = VenueAvatar.Initials("CL"),
          slotStart = 0L,
          partySize = 3,
          status = OverviewRowStatus.WAITING,
      )
}
