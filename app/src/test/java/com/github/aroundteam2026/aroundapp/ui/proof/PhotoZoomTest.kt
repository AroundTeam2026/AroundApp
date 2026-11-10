// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.proof

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The zoom of a full-screen proof photo: how far it zooms, where it can move, and its label. */
class PhotoZoomTest {

  private fun PhotoZoom.pinched(
      zoom: Float,
      centroid: Offset = SCREEN_CENTRE,
      pan: Offset = Offset.Zero,
  ) = transformed(centroid, pan, zoom, PHOTO, SCREEN)

  private fun assertOffsetEquals(expected: Offset, actual: Offset) {
    assertEquals(expected.x, actual.x, DELTA)
    assertEquals(expected.y, actual.y, DELTA)
  }

  @Test
  fun aWidePhotoFitsTheScreensWidth() {
    assertEquals(Size(400f, 200f), fittedSize(PHOTO, SCREEN))
  }

  @Test
  fun aTallPhotoFitsTheScreensHeight() {
    assertEquals(Size(400f, 800f), fittedSize(Size(100f, 200f), SCREEN))
  }

  @Test
  fun aPhotoWithoutPixelsHasNoSize() {
    assertEquals(Size.Zero, fittedSize(Size.Zero, SCREEN))
  }

  @Test
  fun aPinchZoomsInAndOutWithinItsLimits() {
    assertEquals(2f, PhotoZoom().pinched(2f).scale, DELTA)
    assertEquals(MAX_ZOOM, PhotoZoom().pinched(20f).scale, DELTA)
    assertEquals(MIN_ZOOM, PhotoZoom(scale = 2f).pinched(0.1f).scale, DELTA)
  }

  @Test
  fun aFittedPhotoDoesNotMove() {
    assertOffsetEquals(Offset.Zero, PhotoZoom().pinched(1f, pan = Offset(100f, 100f)).offset)
  }

  @Test
  fun theMiddleOfAPinchStaysUnderTheFingers() {
    // 2× around a point 100px right of the centre moves the photo's centre 100px left
    val zoomed = PhotoZoom().pinched(2f, centroid = SCREEN_CENTRE + Offset(100f, 0f))

    assertOffsetEquals(Offset(-100f, 0f), zoomed.offset)
  }

  @Test
  fun aZoomedPhotoMovesOnlyAsFarAsItsEdges() {
    val zoomed = PhotoZoom(scale = 2f)

    // At 2× the photo is 800 × 400: 200px past each side, and still shorter than the screen
    assertOffsetEquals(Offset(50f, 0f), zoomed.pinched(1f, pan = Offset(50f, 50f)).offset)
    assertOffsetEquals(Offset(200f, 0f), zoomed.pinched(1f, pan = Offset(1_000f, 0f)).offset)
    assertOffsetEquals(Offset(-200f, 0f), zoomed.pinched(1f, pan = Offset(-1_000f, 0f)).offset)
  }

  @Test
  fun zoomingOutBringsAMovedPhotoBack() {
    val moved = PhotoZoom(scale = 2f, offset = Offset(200f, 0f))

    assertOffsetEquals(Offset.Zero, moved.pinched(0.5f).offset)
  }

  @Test
  fun theZoomLevelHasAtMostOneDecimal() {
    assertEquals("1", zoomLevel(1f, Locale.US))
    assertEquals("1.5", zoomLevel(1.5f, Locale.US))
    assertEquals("2", zoomLevel(2.04f, Locale.US))
    assertEquals("2,5", zoomLevel(2.5f, Locale.FRANCE))
  }

  private companion object {
    const val DELTA = 0.001f
    val SCREEN = Size(400f, 800f)
    val SCREEN_CENTRE = Offset(200f, 400f)
    val PHOTO = Size(200f, 100f)
  }
}
