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
import com.github.aroundteam2026.aroundapp.model.common.distanceTo
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.StillRadiusMap
import com.github.aroundteam2026.aroundapp.ui.quest.distanceText
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

/**
 * A still map of the unlock radius: the quest's pin at [center] and the circle of [radiusMeters]
 * around it. At the venue, [venue] is null and the chip says about how many minutes, [walkMinutes],
 * it takes to walk across the circle. Elsewhere, the venue's own pin shows at [venue], the chip
 * says how far the quest is from [venueName], and tapping the map or dragging the pin calls
 * [onMoved].
 */
@Composable
internal fun QuestRadiusMap(
    center: Location,
    radiusMeters: Int,
    walkMinutes: Int,
    venue: Location?,
    venueName: String,
    onMoved: (Location) -> Unit,
) {
  val description =
      if (venue == null) R.string.create_quest_radius_map
      else R.string.create_quest_radius_map_elsewhere
  StillRadiusMap(
      center,
      radiusMeters,
      stringResource(description, radiusMeters),
      Modifier.border(BORDER, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
          .testTag(C.Tag.CREATE_QUEST_RADIUS_MAP),
      onMoved = onMoved.takeIf { venue != null },
      venue = venue,
  ) {
    val chipModifier = Modifier.padding(AroundSpacing.CompactGap)
    if (venue == null) {
      MapChip(
          stringResource(R.string.create_quest_walk_time, walkMinutes),
          C.Tag.CREATE_QUEST_WALK_TIME,
          chipModifier.align(Alignment.TopStart),
      )
    } else {
      MapChip(
          stringResource(
              R.string.create_quest_distance_from_venue,
              distanceText(venue.distanceTo(center)),
              venueName,
          ),
          C.Tag.CREATE_QUEST_DISTANCE_FROM_VENUE,
          chipModifier.align(Alignment.TopEnd),
      )
    }
  }
}

/**
 * A pill in the map's corner with the clock icon: the walk time, or the distance from the venue.
 */
@Composable
private fun MapChip(text: String, tag: String, modifier: Modifier = Modifier) {
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
          text,
          style = AroundTextStyles.Badge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag(tag),
      )
    }
  }
}

private val BORDER: Dp = 1.dp
private val CHIP_ELEVATION: Dp = 2.dp
private val CLOCK_SIZE: Dp = 14.dp
