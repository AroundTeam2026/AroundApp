// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.SaveState
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the quest form in edit mode, as in Figma's "Edit quest" frames. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class EditQuestScreenTest {
  @get:Rule val compose = createComposeRule()
  private val quests =
      FakeQuestRepository(
          initialQuests = listOf(testQuest(id = QUEST_ID, venueId = VENUE_ID, title = TITLE))
      )
  private val reservations = FakeReservationRepository()
  private var closeCalls = 0
  private val saved = mutableListOf<String>()

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  /** Shows the real screen editing [questId] from [questRepository], on fakes. */
  private fun showScreen(
      questId: String = QUEST_ID,
      questRepository: QuestRepository = quests,
  ): CreateQuestViewModel {
    val viewModel =
        CreateQuestViewModel(
            FakeAuthRepository(VENUE_ID),
            FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
            questRepository,
            FakeAddressSearchRepository(),
            questId = questId,
            reservationRepository = reservations,
        )
    compose.setContent {
      AroundAppTheme {
        CreateQuestScreen(viewModel, onClose = { closeCalls++ }, onCreated = { saved += it })
      }
    }
    compose.waitForIdle()
    return viewModel
  }

  /** Shows the stateless form editing a valid quest, in [saveState]. */
  private fun showContent(saveState: SaveState) {
    compose.setContent {
      AroundAppTheme {
        CreateQuestContent(
            state =
                CreateQuestUiState(
                    title = TITLE,
                    description = TITLE,
                    requirements = TITLE,
                    venueLocation = Location(46.5, 6.6),
                    editing = true,
                    saveState = saveState,
                ),
            onTitleChange = {},
            onDescriptionChange = {},
            onRequirementsChange = {},
            completionActions = QuestCompletionActions(),
            rewardActions = QuestRewardActions(),
            onSubmit = {},
            onClose = {},
            onRetryVenue = {},
        )
      }
    }
  }

  @Test
  fun theFormOpensFilledInUnderEditQuest() {
    showScreen()

    compose.onNodeWithText(string(R.string.edit_quest_heading)).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).assertTextContains(TITLE)
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT)
        .assertTextEquals(string(R.string.edit_quest_submit))
        .assertIsEnabled()
  }

  @Test
  fun savingChangesUpdatesTheQuestAndLeaves() {
    showScreen()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).performTextReplacement("Find the golden fox")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()
    compose.waitForIdle()

    assertEquals(listOf(QUEST_ID), saved)
    assertEquals("Find the golden fox", runBlocking { quests.getQuest(QUEST_ID) }!!.title)
  }

  @Test
  fun theEditPreviewShowsAFailedSave() {
    compose.setContent { EditQuestPreview() }
    compose.onNodeWithTag(C.Tag.EDIT_QUEST_SAVE_ERROR).assertIsDisplayed()
  }

  // Explorers reserved it for this proof, and a pending party must keep fitting its size
  @Test
  fun aReservedQuestLocksItsProofTypeAndPartySize() {
    runBlocking {
      reservations.createReservation(
          newPendingReservation(QUEST_ID, VENUE_ID, "Ana", 0),
          "explorer",
      )
    }
    showScreen()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_TEXT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_INCREMENT).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXACTLY).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_LOCKED).assertExists()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_LOCKED).assertExists()
  }

  @Test
  fun aQuestWithoutReservationsCanChangeEverything() {
    showScreen()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_TEXT).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_INCREMENT).assertIsEnabled()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_LOCKED).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_LOCKED).assertDoesNotExist()
  }

  // A network blip mustn't close the form
  @Test
  fun aQuestThatCantBeReadCanBeReadAgain() {
    var offline = true
    val flaky =
        object : QuestRepository by quests {
          override suspend fun getQuest(questId: String): Quest? =
              if (offline) throw IOException("offline") else quests.getQuest(questId)
        }
    showScreen(questRepository = flaky)
    compose
        .onNodeWithTag(C.Tag.EDIT_QUEST_LOAD_FAILED)
        .assertTextEquals(string(R.string.edit_quest_load_failed))
    assertEquals(0, closeCalls)

    offline = false
    compose.onNodeWithTag(C.Tag.EDIT_QUEST_RETRY).performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.EDIT_QUEST_LOAD_FAILED).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).assertTextContains(TITLE)
  }

  // Unlike creating, which shows a snackbar once, the message stays above the button
  @Test
  fun aFailedSaveShowsWhyAboveTheButtonUntilTheNextSave() {
    showScreen()
    quests.forcedFailure = IOException("offline")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()
    compose.waitForIdle()

    compose
        .onNodeWithTag(C.Tag.EDIT_QUEST_SAVE_ERROR)
        .assertTextEquals(string(R.string.edit_quest_save_failed))
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).assertIsEnabled()

    quests.forcedFailure = null
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()
    compose.waitForIdle()
    assertEquals(listOf(QUEST_ID), saved)
  }

  @Test
  fun whileSavingTheButtonSaysSavingAndTheLineAboveExplains() {
    showContent(SaveState.Saving)

    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT)
        .assertTextEquals(string(R.string.edit_quest_saving))
        .assertIsNotEnabled()
    compose
        .onNodeWithTag(C.Tag.EDIT_QUEST_SAVING)
        .assertTextEquals(string(R.string.edit_quest_saving_line))
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY).assertDoesNotExist()
  }

  @Test
  fun anIdleEditShowsTheSummaryAboveTheButton() {
    showContent(SaveState.Idle)

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.EDIT_QUEST_SAVING).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.EDIT_QUEST_SAVE_ERROR).assertDoesNotExist()
  }

  // Nothing to edit: the form leaves rather than showing an empty form
  @Test
  fun aQuestThatNoLongerExistsClosesTheForm() {
    showScreen(questId = "gone")
    assertEquals(1, closeCalls)
    assertEquals(emptyList<String>(), saved)
  }

  private companion object {
    const val VENUE_ID = "venue-1"
    const val QUEST_ID = "quest-1"
    const val TITLE = "Find the hidden fox"
  }
}
