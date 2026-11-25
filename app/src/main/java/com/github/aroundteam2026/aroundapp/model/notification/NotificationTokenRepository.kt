// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

/**
 * Stores this phone's Firebase Cloud Messaging token under its user, so Cloud Functions know where
 * to send that user's notifications.
 *
 * A user has one token per phone they are signed in on. Only the user writes and deletes their own
 * tokens; the app never reads them, since the functions read them with the admin SDK.
 *
 * Both calls return once the server has answered, so offline they may not return until the
 * connection is back: a caller that mustn't wait for them, such as sign-out, doesn't block on them.
 */
interface NotificationTokenRepository {
  /**
   * Saves this phone's FCM [token] for [uid], at users/{uid}/tokens/{token}. Saving a token again
   * succeeds and keeps one document. Fails with [IllegalArgumentException] if [uid] or [token] is
   * blank or contains a slash.
   */
  suspend fun save(uid: String, token: String): Result<Unit>

  /**
   * Deletes it, e.g. at sign-out. Deleting a token that isn't saved succeeds. Fails with
   * [IllegalArgumentException] if [uid] or [token] is blank or contains a slash.
   */
  suspend fun delete(uid: String, token: String): Result<Unit>
}

/**
 * Checks that [uid] and [token] can each name one document: not blank and without a slash, which
 * would turn the id into a path, e.g. to another user's tokens.
 *
 * @throws IllegalArgumentException if either can't.
 */
internal fun requireTokenPath(uid: String, token: String) {
  require(uid.isNotBlank() && '/' !in uid) { "Invalid user id" }
  require(token.isNotBlank() && '/' !in token) { "Invalid notification token" }
}
