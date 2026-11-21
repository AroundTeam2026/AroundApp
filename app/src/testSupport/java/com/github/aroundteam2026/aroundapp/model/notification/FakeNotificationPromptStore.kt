// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import java.io.IOException

/**
 * [NotificationPromptStore] in memory. Set [answered] to start from an earlier answer, or
 * [failWrites] to make [markAnswered] fail as a full disk would.
 */
class FakeNotificationPromptStore(var answered: Boolean = false) : NotificationPromptStore {
  var failWrites = false

  override suspend fun wasAnswered() = answered

  override suspend fun markAnswered() {
    if (failWrites) throw IOException("Disk full")
    answered = true
  }
}
