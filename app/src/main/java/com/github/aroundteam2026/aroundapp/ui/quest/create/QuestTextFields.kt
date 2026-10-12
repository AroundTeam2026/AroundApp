// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/** The title, description and requirements of the quest, each with its counter and error. */
@Composable
fun QuestTextFields(
    state: CreateQuestUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequirementsChange: (String) -> Unit,
) {
  Column {
    QuestTextField(
        label = stringResource(R.string.create_quest_field_title),
        hint = stringResource(R.string.create_quest_field_title_hint),
        value = state.title,
        onValueChange = onTitleChange,
        error = state.titleError,
        maxLength = QuestLimits.TITLE_MAX_LENGTH,
        singleLine = true,
        tag = C.Tag.CREATE_QUEST_TITLE,
        errorTag = C.Tag.CREATE_QUEST_TITLE_ERROR,
        counterTag = C.Tag.CREATE_QUEST_TITLE_COUNTER,
    )
    Spacer(Modifier.height(AroundSpacing.Large))
    QuestTextField(
        label = stringResource(R.string.create_quest_field_description),
        hint = stringResource(R.string.create_quest_field_description_hint),
        value = state.description,
        onValueChange = onDescriptionChange,
        error = state.descriptionError,
        maxLength = QuestLimits.DESCRIPTION_MAX_LENGTH,
        singleLine = false,
        tag = C.Tag.CREATE_QUEST_DESCRIPTION,
        errorTag = C.Tag.CREATE_QUEST_DESCRIPTION_ERROR,
        counterTag = C.Tag.CREATE_QUEST_DESCRIPTION_COUNTER,
    )
    Spacer(Modifier.height(AroundSpacing.Large))
    QuestTextField(
        label = stringResource(R.string.create_quest_field_requirements),
        hint = stringResource(R.string.create_quest_field_requirements_hint),
        value = state.requirements,
        onValueChange = onRequirementsChange,
        error = state.requirementsError,
        maxLength = QuestLimits.REQUIREMENTS_MAX_LENGTH,
        singleLine = false,
        tag = C.Tag.CREATE_QUEST_REQUIREMENTS,
        errorTag = C.Tag.CREATE_QUEST_REQUIREMENTS_ERROR,
        counterTag = C.Tag.CREATE_QUEST_REQUIREMENTS_COUNTER,
    )
  }
}

/**
 * A labelled quest text field, with a counter of its trimmed length out of [maxLength] under it, as
 * the trimmed text is what gets validated, and [error] below.
 */
@Composable
internal fun QuestTextField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: QuestTextError?,
    maxLength: Int,
    singleLine: Boolean,
    tag: String,
    errorTag: String,
    counterTag: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
    FieldLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        isError = error != null,
        placeholder = { Text(hint) },
        shape = MaterialTheme.shapes.small,
        textStyle = if (singleLine) AroundTextStyles.FieldValue else AroundTextStyles.TextArea,
        colors = questFieldColors(),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label },
    )
    Text(
        stringResource(R.string.create_quest_counter, value.trim().length, maxLength),
        style = AroundTextStyles.Small,
        color =
            if (error == QuestTextError.TOO_LONG) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.End).testTag(counterTag),
    )
    error?.let {
      Text(
          when (it) {
            QuestTextError.BLANK -> stringResource(R.string.create_quest_blank)
            QuestTextError.TOO_LONG ->
                pluralStringResource(R.plurals.create_quest_too_long, maxLength, maxLength)
          },
          style = AroundTextStyles.Caption,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(errorTag).semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

/** The colours of every quest form text field: the surface inside, purple once focused. */
@Composable
internal fun questFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        errorContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Preview(widthDp = 413)
@Composable
private fun QuestTextFieldsPreview() {
  AroundAppTheme {
    Column(
        Modifier.background(MaterialTheme.colorScheme.background)
            .padding(AroundSpacing.ScreenHorizontal)
    ) {
      QuestTextFields(
          state =
              CreateQuestUiState(
                  title = "Find the hidden fox",
                  description =
                      "A tiny ceramic fox hides somewhere in the café. Spot it and snap a photo.",
                  requirements = "Order any drink. One photo per party.",
              ),
          onTitleChange = {},
          onDescriptionChange = {},
          onRequirementsChange = {},
      )
    }
  }
}
