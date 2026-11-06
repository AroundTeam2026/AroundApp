// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Reports [online], which a test sets to take the device offline or back online. */
class FakeConnectivityRepository(online: Boolean = true) : ConnectivityRepository {
  val online = MutableStateFlow(online)

  override val isOnline: Flow<Boolean> = this.online
}
