// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import java.io.IOException

/**
 * In-memory [PendingRoleStore]. Share one instance between two [ProfileSetup]s to play an app
 * restart: [pending] stands for what is on disk.
 */
class FakePendingRoleStore(var pending: PendingRole? = null) : PendingRoleStore {
  /** When true, [save] fails with [IOException] and records nothing. */
  var failSave = false

  /** When true, the next [clear] fails with [IOException] and keeps the record. */
  var failNextClear = false

  override suspend fun load() = pending

  override suspend fun save(pending: PendingRole) {
    if (failSave) throw IOException("disk full")
    this.pending = pending
  }

  override suspend fun clear() {
    if (failNextClear) {
      failNextClear = false
      throw IOException("disk full")
    }
    pending = null
  }
}
