// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Saves the ID FCM registers this phone under (its Firebase Installation ID) for whoever is signed
 * in, as a token of [NotificationTokenRepository]. Signed out, it saves nothing.
 *
 * Each sign-in, including a session restored at launch, asks FCM to
 * [NotificationRegistration.register] the phone, and FCM answers through [onRegistered], which
 * saves the ID for whoever is signed in by then. So a user who signs out before FCM answers gets
 * nothing saved.
 *
 * Failures aren't retried, only logged without the ID: Firestore keeps the write while offline, and
 * the next sign-in registers again.
 *
 * @param auth tells who is signed in.
 * @param tokens where the ID is saved.
 * @param registration asks FCM for the ID at each sign-in.
 * @param scope runs the registrations and saves; it should outlive any one screen, since FCM can
 *   answer while no activity is open.
 */
class NotificationTokenRegistrar(
    private val auth: AuthRepository,
    private val tokens: NotificationTokenRepository,
    private val registration: NotificationRegistration,
    private val scope: CoroutineScope,
) {
  private val started = AtomicBoolean(false)

  /**
   * Starts registering the phone at each sign-in. Only the first call counts, so an activity
   * recreated on rotation doesn't register again.
   */
  fun start() {
    if (!started.compareAndSet(false, true)) return
    scope.launch {
      // A sign-out (null) reaches collectLatest too, so it stops waiting on a registration
      // started for the user who left, and doesn't log its failure. FCM still finishes it and
      // calls onRegistered, which is what keeps the ID from being saved for them.
      auth.currentUserId.collectLatest { uid ->
        if (uid == null) return@collectLatest
        registration.register().onFailure {
          Log.w(TAG, "Couldn't register the phone for notifications", it)
        }
      }
    }
  }

  /**
   * Saves [id], the ID FCM registered this phone under, for the signed-in user; signed out,
   * nothing.
   */
  fun onRegistered(id: String) {
    val uid = auth.currentUserId.value ?: return
    scope.launch {
      tokens.save(uid, id).onFailure { Log.w(TAG, "Couldn't save the phone's notification ID", it) }
    }
  }

  private companion object {
    const val TAG = "NotificationTokenRegistrar"
  }
}
