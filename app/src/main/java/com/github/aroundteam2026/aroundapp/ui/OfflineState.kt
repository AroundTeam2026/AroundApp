// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How long a disconnect must last before a screen treats it as offline. */
internal const val OFFLINE_HINT_DELAY_MS = 2_000L

/**
 * Ignores brief disconnects during network handover and reports reconnection immediately. If
 * observation fails, allow actions and let the repositories report any request failures.
 */
@OptIn(FlowPreview::class)
internal fun ConnectivityRepository.observeOffline(logTag: String): Flow<Boolean> =
    // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
    isOnline
        .catch { // NOSONAR
          Log.w(logTag, "Watching connectivity failed", it)
          emit(true)
        }
        .debounce { online -> if (online) 0L else OFFLINE_HINT_DELAY_MS }
        .map { online -> !online }

/**
 * Whether the device is online right now, with no delay: for an action that needs a connection,
 * which can't succeed from the first moment of a disconnect, while [observeOffline] holds back what
 * a screen shows. Online until the connectivity is known, and if observing it fails.
 */
internal fun ConnectivityRepository.observeOnlineNow(
    scope: CoroutineScope,
    logTag: String,
): StateFlow<Boolean> =
    // NOSONAR below: Sonar's S1874 takes Flow.catch for deprecated code, but it isn't deprecated
    isOnline
        .catch { // NOSONAR
          Log.w(logTag, "Watching connectivity failed", it)
          emit(true)
        }
        .stateIn(scope, SharingStarted.Eagerly, true)
