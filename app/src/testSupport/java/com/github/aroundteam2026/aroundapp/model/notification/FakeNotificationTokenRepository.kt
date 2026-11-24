// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

/**
 * In-memory [NotificationTokenRepository] for tests without Firestore. Tests read what was saved
 * with [tokensOf], and make every call fail by setting [failure].
 */
class FakeNotificationTokenRepository : NotificationTokenRepository {
  private val saved = mutableMapOf<String, MutableSet<String>>()

  /** When set, every call returns it as its failure and changes nothing. */
  var failure: Throwable? = null

  /** The tokens saved for [uid], in no particular order. */
  fun tokensOf(uid: String): Set<String> = saved[uid].orEmpty().toSet()

  override suspend fun save(uid: String, token: String): Result<Unit> =
      attempt(uid, token) { saved.getOrPut(uid) { mutableSetOf() } += token }

  override suspend fun delete(uid: String, token: String): Result<Unit> =
      attempt(uid, token) { saved[uid]?.remove(token) }

  /** Checks the ids as the Firestore implementation does, then runs [change] unless [failure]. */
  private fun attempt(uid: String, token: String, change: () -> Unit): Result<Unit> {
    try {
      requireTokenPath(uid, token)
    } catch (e: IllegalArgumentException) {
      return Result.failure(e)
    }
    failure?.let {
      return Result.failure(it)
    }
    change()
    return Result.success(Unit)
  }
}
