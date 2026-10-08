// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * A venue's closed marker: [icon] on a light circle in a ring, whose pointer's tip, at the bottom
 * centre, marks the venue.
 */
@Composable
fun QuestPin(
    icon: VenueAvatar,
    modifier: Modifier = Modifier,
    style: MarkerStyle = MarkerDefaults.style(),
) {
  val dimensions = style.dimensions
  val colors = style.colors
  Box(
      modifier
          .testTag(C.Tag.QUEST_PIN)
          .size(
              width = dimensions.pinDiameter,
              height = dimensions.pinDiameter + dimensions.pointerHeight,
          )
          // The ring's colour fills the whole pin, pointer included; the circle covers its middle
          .background(colors.pinRing, style.pinShape),
      contentAlignment = Alignment.TopCenter,
  ) {
    Box(
        Modifier.size(dimensions.pinDiameter)
            .padding(dimensions.pinRingWidth)
            .background(colors.pinSurface, CircleShape)
    )
    icon.Content(
        tint = colors.accent,
        modifier = Modifier.size(dimensions.pinDiameter).padding(dimensions.pinIconPadding),
    )
  }
}
