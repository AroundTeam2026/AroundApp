// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui

/** Where saving a form stands, e.g. creating a quest or submitting its completion. */
sealed interface SaveState {
  /** Not saved yet. */
  data object Idle : SaveState

  /** Being saved; the form can't be saved again meanwhile. */
  data object Saving : SaveState

  /** Saved, as the record [id]. */
  data class Saved(val id: String) : SaveState

  /** The last save failed; the form keeps its values and can be saved again. */
  data object Failed : SaveState
}
