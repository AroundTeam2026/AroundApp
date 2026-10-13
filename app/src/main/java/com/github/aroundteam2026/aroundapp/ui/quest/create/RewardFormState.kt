// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

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
   * More than [QuestLimits.MAX_DISCOUNT_DECIMALS] decimals besides trailing zeros, e.g. "0.015", or
   * a comma that reads as a thousands separator, e.g. "1,000".
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

/** An amount like "1,000", which some read as a thousand: it is rejected rather than taken as 1. */
private val THOUSANDS_FORMAT = Regex("[0-9]+,[0-9]{3}")

/**
 * The reward part of the quest form. No [type] means the quest has no reward.
 *
 * The values of every type are kept, so switching back to a type restores what was typed. Errors
 * follow the text fields: [QuestTextError.BLANK] only once the field has been edited.
 *
 * @property discountAmount The discount as typed, for [RewardType.DISCOUNT]; a comma counts as a
 *   decimal point, and there is no thousands separator.
 * @property discountUnit Whether [discountAmount] is in percent or francs.
 * @property itemName The free item, for [RewardType.FREE_ITEM].
 * @property specifics Optional details about the reward.
 * @property description The reward itself, for [RewardType.OTHER].
 * @property expiryDate The last day the reward can be used, or null if it never expires.
 * @property expiryInPast Whether [expiryDate] had already passed when it was picked.
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
    val expiryDate: LocalDate? = null,
    val expiryInPast: Boolean = false,
    val discountAmountTouched: Boolean = false,
    val itemNameTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
) {
  /** [discountAmount] as an exact decimal, or null if it doesn't match [AMOUNT_FORMAT]. */
  private val parsedAmount: BigDecimal?
    get() {
      val amount = discountAmount.trim()
      return if (AMOUNT_FORMAT.matches(amount)) amount.replace(',', '.').toBigDecimal() else null
    }

  /** Why [discountAmount] is invalid in [discountUnit], or null if it is valid. */
  private val amountError: DiscountAmountError?
    get() {
      val amount = parsedAmount
      return when {
        discountAmount.isBlank() -> DiscountAmountError.BLANK
        amount == null -> DiscountAmountError.NOT_A_NUMBER
        // Trailing zeros don't count: 5.000 is 5
        amount.stripTrailingZeros().scale() > QuestLimits.MAX_DISCOUNT_DECIMALS ||
            THOUSANDS_FORMAT.matches(discountAmount.trim()) -> DiscountAmountError.TOO_MANY_DECIMALS
        amount < QuestLimits.MIN_DISCOUNT_AMOUNT.toBigDecimal() -> DiscountAmountError.TOO_SMALL
        amount > QuestLimits.maxDiscountAmount(discountUnit).toBigDecimal() ->
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

  /**
   * Whether the fields of [type] and the expiry are valid, touched or not; no reward is always
   * valid.
   */
  val isValid: Boolean
    get() = type == null || (!expiryInPast && fieldsValid)

  private val fieldsValid: Boolean
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
   *
   * It expires at the midnight that ends [expiryDate] in [zone], so it can be used all that day.
   */
  fun toReward(zone: ZoneId = ZoneId.systemDefault()): Reward? {
    val expiresAt = expiryDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val details = specifics.trim().ifEmpty { null }
    return when (type) {
      null -> null
      RewardType.DISCOUNT ->
          Reward.Discount(
              requireNotNull(parsedAmount).toDouble(),
              discountUnit,
              details,
              expiresAt,
          )
      RewardType.FREE_ITEM -> Reward.FreeItem(itemName.trim(), details, expiresAt)
      RewardType.OTHER -> Reward.Other(description.trim(), expiresAt)
    }
  }
}
