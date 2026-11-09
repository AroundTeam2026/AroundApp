// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenueQuestsScreenTest {
  @get:Rule val compose = createComposeRule()
  private var formsOpened = 0
  private val questsEdited = mutableListOf<String>()

  private fun show(state: VenueQuestsUiState) {
    compose.setContent {
      AroundAppTheme {
        VenueQuestsContent(
            state,
            onCreateQuest = { formsOpened++ },
            onRetry = {},
            onEditQuest = { questsEdited += it },
        )
      }
    }
  }

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  /** The text of every node tagged [tag], top to bottom. */
  private fun texts(tag: String) =
      compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map {
        it.config[SemanticsProperties.Text].joinToString()
      }

  @Test
  fun eachQuestShowsItsTitleAndStatusInOrder() {
    val statuses = listOf(QuestStatus.DRAFT, QuestStatus.ACTIVE, QuestStatus.ARCHIVED)
    show(
        VenueQuestsUiState.Loaded(
            statuses.map { testQuest(id = it.name, title = it.name, status = it) }
        )
    )

    assertEquals(statuses.map { it.name }, texts(C.Tag.VENUE_QUEST_TITLE))
    assertEquals(
        listOf(
                R.string.venue_quests_status_draft,
                R.string.venue_quests_status_active,
                R.string.venue_quests_status_archived,
            )
            .map(::string),
        texts(C.Tag.VENUE_QUEST_STATUS),
    )
    compose.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).performClick()
    assertEquals(1, formsOpened)
  }

  // The whole card, its pencil included, is one button
  @Test
  fun tappingACardEditsItsQuest() {
    show(VenueQuestsUiState.Loaded(listOf(testQuest(id = "a"), testQuest(id = "b"))))

    compose.onAllNodesWithTag(C.Tag.VENUE_QUEST_CARD)[0].performClick()

    assertEquals(listOf("a"), questsEdited)
  }

  // One stop per card for TalkBack, which names the quest it edits
  @Test
  fun eachCardIsOneTargetNamingItsQuest() {
    show(VenueQuestsUiState.Loaded(listOf(testQuest(title = "Fox"))))

    compose.onAllNodes(hasClickAction()).assertCountEquals(2) // the card and the + button
    compose
        .onNodeWithTag(C.Tag.VENUE_QUEST_CARD)
        .assert(
            SemanticsMatcher("click label names the quest") {
              it.config[SemanticsActions.OnClick].label ==
                  ApplicationProvider.getApplicationContext<Context>()
                      .getString(R.string.venue_quests_edit, "Fox")
            }
        )
  }

  @Test
  fun noQuestsOffersToCreateTheFirstInsteadOfThePlus() {
    show(VenueQuestsUiState.Loaded(emptyList()))

    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_CREATE_FIRST).performClick()
    assertEquals(1, formsOpened)
  }

  @Test
  fun tryingAgainAfterALoadErrorListsTheQuests() {
    val failingOnce = failingFirstListen(FakeQuestRepository())
    val viewModel = VenueQuestsViewModel(FakeAuthRepository(VENUE_ID), failingOnce)
    compose.setContent {
      AroundAppTheme { VenueQuestsScreen(viewModel, onCreateQuest = { formsOpened++ }) }
    }
    compose
        .onNodeWithTag(C.Tag.VENUE_QUESTS_FAILED)
        .assertTextEquals(string(R.string.venue_quests_failed))

    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_RETRY).performClick()

    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_FAILED).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_EMPTY).assertIsDisplayed()
  }

  @Test
  fun loadingShowsProgress() {
    show(VenueQuestsUiState.Loading)
    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_LOADING).assertIsDisplayed()
  }

  private companion object {
    const val VENUE_ID = "venue-1"
  }
}
