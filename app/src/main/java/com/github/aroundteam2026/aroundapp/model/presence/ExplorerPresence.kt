// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.presence

import com.github.aroundteam2026.aroundapp.model.common.Location

/**
 * An explorer whom other explorers can see near them until [availableUntil].
 *
 * @property location Where they are, rounded to [PRESENCE_LOCATION_DECIMALS] decimals before it is
 *   stored.
 * @property availableUntil When they stop being visible, in epoch milliseconds.
 * @property updatedAt When the document was last written, in epoch milliseconds.
 */
data class ExplorerPresence(
    val uid: String,
    val location: Location,
    val availableUntil: Long,
    val updatedAt: Long,
)

/** Decimals kept of a presence's location: 4 is about 10 m, enough to meet up but never exact. */
const val PRESENCE_LOCATION_DECIMALS = 4
