// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * [message], a button to try again and one to sign out, centred on the screen. Shown when what a
 * signed-in user needs couldn't be read, such as their profile or their venue; retrying keeps the
 * session.
 *
 * @param modifier Tags the screen, so each caller's tests can tell which one is showing.
 */
@Composable
internal fun RetryOrSignOut(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(message)
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.RETRY_OR_SIGN_OUT_RETRY)) {
      Text(stringResource(R.string.role_profile_retry))
    }
    TextButton(onClick = onSignOut, modifier = Modifier.testTag(C.Tag.RETRY_OR_SIGN_OUT_SIGN_OUT)) {
      Text(stringResource(R.string.auth_sign_out))
    }
  }
}
