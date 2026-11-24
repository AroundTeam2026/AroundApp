// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileCard
import com.github.aroundteam2026.aroundapp.ui.profile.SignOutCard
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.initialsOf
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.venuepage.venueAvatarStyle

/**
 * The venue's Profile tab: the venue's avatar, name and address in a card, then "Sign out", in the
 * same cards as the explorer's profile. Sign-out stays reachable while the venue loads, can't be
 * read, or is missing.
 *
 * @param viewModel Holds the signed-in venue's profile.
 * @param onSignOut Ends the current session.
 * @param onEditDetails Opens the venue's details form, from "Edit details" once the venue loaded.
 */
@Composable
fun VenueProfileScreen(
    viewModel: VenueProfileViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onEditDetails: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VenueProfileContent(state, onSignOut, viewModel::retry, modifier, onEditDetails)
}

/** The Profile tab showing [state]; see [VenueProfileScreen]. */
@Composable
internal fun VenueProfileContent(
    state: VenueProfileUiState,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onEditDetails: () -> Unit = {},
) {
  Column(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          // So "Sign out" stays reachable on a small screen or with large fonts
          .verticalScroll(rememberScrollState())
          .padding(AroundSpacing.ScreenHorizontal),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Large),
  ) {
    Text(
        stringResource(R.string.tab_profile),
        style = AroundTextStyles.SetupTitle,
        color = MaterialTheme.colorScheme.onBackground,
    )
    when (state.status) {
      VenueProfileStatus.LOADING ->
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag(C.Tag.VENUE_PROFILE_LOADING))
          }
      VenueProfileStatus.FAILED -> LoadFailed(onRetry)
      VenueProfileStatus.MISSING ->
          Text(
              stringResource(R.string.venue_profile_missing),
              Modifier.fillMaxWidth().testTag(C.Tag.VENUE_PROFILE_MISSING),
              color = MaterialTheme.colorScheme.onBackground,
              textAlign = TextAlign.Center,
          )
      VenueProfileStatus.LOADED -> {
        VenueCard(state.name, state.address, state.picture)
        EditDetailsCard(onEditDetails)
      }
    }
    SignOutCard(onSignOut)
  }
}

/**
 * The venue's picture, or its initials without one, its name and address, as the explorer's profile
 * card shows theirs.
 */
@Composable
private fun VenueCard(name: String, address: String?, picture: ImageBitmap?) {
  ProfileCard {
    QuestAvatar(
        picture?.let(VenueAvatar::Picture) ?: VenueAvatar.Initials(initialsOf(name)),
        venueAvatarStyle(),
    )
    Column {
      Text(
          name,
          Modifier.testTag(C.Tag.VENUE_PROFILE_NAME),
          style = MaterialTheme.typography.titleLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          address ?: stringResource(R.string.venue_profile_no_address),
          Modifier.testTag(C.Tag.VENUE_PROFILE_ADDRESS),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * "Edit details", which opens the form for the venue's name and address, as the explorer's card.
 */
@Composable
private fun EditDetailsCard(onEditDetails: () -> Unit) {
  ProfileCard(
      Modifier.testTag(C.Tag.VENUE_PROFILE_EDIT)
          .clickable(role = Role.Button, onClick = onEditDetails)
  ) {
    Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(EDIT_ICON_SIZE))
    Column(Modifier.weight(1f)) {
      Text(
          stringResource(R.string.venue_profile_edit),
          style = MaterialTheme.typography.titleMedium,
      )
      Text(
          stringResource(R.string.venue_profile_edit_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(painterResource(R.drawable.ic_chevron_right), null)
  }
}

private val EDIT_ICON_SIZE = 20.dp

/** Says the venue couldn't be read, with "Try again". */
@Composable
private fun LoadFailed(onRetry: () -> Unit) {
  Column(
      Modifier.fillMaxWidth().testTag(C.Tag.VENUE_PROFILE_FAILED),
      verticalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        stringResource(R.string.venue_profile_failed),
        color = MaterialTheme.colorScheme.onBackground,
    )
    Button(onRetry, Modifier.testTag(C.Tag.VENUE_PROFILE_RETRY)) {
      Text(stringResource(R.string.dashboard_retry))
    }
  }
}

@Preview(name = "Light", widthDp = 413)
@Preview(name = "Dark", widthDp = 413, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun VenueProfilePreview() {
  AroundAppTheme {
    VenueProfileContent(
        VenueProfileUiState(VenueProfileStatus.LOADED, "Café Lumière", "Rue de Bourg 12, Lausanne"),
        onSignOut = {},
        onRetry = {},
    )
  }
}
