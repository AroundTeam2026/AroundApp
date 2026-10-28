// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.FlakyVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CreateQuestBarTest {
  @get:Rule val compose = createComposeRule()
  private val quests = FakeQuestRepository()
  private lateinit var viewModel: CreateQuestViewModel
  private val created = mutableListOf<String>()

  /**
   * Shows the form of the venue read from [venues], by default a placed venue whose radius is 50 m,
   * saving to [questRepository].
   */
  private fun show(
      questRepository: QuestRepository = quests,
      venues: VenueRepository = FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
  ) {
    viewModel = CreateQuestViewModel(FakeAuthRepository(VENUE_ID), venues, questRepository)
    compose.setContent {
      AroundAppTheme { CreateQuestScreen(viewModel, onClose = {}, onCreated = { created += it }) }
    }
    compose.waitForIdle()
  }

  private fun fillValid() {
    compose.runOnIdle {
      viewModel.updateTitle("Find the fox")
      viewModel.updateDescription("A fox hides here.")
      viewModel.updateRequirements("Order a drink.")
    }
  }

  private fun submitButton() = compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT)

  @Test
  fun theButtonIsEnabledOnlyOnceTheFormCanBeSaved() {
    show()
    submitButton().assertIsDisplayed().assertIsNotEnabled()
    fillValid()
    submitButton().assertIsEnabled()
  }

  @Test
  fun theSummaryFollowsTheRules() {
    show()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY)
        .assertTextEquals("Photo proof · 50 m · 1+ people · No reward")

    compose.runOnIdle {
      viewModel.updateProofType(ProofType.TEXT)
      viewModel.selectRadiusPreset(100)
      viewModel.incrementPartySize()
      viewModel.updateExactPartySize(true)
      viewModel.selectRewardType(RewardType.FREE_ITEM)
    }
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY)
        .assertTextEquals("Text proof · 100 m · 2 people · Free item")

    compose.runOnIdle { viewModel.selectRewardType(RewardType.DISCOUNT) }
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY)
        .assertTextEquals("Text proof · 100 m · 2 people · Discount")
    compose.runOnIdle { viewModel.selectRewardType(RewardType.OTHER) }
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_SUMMARY)
        .assertTextEquals("Text proof · 100 m · 2 people · Other")
  }

  @Test
  fun whileSavingTheButtonShowsProgressAndIsDisabled() {
    val answer = CompletableDeferred<Result<String>>()
    show(
        object : QuestRepository by quests {
          override suspend fun createQuest(quest: Quest): Result<String> = answer.await()
        }
    )
    fillValid()
    submitButton().performClick()

    submitButton().assertIsNotEnabled()
    // What TalkBack reads on the button while it has no text
    submitButton().assertContentDescriptionEquals("Saving the quest…")
    compose.onNodeWithText("Create quest").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).assertIsNotEnabled()

    answer.complete(Result.success("quest-9"))
    compose.waitForIdle()
    assertEquals(listOf("quest-9"), created)
    // Still disabled while the screen closes, so a tap can't close it twice
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).assertIsNotEnabled()
  }

  @Test
  fun aFailedSaveShowsASnackbarOnce() {
    show()
    fillValid()
    quests.forcedFailure = IOException("offline")
    submitButton().performClick()

    compose.onNodeWithText("Couldn't save the quest. Try again.").assertIsDisplayed()
    // Cleared once shown, so a recreated screen doesn't show it again
    assertEquals(SaveState.Idle, viewModel.uiState.value.saveState)
    submitButton().assertIsEnabled()
    assertEquals(emptyList<String>(), created)
  }

  @Test
  fun aVenueThatCannotBeReadIsExplainedUntilARetryLoadsIt() {
    val read = CompletableDeferred<Venue?>()
    show(venues = FlakyVenueRepository(failures = 1, testVenue(id = VENUE_ID), gate = read))
    fillValid()
    val failed = compose.onNodeWithTag(C.Tag.CREATE_QUEST_VENUE_FAILED)
    failed.assertTextEquals(
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.create_quest_venue_failed)
    )
    submitButton().assertIsNotEnabled()

    val retry = compose.onNodeWithTag(C.Tag.CREATE_QUEST_VENUE_RETRY)
    retry.performClick()
    // While the venue is read again, the explanation stays and can't be retried twice
    failed.assertExists()
    retry.assertIsNotEnabled()

    read.complete(null)
    failed.assertDoesNotExist()
    submitButton().assertIsEnabled()
  }

  private companion object {
    const val VENUE_ID = "venue-1"
  }
}
