// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateQuestViewModelTest {
  private val viewModel = CreateQuestViewModel()
  private val state
    get() = viewModel.uiState.value

  private fun fillValid() {
    viewModel.updateTitle("Find the hidden fox")
    viewModel.updateDescription("A tiny ceramic fox hides somewhere in the café.")
    viewModel.updateRequirements("Order any drink.")
  }

  /** One field of the form: how to edit it, read its error, and its limit. */
  private class Field(
      val name: String,
      val update: (CreateQuestViewModel, String) -> Unit,
      val error: (CreateQuestUiState) -> QuestTextError?,
      val maxLength: Int,
  )

  private val fields =
      listOf(
          Field(
              "title",
              CreateQuestViewModel::updateTitle,
              { it.titleError },
              QuestLimits.TITLE_MAX_LENGTH,
          ),
          Field(
              "description",
              CreateQuestViewModel::updateDescription,
              { it.descriptionError },
              QuestLimits.DESCRIPTION_MAX_LENGTH,
          ),
          Field(
              "requirements",
              CreateQuestViewModel::updateRequirements,
              { it.requirementsError },
              QuestLimits.REQUIREMENTS_MAX_LENGTH,
          ),
      )

  @Test
  fun initialFormIsEmptyShowsNoErrorsAndCannotBeSubmitted() {
    assertEquals(CreateQuestUiState(), state)
    fields.forEach { assertNull(it.name, it.error(state)) }
    assertFalse(state.canSubmit)
  }

  @Test
  fun updatesKeepTheTypedTextUntrimmed() {
    viewModel.updateTitle("  Fox ")
    viewModel.updateDescription("Desc\n")
    viewModel.updateRequirements(" Req")
    assertEquals("  Fox ", state.title)
    assertEquals("Desc\n", state.description)
    assertEquals(" Req", state.requirements)
  }

  @Test
  fun validFormCanBeSubmitted() {
    fillValid()
    fields.forEach { assertNull(it.name, it.error(state)) }
    assertTrue(state.canSubmit)
  }

  @Test
  fun emptyOrWhitespaceFieldIsBlankOnceEdited() {
    fields.forEach { field ->
      fillValid()
      listOf("", " \t\n ").forEach { value ->
        field.update(viewModel, value)
        assertEquals(field.name, QuestTextError.BLANK, field.error(state))
        assertFalse(field.name, state.canSubmit)
      }
    }
  }

  @Test
  fun untouchedBlankFieldShowsNoErrorButBlocksSubmit() {
    viewModel.updateTitle("Find the hidden fox")
    assertNull(state.descriptionError)
    assertNull(state.requirementsError)
    assertFalse(state.canSubmit)
  }

  @Test
  fun fieldAtItsLimitIsValid() {
    fields.forEach { field ->
      fillValid()
      field.update(viewModel, "a".repeat(field.maxLength))
      assertNull(field.name, field.error(state))
      assertTrue(field.name, state.canSubmit)
    }
  }

  @Test
  fun fieldOverItsLimitIsTooLong() {
    fields.forEach { field ->
      fillValid()
      field.update(viewModel, "a".repeat(field.maxLength + 1))
      assertEquals(field.name, QuestTextError.TOO_LONG, field.error(state))
      assertFalse(field.name, state.canSubmit)
    }
  }

  @Test
  fun surroundingWhitespaceDoesNotCountTowardsTheLimit() {
    fields.forEach { field ->
      fillValid()
      field.update(viewModel, "  " + "a".repeat(field.maxLength) + " \n")
      assertNull(field.name, field.error(state))
      assertTrue(field.name, state.canSubmit)
    }
  }

  @Test
  fun tooLongShowsBeforeTheFieldIsTouched() {
    val state = CreateQuestUiState(title = "a".repeat(QuestLimits.TITLE_MAX_LENGTH + 1))
    assertEquals(QuestTextError.TOO_LONG, state.titleError)
  }
}
