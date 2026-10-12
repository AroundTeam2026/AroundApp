// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward

/** The kinds of reward a venue can pick, in the picker's order. */
enum class RewardType {
  /** A discount on the price, in percent or francs, with optional specifics. */
  DISCOUNT,
  /** An item given for free, with optional specifics. */
  FREE_ITEM,
  /** A reward the venue describes in its own words. */
  OTHER,
}

/** Why a discount amount is invalid. */
enum class DiscountAmountError {
  /** Empty or whitespace only. */
  BLANK,
  /** Not a finite number. */
  NOT_A_NUMBER,
  /** Below [QuestLimits.MIN_DISCOUNT_AMOUNT]. */
  TOO_SMALL,
  /** Over [QuestLimits.MAX_PERCENT_DISCOUNT] in [DiscountUnit.PERCENT]. */
  TOO_LARGE,
}

/**
 * The reward part of the quest form. No [type] means the quest has no reward.
 *
 * The values of every type are kept, so switching back to a type restores what was typed. Errors
 * follow the text fields: [QuestTextError.BLANK] only once the field has been edited.
 *
 * @property discountAmount The discount as typed, for [RewardType.DISCOUNT]; a comma counts as a
 *   decimal point.
 * @property discountUnit Whether [discountAmount] is in percent or francs.
 * @property itemName The free item, for [RewardType.FREE_ITEM].
 * @property specifics Optional details about the reward.
 * @property description The reward itself, for [RewardType.OTHER].
 * @property discountAmountTouched Whether [discountAmount] has been edited; likewise for the other
 *   `Touched` flags.
 */
data class RewardFormState(
    val type: RewardType? = null,
    val discountAmount: String = "",
    val discountUnit: DiscountUnit = DiscountUnit.PERCENT,
    val itemName: String = "",
    val specifics: String = "",
    val description: String = "",
    val discountAmountTouched: Boolean = false,
    val itemNameTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
) {
  /** [discountAmount] as a number, or null if it isn't one. */
  private val parsedAmount: Double?
    get() = discountAmount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

  /** Why [discountAmount] is invalid in [discountUnit], or null if it is valid. */
  private val amountError: DiscountAmountError?
    get() {
      val amount = parsedAmount
      return when {
        discountAmount.isBlank() -> DiscountAmountError.BLANK
        amount == null -> DiscountAmountError.NOT_A_NUMBER
        amount < QuestLimits.MIN_DISCOUNT_AMOUNT -> DiscountAmountError.TOO_SMALL
        discountUnit == DiscountUnit.PERCENT && amount > QuestLimits.MAX_PERCENT_DISCOUNT ->
            DiscountAmountError.TOO_LARGE
        else -> null
      }
    }

  /** The error shown under the discount amount, or null; blank only once it has been edited. */
  val discountAmountError: DiscountAmountError?
    get() = amountError.takeUnless { it == DiscountAmountError.BLANK && !discountAmountTouched }

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
          RewardType.DISCOUNT -> amountError == null && specificsError == null
          RewardType.FREE_ITEM ->
              validateQuestText(itemName, QuestLimits.REWARD_ITEM_MAX_LENGTH) == null &&
                  specificsError == null
          RewardType.OTHER ->
              validateQuestText(description, QuestLimits.REWARD_DESCRIPTION_MAX_LENGTH) == null
        }

  /**
   * The reward to save, trimmed, or null for no reward. Only call it when [isValid]: a discount
   * whose amount isn't a number throws.
   */
  fun toReward(): Reward? =
      when (type) {
        null -> null
        RewardType.DISCOUNT ->
            Reward.Discount(
                requireNotNull(parsedAmount),
                discountUnit,
                specifics.trim().ifEmpty { null },
            )
        RewardType.FREE_ITEM -> Reward.FreeItem(itemName.trim(), specifics.trim().ifEmpty { null })
        RewardType.OTHER -> Reward.Other(description.trim())
      }
}
