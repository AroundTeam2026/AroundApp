// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui

import android.util.Log
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

/** How long a disconnect must last before a screen treats it as offline. */
internal const val OFFLINE_HINT_DELAY_MS = 2_000L

/**
 * Ignores brief disconnects during network handover and reports reconnection immediately. If
 * observation fails, allow actions and let the repositories report any request failures.
 */
@OptIn(FlowPreview::class)
internal fun ConnectivityRepository.observeOffline(logTag: String): Flow<Boolean> =
    isOnline
        .catch {
          Log.w(logTag, "Watching connectivity failed", it)
          emit(true)
        }
        .debounce { online -> if (online) 0L else OFFLINE_HINT_DELAY_MS }
        .map { online -> !online }
