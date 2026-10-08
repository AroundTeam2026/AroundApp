// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.map.marker.VenueAvatar

/**
 * A venue's marker on the map: a pin standing on the venue, over its area.
 *
 * @property venueId Id of the venue.
 * @property venueName The venue's name.
 * @property location Where the pin stands.
 * @property icon What the pin shows for the venue.
 * @property featuredQuest The quest the venue shows first: its pick, or else its newest.
 * @property otherQuestCount How many other valid quests the venue has.
 * @property areaRadiusMeters Radius of the venue's area, drawn around the pin.
 */
data class VenuePin(
    val venueId: String,
    val venueName: String,
    val location: Location,
    val icon: VenueAvatar,
    val featuredQuest: Quest,
    val otherQuestCount: Int,
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
 * @param iconOf What the pin shows for a venue, given its record when known.
 */
fun buildVenuePins(
    quests: List<Quest>,
    venues: Map<String, Venue>,
    now: Long,
    iconOf: (Venue?) -> VenueAvatar = { VenueAvatar.QuestFlag },
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
          VenuePin(
              venueId = venueId,
              venueName = venue?.name ?: featured.venueName,
              location = venue?.location ?: featured.location,
              icon = iconOf(venue),
              featuredQuest = featured,
              otherQuestCount = own.size - 1,
              areaRadiusMeters = venue?.radiusMeters ?: featured.radiusMeters,
          )
        }
