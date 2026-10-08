// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.rewardExpiringAt
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.map.marker.TestAvatar
import com.github.aroundteam2026.aroundapp.ui.map.marker.VenueAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [buildVenuePins]: one pin per venue with valid quests, and which quest its card shows. */
class VenuePinsTest {

  private val now = 10_000L

  private fun pins(quests: List<Quest>, vararg venues: Venue) =
      buildVenuePins(quests, venues.associateBy { it.id }, now)

  @Test
  fun noQuestsMeanNoPins() {
    assertEquals(emptyList<VenuePin>(), pins(emptyList(), testVenue()))
  }

  @Test
  fun aVenueWithOneQuestGetsAPinShowingIt() {
    val quest = testQuest(id = "q1", venueId = "cafe", venueName = "Café Lumen")

    val pin = pins(listOf(quest)).single()

    assertEquals("cafe", pin.venueId)
    assertEquals("Café Lumen", pin.venueName)
    assertEquals(quest.location, pin.location)
    assertEquals(quest, pin.featuredQuest)
    assertEquals(0, pin.otherQuestCount)
  }

  @Test
  fun aVenuesQuestsShareOnePinThatCountsTheOthers() {
    val quests =
        listOf(
            testQuest(id = "q1", venueId = "cafe", createdAt = 1),
            testQuest(id = "q2", venueId = "cafe", createdAt = 2),
            testQuest(id = "q3", venueId = "cafe", createdAt = 3),
        )

    val pin = pins(quests).single()

    assertEquals(2, pin.otherQuestCount)
  }

  @Test
  fun eachVenueGetsItsOwnPin() {
    val quests =
        listOf(
            testQuest(id = "q1", venueId = "cafe"),
            testQuest(id = "q2", venueId = "bar"),
            testQuest(id = "q3", venueId = "cafe"),
        )

    val pins = pins(quests).associateBy { it.venueId }

    assertEquals(setOf("cafe", "bar"), pins.keys)
    assertEquals(1, pins.getValue("cafe").otherQuestCount)
    assertEquals(0, pins.getValue("bar").otherQuestCount)
  }

  @Test
  fun pinsComeInTheSameOrderWhateverTheQuestsOrder() {
    // The map redraws markers whose order changed, so a stable order avoids flicker
    val quests =
        listOf(
            testQuest(id = "q1", venueId = "cafe"),
            testQuest(id = "q2", venueId = "bar"),
            testQuest(id = "q3", venueId = "brasserie"),
        )

    assertEquals(pins(quests), pins(quests.reversed()))
    assertEquals(listOf("bar", "brasserie", "cafe"), pins(quests).map { it.venueId })
  }

  @Test
  fun questsThatAreNoLongerValidAreLeftOutOfTheCount() {
    val quests =
        listOf(
            testQuest(id = "valid", venueId = "cafe"),
            testQuest(id = "expired", venueId = "cafe", reward = rewardExpiringAt(now)),
            testQuest(id = "draft", venueId = "cafe", status = QuestStatus.DRAFT),
            testQuest(id = "archived", venueId = "cafe", status = QuestStatus.ARCHIVED),
        )

    val pin = pins(quests).single()

    assertEquals("valid", pin.featuredQuest.id)
    assertEquals(0, pin.otherQuestCount)
  }

  @Test
  fun aVenueWithoutValidQuestsGetsNoPin() {
    val quests =
        listOf(
            testQuest(id = "expired", venueId = "cafe", reward = rewardExpiringAt(now - 1)),
            testQuest(id = "draft", venueId = "cafe", status = QuestStatus.DRAFT),
            testQuest(id = "valid", venueId = "bar"),
        )

    assertEquals(listOf("bar"), pins(quests).map { it.venueId })
  }

  @Test
  fun withoutAPickTheNewestQuestIsFeatured() {
    val quests =
        listOf(
            testQuest(id = "old", venueId = "cafe", createdAt = 100),
            testQuest(id = "new", venueId = "cafe", createdAt = 300),
            testQuest(id = "mid", venueId = "cafe", createdAt = 200),
        )

    assertEquals("new", pins(quests, testVenue(id = "cafe")).single().featuredQuest.id)
  }

