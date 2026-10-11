// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits

/** Why a text field of the quest form is invalid. */
enum class QuestTextError {
  /** Empty or whitespace only. */
  BLANK,
  /** Longer than the field's limit once trimmed. */
  TOO_LONG,
}

/**
 * The error of a quest text field holding [value], or null if it is valid: not blank, and at most
 * [maxLength] characters once trimmed, since the quest is saved trimmed.
 */
private fun validateQuestText(value: String, maxLength: Int): QuestTextError? {
  val trimmed = value.trim()
  return when {
    trimmed.isEmpty() -> QuestTextError.BLANK
    trimmed.length > maxLength -> QuestTextError.TOO_LONG
    else -> null
  }
}

/**
 * The quest form being filled in by a venue.
 *
 * Errors are derived from the values, so they can never disagree with them. A
 * [QuestTextError.BLANK] error is only shown once its field has been edited, so the form doesn't
 * open full of errors; [QuestTextError.TOO_LONG] shows at once.
 *
 * @property titleTouched Whether [title] has been edited; likewise for the other `Touched` flags.
 */
data class CreateQuestUiState(
    val title: String = "",
    val description: String = "",
    val requirements: String = "",
    val titleTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
    val requirementsTouched: Boolean = false,
) {
  /** The error shown under the title, or null. */
  val titleError: QuestTextError?
    get() = shownError(title, QuestLimits.TITLE_MAX_LENGTH, titleTouched)

  /** The error shown under the description, or null. */
  val descriptionError: QuestTextError?
    get() = shownError(description, QuestLimits.DESCRIPTION_MAX_LENGTH, descriptionTouched)

  /** The error shown under the requirements, or null. */
  val requirementsError: QuestTextError?
    get() = shownError(requirements, QuestLimits.REQUIREMENTS_MAX_LENGTH, requirementsTouched)

  /** Whether every field is valid, touched or not. */
  val canSubmit: Boolean
    get() =
        validateQuestText(title, QuestLimits.TITLE_MAX_LENGTH) == null &&
            validateQuestText(description, QuestLimits.DESCRIPTION_MAX_LENGTH) == null &&
            validateQuestText(requirements, QuestLimits.REQUIREMENTS_MAX_LENGTH) == null

  private fun shownError(value: String, maxLength: Int, touched: Boolean): QuestTextError? {
    val error = validateQuestText(value, maxLength)
    return if (error == QuestTextError.BLANK && !touched) null else error
  }
}
