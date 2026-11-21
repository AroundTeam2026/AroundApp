// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

/**
 * Remembers whether the explanation before Android's notification permission dialog was answered.
 *
 * It must survive restarts: an answer, "Not now" or a refusal in Android's dialog, means the app
 * doesn't ask again on every launch. People can still turn notifications on from Profile.
 */
interface NotificationPromptStore {
  /** Returns true once the explanation was answered on this phone, either way. */
  suspend fun wasAnswered(): Boolean

  /**
   * Records that the explanation was answered, and returns once that is on disk.
   *
   * @throws java.io.IOException if it couldn't be written. The app then asks again next launch.
   */
  suspend fun markAnswered()
}
