// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals

/** Asserts two positions match to within half a dp, the rounding of laying out on pixels. */
fun assertDpEquals(message: String, expected: Dp, actual: Dp) =
    assertEquals(message, expected.value, actual.value, 0.5f)
