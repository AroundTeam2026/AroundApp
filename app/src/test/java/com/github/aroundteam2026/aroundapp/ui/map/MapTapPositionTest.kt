// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the map's pointer observer through Compose input. */
@RunWith(AndroidJUnit4::class)
class MapTapPositionTest {
  @get:Rule val compose = createComposeRule()
  private val taps = MapTapPosition()
  private val surface
    get() = compose.onNodeWithTag(SURFACE)

  @Before
  fun showObserver() {
    compose.setContent { Box(Modifier.size(120.dp).testTag(SURFACE).observeMapTaps(taps)) }
  }

  @Test
  fun aTapSuppliesItsPositionToOnlyOneSdkClick() {
    val point = Offset(24f, 32f)
    surface.performTouchInput { click(point) }

    assertEquals(point, compose.runOnIdle { taps.take() })
    assertNull(compose.runOnIdle { taps.take() })
  }

  @Test
  fun anExpiredTouchCannotOverrideANonTouchActivation() {
    surface.performTouchInput { click() }
    SystemClock.sleep(EXPIRED_TAP_DELAY_MS)

    assertNull(compose.runOnIdle { taps.take() })
  }

  @Test
  fun draggingClearsThePreviousTapAndDoesNotRecordAReplacement() {
    surface.performTouchInput { click() }
    surface.performTouchInput { swipe(centerLeft, centerRight) }

    assertNull(compose.runOnIdle { taps.take() })
  }

  @Test
  fun aSecondFingerCancelsTheTapEvenWithoutAnyMovement() {
    surface.performTouchInput {
      down(pointerId = 0, position = center)
      down(pointerId = 1, position = center + Offset(10f, 0f))
      up(pointerId = 1)
      up(pointerId = 0)
    }

    assertNull(compose.runOnIdle { taps.take() })
  }

  @Test
  fun aLongPressDoesNotLeaveATapForTheNextSdkClick() {
    surface.performTouchInput { longClick() }

    assertNull(compose.runOnIdle { taps.take() })
  }

  private companion object {
    const val SURFACE = "map_touch_surface"
    const val EXPIRED_TAP_DELAY_MS = 3_000L
  }
}
