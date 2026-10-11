// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CreateQuestScreenTest {
  @get:Rule val compose = createComposeRule()
  private val viewModel = CreateQuestViewModel()
  private var closeCalls = 0

  @Before
  fun setUp() {
    compose.setContent {
      AroundAppTheme { CreateQuestScreen(viewModel, onClose = { closeCalls++ }) }
    }
  }

  @Test
  fun showsHeaderAndTheThreeFields() {
    compose.onNodeWithText("New quest").assertIsDisplayed()
    listOf(
            C.Tag.CREATE_QUEST_TITLE,
            C.Tag.CREATE_QUEST_DESCRIPTION,
            C.Tag.CREATE_QUEST_REQUIREMENTS,
        )
        .forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_COUNTER)
        .assertTextEquals("0 / ${QuestLimits.TITLE_MAX_LENGTH}")
  }

  @Test
  fun typingUpdatesTheStateAndTheCounters() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).performTextInput("Find the hidden fox")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_DESCRIPTION).performTextInput("A fox.")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS).performTextInput("A drink.")

    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_COUNTER)
        .assertTextEquals("19 / ${QuestLimits.TITLE_MAX_LENGTH}")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_DESCRIPTION_COUNTER)
        .performScrollTo()
        .assertTextEquals("6 / ${QuestLimits.DESCRIPTION_MAX_LENGTH}")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS_COUNTER)
        .performScrollTo()
        .assertTextEquals("8 / ${QuestLimits.REQUIREMENTS_MAX_LENGTH}")
    val state = viewModel.uiState.value
    assertEquals("Find the hidden fox", state.title)
    assertEquals("A fox.", state.description)
    assertEquals("A drink.", state.requirements)
  }

  @Test
  fun clearingAFieldShowsTheBlankError() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS).performTextInput("A drink.")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS).performTextClearance()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS_ERROR)
        .performScrollTo()
        .assertTextEquals("This field is required.")
  }

  @Test
  fun tooLongTitleShowsTheLengthError() {
    val max = QuestLimits.TITLE_MAX_LENGTH
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).performTextInput("a".repeat(max + 1))
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_COUNTER).assertTextEquals("${max + 1} / $max")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_ERROR)
        .assertTextEquals("Use $max characters or fewer.")
  }

  @Test
  fun tooLongDescriptionShowsTheLengthError() {
    val max = QuestLimits.DESCRIPTION_MAX_LENGTH
    viewModel.updateDescription("a".repeat(max + 1))
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_DESCRIPTION_ERROR)
        .performScrollTo()
        .assertTextEquals("Use $max characters or fewer.")
  }

  @Test
  fun closeButtonCallsOnClose() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    assertEquals(1, closeCalls)
  }
}
