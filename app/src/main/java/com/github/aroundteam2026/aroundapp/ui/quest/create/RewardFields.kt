// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** What the reward fields do when the venue changes them; each does nothing by default. */
class QuestRewardActions(
    val onTypeSelect: (RewardType?) -> Unit = {},
    val onDiscountAmountChange: (String) -> Unit = {},
    val onDiscountUnitChange: (DiscountUnit) -> Unit = {},
    val onItemNameChange: (String) -> Unit = {},
    val onSpecificsChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
)

/**
 * The optional reward explorers get: no reward, or one option per [RewardType] with the picked
 * type's fields in a card.
 */
@Composable
fun RewardFields(reward: RewardFormState, actions: QuestRewardActions) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(AroundSpacing.SectionGap))
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
        verticalAlignment = Alignment.Bottom,
    ) {
      FieldLabel(stringResource(R.string.create_quest_reward_type))
      Text(
          stringResource(R.string.create_quest_reward_optional),
          style = AroundTextStyles.Small,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.height(AroundSpacing.Small))
    NoRewardOption(selected = reward.type == null, onClick = { actions.onTypeSelect(null) })
    Spacer(Modifier.height(AroundSpacing.Small))
    RewardTypeOptions(reward.type, actions.onTypeSelect)
    reward.type?.let {
      Spacer(Modifier.height(AroundSpacing.Medium))
      RewardDetails(reward, it, actions)
    }
  }
}

/** The full-width option for a quest without a reward, with its trophy. */
@Composable
private fun NoRewardOption(selected: Boolean, onClick: () -> Unit) {
  SelectableOption(
      selected = selected,
      onClick = onClick,
      tag = C.Tag.CREATE_QUEST_REWARD_NONE,
      shape = MaterialTheme.shapes.small,
      padding =
          PaddingValues(horizontal = AroundSpacing.InsetPadding, vertical = AroundSpacing.Medium),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(R.drawable.ic_trophy),
          contentDescription = null,
          tint =
              if (selected) AroundTheme.colors.accentText
              else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Column(verticalArrangement = Arrangement.spacedBy(OPTION_TEXT_GAP)) {
        OptionText(
            stringResource(R.string.create_quest_reward_none),
            stringResource(R.string.create_quest_reward_none_hint),
            selected,
        )
      }
    }
  }
}

/** One option per [RewardType], side by side. */
@Composable
private fun RewardTypeOptions(selected: RewardType?, onSelect: (RewardType?) -> Unit) {
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    RewardType.entries.forEach { type ->
      val isSelected = type == selected
      val (title, tag) =
          when (type) {
            RewardType.DISCOUNT ->
                R.string.create_quest_reward_discount to C.Tag.CREATE_QUEST_REWARD_DISCOUNT
            RewardType.FREE_ITEM ->
                R.string.create_quest_reward_free_item to C.Tag.CREATE_QUEST_REWARD_FREE_ITEM
            RewardType.OTHER ->
                R.string.create_quest_reward_other to C.Tag.CREATE_QUEST_REWARD_OTHER
          }
      SelectableOption(
          selected = isSelected,
          onClick = { onSelect(type) },
          tag = tag,
          shape = MaterialTheme.shapes.small,
          padding = PaddingValues(vertical = AroundSpacing.Large),
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
            stringResource(title),
            style = AroundTextStyles.Subtitle,
            color =
                if (isSelected) AroundTheme.colors.accentText
                else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/** The fields of the picked reward [type], on a panel card. */
@Composable
private fun RewardDetails(
    reward: RewardFormState,
    type: RewardType,
    actions: QuestRewardActions,
) {
  Surface(
      shape = MaterialTheme.shapes.small,
      color = AroundTheme.colors.panel,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
        Modifier.fillMaxWidth().padding(AroundSpacing.InsetPadding),
        verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
    ) {
      when (type) {
        RewardType.DISCOUNT -> {
          DiscountAmount(reward, actions)
          RewardSpecifics(
              reward,
              stringResource(R.string.create_quest_reward_discount_specifics_hint),
              actions.onSpecificsChange,
          )
        }
        RewardType.FREE_ITEM -> {
          QuestTextField(
              label = stringResource(R.string.create_quest_reward_free_item),
              hint = stringResource(R.string.create_quest_reward_item_hint),
              value = reward.itemName,
              onValueChange = actions.onItemNameChange,
              error = reward.itemNameError,
              maxLength = QuestLimits.REWARD_ITEM_MAX_LENGTH,
              singleLine = true,
              tag = C.Tag.CREATE_QUEST_REWARD_ITEM,
              errorTag = C.Tag.CREATE_QUEST_REWARD_ITEM_ERROR,
              counterTag = C.Tag.CREATE_QUEST_REWARD_ITEM_COUNTER,
          )
          RewardSpecifics(
              reward,
              stringResource(R.string.create_quest_reward_specifics_hint),
              actions.onSpecificsChange,
          )
        }
        RewardType.OTHER ->
            QuestTextField(
                label = stringResource(R.string.create_quest_reward_description),
                hint = stringResource(R.string.create_quest_reward_description_hint),
                value = reward.description,
                onValueChange = actions.onDescriptionChange,
                error = reward.descriptionError,
                maxLength = QuestLimits.REWARD_DESCRIPTION_MAX_LENGTH,
                singleLine = false,
                tag = C.Tag.CREATE_QUEST_REWARD_DESCRIPTION,
                errorTag = C.Tag.CREATE_QUEST_REWARD_DESCRIPTION_ERROR,
                counterTag = C.Tag.CREATE_QUEST_REWARD_DESCRIPTION_COUNTER,
            )
      }
    }
  }
}

/** The discount amount beside its unit, with the amount's error below. */
@Composable
private fun DiscountAmount(reward: RewardFormState, actions: QuestRewardActions) {
  val label = stringResource(R.string.create_quest_reward_amount)
  val error = reward.discountAmountError
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small)) {
    FieldLabel(label)
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
          value = reward.discountAmount,
          onValueChange = actions.onDiscountAmountChange,
          singleLine = true,
          isError = error != null,
          placeholder = { Text(stringResource(R.string.create_quest_reward_amount_hint)) },
          shape = MaterialTheme.shapes.small,
          textStyle = AroundTextStyles.FieldValue,
          colors = questFieldColors(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier =
              Modifier.weight(1f).testTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).semantics {
                contentDescription = label
              },
      )
      DiscountUnitToggle(reward.discountUnit, actions.onDiscountUnitChange)
    }
    error?.let {
      Text(
          discountErrorText(it),
          style = AroundTextStyles.Caption,
          color = MaterialTheme.colorScheme.error,
          modifier =
              Modifier.testTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR).semantics {
                liveRegion = LiveRegionMode.Polite
              },
      )
    }
  }
}

