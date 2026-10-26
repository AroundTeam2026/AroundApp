// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.venue.VenueAreaScreen

/**
 * Draws the area step of onboarding for the venue [venueId].
 *
 * @param venueId id of the venue whose area is set.
 * @param onSaved called once the area is saved.
 * @param onBack called when the venue goes back from the area.
 */
typealias VenueAreaStep =
    @Composable (venueId: String, onSaved: () -> Unit, onBack: () -> Unit) -> Unit

/**
 * Where a signed-in venue user enters the app: C2's `venueEntry` slot, which role routing fills
 * with the user's uid. It reads the user's venue, then opens [VenueEntryNavigation] at the first
 * onboarding step left (see [startFor]): the business name, the area, or the venue home.
 *
 * @param userId uid of the signed-in venue user, which is also its venue's id.
 * @param authRepository the session, used by the business-name step and the home's sign-out.
 * @param venueRepository where the venue is read and saved.
 * @param onSignOut signs the venue out when its venue can't be read.
 * @param viewModel chooses where the venue starts.
 * @param navController the venue's NavHost controller; tests pass their own to read it.
 * @param areaStep draws the area step; a [VenueAreaScreen] unless replaced, e.g. by tests.
 * @param createQuestFactory builds the create-quest form's ViewModel; tests pass one on fakes.
 */
@Composable
fun VenueEntry(
    userId: String,
    authRepository: AuthRepository,
    venueRepository: VenueRepository,
    onSignOut: () -> Unit,
    viewModel: VenueEntryViewModel =
        viewModel(key = userId, factory = VenueEntryViewModel.factory(userId, venueRepository)),
    navController: NavHostController = rememberNavController(),
    areaStep: VenueAreaStep = { venueId, onSaved, onBack ->
      VenueAreaScreen(venueId, onSaved, onBack)
    },
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
) {
  when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
    VenueEntryUiState.Loading ->
        Box(
            Modifier.fillMaxSize().testTag(C.Tag.VENUE_ENTRY_LOADING),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
    VenueEntryUiState.Failed -> Failed(viewModel::onRetry, onSignOut)
    is VenueEntryUiState.Ready -> {
      VenueEntryNavigation(
          authRepository,
          venueRepository,
          locationScreen = areaStep,
          createQuestFactory = createQuestFactory,
          start = state.start,
          venueId = userId,
          navController = navController,
      )
    }
  }
}

/** The venue couldn't be read: try again, or sign out. */
@Composable
private fun Failed(onRetry: () -> Unit, onSignOut: () -> Unit) {
  Column(
      Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).testTag(C.Tag.VENUE_ENTRY_FAILED),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.venue_entry_failed))
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.VENUE_ENTRY_RETRY)) {
      Text(stringResource(R.string.role_profile_retry))
    }
    TextButton(onClick = onSignOut, modifier = Modifier.testTag(C.Tag.VENUE_ENTRY_SIGN_OUT)) {
      Text(stringResource(R.string.auth_sign_out))
    }
  }
}
