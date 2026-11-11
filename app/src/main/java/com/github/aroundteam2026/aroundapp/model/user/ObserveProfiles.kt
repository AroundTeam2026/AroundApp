// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val TAG = "ObserveProfiles"

/**
 * Observes the profiles of [uids], keyed by uid. Emits once every profile is read, then on each
 * change; at once an empty map without uids. A profile only names someone, so one that is missing
 * or fails to load is null, and the failure is only logged.
 */
// NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
fun UserRepository.observeProfiles(uids: Collection<String>): Flow<Map<String, User?>> {
  val ids = uids.distinct()
  if (ids.isEmpty()) return flowOf(emptyMap())
  return combine(
      ids.map { uid ->
        observeUser(uid)
            .catch { // NOSONAR
              Log.w(TAG, "Listening to the profile of $uid failed", it)
              emit(null)
            }
            .map { uid to it }
      }
  ) {
    it.toMap()
  }
}
