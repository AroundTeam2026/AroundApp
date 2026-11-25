// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.notification.NotificationPromptStore
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundShapes
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

/**
 * After sign-in on Android 13 and later, shows the explanation before Android's notification
 * permission dialog, then [home] once it's answered. Shows [home] straight away when there is
 * nothing to ask: answered before, already allowed, or Android below 13.
 *
 * Home isn't composed before then, so its own permission requests, such as the map's location
 * dialog on its first resume, come after this one rather than over the explanation.
 *
 * @param role picks the explorer's or the venue's explanation.
 * @param store remembers the answer, so a refusal isn't asked about again.
 * @param model decides whether to explain; tests pass one built on a fake store.
 */
// POST_NOTIFICATIONS is only used on Android 13 and later: the ViewModel doesn't explain below that
@SuppressLint("InlinedApi")
@Composable
fun NotificationPermissionGate(
    role: Role,
    store: NotificationPromptStore,
    model: NotificationPermissionViewModel = defaultModel(store),
    home: @Composable () -> Unit,
) {
  val prompt by model.prompt.collectAsStateWithLifecycle()
  val dialog = rememberLauncherForActivityResult(RequestPermission()) { model.onDialogClosed() }
  when (prompt) {
    // The earlier answer is read in a moment; nothing until then, so home can't start early
    NotificationPrompt.CHECKING -> Unit
    NotificationPrompt.EXPLAINING,
    NotificationPrompt.ASKING -> {
      BackHandler(onBack = model::notNow)
      NotificationPermissionScreen(
          role,
          onAllow = {
            model.allow()
            dialog.launch(POST_NOTIFICATIONS)
          },
          onNotNow = model::notNow,
      )
    }
    NotificationPrompt.DONE -> home()
  }
}

@SuppressLint("InlinedApi")
@Composable
private fun defaultModel(store: NotificationPromptStore): NotificationPermissionViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel(
      key = "notification-prompt",
      factory =
          NotificationPermissionViewModel.factory(store, Build.VERSION.SDK_INT) {
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED
          },
  )
}

/** The explanation from the light and dark Sprint 7 frames (#631). */
@Composable
internal fun NotificationPermissionScreen(role: Role, onAllow: () -> Unit, onNotNow: () -> Unit) {
  val explorer = role == Role.EXPLORER
  Surface(
      Modifier.fillMaxSize().testTag(C.Tag.NOTIFICATION_PROMPT),
      color = MaterialTheme.colorScheme.background,
  ) {
    Column(
        Modifier.safeDrawingPadding()
            .padding(
                start = AroundSpacing.ScreenHorizontal,
                end = AroundSpacing.ScreenHorizontal,
                top = AroundSpacing.ScreenTop,
                bottom = AroundSpacing.ScreenBottom,
            )
    ) {
      Box(
          Modifier.size(BELL_TILE).background(AroundTheme.colors.lavender, AroundShapes.large),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painterResource(R.drawable.ic_notifications),
            null,
            Modifier.size(BELL_ICON),
            tint = AroundTheme.colors.accentText,
        )
      }
      Spacer(Modifier.height(AroundSpacing.SetupGap))
      Text(
          stringResource(
              if (explorer) R.string.notification_prompt_title_explorer
              else R.string.notification_prompt_title_venue
          ),
          style = MaterialTheme.typography.displayMedium,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(AroundSpacing.Medium))
      Text(
          stringResource(
              if (explorer) R.string.notification_prompt_body_explorer
              else R.string.notification_prompt_body_venue
          ),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(AroundSpacing.ExtraLarge))
      Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = CARD_ELEVATION),
          shape = AroundShapes.large,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
            Modifier.padding(AroundSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(AroundSpacing.InsetPadding),
        ) {
          val kinds =
              if (explorer) {
                listOf(
                    R.drawable.ic_event_available to R.string.notification_prompt_reservation,
                    R.drawable.ic_proof_photo to R.string.notification_prompt_proof,
                )
              } else {
                listOf(
                    R.drawable.ic_group_add to R.string.notification_prompt_booking,
                    R.drawable.ic_proof_photo to R.string.notification_prompt_proof_sent,
                    R.drawable.ic_close to R.string.notification_prompt_cancelled,
                )
              }
          kinds.forEach { (icon, text) -> KindRow(icon, text) }
        }
      }
      Spacer(Modifier.weight(1f))
      Text(
          stringResource(R.string.notification_prompt_footnote),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(AroundSpacing.InsetPadding))
      Button(
          onClick = onAllow,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .height(BUTTON_HEIGHT)
                  .testTag(C.Tag.NOTIFICATION_PROMPT_ALLOW),
      ) {
        Text(stringResource(R.string.notification_prompt_allow), style = AroundTextStyles.Button)
      }
      Spacer(Modifier.height(AroundSpacing.CompactGap))
      OutlinedButton(
          onClick = onNotNow,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(OUTLINE_WIDTH, MaterialTheme.colorScheme.primary),
          colors =
              ButtonDefaults.outlinedButtonColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = AroundTheme.colors.accentText,
              ),
          modifier =
              Modifier.fillMaxWidth()
                  .height(BUTTON_HEIGHT)
                  .testTag(C.Tag.NOTIFICATION_PROMPT_NOT_NOW),
      ) {
        Text(stringResource(R.string.notification_prompt_not_now), style = AroundTextStyles.Button)
      }
    }
  }
}

/** One kind of notification: its icon on a light teal tile, then what it's about. */
@Composable
private fun KindRow(@DrawableRes icon: Int, @StringRes text: Int) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.InsetPadding),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        Modifier.size(KIND_TILE).background(AroundTheme.colors.babyBlue, AroundCorners.InsetField),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(icon),
          null,
          Modifier.size(KIND_ICON),
          tint = MaterialTheme.colorScheme.onSurface,
      )
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private val BELL_TILE = 72.dp
private val BELL_ICON = 36.dp
private val KIND_TILE = 40.dp
private val KIND_ICON = 20.dp
private val CARD_ELEVATION = 6.dp
private val BUTTON_HEIGHT = 54.dp
private val OUTLINE_WIDTH = 1.5.dp

@Preview(name = "Explorer · Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Explorer · Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun NotificationPermissionExplorerPreview() {
  AroundAppTheme { NotificationPermissionScreen(Role.EXPLORER, onAllow = {}, onNotNow = {}) }
}

@Preview(name = "Venue · Light", widthDp = 413, heightDp = 917)
@Preview(
    name = "Venue · Dark",
    widthDp = 413,
    heightDp = 917,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun NotificationPermissionVenuePreview() {
  AroundAppTheme { NotificationPermissionScreen(Role.VENUE, onAllow = {}, onNotNow = {}) }
}
