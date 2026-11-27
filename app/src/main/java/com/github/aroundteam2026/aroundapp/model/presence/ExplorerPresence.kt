// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.presence

import com.github.aroundteam2026.aroundapp.model.common.Location

// Placeholder copied from #721's issue so #727 compiles; #721 replaces it.

/**
 * An explorer who chose to be seen by other explorers.
 *
 * @property location Where they are, rounded to 4 decimals (about 10 m) before it is stored.
 * @property availableUntil When they stop being visible, in epoch milliseconds.
 * @property updatedAt When they last showed themselves, in epoch milliseconds.
 */
data class ExplorerPresence(
    val uid: String,
    val location: Location,
    val availableUntil: Long,
    val updatedAt: Long,
)
