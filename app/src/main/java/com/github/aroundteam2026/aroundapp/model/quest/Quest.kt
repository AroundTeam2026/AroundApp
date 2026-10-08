// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

import com.github.aroundteam2026.aroundapp.model.common.Location

/**
 * A quest offered by a venue, which explorers complete by visiting it and submitting proof.
 *
 * The repository assigns [Quest.id], [Quest.createdAt] and [Quest.updatedAt]; any values the caller
 * sets for them are overwritten.
 *
 * @property venueId Id of the owning venue, equal to the venue owner's uid.
 * @property venueName Copied from the venue at creation, so the map needs a single query.
 * @property location Copied from the venue at creation; where the quest takes place.
 * @property radiusMeters Distance from [location] within which a visit counts, between
 *   [QuestLimits.MIN_RADIUS_METERS] and [QuestLimits.MAX_RADIUS_METERS].
 * @property title Short name shown on the map, at most [QuestLimits.TITLE_MAX_LENGTH] characters.
 * @property description What the quest is about, at most [QuestLimits.DESCRIPTION_MAX_LENGTH]
 *   characters.
 * @property requirements What explorers must do to complete it, at most
 *   [QuestLimits.REQUIREMENTS_MAX_LENGTH] characters.
 * @property proofType The kind of proof explorers submit.
 * @property minPartySize Smallest party allowed to take the quest; defaults to a solo visit.
 * @property reward What explorers get on completion, or null if there is none.
 * @property status Lifecycle state; only [QuestStatus.ACTIVE] quests are visible to explorers.
 * @property createdAt Creation time, in epoch milliseconds. Set by [QuestRepository.createQuest].
 * @property updatedAt Last modification time, in epoch milliseconds. Set by
 *   [QuestRepository.createQuest].
 */
data class Quest(
    val id: String,
    val venueId: String,
    val venueName: String,
    val location: Location,
    val radiusMeters: Int,
    val title: String,
    val description: String,
    val requirements: String,
    val proofType: ProofType,
    val minPartySize: Int = QuestLimits.MIN_PARTY_SIZE,
    val reward: Reward?,
    val status: QuestStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * What an explorer gets for completing a quest: a [Discount], a [FreeItem] or [Other].
 *
 * @property expiresAt When the reward stops being valid, in epoch milliseconds, or null if never.
 */
sealed interface Reward {
  val expiresAt: Long?

  /**
   * A discount, e.g. 10 % or 5 CHF off.
   *
   * @property amount Size of the discount, in [unit].
   * @property specifics What the discount applies to, or null if not specified.
   */
  data class Discount(
      val amount: Double,
      val unit: DiscountUnit,
      val specifics: String?,
      override val expiresAt: Long? = null,
  ) : Reward

  /**
   * An item given for free, e.g. a coffee.
   *
   * @property name The item.
   * @property specifics Details about the item, or null if not specified.
   */
  data class FreeItem(
      val name: String,
      val specifics: String?,
      override val expiresAt: Long? = null,
  ) : Reward

  /**
   * A reward that is neither a discount nor a free item.
   *
   * @property description The reward itself.
   */
  data class Other(val description: String, override val expiresAt: Long? = null) : Reward
}

/** The unit of a [Reward.Discount] amount. */
enum class DiscountUnit {
  /** A percentage of the price. */
  PERCENT,
  /** An amount in Swiss francs. */
  CHF,
}

/** The kind of proof an explorer submits to complete a quest. */
enum class ProofType {
  /** A photo taken at the venue. */
  PHOTO,
  /** A written answer. */
  TEXT,
}

/** Lifecycle state of a quest. */
enum class QuestStatus {
  /** Being written by the venue; not visible to explorers. */
  DRAFT,
  /** Published and visible to explorers. */
  ACTIVE,
  /** No longer offered; kept for history and not visible to explorers. */
  ARCHIVED,
}
