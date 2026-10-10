// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.quest.Reward

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
