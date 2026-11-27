// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.visibility

/** How long an explorer chooses to be seen by other explorers. */
enum class VisibilityChoice {
  ONE_HOUR,
  TWO_HOURS,
  /** Until [VisibilityViewModel.TONIGHT_HOUR]:00 today, only offered before then. */
  TONIGHT,
}

/** What went wrong last. Each one has a retry. */
enum class VisibilityError {
  /** The explorer's own visibility couldn't be read. */
  LOAD_FAILED,
  /** The phone couldn't tell where it is: no location permission, or no fix. Nothing was shared. */
  NO_LOCATION,
  SHOW_FAILED,
  HIDE_FAILED,
}

/**
 * The state of the "Meet explorers" card.
 *
 * @property loading Until the explorer's own visibility is first read.
 * @property visibleUntil While others can see the explorer, when that ends (epoch ms); null while
 *   hidden.
 * @property choices What a hidden explorer can pick, in order. [VisibilityChoice.TONIGHT] is left
 *   out from [VisibilityViewModel.TONIGHT_HOUR]:00.
 * @property offline Showing and hiding need a connection, so both are unavailable.
 * @property busy A show or hide is being sent, so the buttons ignore taps.
 * @property error The last failure, until the next attempt.
 */
data class VisibilityUiState(
    val loading: Boolean = true,
    val visibleUntil: Long? = null,
    val choices: List<VisibilityChoice> = emptyList(),
    val offline: Boolean = false,
    val busy: Boolean = false,
    val error: VisibilityError? = null,
)