/** What [error] tells the venue to fix in the discount amount. */
@Composable
private fun discountErrorText(error: DiscountAmountError) =
    when (error) {
      DiscountAmountError.BLANK -> stringResource(R.string.create_quest_blank)
      DiscountAmountError.NOT_A_NUMBER ->
          stringResource(R.string.create_quest_reward_amount_not_a_number)
      DiscountAmountError.TOO_SMALL ->
          stringResource(
              R.string.create_quest_reward_amount_too_small,
              QuestLimits.MIN_DISCOUNT_AMOUNT.toString(),
          )
      DiscountAmountError.TOO_LARGE ->
          stringResource(
              R.string.create_quest_reward_amount_too_large,
              QuestLimits.MAX_PERCENT_DISCOUNT.toInt(),
          )
    }

/** Percent or francs, as a segmented control: the chosen unit is filled in purple. */
@Composable
private fun DiscountUnitToggle(selected: DiscountUnit, onSelect: (DiscountUnit) -> Unit) {
  Row(
      Modifier.background(MaterialTheme.colorScheme.surface, AroundCorners.InsetField)
          .border(1.dp, MaterialTheme.colorScheme.outline, AroundCorners.InsetField)
          .padding(AroundSpacing.ExtraSmall)
          .selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
  ) {
    DiscountUnit.entries.forEach { unit ->
      val isSelected = unit == selected
      val (title, tag) =
          when (unit) {
            DiscountUnit.PERCENT ->
                R.string.create_quest_reward_unit_percent to C.Tag.CREATE_QUEST_REWARD_UNIT_PERCENT
            DiscountUnit.CHF ->
                R.string.create_quest_reward_unit_chf to C.Tag.CREATE_QUEST_REWARD_UNIT_CHF
          }
      Surface(
          selected = isSelected,
          onClick = { onSelect(unit) },
          shape = AroundCorners.SegmentedOption,
          color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
          modifier = Modifier.height(UNIT_HEIGHT).testTag(tag),
      ) {
        Box(Modifier.padding(horizontal = AroundSpacing.InsetPadding), Alignment.Center) {
          Text(
              stringResource(title),
              style = AroundTextStyles.FieldLabel,
              color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

/** The optional details of the reward, with the [hint] of its type. */
@Composable
private fun RewardSpecifics(reward: RewardFormState, hint: String, onChange: (String) -> Unit) {
  QuestTextField(
      label = stringResource(R.string.create_quest_reward_specifics),
      hint = hint,
      value = reward.specifics,
      onValueChange = onChange,
      error = reward.specificsError,
      maxLength = QuestLimits.REWARD_SPECIFICS_MAX_LENGTH,
      singleLine = false,
      tag = C.Tag.CREATE_QUEST_REWARD_SPECIFICS,
      errorTag = C.Tag.CREATE_QUEST_REWARD_SPECIFICS_ERROR,
      counterTag = C.Tag.CREATE_QUEST_REWARD_SPECIFICS_COUNTER,
  )
}

private val UNIT_HEIGHT: Dp = 36.dp
