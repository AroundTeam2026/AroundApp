// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.connectivity

import kotlinx.coroutines.flow.Flow

/** Tells whether the device can reach the internet, so screens can adapt while it can't. */
interface ConnectivityRepository {
  /** Whether the device is online: the current state on collection, then each change. */
  val isOnline: Flow<Boolean>
}
