// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.EARTH_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.rewardExpiringAt
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [nearbyQuests]: which quests the list holds, and in which order. */
class NearbyQuestsTest {

  private val now = 1_800_000_000_000L
  private val here = Location(46.5197, 6.6323)

  /** A quest at [meters] due north of [here], so its distance is known exactly. */
  private fun questAt(
      meters: Double,
      id: String = "q-$meters",
      venueId: String = "v-$meters",
      venueName: String = "Venue $meters",
      createdAt: Long = 1_000L,
      reward: Reward? = Reward("Free coffee", terms = null, expiresAt = null),
      status: QuestStatus = QuestStatus.ACTIVE,
  ) =
      testQuest(
          id = id,
          venueId = venueId,
          venueName = venueName,
          location = north(meters),
          reward = reward,
          status = status,
          createdAt = createdAt,
      )

  private fun north(meters: Double) =
      Location(here.lat + Math.toDegrees(meters / EARTH_RADIUS_METERS), here.lng)

  /** The list for [quests], with names sorted as in English unless [locale] says otherwise. */
  private fun nearby(
      quests: List<Quest>,
      venues: List<Venue> = emptyList(),
      from: Location? = here,
      locale: Locale = Locale.ENGLISH,
  ) = nearbyQuests(quests, venues.associateBy { it.id }, now, from, locale)

  private fun List<NearbyQuest>.ids() = map { it.questId }

  @Test
  fun aRowCarriesItsQuestAndVenue() {
    val quest =
        testQuest(
            id = "fox",
            venueId = "lumen",
            venueName = "Café Lumen",
            location = north(300.0),
            title = "Find the hidden fox",
            reward = Reward("Free coffee", terms = "One per visit", expiresAt = null),
            minPartySize = 3,
        )

    val row = nearby(listOf(quest)).single()

    assertEquals("lumen", row.venueId)
    assertEquals("Café Lumen", row.venueName)
    assertEquals("fox", row.questId)
    assertEquals("Find the hidden fox", row.title)
    assertEquals(Reward("Free coffee", terms = "One per visit", expiresAt = null), row.reward)
    assertEquals(3, row.minPartySize)
    assertEquals(300.0, row.distanceMeters!!, 0.01)
  }

  @Test
  fun nearestComesFirst() {
    val far = questAt(2_000.0)
    val near = questAt(150.0)
    val middle = questAt(900.0)

    assertEquals(listOf(near.id, middle.id, far.id), nearby(listOf(far, near, middle)).ids())
  }

  @Test
  fun questsAtTheSameDistanceAreNewestFirst() {
    val older = questAt(400.0, id = "older", venueId = "lumen", createdAt = 1_000L)
    val newer = questAt(400.0, id = "newer", venueId = "lumen", createdAt = 2_000L)

    assertEquals(listOf("newer", "older"), nearby(listOf(older, newer)).ids())
  }

  @Test
  fun questsAtTheSameDistanceAndAgeGoBySmallestId() {
    // One row per quest, not per venue as on the map, in an order that never depends on how the
    // repository listed them
    val b = questAt(400.0, id = "b", venueId = "lumen")
    val a = questAt(400.0, id = "a", venueId = "lumen")

    assertEquals(listOf("a", "b"), nearby(listOf(b, a)).ids())
    assertEquals(listOf("a", "b"), nearby(listOf(a, b)).ids())
  }

  @Test
  fun venuesAtTheSameDistanceKeepTheirQuestsTogether() {
    // Newest first across venues would interleave them: a1, b1, a2, b2
    val a1 = questAt(400.0, id = "a1", venueId = "a", createdAt = 4_000L)
    val b1 = questAt(400.0, id = "b1", venueId = "b", createdAt = 3_000L)
    val a2 = questAt(400.0, id = "a2", venueId = "a", createdAt = 2_000L)
    val b2 = questAt(400.0, id = "b2", venueId = "b", createdAt = 1_000L)

    assertEquals(listOf("a1", "a2", "b1", "b2"), nearby(listOf(b2, a2, b1, a1)).ids())
  }

