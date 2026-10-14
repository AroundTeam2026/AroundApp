// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing

/**
 * The nearby quests' filters: the options for the distance and for the reward, the chosen one
 * selected. Without [locationOn] there is no distance to filter by, so its options can't be chosen.
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
      modifier.testTag(C.Tag.NEARBY_FILTERS).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
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

/** One filter: its [title] over its [options], [selected] selected. */
@Composable
private fun <T> FilterRow(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
  Text(stringResource(title), style = MaterialTheme.typography.labelMedium)
  Row(
      Modifier.selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    options.forEach { option ->
      FilterChip(
          selected = option == selected,
          onClick = { onSelect(option) },
          label = { Text(stringResource(label(option))) },
          enabled = enabled,
      )
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
