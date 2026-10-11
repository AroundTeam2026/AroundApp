// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [dotWindow]: which pages get a dot, so the shown page always has one. */
class NearbyQuestDotsTest {

  @Test
  fun noPagesNoDots() {
    assertEquals(IntRange.EMPTY, dotWindow(current = 0, count = 0))
  }

  @Test
  fun upToFivePagesEachGetADot() {
    assertEquals(0..2, dotWindow(current = 2, count = 3))
    assertEquals(0..4, dotWindow(current = 4, count = 5))
  }

  @Test
  fun nearTheStartTheDotsStartAtTheFirstPage() {
    assertEquals(0..4, dotWindow(current = 0, count = 12))
    assertEquals(0..4, dotWindow(current = 2, count = 12))
  }

  @Test
  fun inTheMiddleTheShownPageHasTheCentreDot() {
    assertEquals(1..5, dotWindow(current = 3, count = 12))
    assertEquals(3..7, dotWindow(current = 5, count = 12))
  }

  @Test
  fun nearTheEndTheDotsEndAtTheLastPage() {
    assertEquals(7..11, dotWindow(current = 9, count = 12))
    assertEquals(7..11, dotWindow(current = 11, count = 12))
  }

  @Test
  fun sixPagesSlideByOne() {
    // The smallest count that slides at all
    assertEquals(0..4, dotWindow(current = 2, count = 6))
    assertEquals(1..5, dotWindow(current = 3, count = 6))
  }
}
