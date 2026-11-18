// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

// The SDK confirms single taps asynchronously, including while its renderer starts up.
private const val MAX_MAP_TAP_AGE_MILLIS = 2_000L

/** Supplies the touch coordinate missing from a Maps SDK marker click. */
internal class MapTapPosition {
  private var latest: Pair<Offset, Long>? = null

  fun clear() {
    latest = null
  }

  fun record(point: Offset) {
    latest = point to SystemClock.uptimeMillis()
  }

  /** A click consumes the coordinate once. Accessibility actions can arrive without a touch. */
  fun take(): Offset? {
    val tap = latest
    clear()
    return tap?.takeIf { SystemClock.uptimeMillis() - it.second <= MAX_MAP_TAP_AGE_MILLIS }?.first
  }
}

/** Observes single-finger taps in map-local pixels and leaves gesture handling to the SDK. */
internal fun Modifier.observeMapTaps(taps: MapTapPosition): Modifier =
    pointerInput(taps) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        taps.clear()
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val pointer = event.changes.singleOrNull()?.takeIf { it.id == down.id } ?: break
          if ((pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) break
          if (!pointer.pressed) {
            if (
                pointer.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
            ) {
              taps.record(pointer.position)
            }
            break
          }
        }
      }
    }
