// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * Says the quests or their venues failed to load, so the map has no pins although there may be
 * quests around, with a "Try again" button, as in Figma (frame 4484:9165).
 *
 * @param onTryAgain Called when the explorer taps "Try again"; see [MapViewModel.onRetry].
 * @param retrying Whether a retry waits for its answer, so "Try again" can't be tapped yet.
 */
@Composable
fun VenuesErrorBanner(onTryAgain: () -> Unit, retrying: Boolean, modifier: Modifier = Modifier) {
  MapBanner(
      icon = R.drawable.ic_cloud_off,
      title = stringResource(R.string.map_venues_failed),
      caption = stringResource(R.string.map_venues_failed_detail),
      action = stringResource(R.string.map_venues_try_again),
      onAction = onTryAgain,
      actionTag = C.Tag.VENUES_TRY_AGAIN,
      modifier = modifier.testTag(C.Tag.VENUES_ERROR_BANNER),
      actionEnabled = !retrying,
      isError = true,
  )
}
