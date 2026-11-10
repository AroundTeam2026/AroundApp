// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf

/**
 * A venue's marker on the map: a pin that opens into a card showing one of its quests.
 *
 * @property key Tells the pins apart: the venue's id, or for a quest away from its venue, the
 *   quest's.
 * @property venueId Id of the venue.
 * @property venueName The venue's name, shown on the card.
 * @property location Where the pin stands.
 * @property icon What the pin shows for the venue.
 * @property avatar What the card shows for the venue.
 * @property featuredQuest The quest the card shows: the venue's pick, or else its newest.
 * @property otherQuestCount How many other valid quests the venue has.
 * @property distanceMeters How far the pin is from the explorer, or null when their position is
 *   unknown.
 * @property areaRadiusMeters Radius drawn around the pin: the venue's area, or the quest's unlock
 *   radius for a quest away from its venue.
 */
data class VenuePin(
    val key: String,
    val venueId: String,
    val venueName: String,
    val location: Location,
    val icon: VenueAvatar,
    val avatar: VenueAvatar,
    val featuredQuest: Quest,
    val otherQuestCount: Int,
    val distanceMeters: Double?,
    val areaRadiusMeters: Int,
)

/**
 * One pin per venue with quests valid at [now], plus one per such quest away from its venue,
 * ordered by key (see [pinKey]).
 *
 * A pin features the venue's [Venue.featuredQuestId] when it is one of the venue's valid quests,
 * else its newest valid quest (ties go to the smallest id). It takes the venue's name, location and
 * area radius from [venues]; a venue missing from it, or without a location yet, gets no pin.
 *
 * @param venues The known venues, by id.
 * @param from The explorer's position, to tell how far each pin is, or null when unknown.
 * @param iconOf What the pin shows for a venue, given its record when known.
 * @param avatarOf What the card shows for a venue, given its record when known and its name.
 */
fun buildVenuePins(
    quests: List<Quest>,
    venues: Map<String, Venue>,
    now: Long,
    from: Location? = null,
    iconOf: (Venue?) -> VenueAvatar = { VenueAvatar.QuestFlag },
    avatarOf: (Venue?, venueName: String) -> VenueAvatar = INITIALS_AVATAR,
): List<VenuePin> =
    quests
        .filter { it.isValidAt(now) }
        .groupBy(::pinKey)
        .toSortedMap()
        .mapNotNull { (key, own) ->
          val venueId = own.first().venueId
          val venue = venues[venueId] ?: return@mapNotNull null
          val shown = shownVenue(own, venue) ?: return@mapNotNull null
          VenuePin(
              key = key,
              venueId = venueId,
              venueName = shown.name,
              location = shown.location,
              icon = iconOf(venue),
              avatar = avatarOf(venue, shown.name),
              featuredQuest = shown.featured,
              otherQuestCount = own.size - 1,
              distanceMeters = from?.distanceTo(shown.location),
              areaRadiusMeters =
                  if (own.first().location == null) venue.radiusMeters
                  else own.first().radiusMeters,
          )
        }

/**
 * Which pin shows [quest]: its venue's (the venue's id), or its own (its id) when it has a location
 * of its own. The pins and the nearby list both group quests by it.
 */
internal fun pinKey(quest: Quest): String = if (quest.location == null) quest.venueId else quest.id

/**
 * What the map shows for a venue until venues have images: its name's initials. The pins and the
 * nearby pager both default to it, so a venue looks the same in both.
 */
internal val INITIALS_AVATAR: (Venue?, venueName: String) -> VenueAvatar = { _, name ->
  VenueAvatar.Initials(initialsOf(name))
}

/** Newest first, ties going to the smallest id, so the order never depends on the repository's. */
internal val NEWEST_FIRST: Comparator<Quest> =
    compareByDescending<Quest> { it.createdAt }.thenBy { it.id }

/**
 * How the map shows a venue: the quest it features, its name and its location.
 *
 * @property featured The venue's [Venue.featuredQuestId] when it is one of its valid quests, else
 *   its newest valid quest.
 * @property name The venue's name.
 * @property location Where the quests are: their own location, or else the venue's.
 */
internal data class ShownVenue(val featured: Quest, val name: String, val location: Location)

/**
 * How the map shows the venue [venue] whose valid quests are [own], all of one pin (see [pinKey]),
 * or null while they and the venue have no location, as there is nowhere to show them. The pin and
 * the nearby list both use it, so they show the same name and distance for every quest of the
 * venue.
 */
internal fun shownVenue(own: List<Quest>, venue: Venue): ShownVenue? {
  val location = own.first().location ?: venue.location ?: return null
  val featured = own.find { it.id == venue.featuredQuestId } ?: own.minWith(NEWEST_FIRST)
  return ShownVenue(featured = featured, name = venue.name, location = location)
}
