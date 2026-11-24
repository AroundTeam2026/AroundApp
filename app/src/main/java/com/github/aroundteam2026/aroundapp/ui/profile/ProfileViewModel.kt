// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ProofUploadQueue
import kotlinx.coroutines.launch

/**
 * Profile session actions; navigation observes the repository's session state.
 *
 * @param proofUploadQueue The explorer's proofs queued offline, cancelled at sign-out since each
 *   keeps the uid of the explorer who queued it; null for a venue, which queues none.
 */
class ProfileViewModel(
    private val repository: AuthRepository,
    private val proofUploadQueue: ProofUploadQueue?,
) : ViewModel() {
  /**
   * Cancels every queued proof, then ends the shared auth session; the session observer returns
   * navigation to auth. A failure to cancel them is logged and doesn't keep the explorer signed in.
   */
  fun signOut() {
    viewModelScope.launch {
      proofUploadQueue?.cancelAll()?.onFailure {
        Log.w(TAG, "Cancelling the queued proofs at sign-out failed", it)
      }
      repository.signOut()
    }
  }

  companion object {
    private const val TAG = "ProfileViewModel"

    /**
     * Supplies the repository and queue interfaces when Compose creates this ViewModel; a venue
     * passes no queue.
     */
    fun factory(
        repository: AuthRepository,
        proofUploadQueue: ProofUploadQueue? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { ProfileViewModel(repository, proofUploadQueue) }
    }
  }
}