  @Test
  fun questsJustInsideTheRadiusAreListed() {
    val inside = questAt(NEARBY_RADIUS_METERS - 10)

    assertEquals(listOf(inside.id), nearby(listOf(inside)).ids())
  }

  @Test
  fun questsJustOutsideTheRadiusAreLeftOut() {
    val outside = questAt(NEARBY_RADIUS_METERS + 10)
    val inside = questAt(100.0)

    assertEquals(listOf(inside.id), nearby(listOf(outside, inside)).ids())
  }

  @Test
  fun questsThatAreNotActiveAreLeftOut() {
    val draft = questAt(100.0, status = QuestStatus.DRAFT)
    val archived = questAt(200.0, status = QuestStatus.ARCHIVED)
    val active = questAt(300.0)

    assertEquals(listOf(active.id), nearby(listOf(draft, archived, active)).ids())
  }

  @Test
  fun aRewardExpiringNowIsLeftOutButOneExpiringLaterIsListed() {
    // The same rule as the pins: a reward stops being valid at its expiry
    val expired = questAt(100.0, reward = rewardExpiringAt(now - 1))
    val expiringNow = questAt(200.0, reward = rewardExpiringAt(now))
    val expiringLater = questAt(300.0, reward = rewardExpiringAt(now + 1))

    assertEquals(
        listOf(expiringLater.id),
        nearby(listOf(expired, expiringNow, expiringLater)).ids(),
    )
  }

  @Test
  fun theVenuesRecordWinsOverWhatTheQuestCopied() {
    // Quests copy their venue's name and location at creation; the venue may have changed since
    val quest = questAt(100.0, venueId = "lumen", venueName = "Old name")
    val venue = testVenue(id = "lumen", name = "Café Lumen", location = north(1_200.0))

    val row = nearby(listOf(quest), listOf(venue)).single()

    assertEquals("Café Lumen", row.venueName)
    assertEquals(1_200.0, row.distanceMeters!!, 0.01)
  }

  @Test
  fun aVenueThatMovedOutOfTheRadiusTakesItsQuestsWithIt() {
    val quest = questAt(100.0, venueId = "lumen")
    val moved = testVenue(id = "lumen", location = north(NEARBY_RADIUS_METERS + 500))

    assertEquals(emptyList<String>(), nearby(listOf(quest), listOf(moved)).ids())
  }

  @Test
  fun aVenueNotPlacedYetIsMeasuredWhereItsQuestSays() {
    val quest = questAt(700.0, venueId = "lumen")
    val unplaced = testVenue(id = "lumen", name = "Café Lumen", location = null)

    val row = nearby(listOf(quest), listOf(unplaced)).single()

    assertEquals("Café Lumen", row.venueName)
    assertEquals(700.0, row.distanceMeters!!, 0.01)
  }

  @Test
  fun withoutItsRecordAVenueIsListedAsItsPinShowsIt() {
    // The venue moved and was renamed between its two quests, and its record isn't known
    val newer =
        questAt(
            100.0,
            id = "newer",
            venueId = "lumen",
            venueName = "Café Lumen",
            createdAt = 2_000L,
        )
    val older =
        questAt(
            NEARBY_RADIUS_METERS + 500,
            id = "older",
            venueId = "lumen",
            venueName = "Lumen",
            createdAt = 1_000L,
        )

    val rows = nearby(listOf(newer, older))
    val pin = buildVenuePins(listOf(newer, older), emptyMap(), now, from = here).single()

    assertEquals(listOf("newer", "older"), rows.ids())
    rows.forEach { row ->
      assertEquals(pin.venueName, row.venueName)
      assertEquals(pin.distanceMeters!!, row.distanceMeters!!, 0.01)
    }
  }

  @Test
  fun distancesAreMeasuredFromTheExplorer() {
    val quest = questAt(0.0, venueId = "lumen")
    val elsewhere = north(250.0)

    val row = nearbyQuests(listOf(quest), emptyMap(), now, from = elsewhere).single()

    assertEquals(here.distanceTo(elsewhere), row.distanceMeters!!, 0.01)
  }

