// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.model.presence.ExplorerPresence
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.ui.map.MapViewModel.Companion.NEARBY_RADIUS_METERS

/**
 * One explorer as the map and the list show them.
 *
 * @property uid The explorer's user id.
 * @property name Their display name.
 * @property pictureUrl The Cloud Storage location of their picture, or null when they have none.
 * @property distanceMeters How far they are from the user, or null when the user's position is
 *   unknown.
 * @property availableUntil When they stop being visible, in epoch milliseconds.
 */
data class NearbyExplorer(
    val uid: String,
    val name: String,
    val pictureUrl: String?,
    val distanceMeters: Double?,
    val availableUntil: Long,
)

/**
 * The explorers in [presences] that the user [me] at [from] can meet at [now], nearest first.
 *
 * It leaves out [me], anyone no longer available at [now], anyone farther than
 * [NEARBY_RADIUS_METERS], and anyone without an explorer profile in [profiles]: their name and
 * picture come from there, and venues never show up as explorers.
 */
fun nearbyExplorers(
    presences: List<ExplorerPresence>,
    profiles: Map<String, User?>,
    me: String,
    from: Location,
    now: Long,
): List<NearbyExplorer> =
    presences
        .filter { it.uid != me && it.availableUntil > now }
        .mapNotNull { presence ->
          val profile = profiles[presence.uid]?.takeIf { it.role == Role.EXPLORER }
          val distance = from.distanceTo(presence.location)
          if (profile == null || distance > NEARBY_RADIUS_METERS) return@mapNotNull null
          NearbyExplorer(
              uid = presence.uid,
              name = profile.displayName,
              pictureUrl = profile.pictureUrl,
              distanceMeters = distance,
              availableUntil = presence.availableUntil,
          )
        }
        .sortedWith(compareBy<NearbyExplorer> { it.distanceMeters }.thenBy { it.uid })