  @Test
  fun questsCreatedTogetherFeatureTheSameOneEveryTime() {
    // Ties go to the smallest id, so the card doesn't switch quests between updates
    val quests =
        listOf(
            testQuest(id = "b", venueId = "cafe", createdAt = 100),
            testQuest(id = "a", venueId = "cafe", createdAt = 100),
        )

    assertEquals("a", pins(quests).single().featuredQuest.id)
    assertEquals("a", pins(quests.reversed()).single().featuredQuest.id)
  }

  @Test
  fun theVenuesPickIsFeaturedOverItsNewestQuest() {
    val quests =
        listOf(
            testQuest(id = "picked", venueId = "cafe", createdAt = 100),
            testQuest(id = "newest", venueId = "cafe", createdAt = 300),
        )
    val venue = testVenue(id = "cafe", featuredQuestId = "picked")

    val pin = pins(quests, venue).single()

    assertEquals("picked", pin.featuredQuest.id)
    assertEquals(1, pin.otherQuestCount)
  }

  @Test
  fun aPickThatIsNoLongerValidFallsBackToTheNewest() {
    val quests =
        listOf(
            testQuest(
                id = "picked",
                venueId = "cafe",
                createdAt = 300,
                reward = rewardExpiringAt(now),
            ),
            testQuest(id = "older", venueId = "cafe", createdAt = 100),
            testQuest(id = "newer", venueId = "cafe", createdAt = 200),
        )
    val venue = testVenue(id = "cafe", featuredQuestId = "picked")

    val pin = pins(quests, venue).single()

    assertEquals("newer", pin.featuredQuest.id)
    assertEquals(1, pin.otherQuestCount)
  }

  @Test
  fun aPickOfAnotherVenuesQuestIsIgnored() {
    // A venue can only feature its own quests
    val quests =
        listOf(
            testQuest(id = "mine", venueId = "cafe", createdAt = 100),
            testQuest(id = "theirs", venueId = "bar", createdAt = 300),
        )
    val cafe = testVenue(id = "cafe", featuredQuestId = "theirs")

    val pins = pins(quests, cafe).associateBy { it.venueId }

    assertEquals("mine", pins.getValue("cafe").featuredQuest.id)
    assertEquals("theirs", pins.getValue("bar").featuredQuest.id)
  }

  @Test
  fun aPickOfAnUnknownQuestFallsBackToTheNewest() {
    val quests = listOf(testQuest(id = "only", venueId = "cafe"))
    val venue = testVenue(id = "cafe", featuredQuestId = "deleted")

    assertEquals("only", pins(quests, venue).single().featuredQuest.id)
  }

  @Test
  fun aKnownVenueGivesThePinItsCurrentNameAndPlace() {
    // Quests copy both at creation, so the venue's own record is newer when the map has it
    val quests =
        listOf(testQuest(venueId = "cafe", venueName = "Old name", location = Location(1.0, 1.0)))
    val venue = testVenue(id = "cafe", name = "New name", location = Location(2.0, 2.0))

    val pin = pins(quests, venue).single()

    assertEquals("New name", pin.venueName)
    assertEquals(Location(2.0, 2.0), pin.location)
  }

  @Test
  fun aVenueNotPlacedYetIsDrawnWhereItsFeaturedQuestIs() {
    val quests =
        listOf(
            testQuest(id = "old", venueId = "cafe", location = Location(1.0, 1.0), createdAt = 1),
            testQuest(id = "new", venueId = "cafe", location = Location(3.0, 3.0), createdAt = 3),
        )
    val venue = testVenue(id = "cafe", location = null)

    assertEquals(Location(3.0, 3.0), pins(quests, venue).single().location)
  }

  @Test
  fun anUnknownVenueIsDrawnFromItsFeaturedQuest() {
    // As with Firestore quests until venues are in Firestore too
    val quests =
        listOf(
            testQuest(
                id = "old",
                venueId = "cafe",
                venueName = "Old",
                location = Location(1.0, 1.0),
                createdAt = 1,
            ),
            testQuest(
                id = "new",
                venueId = "cafe",
                venueName = "New",
                location = Location(3.0, 3.0),
                createdAt = 3,
            ),
        )

    val pin = pins(quests).single()

    assertEquals("New", pin.venueName)
    assertEquals(Location(3.0, 3.0), pin.location)
  }

