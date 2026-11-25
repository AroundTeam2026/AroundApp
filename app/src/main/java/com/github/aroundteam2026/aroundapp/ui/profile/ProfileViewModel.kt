// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.notification.NotificationRegistration
import com.github.aroundteam2026.aroundapp.model.notification.NotificationTokenRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ProofUploadQueue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Profile session actions; navigation observes the repository's session state.
 *
 * @param proofUploadQueue The explorer's proofs queued offline, cancelled at sign-out since each
 *   keeps the uid of the explorer who queued it; null for a venue, which queues none.
 * @param notificationTokens where this phone's notification ID is saved for the user, deleted at
 *   sign-out so the next user of the phone doesn't get their notifications.
 * @param notificationRegistration gives this phone's notification ID.
 */
class ProfileViewModel(
    private val repository: AuthRepository,
    private val proofUploadQueue: ProofUploadQueue?,
    private val notificationTokens: NotificationTokenRepository,
    private val notificationRegistration: NotificationRegistration,
) : ViewModel() {
  /**
   * Cancels every queued proof and deletes this phone's notification ID while still signed in, then
   * ends the shared auth session; the session observer returns navigation to auth. A failure of
   * either is logged and doesn't keep the user signed in, and neither does this ViewModel being
   * cleared while it waits. Deleting the ID waits at most [TOKEN_DELETE_TIMEOUT_MS], since offline
   * the delete only returns once the connection is back.
   */
  fun signOut() {
    viewModelScope.launch {
      try {
        proofUploadQueue?.cancelAll()?.onFailure {
          Log.w(TAG, "Cancelling the queued proofs at sign-out failed", it)
        }
        deletePhoneToken()
      } finally {
        repository.signOut()
      }
    }
  }

  /**
   * Deletes this phone's notification ID for the signed-in user, if any, logging why it couldn't
   * without the ID. It must run before signing out: only the user may delete their own tokens.
   */
  private suspend fun deletePhoneToken() {
    val uid = repository.currentUserId.value ?: return
    val deleted =
        withTimeoutOrNull(TOKEN_DELETE_TIMEOUT_MS) {
          notificationRegistration
              .id()
              .fold({ id -> notificationTokens.delete(uid, id) }, { Result.failure(it) })
        }
    when {
      deleted == null -> Log.w(TAG, "Deleting the phone's notification ID at sign-out timed out")
      deleted.isFailure ->
          Log.w(
              TAG,
              "Deleting the phone's notification ID at sign-out failed",
              deleted.exceptionOrNull(),
          )
    }
  }

  companion object {
    private const val TAG = "ProfileViewModel"

    /** How long sign-out waits for this phone's notification ID to be deleted, in milliseconds. */
    @VisibleForTesting internal const val TOKEN_DELETE_TIMEOUT_MS = 2_000L

    /**
     * Supplies the repository, queue and notification interfaces when Compose creates this
     * ViewModel; a venue passes null for the queue.
     */
    fun factory(
        repository: AuthRepository,
        proofUploadQueue: ProofUploadQueue?,
        notificationTokens: NotificationTokenRepository,
        notificationRegistration: NotificationRegistration,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        ProfileViewModel(
            repository,
            proofUploadQueue,
            notificationTokens,
            notificationRegistration,
        )
      }
    }
  }
}
