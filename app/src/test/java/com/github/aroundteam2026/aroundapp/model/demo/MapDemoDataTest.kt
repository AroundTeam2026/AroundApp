// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.demo

import com.github.aroundteam2026.aroundapp.model.common.boundsWithin
import com.github.aroundteam2026.aroundapp.model.common.contains
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit.CHF
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit.PERCENT
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.ui.map.DEFAULT_MAP_CENTER
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that the demo data stays consistent, and keeps showing every case the map handles, so a map
 * filled with it shows them all.
 */
class MapDemoDataTest {

  private val now = 1_800_000_000_000L
  private val venues = MapDemoData.venues()
  private val quests = MapDemoData.quests(now)
  private val valid = quests.filter { it.isValidAt(now) }

  /** Where the map opens, with the area it frames: Lausanne. */
  private val lausanne = DEFAULT_MAP_CENTER.boundsWithin(NEARBY_RADIUS_METERS)

  @Test
  fun idsAreUnique() {
    assertEquals(venues.size, venues.map { it.id }.toSet().size)
    assertEquals(quests.size, quests.map { it.id }.toSet().size)
  }

  @Test
  fun everyQuestBelongsToAPlacedVenue() {
    // The map takes a quest's name and location from its venue, so it can't draw one without it
    val byId = venues.associateBy { it.id }
    quests.forEach { quest ->
      val venue = byId[quest.venueId]
      assertTrue("${quest.id} names an unknown venue", venue != null)
      assertTrue("${quest.id}'s venue has no location", venue!!.location != null)
    }
  }

  @Test
  fun questsRespectTheQuestLimits() {
    quests.forEach {
      assertTrue(it.id, it.title.length <= QuestLimits.TITLE_MAX_LENGTH)
      assertTrue(
          it.id,
          it.radiusMeters in QuestLimits.MIN_RADIUS_METERS..QuestLimits.MAX_RADIUS_METERS,
      )
      assertTrue(it.id, it.minPartySize >= QuestLimits.MIN_PARTY_SIZE)
    }
  }

  @Test
  fun featuredQuestsAreValidQuestsOfTheirOwnVenue() {
    venues
        .filter { it.featuredQuestId != null }
        .forEach { venue ->
          val featured = valid.find { it.id == venue.featuredQuestId }
          assertTrue("${venue.id} features a quest it can't", featured?.venueId == venue.id)
        }
  }

  @Test
  fun showsAVenuePickingAnOlderQuestOverItsNewestOne() {
    // Otherwise the venue's pick and the newest-quest default would look the same on the map
    val picky = venues.filter { venue ->
      val own = valid.filter { it.venueId == venue.id }
      val pick = own.find { it.id == venue.featuredQuestId }
      pick != null && pick != own.maxBy { it.createdAt }
    }
    assertTrue("No venue picks an older quest", picky.isNotEmpty())
  }

  @Test
  fun showsAVenueWithSeveralQuestsSoTheCardSaysHowManyMore() {
    val counts = valid.groupingBy { it.venueId }.eachCount()
    assertTrue("No venue has 3 valid quests", counts.values.any { it >= 3 })
  }

  @Test
  fun showsQuestsTheMapMustHide() {
    assertTrue(
        "No expired reward",
        quests.any { it.status == QuestStatus.ACTIVE && !it.isValidAt(now) },
    )
    assertTrue("No draft", quests.any { it.status == QuestStatus.DRAFT })
  }

  @Test
  fun showsEveryKindOfQuest() {
    assertTrue("No group quest", valid.any { it.minPartySize > 1 })
    assertTrue("No solo quest", valid.any { it.minPartySize == 1 })
    assertTrue("No quest without a reward", valid.any { it.reward == null })
    assertTrue(
        "No discount in percent",
        valid.any { (it.reward as? Reward.Discount)?.unit == PERCENT },
    )
    assertTrue("No discount in CHF", valid.any { (it.reward as? Reward.Discount)?.unit == CHF })
    assertTrue("No free item", valid.any { it.reward is Reward.FreeItem })
    assertTrue("No other reward", valid.any { it.reward is Reward.Other })
  }

  @Test
  fun mostVenuesAreAroundLausanneAndOneIsOutOfView() {
    // The one out of view only shows up once the explorer pans to it
    val outside = venues.filterNot { it.location!! in lausanne }
    assertEquals(1, outside.size)
    assertTrue(venues.size - outside.size >= 3)
  }

  @Test
  fun theExpiredRewardIsExpiredWheneverTheDemoIsBuilt() {
    // Built from the current time, so the demo never needs updating as dates pass
    val later = now + 365L * 24 * 60 * 60 * 1000
    val expiredLater =
        MapDemoData.quests(later).count { it.status == QuestStatus.ACTIVE && !it.isValidAt(later) }
    assertEquals(
        quests.count { it.status == QuestStatus.ACTIVE && !it.isValidAt(now) },
        expiredLater,
    )
  }
}
