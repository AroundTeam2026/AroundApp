// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
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

  private fun show(state: VenueQuestsUiState) {
    compose.setContent {
      AroundAppTheme { VenueQuestsContent(state, onCreateQuest = { formsOpened++ }) }
    }
  }

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  /** The text of every node tagged [tag], top to bottom. */
  private fun texts(tag: String) =
      compose.onAllNodesWithTag(tag).fetchSemanticsNodes().map {
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

  @Test
  fun noQuestsOffersToCreateTheFirstInsteadOfThePlus() {
    show(VenueQuestsUiState.Loaded(emptyList()))

    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_CREATE_FIRST).performClick()
    assertEquals(1, formsOpened)
  }

  @Test
  fun aLoadErrorIsShown() {
    show(VenueQuestsUiState.Failed)

    compose
        .onNodeWithTag(C.Tag.VENUE_QUESTS_FAILED)
        .assertTextEquals(string(R.string.venue_quests_failed))
    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_LIST).assertDoesNotExist()
  }
}
