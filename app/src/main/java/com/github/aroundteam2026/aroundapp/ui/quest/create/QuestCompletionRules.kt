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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import kotlin.math.roundToInt

/** What the completion rules do when the venue changes them; each does nothing by default. */
class QuestCompletionActions(
    val onProofTypeChange: (ProofType) -> Unit = {},
    val onRadiusPresetSelect: (Int) -> Unit = {},
    val onCustomRadiusSelect: () -> Unit = {},
    val onRadiusChange: (Int) -> Unit = {},
    val onIncrementPartySize: () -> Unit = {},
    val onDecrementPartySize: () -> Unit = {},
    val onExactPartySizeChange: (Boolean) -> Unit = {},
)

/** How explorers complete the quest: the proof they submit, the unlock radius and the party. */
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
    Spacer(Modifier.height(AroundSpacing.ExtraLarge))
    UnlockRadius(state, actions)
    Spacer(Modifier.height(AroundSpacing.ExtraLarge))
    PartyCard(state, actions)
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
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
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
        horizontalAlignment = horizontalAlignment,
    ) {
      content()
    }
  }
}

/** The title and hint of a [SelectableOption], in the accent colour when it is selected. */
@Composable
private fun OptionText(
    title: String,
    hint: String,
    selected: Boolean,
    hintStyle: TextStyle = AroundTextStyles.Small,
) {
  val accent = AroundTheme.colors.accentText
  Text(
      title,
      style = AroundTextStyles.Subtitle,
      color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
  )
  Text(
      hint,
      style = hintStyle,
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

/** The radius with its value, a preset per row, and the slider once Custom is chosen. */
@Composable
private fun UnlockRadius(state: CreateQuestUiState, actions: QuestCompletionActions) {
  val label = stringResource(R.string.create_quest_radius)
  val value = stringResource(R.string.create_quest_meters, state.radiusMeters)
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    FieldLabel(label)
    Text(
        value,
        style = AroundTextStyles.Subtitle,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(C.Tag.CREATE_QUEST_RADIUS),
    )
  }
  Spacer(Modifier.height(AroundSpacing.ExtraSmall))
  Text(
      stringResource(R.string.create_quest_radius_hint),
      style = AroundTextStyles.Caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  state.venueLocation?.let {
    Spacer(Modifier.height(AroundSpacing.CompactGap))
    QuestRadiusMap(it, state.radiusMeters, state.walkMinutes)
  }
  Spacer(Modifier.height(AroundSpacing.CompactGap))
  Row(
      Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    RadiusPreset.entries.forEach { preset ->
      val meters = preset.meters
      val hint =
          when (preset) {
            RadiusPreset.INSIDE -> R.string.create_quest_radius_inside
            RadiusPreset.AT_THE_DOOR -> R.string.create_quest_radius_door
            RadiusPreset.ON_THE_STREET -> R.string.create_quest_radius_street
          }
      RadiusOption(
          title = stringResource(R.string.create_quest_meters, meters),
          hint = stringResource(hint),
          selected = state.selectedRadiusPreset == meters,
          onClick = { actions.onRadiusPresetSelect(meters) },
          tag = "${C.Tag.CREATE_QUEST_RADIUS_PRESET}_$meters",
      )
    }
    RadiusOption(
        title = stringResource(R.string.create_quest_radius_custom),
        hint =
            stringResource(
                R.string.create_quest_radius_range,
                QuestLimits.MIN_RADIUS_METERS,
                QuestLimits.MAX_RADIUS_METERS,
            ),
        selected = state.customRadius,
        onClick = actions.onCustomRadiusSelect,
        tag = C.Tag.CREATE_QUEST_RADIUS_CUSTOM,
    )
  }
  if (state.customRadius) {
    Slider(
        value = state.radiusMeters.toFloat(),
        onValueChange = { actions.onRadiusChange(it.roundToInt()) },
        valueRange =
            QuestLimits.MIN_RADIUS_METERS.toFloat()..QuestLimits.MAX_RADIUS_METERS.toFloat(),
        // TalkBack would otherwise read an unnamed slider, in percent
        modifier =
            Modifier.fillMaxWidth().testTag(C.Tag.CREATE_QUEST_RADIUS_SLIDER).semantics {
              contentDescription = label
              stateDescription = value
            },
    )
  }
}

/** One of the radius choices, a quarter of the row wide. */
@Composable
private fun RowScope.RadiusOption(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
  SelectableOption(
      selected = selected,
      onClick = onClick,
      tag = tag,
      shape = MaterialTheme.shapes.small,
      padding =
          PaddingValues(horizontal = AroundSpacing.ExtraSmall, vertical = AroundSpacing.CompactGap),
      modifier = Modifier.weight(1f).fillMaxHeight(),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    OptionText(title, hint, selected, hintStyle = RADIUS_HINT_STYLE)
  }
}

/** The party size with its stepper, a seat filled per member, and at least or exactly. */
@Composable
private fun PartyCard(state: CreateQuestUiState, actions: QuestCompletionActions) {
  val size = state.minPartySize
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    FieldLabel(stringResource(R.string.create_quest_party_size))
    Text(
        stringResource(R.string.create_quest_party_per_visit),
        style = AroundTextStyles.Caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  Spacer(Modifier.height(AroundSpacing.CompactGap))
  Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(BORDER, MaterialTheme.colorScheme.outline),
  ) {
    Column(
        Modifier.padding(AroundSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.InsetPadding),
    ) {
      Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            size.toString(),
            style = AroundTextStyles.Subtitle.copy(fontSize = PARTY_COUNT_FONT_SIZE),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(C.Tag.CREATE_QUEST_PARTY_SIZE),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap)) {
          StepperButton(
              icon = R.drawable.ic_minus,
              description = stringResource(R.string.create_quest_party_decrement),
              enabled = size > QuestLimits.MIN_PARTY_SIZE,
              filled = false,
              onClick = actions.onDecrementPartySize,
              tag = C.Tag.CREATE_QUEST_PARTY_DECREMENT,
          )
          StepperButton(
              icon = R.drawable.ic_plus,
              description = stringResource(R.string.create_quest_party_increment),
              filled = true,
              onClick = actions.onIncrementPartySize,
              tag = C.Tag.CREATE_QUEST_PARTY_INCREMENT,
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap)) {
        repeat(SEAT_COUNT) { Seat(filled = it < size) }
      }
      Row(
          Modifier.fillMaxWidth().height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
      ) {
        PartyRule(
            title = stringResource(R.string.create_quest_party_at_least, size),
            hint = stringResource(R.string.create_quest_party_at_least_hint),
            selected = !state.exactPartySize,
            onClick = { actions.onExactPartySizeChange(false) },
            tag = C.Tag.CREATE_QUEST_PARTY_AT_LEAST,
        )
        PartyRule(
            title = stringResource(R.string.create_quest_party_exactly, size),
            hint = pluralStringResource(R.plurals.create_quest_party_exactly_hint, size, size),
            selected = state.exactPartySize,
            onClick = { actions.onExactPartySizeChange(true) },
            tag = C.Tag.CREATE_QUEST_PARTY_EXACTLY,
        )
      }
    }
  }
}

