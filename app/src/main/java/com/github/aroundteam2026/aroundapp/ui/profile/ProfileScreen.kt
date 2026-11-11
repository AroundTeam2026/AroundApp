// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundShapes
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import java.text.SimpleDateFormat
import java.util.Date

private val CARD_ELEVATION = 6.dp
private val CARD_PADDING = 18.dp
private val ROW_ICON_SIZE = 20.dp

/**
 * The Profile tab. With a [model] and [pictures], the Explorer's profile: their picture, name and
 * join date, an action that opens the edit form, and sign-out. Without them, the sign-out-only
 * placeholder venues still use.
 *
 * @param onSignOut ends the current session.
 * @param model the Explorer's profile, shared with the setup gate so a save shows here at once.
 * @param pictures loads the saved picture and uploads a new one.
 */
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    model: ProfileSetupViewModel? = null,
    pictures: UserPictureRepository? = null,
) {
  if (model == null || pictures == null) {
    SignOutOnlyProfile(onSignOut)
  } else {
    ExplorerProfile(onSignOut, model, pictures)
  }
}

@Composable
private fun SignOutOnlyProfile(onSignOut: () -> Unit) {
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

@Composable
private fun ExplorerProfile(
    onSignOut: () -> Unit,
    model: ProfileSetupViewModel,
    pictures: UserPictureRepository,
) {
  val user by model.profile.collectAsStateWithLifecycle()
  val editing by model.editing.collectAsStateWithLifecycle()
  val locale = LocalConfiguration.current.locales[0]
  Column(
      Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag(C.Tag.PROFILE_SCREEN)
          .padding(AroundSpacing.ScreenHorizontal),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Text(
        stringResource(R.string.tab_profile),
        style = AroundTextStyles.SetupTitle,
        color = MaterialTheme.colorScheme.onBackground,
    )
    ProfileCard {
      ProfileAvatar(user?.pictureUrl, null, pictures)
      Column {
        Text(user?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge)
        // The month and year in the order and form this locale writes them
        val since =
            user?.createdAt?.let {
              SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
                  .format(Date(it))
            }
        if (since != null) {
          Text(
              stringResource(R.string.profile_explorer_since, since),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
    ProfileCard(
        Modifier.testTag(C.Tag.PROFILE_EDIT_ACTION)
            .clickable(role = Role.Button, onClick = model::startEditing)
    ) {
      Icon(painterResource(R.drawable.ic_profile), null, Modifier.size(ROW_ICON_SIZE))
      Column(Modifier.weight(1f)) {
        Text(
            stringResource(R.string.profile_edit_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.profile_edit_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(painterResource(R.drawable.ic_chevron_right), null)
    }
    ProfileCard(
        Modifier.testTag(C.Tag.PROFILE_SIGN_OUT).clickable(role = Role.Button, onClick = onSignOut)
    ) {
      Icon(
          painterResource(R.drawable.ic_profile_logout),
          null,
          Modifier.size(ROW_ICON_SIZE),
          tint = MaterialTheme.colorScheme.error,
      )
      Text(stringResource(R.string.auth_sign_out), color = MaterialTheme.colorScheme.error)
    }
  }
  if (editing) {
    Dialog(
        onDismissRequest = model::cancelEditing,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ProfileForm(model, pictures, editing = true, onBack = model::cancelEditing)
      }
    }
  }
}

/** A full-width surface card holding one row, as the profile cards in Figma. */
@Composable
private fun ProfileCard(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = CARD_ELEVATION),
      shape = AroundShapes.large,
      modifier = modifier.fillMaxWidth(),
  ) {
    Row(
        Modifier.fillMaxWidth().padding(CARD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
  }
}
