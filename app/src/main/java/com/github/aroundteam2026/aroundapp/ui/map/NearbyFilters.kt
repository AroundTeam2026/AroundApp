// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.quest.Reward

/**
 * How far from the explorer a nearby quest may be.
 *
 * @property maxMeters The farthest a quest may be, inclusive, or null for any distance.
 */
enum class DistanceFilter(val maxMeters: Double?) {
  ANY(null),
  WITHIN_500_M(500.0),
  WITHIN_1_KM(1_000.0),
  WITHIN_3_KM(3_000.0),
}

/** Which reward a nearby quest must have. */
enum class RewardFilter {
  /** Any reward, or none. */
  ANY,
  /** A discount. */
  DISCOUNT,
  /** A reward that isn't a discount: a free item or another reward. */
  OTHER,
}

/**
 * The filters the explorer chose for the nearby quests. Without them, every quest is listed.
 *
 * @property distance How far a quest may be. It only applies to quests with a distance, so it waits
 *   while the explorer's position is unknown.
 * @property reward Which reward a quest must have.
 */
data class NearbyFilters(
    val distance: DistanceFilter = DistanceFilter.ANY,
    val reward: RewardFilter = RewardFilter.ANY,
) {
  /** Whether a filter is set, so some quests may be left out. */
  val isActive: Boolean
    get() = distance != DistanceFilter.ANY || reward != RewardFilter.ANY

  /** Whether [quest] passes both filters. */
  fun matches(quest: NearbyQuest): Boolean {
    val max = distance.maxMeters
    val near = max == null || quest.distanceMeters == null || quest.distanceMeters <= max
    val rewarded =
        when (reward) {
          RewardFilter.ANY -> true
          RewardFilter.DISCOUNT -> quest.reward is Reward.Discount
          RewardFilter.OTHER -> quest.reward != null && quest.reward !is Reward.Discount
        }
    return near && rewarded
  }
}
