// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.isValidAt
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS
import java.text.Collator
import java.util.Locale

/**
 * A row of the list of quests near the explorer: one valid quest and its venue.
 *
 * @property venueId Id of the quest's venue.
 * @property venueName The venue's name.
 * @property questId Id of the quest.
 * @property title The quest's title.
 * @property reward The quest's reward, or null when it has none.
 * @property minPartySize How many people the quest needs at least.
 * @property distanceMeters How far the venue is from the explorer, or null when their position is
 *   unknown.
 */
data class NearbyQuest(
    val venueId: String,
    val venueName: String,
    val questId: String,
    val title: String,
    val reward: Reward?,
    val minPartySize: Int,
    val distanceMeters: Double?,
)

/**
 * One row per quest valid at [now], by the same rule as the map's pins.
 *
 * Each row shows its venue as the venue's pin does (see [shownVenue]): its name and location from
 * [venues] when known there, else from the venue's featured quest, so every quest of a venue gets
 * the same name and distance.
 *
 * With the explorer's position [from], it lists the quests within [NEARBY_RADIUS_METERS] of it,
 * nearest first. Without it, there is nothing to measure from: it lists every valid quest, by venue
 * name as [locale] sorts them, with no distance. Either way, a venue's quests stay together, even
 * beside another venue at the same distance or with the same name, newest first, then by smallest
 * id.
 *
 * @param venues The known venues, by id; venues missing from it are drawn from their quests.
 * @param from The explorer's position, or null when unknown.
 * @param locale The language whose rules sort venue names when [from] is unknown.
 */
fun nearbyQuests(
    quests: List<Quest>,
    venues: Map<String, Venue>,
    now: Long,
    from: Location?,
    locale: Locale = Locale.getDefault(),
): List<NearbyQuest> {
  val rows =
      quests
          .filter { it.isValidAt(now) }
          .groupBy { it.venueId }
          .flatMap { (venueId, own) ->
            val shown = shownVenue(own, venues[venueId])
            val distance = from?.distanceTo(shown.location)
            if (distance != null && distance > NEARBY_RADIUS_METERS) return@flatMap emptyList()
            own.sortedWith(NEWEST_FIRST).map { quest ->
              NearbyQuest(
                  venueId = venueId,
                  venueName = shown.name,
                  questId = quest.id,
                  title = quest.title,
                  reward = quest.reward,
                  minPartySize = quest.minPartySize,
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
