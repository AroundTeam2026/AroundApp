// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests pulling the nearby quests sheet up and back down, alone over a screen-sized area, as the
 * map shows it.
 */
@RunWith(AndroidJUnit4::class)
class NearbyQuestOverlayTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var closed = 0

  /** Shows the overlay with [QUEST_COUNT] quests, enough for the list to scroll pulled up. */
  private fun show(restoration: StateRestorationTester? = null) {
    val content: @Composable () -> Unit = {
      Box(Modifier.testTag(AREA).fillMaxSize()) {
        NearbyQuestOverlay(
            nearbyQuests(QUEST_COUNT),
            locationOn = true,
            onOpenQuest = {},
            onShowOnMap = {},
            onClose = { closed++ },
            filters = NearbyFilters(),
            onFiltersChange = {},
        )
      }
    }
    if (restoration != null) restoration.setContent(content)
    else composeTestRule.setContent(content)
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun top(tag: String): Dp = node(tag).getBoundsInRoot().top

  /** Drags the grabber up slowly, well past where the sheet stops, and lets go. */
  private fun pullUp() {
    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipe(center, center - Offset(0f, LONG_DRAG.toPx()), durationMillis = SLOW_DRAG_MS)
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun aLongDragUpDocksTheSheetUnderTheTopAcrossTheWidthAndNoFurther() {
    show()

    pullUp()

    val sheet = node(C.Tag.NEARBY_OVERLAY).getBoundsInRoot()
    val area = node(AREA).getBoundsInRoot()
    assertDpEquals("top", PULLED_UP_TOP, sheet.top)
    assertDpEquals("left", area.left, sheet.left)
    assertDpEquals("right", area.right, sheet.right)
    assertDpEquals("bottom", area.bottom, sheet.bottom)
    node(C.Tag.NEARBY_SCRIM).assertIsDisplayed()
  }

  @Test
  fun aDragUpOnTheListGrowsTheSheetBeforeTheListScrolls() {
    show()
    val restTop = top(C.Tag.NEARBY_OVERLAY)

    node(C.Tag.NEARBY_LIST).performTouchInput {
      down(center)
      repeat(STEPS) { moveBy(Offset(0f, -SHORT_DRAG.toPx() / STEPS)) }
    }
    composeTestRule.waitForIdle()

    // Still under the finger, the sheet has moved up with it and the list hasn't scrolled
    val sheetTop = top(C.Tag.NEARBY_OVERLAY)
    val listScrolled = node(C.Tag.NEARBY_LIST).scrolled()
    node(C.Tag.NEARBY_LIST).performTouchInput { up() }
    assertTrue("the sheet went from $restTop to $sheetTop", sheetTop < restTop)
    assertEquals(0f, listScrolled)
  }

  @Test
  fun aDragUpOnAPulledUpListScrollsIt() {
    show()
    pullUp()

    node(C.Tag.NEARBY_LIST).performTouchInput {
      down(center)
      repeat(STEPS) { moveBy(Offset(0f, -SHORT_DRAG.toPx() / STEPS)) }
    }
    composeTestRule.waitForIdle()

    // Still under the finger, the list has scrolled while the sheet stays pulled up
    val sheetTop = top(C.Tag.NEARBY_OVERLAY)
    val listScrolled = node(C.Tag.NEARBY_LIST).scrolled()
    node(C.Tag.NEARBY_LIST).performTouchInput { up() }
    assertDpEquals("sheet", PULLED_UP_TOP, sheetTop)
    assertTrue("the list scrolled by $listScrolled", listScrolled > 0f)
  }

  @Test
  fun aDragDownOnAScrolledPulledUpListScrollsItBackBeforeTheSheetShrinks() {
    show()
    pullUp()
    node(C.Tag.NEARBY_LIST).performScrollToIndex(QUEST_COUNT - 1)
    composeTestRule.waitForIdle()
    val listScrolled = node(C.Tag.NEARBY_LIST).scrolled()

    node(C.Tag.NEARBY_LIST).performTouchInput {
      down(center)
      repeat(STEPS) { moveBy(Offset(0f, SHORT_DRAG.toPx() / STEPS)) }
    }
    composeTestRule.waitForIdle()

    // Still under the finger, the list has scrolled back while the sheet stays pulled up
    val sheetTop = top(C.Tag.NEARBY_OVERLAY)
    val listScrolledBack = node(C.Tag.NEARBY_LIST).scrolled()
    node(C.Tag.NEARBY_LIST).performTouchInput { up() }
    assertDpEquals("sheet", PULLED_UP_TOP, sheetTop)
    assertTrue(
        "the list went from $listScrolled to $listScrolledBack",
        listScrolledBack < listScrolled,
    )
  }

  @Test
  fun aFlingDownOnThePulledUpSheetTakesItBackToRestWithoutClosingIt() {
    show()
    val restTop = top(C.Tag.NEARBY_OVERLAY)
    pullUp()

    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipeWithVelocity(
          center,
          center + Offset(0f, SHORT_DRAG.toPx()),
          endVelocity = FAST_FLING.toPx(),
      )
    }
    composeTestRule.waitForIdle()

    assertDpEquals("sheet", restTop, top(C.Tag.NEARBY_OVERLAY))
    node(C.Tag.NEARBY_SCRIM).assertDoesNotExist()
    assertEquals(0, closed)
  }

  @Test
  fun aShortSlowDragUpSettlesBackAtRest() {
    show()
    val restTop = top(C.Tag.NEARBY_OVERLAY)

    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipe(center, center - Offset(0f, SHORT_DRAG.toPx()), durationMillis = SLOW_DRAG_MS)
    }
    composeTestRule.waitForIdle()

    assertDpEquals("sheet", restTop, top(C.Tag.NEARBY_OVERLAY))
    node(C.Tag.NEARBY_SCRIM).assertDoesNotExist()
  }

  @Test
  fun aShortFastFlingUpPullsTheSheetUp() {
    show()

    node(C.Tag.NEARBY_GRABBER).performTouchInput {
      swipeWithVelocity(
          center,
          center - Offset(0f, SHORT_DRAG.toPx()),
          endVelocity = FAST_FLING.toPx(),
      )
    }
    composeTestRule.waitForIdle()

    assertDpEquals("sheet", PULLED_UP_TOP, top(C.Tag.NEARBY_OVERLAY))
  }

  @Test
  fun tappingTheDimmedMapTakesTheSheetBackToRest() {
    show()
    val restTop = top(C.Tag.NEARBY_OVERLAY)
    pullUp()

    node(C.Tag.NEARBY_SCRIM).performTouchInput { click(Offset(centerX, PULLED_UP_TOP.toPx() / 2)) }
    composeTestRule.waitForIdle()

    assertDpEquals("sheet", restTop, top(C.Tag.NEARBY_OVERLAY))
    node(C.Tag.NEARBY_SCRIM).assertDoesNotExist()
  }

  @Test
  fun thePulledUpSheetStaysPulledUpOverRecreation() {
    val restoration = StateRestorationTester(composeTestRule)
    show(restoration)
    pullUp()

    restoration.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()

    assertDpEquals("sheet", PULLED_UP_TOP, top(C.Tag.NEARBY_OVERLAY))
    node(C.Tag.NEARBY_SCRIM).assertIsDisplayed()
  }

  private companion object {
    const val AREA = "area"
    const val QUEST_COUNT = 10
    /** A drag far longer than the sheet can grow on the test screen. */
    val LONG_DRAG = 600.dp
    /** A drag well under halfway to pulled up. */
    val SHORT_DRAG = 40.dp
    const val SLOW_DRAG_MS = 1_000L
    const val STEPS = 10
    /** Faster than the overlay's fling threshold. */
    val FAST_FLING = 1_800.dp
  }
}
