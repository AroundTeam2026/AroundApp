// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** What the completion rules do when the venue changes them; each does nothing by default. */
class QuestCompletionActions(
    val onProofTypeChange: (ProofType) -> Unit = {},
)

/** How explorers complete the quest: the proof they submit. */
@Composable
fun QuestCompletionRules(state: CreateQuestUiState, actions: QuestCompletionActions) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(AroundSpacing.SectionGap))
    FieldLabel(stringResource(R.string.create_quest_proof_type))
    Spacer(Modifier.height(AroundSpacing.CompactGap))
    ProofOptions(state.proofType, actions.onProofTypeChange)
    Spacer(Modifier.height(AroundSpacing.Small))
    ExplorerPreview()
  }
}

/** A card that shows whether it is [selected], in the light purple of a chosen option. */
@Composable
private fun SelectableOption(
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    shape: Shape,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
      selected = selected,
      onClick = onClick,
      shape = shape,
      color =
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surface,
      border =
          if (selected) BorderStroke(SELECTED_BORDER, MaterialTheme.colorScheme.primary)
          else BorderStroke(BORDER, MaterialTheme.colorScheme.outline),
      modifier = modifier.testTag(tag),
  ) {
    Column(
        Modifier.padding(padding),
        verticalArrangement = Arrangement.spacedBy(OPTION_TEXT_GAP),
    ) {
      content()
    }
  }
}

/** The title and hint of a [SelectableOption], in the accent colour when it is selected. */
@Composable
private fun OptionText(title: String, hint: String, selected: Boolean) {
  val accent = AroundTheme.colors.accentText
  Text(
      title,
      style = AroundTextStyles.Subtitle,
      color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
  )
  Text(
      hint,
      style = AroundTextStyles.Small,
      color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** One card per [ProofType], side by side and as tall as each other. */
@Composable
private fun ProofOptions(selected: ProofType, onSelect: (ProofType) -> Unit) {
  Row(
      Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    ProofType.entries.forEach { type ->
      val isSelected = type == selected
      val (icon, title, hint, tag) =
          when (type) {
            ProofType.PHOTO ->
                ProofCopy(
                    R.drawable.ic_proof_photo,
                    R.string.create_quest_proof_photo,
                    R.string.create_quest_proof_photo_hint,
                    C.Tag.CREATE_QUEST_PROOF_PHOTO,
                )
            ProofType.TEXT ->
                ProofCopy(
                    R.drawable.ic_proof_text,
                    R.string.create_quest_proof_text,
                    R.string.create_quest_proof_text_hint,
                    C.Tag.CREATE_QUEST_PROOF_TEXT,
                )
          }
      SelectableOption(
          selected = isSelected,
          onClick = { onSelect(type) },
          tag = tag,
          shape = MaterialTheme.shapes.medium,
          padding = PaddingValues(AroundSpacing.InsetPadding),
          modifier = Modifier.weight(1f).fillMaxHeight(),
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          IconTile(icon, isSelected)
          SelectionMark(isSelected)
        }
        Spacer(Modifier.height(AroundSpacing.CompactGap))
        OptionText(stringResource(title), stringResource(hint), isSelected)
      }
    }
  }
}

/** The icon, title, hint and test tag of one proof card. */
private data class ProofCopy(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val tag: String,
)

/** The rounded square behind a proof card's icon: sage when selected, the panel colour if not. */
@Composable
private fun IconTile(@DrawableRes icon: Int, selected: Boolean) {
  Box(
      Modifier.size(ICON_TILE_SIZE)
          .background(
              if (selected) AroundTheme.colors.sage else AroundTheme.colors.panel,
              AroundCorners.InsetField,
          ),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint =
            if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(ICON_SIZE),
    )
  }
}

/** A filled purple check when selected, an empty ring when not. */
@Composable
private fun SelectionMark(selected: Boolean) {
  if (selected) {
    Box(
        Modifier.size(SELECTION_MARK_SIZE)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(R.drawable.ic_check),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(CHECK_ICON_SIZE),
      )
    }
  } else {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(RING_BORDER, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(SELECTION_MARK_SIZE),
    ) {}
  }
}

/** What explorers will read about the proof; a placeholder until their quest view exists. */
@Composable
private fun ExplorerPreview() {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
      Modifier.fillMaxWidth()
          .background(AroundTheme.colors.panel, AroundCorners.InsetField)
          .padding(horizontal = AroundSpacing.Medium, vertical = AroundSpacing.CompactGap)
          // TalkBack reads the label and the preview as one sentence
          .semantics(mergeDescendants = true) {}
          .testTag(C.Tag.CREATE_QUEST_EXPLORER_PREVIEW),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    Icon(
        painterResource(R.drawable.ic_eye),
        contentDescription = null,
        tint = muted,
        modifier = Modifier.size(SMALL_ICON_SIZE),
    )
    val label = stringResource(R.string.create_quest_explorers_see)
    val preview = stringResource(R.string.create_quest_explorer_preview)
    Text(
        buildAnnotatedString {
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = muted)) { append(label) }
          append(" ")
          append(preview)
        },
        style = AroundTextStyles.Caption,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private val BORDER: Dp = 1.dp
private val RING_BORDER: Dp = 1.5.dp
private val SELECTED_BORDER: Dp = 2.dp
private val OPTION_TEXT_GAP: Dp = 2.dp
private val CHECK_ICON_SIZE: Dp = 14.dp
private val SMALL_ICON_SIZE: Dp = 16.dp
private val ICON_SIZE: Dp = 20.dp
private val SELECTION_MARK_SIZE: Dp = 22.dp
private val ICON_TILE_SIZE: Dp = 40.dp
