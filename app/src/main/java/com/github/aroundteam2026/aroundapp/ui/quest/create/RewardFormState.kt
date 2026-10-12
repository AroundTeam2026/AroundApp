// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward

/** The kinds of reward a venue can pick, in the picker's order. */
enum class RewardType {
  FREE_ITEM,
  OTHER,
}

/**
 * The reward part of the quest form. No [type] means the quest has no reward.
 *
 * The values of every type are kept, so switching back to a type restores what was typed. Errors
 * follow the text fields: [QuestTextError.BLANK] only once the field has been edited.
 *
 * @property itemName The free item, for [RewardType.FREE_ITEM].
 * @property specifics Optional details about the reward.
 * @property description The reward itself, for [RewardType.OTHER].
 * @property itemNameTouched Whether [itemName] has been edited; likewise for [descriptionTouched].
 */
data class RewardFormState(
    val type: RewardType? = null,
    val itemName: String = "",
    val specifics: String = "",
    val description: String = "",
    val itemNameTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
) {
  /** The error shown under the item name, or null. */
  val itemNameError: QuestTextError?
    get() = shownQuestTextError(itemName, QuestLimits.REWARD_ITEM_MAX_LENGTH, itemNameTouched)

  /** The error shown under the specifics, or null; they may be left empty. */
  val specificsError: QuestTextError?
    get() =
        QuestTextError.TOO_LONG.takeIf {
          specifics.trim().length > QuestLimits.REWARD_SPECIFICS_MAX_LENGTH
        }

  /** The error shown under the description, or null. */
  val descriptionError: QuestTextError?
    get() =
        shownQuestTextError(
            description,
            QuestLimits.REWARD_DESCRIPTION_MAX_LENGTH,
            descriptionTouched,
        )

  /** Whether the fields of [type] are valid, touched or not; no reward is always valid. */
  val isValid: Boolean
    get() =
        when (type) {
          null -> true
          RewardType.FREE_ITEM ->
              validateQuestText(itemName, QuestLimits.REWARD_ITEM_MAX_LENGTH) == null &&
                  specificsError == null
          RewardType.OTHER ->
              validateQuestText(description, QuestLimits.REWARD_DESCRIPTION_MAX_LENGTH) == null
        }

  /** The reward to save, trimmed, or null for no reward. Only meaningful when [isValid]. */
  fun toReward(): Reward? =
      when (type) {
        null -> null
        RewardType.FREE_ITEM -> Reward.FreeItem(itemName.trim(), specifics.trim().ifEmpty { null })
        RewardType.OTHER -> Reward.Other(description.trim())
      }
}
