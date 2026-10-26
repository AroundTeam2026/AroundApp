// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

private val WordmarkWidth = 227.dp
private val SignInBorderWidth = 1.5.dp
private const val SIGN_IN_FILL_ALPHA = 0.15f
private const val SIGN_IN_BORDER_ALPHA = 0.35f

/**
 * The signed-out entry: the wordmark, the tagline and the two ways in.
 *
 * @param onCreateAccount opens the Create account form.
 * @param onSignIn opens the Sign in form.
 */
@Composable
internal fun WelcomeContent(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
  val primary = MaterialTheme.colorScheme.primary
  BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag(C.Tag.AUTH_WELCOME)) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(
                    start = AroundSpacing.ScreenHorizontal,
                    end = AroundSpacing.ScreenHorizontal,
                    top = AroundSpacing.HeroTop,
                    bottom = AroundSpacing.ExtraLarge,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.weight(1f))
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
      ) {
        Image(
            painterResource(R.drawable.around_wordmark),
            contentDescription = stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(AroundTheme.colors.accentText),
            modifier = Modifier.width(WordmarkWidth),
        )
        Text(
            stringResource(R.string.auth_tagline),
            style = AroundTextStyles.Tagline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
      }
      Spacer(Modifier.weight(1f))
      Button(
          onClick = onCreateAccount,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .height(AuthButtonHeight)
                  .testTag(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT),
      ) {
        Text(stringResource(R.string.auth_create_account), style = AroundTextStyles.Button)
      }
      Spacer(Modifier.height(AroundSpacing.Medium))
      OutlinedButton(
          onClick = onSignIn,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(SignInBorderWidth, primary.copy(alpha = SIGN_IN_BORDER_ALPHA)),
          colors =
              ButtonDefaults.outlinedButtonColors(
                  containerColor = primary.copy(alpha = SIGN_IN_FILL_ALPHA),
                  contentColor = AroundTheme.colors.accentText,
              ),
          modifier =
              Modifier.fillMaxWidth().height(AuthButtonHeight).testTag(C.Tag.AUTH_WELCOME_SIGN_IN),
      ) {
        Text(stringResource(R.string.auth_sign_in), style = AroundTextStyles.Button)
      }
    }
  }
}

@Preview(widthDp = 413, heightDp = 917)
@Composable
private fun WelcomeContentPreview() {
  AroundAppTheme { WelcomeContent(onCreateAccount = {}, onSignIn = {}) }
}
