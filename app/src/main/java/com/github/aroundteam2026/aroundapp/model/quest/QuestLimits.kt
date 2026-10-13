// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

/**
 * Validation limits for [Quest] fields, enforced by the future ViewModel.
 *
 * The length limits are placeholders until the team agrees on them (open decision 6 in the
 * Firestore schema).
 */
object QuestLimits {
  /** Maximum number of characters in [Quest.title]. */
  const val TITLE_MAX_LENGTH = 60

  /** Maximum number of characters in [Quest.description]. */
  const val DESCRIPTION_MAX_LENGTH = 500

  /** Maximum number of characters in [Quest.requirements]. */
  const val REQUIREMENTS_MAX_LENGTH = 300

  /** Maximum number of characters in [Reward.FreeItem.name]. */
  const val REWARD_ITEM_MAX_LENGTH = 60

  /** Maximum number of characters in the specifics of a [Reward]. */
  const val REWARD_SPECIFICS_MAX_LENGTH = 200

  /** Maximum number of characters in [Reward.Other.description]. */
  const val REWARD_DESCRIPTION_MAX_LENGTH = 200

  /** Smallest allowed [Quest.minPartySize]: a solo visit. */
  const val MIN_PARTY_SIZE = 1

  /** Smallest allowed [Quest.radiusMeters]. */
  const val MIN_RADIUS_METERS = 20

  /** Largest allowed [Quest.radiusMeters]. */
  const val MAX_RADIUS_METERS = 200

  /** [Quest.radiusMeters] a new quest starts with until its venue's radius is known. */
  const val DEFAULT_RADIUS_METERS = 50

  /**
   * Smallest allowed [Reward.Discount.amount], in either unit: one cent or 0.01 %. A check written
   * as `amount >= MIN_DISCOUNT_AMOUNT` also rejects negative amounts and `NaN`.
   */
  const val MIN_DISCOUNT_AMOUNT = 0.01

  /** Largest allowed [Reward.Discount.amount] in [DiscountUnit.PERCENT]: the whole price. */
  const val MAX_PERCENT_DISCOUNT = 100.0
}
