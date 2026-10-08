// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.map.marker.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.map.marker.initialsOf

/**
 * A venue's marker on the map: a pin that opens into a card showing one of its quests.
 *
 * @property venueId Id of the venue.
 * @property venueName The venue's name, shown on the card.
 * @property location Where the pin stands.
 * @property icon What the pin shows for the venue.
 * @property avatar What the card shows for the venue.
 * @property featuredQuest The quest the card shows: the venue's pick, or else its newest.
 * @property otherQuestCount How many other valid quests the venue has.
 * @property distanceMeters How far the pin is from the explorer, or null when their position is
 *   unknown.
 * @property areaRadiusMeters Radius of the venue's area, drawn around the pin.
 */
data class VenuePin(
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
 * One pin per venue with quests valid at [now], ordered by venue id.
 *
 * A pin features the venue's [Venue.featuredQuestId] when it is one of the venue's valid quests,
 * else its newest valid quest (ties go to the smallest id). It takes the venue's name and location
 * from [venues] when known there, else from the featured quest, which copied them at creation, and
 * likewise the radius of its area.
 *
 * @param venues The known venues, by id; venues missing from it are drawn from their quests.
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
    avatarOf: (Venue?, venueName: String) -> VenueAvatar = { _, name ->
      VenueAvatar.Initials(initialsOf(name))
    },
): List<VenuePin> =
    quests
        .filter { it.isValidAt(now) }
        .groupBy { it.venueId }
        .toSortedMap()
        .map { (venueId, own) ->
          val venue = venues[venueId]
          val featured =
              own.find { it.id == venue?.featuredQuestId }
                  ?: own.minWith(compareByDescending<Quest> { it.createdAt }.thenBy { it.id })
          val name = venue?.name ?: featured.venueName
          val location = venue?.location ?: featured.location
          VenuePin(
              venueId = venueId,
              venueName = name,
              location = location,
              icon = iconOf(venue),
              avatar = avatarOf(venue, name),
              featuredQuest = featured,
              otherQuestCount = own.size - 1,
              distanceMeters = from?.distanceTo(location),
              areaRadiusMeters = venue?.radiusMeters ?: featured.radiusMeters,
          )
        }
