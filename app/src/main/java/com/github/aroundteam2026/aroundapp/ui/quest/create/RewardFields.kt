// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** What the reward fields do when the venue changes them; each does nothing by default. */
class QuestRewardActions(
    val onTypeSelect: (RewardType) -> Unit = {},
    val onItemNameChange: (String) -> Unit = {},
    val onSpecificsChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
)

/**
 * The reward explorers get: one option per [RewardType], and the picked type's fields in a card.
 * Tapping the picked option again removes the reward.
 */
@Composable
fun RewardFields(reward: RewardFormState, actions: QuestRewardActions) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(AroundSpacing.SectionGap))
    FieldLabel(stringResource(R.string.create_quest_reward_type))
    Spacer(Modifier.height(AroundSpacing.Small))
    RewardTypeOptions(reward.type, actions.onTypeSelect)
    reward.type?.let {
      Spacer(Modifier.height(AroundSpacing.Medium))
      RewardDetails(reward, it, actions)
    }
  }
}

/** One option per [RewardType], side by side. */
@Composable
private fun RewardTypeOptions(selected: RewardType?, onSelect: (RewardType) -> Unit) {
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    RewardType.entries.forEach { type ->
      val isSelected = type == selected
      val (title, tag) =
          when (type) {
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
        RewardType.FREE_ITEM -> {
          QuestTextField(
              label = stringResource(R.string.create_quest_reward_item),
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
          RewardSpecifics(reward, actions.onSpecificsChange)
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

/** The optional details of the reward. */
@Composable
private fun RewardSpecifics(reward: RewardFormState, onChange: (String) -> Unit) {
  QuestTextField(
      label = stringResource(R.string.create_quest_reward_specifics),
      hint = stringResource(R.string.create_quest_reward_specifics_hint),
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
