// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import java.text.Collator
import java.util.Locale

/**
 * A quest near the explorer, as the nearby quest pager shows it: one valid quest and its venue.
 *
 * @property venueId Id of the quest's venue.
 * @property venueName The venue's name.
 * @property avatar What the pager shows for the venue.
 * @property questId Id of the quest.
 * @property title The quest's title.
 * @property reward The quest's reward, or null when it has none.
 * @property minPartySize How many people the quest needs at least.
 * @property exactPartySize Whether the party must have exactly [minPartySize] people.
 * @property distanceMeters How far the venue is from the explorer, or null when their position is
 *   unknown.
 */
data class NearbyQuest(
    val venueId: String,
    val venueName: String,
    val avatar: VenueAvatar,
    val questId: String,
    val title: String,
    val reward: Reward?,
    val minPartySize: Int,
    val exactPartySize: Boolean,
    val distanceMeters: Double?,
)

/**
 * One row per quest valid at [now], by the same rule as the map's pins.
 *
 * Each row shows its venue as its pin does (see [shownVenue]); a venue missing from [venues] or
 * without a location has no rows. With the explorer's position [from], it lists the quests within
 * [NEARBY_RADIUS_METERS], nearest first; without it, every valid quest by venue name as [locale]
 * sorts them. A venue's quests stay together, newest first, then by smallest id.
 *
 * @param venues The known venues, by id.
 * @param from The explorer's position, or null when unknown.
 * @param locale The language whose rules sort venue names when [from] is unknown.
 * @param avatarOf What the pager shows for a venue, given its record when known and its name, as
 *   for its pin.
 */
fun nearbyQuests(
    quests: List<Quest>,
    venues: Map<String, Venue>,
    now: Long,
    from: Location?,
    locale: Locale = Locale.getDefault(),
    avatarOf: (Venue?, venueName: String) -> VenueAvatar = INITIALS_AVATAR,
): List<NearbyQuest> {
  val rows =
      quests
          .filter { it.isValidAt(now) }
          .groupBy { it.venueId }
          .flatMap { (venueId, own) ->
            val venue = venues[venueId] ?: return@flatMap emptyList()
            val shown = shownVenue(own, venue) ?: return@flatMap emptyList()
            val distance = from?.distanceTo(shown.location)
            if (distance != null && distance > NEARBY_RADIUS_METERS) return@flatMap emptyList()
            val avatar = avatarOf(venue, shown.name)
            own.sortedWith(NEWEST_FIRST).map { quest ->
              NearbyQuest(
                  venueId = venueId,
                  venueName = shown.name,
                  avatar = avatar,
                  questId = quest.id,
                  title = quest.title,
                  reward = quest.reward,
                  minPartySize = quest.minPartySize,
                  exactPartySize = quest.exactPartySize,
                  distanceMeters = distance,
              )
            }
          }
  // A stable sort, so each venue's quests keep the newest-first order they were built in
  return if (from == null) {
    // Not by character code, which would put lower case and accented letters last
    rows.sortedWith(
        compareBy(Collator.getInstance(locale)) { row: NearbyQuest -> row.venueName }
            .thenBy { it.venueId }
    )
  } else {
    rows.sortedWith(compareBy<NearbyQuest> { it.distanceMeters }.thenBy { it.venueId })
  }
}
