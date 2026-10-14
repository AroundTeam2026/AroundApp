// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.ui.quest.TestAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests which nearby quests [NearbyFilters] let through. */
class NearbyFiltersTest {

  private val discount = Reward.Discount(15.0, DiscountUnit.PERCENT, specifics = null)
  private val freeItem = Reward.FreeItem("Coffee", specifics = null)
  private val other = Reward.Other("A tour of the kitchen")

  private fun quest(reward: Reward? = null, distanceMeters: Double? = null) =
      NearbyQuest(
          venueId = "venue",
          venueName = "Café Lumen",
          avatar = TestAvatar("venue"),
          questId = "quest",
          title = "Find the hidden fox",
          reward = reward,
          minPartySize = 1,
          distanceMeters = distanceMeters,
      )

  @Test
  fun noFilterLetsEveryQuestThrough() {
    val filters = NearbyFilters()

    assertFalse(filters.isActive)
    listOf(null, discount, freeItem, other).forEach {
      assertTrue(filters.matches(quest(it, distanceMeters = 4_900.0)))
    }
  }

  @Test
  fun aDistanceKeepsTheQuestsWithinItIncludingItsEdge() {
    val filters = NearbyFilters(distance = DistanceFilter.WITHIN_1_KM)

    assertTrue(filters.isActive)
    assertTrue(filters.matches(quest(distanceMeters = 999.0)))
    assertTrue(filters.matches(quest(distanceMeters = 1_000.0)))
    assertFalse(filters.matches(quest(distanceMeters = 1_000.5)))
  }

  @Test
  fun eachDistanceHasItsOwnLimit() {
    assertEquals(
        listOf(null, 500.0, 1_000.0, 3_000.0),
        DistanceFilter.entries.map { it.maxMeters },
    )
  }

  @Test
  fun withoutTheExplorersPositionTheDistanceLetsEveryQuestThrough() {
    // The quests have no distance then, and the filters can't be set; one chosen earlier waits
    assertTrue(
        NearbyFilters(distance = DistanceFilter.WITHIN_500_M).matches(quest(distanceMeters = null))
    )
  }

  @Test
  fun discountKeepsOnlyDiscounts() {
    val filters = NearbyFilters(reward = RewardFilter.DISCOUNT)

    assertTrue(filters.isActive)
    assertTrue(filters.matches(quest(discount)))
    listOf(null, freeItem, other).forEach { assertFalse(filters.matches(quest(it))) }
  }

  @Test
  fun otherKeepsEveryRewardButADiscount() {
    val filters = NearbyFilters(reward = RewardFilter.OTHER)

    assertTrue(filters.matches(quest(freeItem)))
    assertTrue(filters.matches(quest(other)))
    assertFalse(filters.matches(quest(discount)))
    assertFalse("a quest without a reward has none to match", filters.matches(quest(null)))
  }

  @Test
  fun aQuestMustMatchBothFilters() {
    val filters = NearbyFilters(DistanceFilter.WITHIN_500_M, RewardFilter.DISCOUNT)

    assertTrue(filters.matches(quest(discount, distanceMeters = 400.0)))
    assertFalse(filters.matches(quest(discount, distanceMeters = 600.0)))
    assertFalse(filters.matches(quest(other, distanceMeters = 400.0)))
  }
}
