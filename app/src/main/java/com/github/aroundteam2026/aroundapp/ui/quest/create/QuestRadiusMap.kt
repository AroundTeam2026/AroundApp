// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.StillRadiusMap
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * A still map of the unlock radius: the venue's pin at [center], the circle of [radiusMeters]
 * around it, and about how many minutes, [walkMinutes], it takes to walk across it.
 */
@Composable
internal fun QuestRadiusMap(center: Location, radiusMeters: Int, walkMinutes: Int) {
  StillRadiusMap(
      center,
      radiusMeters,
      stringResource(R.string.create_quest_radius_map, radiusMeters),
      Modifier.border(BORDER, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
          .testTag(C.Tag.CREATE_QUEST_RADIUS_MAP),
  ) {
    WalkChip(walkMinutes, Modifier.align(Alignment.TopStart).padding(AroundSpacing.CompactGap))
  }
}

/** The pill in the map's corner: about how long it takes to walk across the radius. */
@Composable
private fun WalkChip(minutes: Int, modifier: Modifier = Modifier) {
  Surface(
      shape = AroundPillShape,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = CHIP_ELEVATION,
      modifier = modifier,
  ) {
    Row(
        Modifier.padding(
            horizontal = AroundSpacing.CompactGap,
            vertical = AroundSpacing.SegmentGap,
        ),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.SegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(R.drawable.ic_clock),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(CLOCK_SIZE),
      )
      Text(
          stringResource(R.string.create_quest_walk_time, minutes),
          style = AroundTextStyles.Badge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag(C.Tag.CREATE_QUEST_WALK_TIME),
      )
    }
  }
}

private val BORDER: Dp = 1.dp
private val CHIP_ELEVATION: Dp = 2.dp
private val CLOCK_SIZE: Dp = 14.dp
