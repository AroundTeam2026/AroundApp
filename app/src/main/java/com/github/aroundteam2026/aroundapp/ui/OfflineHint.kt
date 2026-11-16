// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/** Explains that an unavailable action needs a connection. */
@Composable
fun OfflineHint(modifier: Modifier = Modifier) {
  OfflineHint(message = R.string.offline_needs_connection, modifier = modifier)
}

/**
 * Shows the offline hint with copy for the action that needs a connection.
 *
 * @param tag The banner's test tag, so a screen's hint and the app-wide `OfflineBanner` can be told
 *   apart when both show.
 */
@Composable
internal fun OfflineHint(
    @StringRes message: Int,
    modifier: Modifier = Modifier,
    tag: String = C.Tag.OFFLINE_HINT,
) {
  StatusBanner(
      icon = R.drawable.ic_offline_hint,
      title = stringResource(R.string.offline_hint_title),
      caption = stringResource(message),
      modifier = modifier.testTag(tag),
      iconTint = AroundTheme.colors.accentText,
  )
}
