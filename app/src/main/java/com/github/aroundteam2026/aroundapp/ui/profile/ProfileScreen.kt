// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * Minimal Profile action until the full profile screen is integrated.
 *
 * @param onSignOut ends the current session through the Profile ViewModel.
 */
@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
  Column(
      Modifier.fillMaxSize().testTag(C.Tag.PROFILE_SCREEN).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineMedium)
    Button(onClick = onSignOut, modifier = Modifier.testTag(C.Tag.PROFILE_SIGN_OUT)) {
      Text(stringResource(R.string.auth_sign_out))
    }
  }
}