  @Test
  fun withoutAPositionRowsHaveNoDistance() {
    val rows = nearby(listOf(questAt(100.0), questAt(900.0)), from = null)

    assertTrue(rows.isNotEmpty())
    assertTrue(rows.all { it.distanceMeters == null })
  }

  @Test
  fun withoutAPositionEveryValidQuestIsListed() {
    // Nothing to measure a radius from: the list holds whatever quests the repository gives
    val geneva = testQuest(id = "geneva", venueId = "atelier", location = Location(46.2044, 6.1432))
    val draft = questAt(100.0, status = QuestStatus.DRAFT)

    assertEquals(listOf("geneva"), nearby(listOf(geneva, draft), from = null).ids())
  }

  @Test
  fun withoutAPositionNamesSortAsPeopleReadThem() {
    // By character code, lower case and accented letters would come after every capital
    val eglise = questAt(100.0, id = "eglise", venueName = "Église Saint-François")
    val fable = questAt(200.0, id = "fable", venueName = "Fable")
    val bar = questAt(300.0, id = "bar", venueName = "bar nocturne")
    val cave = questAt(400.0, id = "cave", venueName = "Cave")

    assertEquals(
        listOf("bar", "cave", "eglise", "fable"),
        nearby(listOf(eglise, fable, bar, cave), from = null).ids(),
    )
  }

  @Test
  fun withoutAPositionNamesSortByTheGivenLanguage() {
    // Swedish sorts Ö after Z; English sorts it with O
    val ol = questAt(100.0, id = "ol", venueName = "Öl & Bar")
    val zebra = questAt(200.0, id = "zebra", venueName = "Zebra Bar")

    assertEquals(listOf("ol", "zebra"), nearby(listOf(zebra, ol), from = null).ids())
    assertEquals(
        listOf("zebra", "ol"),
        nearby(listOf(ol, zebra), from = null, locale = Locale.forLanguageTag("sv")).ids(),
    )
  }

  @Test
  fun withoutAPositionSameNamedVenuesKeepTheirQuestsTogether() {
    // Two different venues called the same, each with two quests
    val a1 = questAt(100.0, id = "a1", venueId = "a", venueName = "Starbucks", createdAt = 4_000L)
    val b1 = questAt(200.0, id = "b1", venueId = "b", venueName = "Starbucks", createdAt = 3_000L)
    val a2 = questAt(100.0, id = "a2", venueId = "a", venueName = "Starbucks", createdAt = 2_000L)
    val b2 = questAt(200.0, id = "b2", venueId = "b", venueName = "Starbucks", createdAt = 1_000L)

    assertEquals(
        listOf("a1", "a2", "b1", "b2"),
        nearby(listOf(b2, a2, b1, a1), from = null).ids(),
    )
  }

  @Test
  fun withoutAPositionAVenuesQuestsAreNewestFirst() {
    val older = questAt(100.0, id = "older", venueName = "Café Lumen", createdAt = 1_000L)
    val newer = questAt(100.0, id = "newer", venueName = "Café Lumen", createdAt = 2_000L)

    assertEquals(listOf("newer", "older"), nearby(listOf(older, newer), from = null).ids())
  }

  @Test
  fun withoutAPositionTheVenuesRecordedNameIsSortedBy() {
    val renamed = questAt(100.0, id = "renamed", venueId = "lumen", venueName = "Zz old name")
    val other = questAt(200.0, id = "other", venueName = "Bar Nocturne")
    val venue = testVenue(id = "lumen", name = "Atelier Lumen")

    assertEquals(
        listOf("renamed", "other"),
        nearby(listOf(renamed, other), listOf(venue), from = null).ids(),
    )
  }

  @Test
  fun noQuestsNoRows() {
    assertEquals(emptyList<NearbyQuest>(), nearby(emptyList()))
    assertEquals(emptyList<NearbyQuest>(), nearby(emptyList(), from = null))
  }
}
