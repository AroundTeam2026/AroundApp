// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles

// The panel's own geometry, from Figma (frame 2016:1872); its gaps come from AroundSpacing
private val LABEL_WIDTH = 58.dp
private val OPTIONS_PADDING = 3.dp
private val OPTION_PADDING = 6.dp
private val OUTLINE_WIDTH = 1.dp

/** Disabled options fade like Material's disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The nearby quests' filters, as in Figma: a row of options for the distance and one for the
 * reward, the chosen one filled in purple. Without [locationOn] there is no distance to filter by,
 * so its options can't be chosen.
 *
 * @param filters The filters chosen now.
 * @param onFiltersChange Called with the filters after the explorer picks an option.
 */
@Composable
fun NearbyFilterPanel(
    filters: NearbyFilters,
    locationOn: Boolean,
    onFiltersChange: (NearbyFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier
          .testTag(C.Tag.NEARBY_FILTERS)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
          .padding(AroundSpacing.Medium),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
  ) {
    FilterRow(
        R.string.map_nearby_filter_distance,
        options = DistanceFilter.entries,
        selected = filters.distance,
        label = ::distanceLabel,
        enabled = locationOn,
        onSelect = { onFiltersChange(filters.copy(distance = it)) },
    )
    FilterRow(
        R.string.map_nearby_filter_reward,
        options = RewardFilter.entries,
        selected = filters.reward,
        label = ::rewardLabel,
        enabled = true,
        onSelect = { onFiltersChange(filters.copy(reward = it)) },
    )
  }
}

/** One filter: its [title], then its [options] side by side, [selected] filled in purple. */
@Composable
private fun <T> FilterRow(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        stringResource(title),
        Modifier.width(LABEL_WIDTH),
        color = colors.onSurfaceVariant,
        style = AroundTextStyles.Badge,
    )
    Row(
        Modifier.weight(1f)
            .border(OUTLINE_WIDTH, colors.outline, AroundCorners.InsetField)
            .padding(OPTIONS_PADDING)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.ExtraSmall),
    ) {
      options.forEach { option ->
        val isSelected = option == selected
        val content = if (isSelected) colors.onPrimary else colors.onSurface
        Surface(
            selected = isSelected,
            onClick = { onSelect(option) },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            enabled = enabled,
            shape = AroundCorners.SegmentedOption,
            color = if (isSelected) colors.primary else Color.Transparent,
        ) {
          Box(Modifier.padding(vertical = OPTION_PADDING), Alignment.Center) {
            Text(
                stringResource(label(option)),
                color = if (enabled) content else content.copy(alpha = DISABLED_ALPHA),
                style = AroundTextStyles.Badge,
            )
          }
        }
      }
    }
  }
}

private fun distanceLabel(distance: DistanceFilter) =
    when (distance) {
      DistanceFilter.ANY -> R.string.map_nearby_filter_any
      DistanceFilter.WITHIN_500_M -> R.string.map_nearby_filter_500_m
      DistanceFilter.WITHIN_1_KM -> R.string.map_nearby_filter_1_km
      DistanceFilter.WITHIN_3_KM -> R.string.map_nearby_filter_3_km
    }

private fun rewardLabel(reward: RewardFilter) =
    when (reward) {
      RewardFilter.ANY -> R.string.map_nearby_filter_any
      RewardFilter.DISCOUNT -> R.string.map_nearby_filter_discount
      RewardFilter.OTHER -> R.string.map_nearby_filter_other
    }

/**
 * The chosen filters in words, for the pager's counter, e.g. "within 3 km · discounts", or null
 * when none is set.
 */
@Composable
internal fun filterSummary(filters: NearbyFilters): String? {
  val distance =
      when (filters.distance) {
        DistanceFilter.ANY -> null
        DistanceFilter.WITHIN_500_M -> stringResource(R.string.map_nearby_within_500_m)
        DistanceFilter.WITHIN_1_KM -> stringResource(R.string.map_nearby_within_1_km)
        DistanceFilter.WITHIN_3_KM -> stringResource(R.string.map_nearby_within_3_km)
      }
  val reward =
      when (filters.reward) {
        RewardFilter.ANY -> null
        RewardFilter.DISCOUNT -> stringResource(R.string.map_nearby_discounts)
        RewardFilter.OTHER -> stringResource(R.string.map_nearby_other_rewards)
      }
  return when {
    distance != null && reward != null ->
        stringResource(R.string.map_nearby_filter_pair, distance, reward)
    else -> distance ?: reward
  }
}
