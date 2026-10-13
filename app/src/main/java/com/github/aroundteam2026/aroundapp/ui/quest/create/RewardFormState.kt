// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import java.math.BigDecimal

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
  /** Not digits with an optional decimal point or comma, e.g. "1e3", "+5" or "-5". */
  NOT_A_NUMBER,
  /**
   * More than [QuestLimits.MAX_DISCOUNT_DECIMALS] decimals as typed, e.g. "0.015". This also
   * rejects a thousands separator, so "1,000" or "1.000" is never misread as 1.
   */
  TOO_MANY_DECIMALS,
  /** Below [QuestLimits.MIN_DISCOUNT_AMOUNT]. */
  TOO_SMALL,
  /** Over [QuestLimits.maxDiscountAmount] of the unit. */
  TOO_LARGE,
}

/**
 * What the amount field accepts: ASCII digits with an optional point or comma, which may start or
 * end it as in ".5" or "5.". `[0-9]` rather than `\d`, which can match other digits on Android.
 */
private val AMOUNT_FORMAT = Regex("[0-9]+([.,][0-9]*)?|[.,][0-9]+")

/**
 * The reward part of the quest form. No [type] means the quest has no reward.
 *
 * The values of every type are kept apart, so switching back to a type restores what was typed and
 * nothing carries over to another type. Errors follow the text fields: [QuestTextError.BLANK] only
 * once the field has been edited.
 *
 * @property discountAmount The discount as typed, for [RewardType.DISCOUNT]; a comma counts as a
 *   decimal point, and there is no thousands separator.
 * @property discountUnit Whether [discountAmount] is in percent or francs.
 * @property itemName The free item, for [RewardType.FREE_ITEM].
 * @property discountSpecifics Optional details about the discount.
 * @property itemSpecifics Optional details about the free item.
 * @property description The reward itself, for [RewardType.OTHER].
 * @property discountAmountTouched Whether [discountAmount] has been edited; likewise for the other
 *   `Touched` flags.
 */
data class RewardFormState(
    val type: RewardType? = null,
    val discountAmount: String = "",
    val discountUnit: DiscountUnit = DiscountUnit.PERCENT,
    val itemName: String = "",
    val discountSpecifics: String = "",
    val itemSpecifics: String = "",
    val description: String = "",
    val discountAmountTouched: Boolean = false,
    val itemNameTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
) {
  /** [discountAmount] as an exact decimal, or null if it doesn't match [AMOUNT_FORMAT]. */
  private val parsedAmount: BigDecimal? =
      discountAmount.trim().let {
        if (AMOUNT_FORMAT.matches(it)) it.replace(',', '.').toBigDecimal() else null
      }

  /** Why [discountAmount] is invalid in [discountUnit], or null if it is valid. */
  private val amountError: DiscountAmountError? =
      when {
        discountAmount.isBlank() -> DiscountAmountError.BLANK
        parsedAmount == null -> DiscountAmountError.NOT_A_NUMBER
        parsedAmount.scale() > QuestLimits.MAX_DISCOUNT_DECIMALS ->
            DiscountAmountError.TOO_MANY_DECIMALS
        parsedAmount < QuestLimits.MIN_DISCOUNT_AMOUNT.toBigDecimal() ->
            DiscountAmountError.TOO_SMALL
        parsedAmount > QuestLimits.maxDiscountAmount(discountUnit).toBigDecimal() ->
            DiscountAmountError.TOO_LARGE
        else -> null
      }

  /** The specifics of [type]: a discount and a free item each have their own; other has none. */
  val specifics: String
    get() =
        when (type) {
          RewardType.DISCOUNT -> discountSpecifics
          RewardType.FREE_ITEM -> itemSpecifics
          RewardType.OTHER,
          null -> ""
        }

  /** This form with the specifics of [type] set to [value]; unchanged if [type] has none. */
  fun withSpecifics(value: String): RewardFormState =
      when (type) {
        RewardType.DISCOUNT -> copy(discountSpecifics = value)
        RewardType.FREE_ITEM -> copy(itemSpecifics = value)
        RewardType.OTHER,
        null -> this
      }

  /** The error shown under the discount amount, or null; blank only once it has been edited. */
  val discountAmountError: DiscountAmountError?
    get() = amountError.takeUnless { it == DiscountAmountError.BLANK && !discountAmountTouched }

  /** The error shown under the item name, or null. */
  val itemNameError: QuestTextError?
    get() = shownQuestTextError(itemName, QuestLimits.REWARD_ITEM_MAX_LENGTH, itemNameTouched)

  /** The error shown under the specifics of [type], or null; they may be left empty. */
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
                requireNotNull(parsedAmount).toDouble(),
                discountUnit,
                specifics.trim().ifEmpty { null },
            )
        RewardType.FREE_ITEM -> Reward.FreeItem(itemName.trim(), specifics.trim().ifEmpty { null })
        RewardType.OTHER -> Reward.Other(description.trim())
      }
}