  @Test
  fun pinsShowTheQuestFlagUntilVenuesHaveCategories() {
    val quests = listOf(testQuest(venueId = "cafe"), testQuest(id = "q2", venueId = "bar"))

    assertTrue(pins(quests).all { it.icon == VenueAvatar.QuestFlag })
  }

  @Test
  fun cardsShowTheVenuesInitialsUntilVenuesHaveImages() {
    val quests = listOf(testQuest(venueId = "cafe", venueName = "Old name"))
    val venue = testVenue(id = "cafe", name = "Bar des Arches")

    // From the name the card shows: the venue's own, not the quest's copy
    assertEquals(VenueAvatar.Initials("BA"), pins(quests, venue).single().avatar)
  }

  @Test
  fun eachPinGetsTheIconAndAvatarChosenForItsVenue() {
    // Where categories and venue images plug in later
    val cafe = testVenue(id = "cafe", name = "Café Lumen")
    val askedIcons = mutableListOf<Venue?>()
    val askedAvatars = mutableListOf<Pair<Venue?, String>>()
    val quests =
        listOf(
            testQuest(venueId = "cafe"),
            testQuest(id = "q2", venueId = "bar", venueName = "Bar Nocturne"),
        )

    val pins =
        buildVenuePins(
                quests,
                mapOf("cafe" to cafe),
                now,
                iconOf = { venue ->
                  askedIcons += venue
                  if (venue == cafe) TestAvatar("cafe-icon") else VenueAvatar.QuestFlag
                },
                avatarOf = { venue, name ->
                  askedAvatars += venue to name
                  TestAvatar(name)
                },
            )
            .associateBy { it.venueId }

    assertEquals(setOf(cafe, null), askedIcons.toSet())
    assertEquals(setOf(cafe to "Café Lumen", null to "Bar Nocturne"), askedAvatars.toSet())
    assertEquals(TestAvatar("cafe-icon"), pins.getValue("cafe").icon)
    assertEquals(VenueAvatar.QuestFlag, pins.getValue("bar").icon)
    assertEquals(TestAvatar("Bar Nocturne"), pins.getValue("bar").avatar)
  }

  @Test
  fun withoutTheExplorersPositionPinsHaveNoDistance() {
    assertNull(pins(listOf(testQuest())).single().distanceMeters)
  }

  @Test
  fun withTheExplorersPositionEachPinKnowsHowFarItIs() {
    val here = Location(46.5197, 6.6323)
    val quests =
        listOf(
            testQuest(venueId = "cafe", location = Location(46.5220, 6.6330)),
            testQuest(id = "q2", venueId = "geneva", location = Location(46.2044, 6.1432)),
        )

    val pins = buildVenuePins(quests, emptyMap(), now, from = here).associateBy { it.venueId }

    assertEquals(
        here.distanceTo(Location(46.5220, 6.6330)),
        pins.getValue("cafe").distanceMeters!!,
        1e-6,
    )
    assertEquals(51_359.2, pins.getValue("geneva").distanceMeters!!, 1.0)
  }

  @Test
  fun theDistanceIsToWhereThePinStands() {
    // The venue's own place when known, as for the pin itself
    val here = Location(46.5197, 6.6323)
    val quests = listOf(testQuest(venueId = "cafe", location = Location(1.0, 1.0)))
    val venue = testVenue(id = "cafe", location = Location(46.5220, 6.6330))

    val pin = buildVenuePins(quests, mapOf("cafe" to venue), now, from = here).single()

    assertEquals(here.distanceTo(Location(46.5220, 6.6330)), pin.distanceMeters!!, 1e-6)
  }

  @Test
  fun theAreaIsTheVenuesRadiusWhenKnown() {
    val quests = listOf(testQuest(venueId = "cafe").copy(radiusMeters = 80))
    val venue = testVenue(id = "cafe").copy(radiusMeters = 120)

    assertEquals(120, pins(quests, venue).single().areaRadiusMeters)
  }

  @Test
  fun anUnknownVenuesAreaIsItsFeaturedQuestsRadius() {
    val quests =
        listOf(
            testQuest(id = "old", venueId = "cafe", createdAt = 1).copy(radiusMeters = 40),
            testQuest(id = "new", venueId = "cafe", createdAt = 2).copy(radiusMeters = 150),
        )

    assertEquals(150, pins(quests).single().areaRadiusMeters)
  }
}
