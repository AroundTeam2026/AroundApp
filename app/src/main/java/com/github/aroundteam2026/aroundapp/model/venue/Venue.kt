// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.venue

import com.github.aroundteam2026.aroundapp.model.common.Location

/**
 * A venue's business profile: its name, where it is, and the area in which a visit to it counts.
 *
 * Each account has at most one venue, so the venue's [id] is its owner's uid.
 *
 * @property id Id of the venue, equal to its owner's uid.
 * @property name The business name shown to explorers.
 * @property location Where the venue is, or null until the venue confirms its marker on the map.
 * @property radiusMeters Distance from [location] within which a visit counts. Checking a visit
 *   against it is not done here. It should be between [VenueLimits.MIN_RADIUS_METERS] and
 *   [VenueLimits.MAX_RADIUS_METERS]; the repository does not enforce this, the ViewModel that edits
 *   it will. A caller creating a venue that has not defined its area yet should pass
 *   [VenueLimits.DEFAULT_RADIUS_METERS].
 * @property address Street address, or null if the venue did not give one.
 * @property createdAt Creation time, in epoch milliseconds, set by the caller.
 */
data class Venue(
    val id: String,
    val name: String,
    val location: Location?,
    val radiusMeters: Int,
    val address: String?,
    val createdAt: Long,
)
