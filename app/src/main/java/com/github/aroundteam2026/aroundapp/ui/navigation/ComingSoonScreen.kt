// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.aroundteam2026.aroundapp.R

/**
 * Stands in for a screen that is not built yet, so that its route always opens something and Back
 * leaves it like any other screen.
 *
 * @param title the screen's name, shown above "Coming soon".
 * @param testTag tag of the whole stub, so tests can tell which screen is shown.
 * @param modifier modifier applied to the stub.
 */
@Composable
fun ComingSoonScreen(@StringRes title: Int, testTag: String, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxSize().testTag(testTag),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
  }
}