/** A round stepper button: purple when [filled], outlined if not, faded when disabled. */
@Composable
private fun StepperButton(
    @DrawableRes icon: Int,
    description: String,
    filled: Boolean,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
  Surface(
      onClick = onClick,
      enabled = enabled,
      shape = CircleShape,
      color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      border = if (filled) null else BorderStroke(RING_BORDER, MaterialTheme.colorScheme.outline),
      modifier =
          Modifier.size(STEPPER_SIZE).alpha(if (enabled) 1f else DISABLED_ALPHA).testTag(tag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
          painterResource(icon),
          contentDescription = description,
          tint =
              if (filled) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(ICON_SIZE),
      )
    }
  }
}

/** A party member's seat: a sage circle with a person when [filled], a dashed ring if not. */
@Composable
private fun Seat(filled: Boolean) {
  if (filled) {
    Box(
        Modifier.size(SEAT_SIZE)
            .background(AroundTheme.colors.sage, CircleShape)
            .testTag(C.Tag.CREATE_QUEST_PARTY_SEAT_FILLED),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(R.drawable.ic_user),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(SMALL_ICON_SIZE),
      )
    }
  } else {
    val line = MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(SEAT_SIZE).drawBehind {
          val stroke = RING_BORDER.toPx()
          drawCircle(
              color = line,
              radius = (size.minDimension - stroke) / 2,
              style =
                  Stroke(
                      width = stroke,
                      pathEffect =
                          PathEffect.dashPathEffect(
                              floatArrayOf(SEAT_DASH.toPx(), SEAT_GAP.toPx())
                          ),
                  ),
          )
        }
    )
  }
}

/** At least or exactly the party size, half of the row wide. */
@Composable
private fun RowScope.PartyRule(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
  SelectableOption(
      selected = selected,
      onClick = onClick,
      tag = tag,
      shape = MaterialTheme.shapes.small,
      padding =
          PaddingValues(horizontal = AroundSpacing.InsetPadding, vertical = AroundSpacing.Medium),
      modifier = Modifier.weight(1f).fillMaxHeight(),
  ) {
    OptionText(title, hint, selected)
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
private val STEPPER_SIZE: Dp = 44.dp
private val SEAT_SIZE: Dp = 34.dp
private val SEAT_DASH: Dp = 4.dp
private val SEAT_GAP: Dp = 3.dp
private val PARTY_COUNT_FONT_SIZE: TextUnit = 38.sp

/** The radius hints are smaller, as four choices share the row. */
private val RADIUS_HINT_STYLE: TextStyle = AroundTextStyles.Small.copy(fontSize = 11.sp)

/** Seats the party card shows; larger parties fill them all. */
private const val SEAT_COUNT = 8

/** Opacity of a stepper button that can't be pressed. */
private const val DISABLED_ALPHA = 0.38f
