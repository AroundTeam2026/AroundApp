// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/** The form a venue fills in to create a quest; [onClose] leaves it without saving. */
@Composable
fun CreateQuestScreen(
    viewModel: CreateQuestViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsState()
  CreateQuestContent(
      state = state,
      onTitleChange = viewModel::updateTitle,
      onDescriptionChange = viewModel::updateDescription,
      onRequirementsChange = viewModel::updateRequirements,
      onClose = onClose,
      modifier = modifier,
  )
}

/** Stateless form, also used by the preview. */
@Composable
fun CreateQuestContent(
    state: CreateQuestUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequirementsChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AroundSpacing.ScreenHorizontal, vertical = AroundSpacing.ExtraLarge)
          .testTag(C.Tag.CREATE_QUEST_SCREEN)
  ) {
    CreateQuestHeader(onClose)
    Spacer(Modifier.height(AroundSpacing.ExtraLarge))
    QuestTextFields(state, onTitleChange, onDescriptionChange, onRequirementsChange)
  }
}

/** The round close button, and the screen title centred on the full width. */
@Composable
private fun CreateQuestHeader(onClose: () -> Unit) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Surface(
        onClick = onClose,
        shape = AroundPillShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier =
            Modifier.size(44.dp).align(Alignment.CenterStart).testTag(C.Tag.CREATE_QUEST_CLOSE),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_search_clear),
            stringResource(R.string.create_quest_close),
            tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
    Text(
        stringResource(R.string.create_quest_heading),
        style = AroundTextStyles.CardTitle,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
  }
}

@Preview(widthDp = 413, heightDp = 917)
@Composable
private fun CreateQuestPreview() {
  AroundAppTheme {
    CreateQuestContent(
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
        onClose = {},
    )
  }
}
