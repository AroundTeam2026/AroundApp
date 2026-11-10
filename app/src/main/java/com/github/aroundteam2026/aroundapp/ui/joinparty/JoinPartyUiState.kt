// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import com.github.aroundteam2026.aroundapp.ui.overview.OverviewRow

/**
 * What the Join a party screen shows: the party code the explorer typed, the party it leads to or
 * why it can't be joined, and whether the explorer is joining it.
 *
 * @property code The party code as typed, in any case and with spaces.
 * @property lookup What the code leads to.
 * @property join Whether the explorer is joining the party [lookup] found, or has.
 */
data class JoinPartyUiState(
    val code: String = "",
    val lookup: Lookup = Lookup.Idle,
    val join: Join = Join.IDLE,
) {
  /**
   * Whether the button works: something is typed, and the explorer isn't joining or done. With a
   * party found, it joins it; otherwise it looks the typed code up, which also retries a failure.
   */
  val canSubmit: Boolean
    get() = code.isNotBlank() && join == Join.IDLE

  /** What the typed code leads to. */
  sealed interface Lookup {
    /** Nothing to show: the code is too short to be looked up, or empty. */
    data object Idle : Lookup

    /** The code is being looked up. */
    data object Looking : Lookup

    /** The code leads to [party], which can be joined. */
    data class Found(val party: OverviewRow) : Lookup

    /** The party can't be joined, for [error]. */
    data class Refused(val error: Error) : Lookup
  }

  /** Why a party can't be joined. */
  sealed interface Error {
    /** No party has the code. */
    data object NotFound : Error

    /** The party already has [joined] explorers, as many as its quest allows, [capacity]. */
    data class Full(val joined: Int, val capacity: Int) : Error

    /** The venue rejected the reservation, or it was cancelled, or its quest is gone. */
    data object Ended : Error

    /** The venue already approved the reservation, so the party takes no one else. */
    data object Closed : Error

    /** The explorer is already in the party, as its creator or as a member who left. */
    data object AlreadyJoined : Error

    /** The device is offline. */
    data object Offline : Error

    /** The party could not be reached or joined, for another reason. */
    data object Failed : Error
  }

  /** Where joining the party stands. */
  enum class Join {
    /** Not joining. */
    IDLE,
    /** The explorer is being added to the party. */
    JOINING,
    /** The explorer joined the party. */
    JOINED,
  }
}
