// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Saves a venue's answers to what waits for it, such as reservations and proofs, the same way on
 * every screen that answers them.
 *
 * An answer stays saving from the tap until the listener shows it answered, so its buttons can't be
 * tapped again in between, which would fail; a double tap saves once. A failure ends saving and is
 * reported until [clearFailure].
 *
 * @param scope Where the saves run: the ViewModel's.
 * @param tag Log tag of the ViewModel that answers.
 */
internal class AnswerSaver(private val scope: CoroutineScope, private val tag: String) {
  /**
   * @property saving Ids answered and not failed. A saved one stays here: [savingAmong] drops it
   *   once the listener no longer shows it waiting.
   * @property failed Whether the last answer failed.
   */
  data class State(val saving: Set<String> = emptySet(), val failed: Boolean = false)

  private val mutableState = MutableStateFlow(State())

  /** What is being saved, and whether the last answer failed. */
  val state: StateFlow<State> = mutableState.asStateFlow()

  /**
   * Answers [id] by running [save]; [what] names the answer in the log. Does nothing while [id] is
   * saving, so a double tap saves once.
   */
  fun answer(id: String, what: String, save: suspend () -> Result<Unit>) {
    val before = mutableState.getAndUpdate { it.copy(saving = it.saving + id) }
    if (id in before.saving) return
    scope.launch {
      save().onFailure {
        Log.w(tag, "Answering $what failed", it)
        mutableState.update { state -> state.copy(saving = state.saving - id, failed = true) }
      }
    }
  }

  /** Clears [State.failed], once the screen has shown it. */
  fun clearFailure() {
    mutableState.update { it.copy(failed = false) }
  }
}

/**
 * The ids being saved among those still [waiting] for an answer: an answer saved and shown by the
 * listener has left them.
 */
internal fun AnswerSaver.State.savingAmong(waiting: Set<String>): Set<String> =
    saving intersect waiting
