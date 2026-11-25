// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Saves this phone's FCM token for whoever is signed in: when FCM gives a new token, and each time
 * someone signs in. Signed out, it saves nothing.
 *
 * Failures aren't retried, only logged without the token: Firestore keeps the write while offline,
 * and FCM calls [onNewToken] again whenever the token changes.
 *
 * @param auth tells who is signed in.
 * @param tokens where the token is saved.
 * @param source gives the phone's current token at sign-in.
 * @param scope runs the saves; it should outlive any one screen, since FCM can give a new token
 *   while no activity is open.
 */
class NotificationTokenRegistrar(
    private val auth: AuthRepository,
    private val tokens: NotificationTokenRepository,
    private val source: NotificationTokenSource,
    private val scope: CoroutineScope,
) {
  private val started = AtomicBoolean(false)

  /**
   * Starts saving the token at each sign-in, including a session restored at launch. Only the first
   * call counts, so an activity recreated on rotation doesn't save again.
   */
  fun start() {
    if (!started.compareAndSet(false, true)) return
    scope.launch {
      // No distinctUntilChanged: once nulls are dropped, it would merge a sign-out and a sign-in
      // back as the same user into one sign-in, though the sign-out deleted the token (#636)
      auth.currentUserId.filterNotNull().collectLatest { uid ->
        source
            .currentToken()
            .onSuccess { save(uid, it) }
            .onFailure { Log.w(TAG, "Couldn't get the notification token", it) }
      }
    }
  }

  /** Saves [token], FCM's new token for this phone, for the signed-in user; signed out, nothing. */
  fun onNewToken(token: String) {
    val uid = auth.currentUserId.value ?: return
    scope.launch { save(uid, token) }
  }

  /** Saves [token] for [uid], logging a failure without the token itself. */
  private suspend fun save(uid: String, token: String) {
    tokens.save(uid, token).onFailure { Log.w(TAG, "Couldn't save the notification token", it) }
  }

  private companion object {
    const val TAG = "NotificationTokenRegistrar"
  }
}
