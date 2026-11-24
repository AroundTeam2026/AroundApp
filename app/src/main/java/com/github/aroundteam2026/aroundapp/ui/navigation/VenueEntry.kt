// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.VenueDashboardViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.list.VenueQuestsViewModel
import com.github.aroundteam2026.aroundapp.ui.venue.VenueProfileViewModel

/**
 * Where a signed-in venue user enters the app: role routing's `venueScreen`, which it fills with
 * the user's uid. It reads the user's venue, then opens [VenueEntryNavigation] at the first
 * onboarding step left (see [startFor]): the business name, the area, or the venue home.
 *
 * @param userId uid of the signed-in venue user, which is also its venue's id.
 * @param authRepository the session, used by the business-name step and the home's sign-out.
 * @param venueRepository where the venue is read and saved.
 * @param onSignOut signs the venue out when its venue can't be read.
 * @param venuePictures stores the venue's picture.
 * @param viewModel chooses where the venue starts.
 * @param navController the venue's NavHost controller; tests pass their own to read it.
 * @param areaStep draws the area step; [defaultVenueAreaStep] unless replaced, e.g. by tests.
 * @param createQuestFactory builds the create-quest form's ViewModel; tests pass one on fakes.
 * @param venueQuestsFactory builds the Quests tab's ViewModel; tests pass one on fakes.
 * @param dashboardFactory builds the Dashboard tab's ViewModel; tests pass one on fakes.
 * @param venueProfileFactory builds the Profile tab's ViewModel; tests pass one on fakes.
 */
@Composable
fun VenueEntry(
    userId: String,
    authRepository: AuthRepository,
    venueRepository: VenueRepository,
    onSignOut: () -> Unit,
    venuePictures: UserPictureRepository,
    viewModel: VenueEntryViewModel =
        viewModel(key = userId, factory = VenueEntryViewModel.factory(userId, venueRepository)),
    navController: NavHostController = rememberNavController(),
    areaStep: VenueAreaStep = defaultVenueAreaStep,
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
    venueQuestsFactory: ViewModelProvider.Factory = VenueQuestsViewModel.factory(),
    dashboardFactory: ViewModelProvider.Factory = VenueDashboardViewModel.factory(),
    venueProfileFactory: ViewModelProvider.Factory = VenueProfileViewModel.factory(),
) {
  when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
    VenueEntryUiState.Loading ->
        Box(
            Modifier.fillMaxSize().testTag(C.Tag.VENUE_ENTRY_LOADING),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
    VenueEntryUiState.Failed ->
        RetryOrSignOut(
            stringResource(R.string.venue_entry_failed),
            viewModel::onRetry,
            onSignOut,
            Modifier.testTag(C.Tag.VENUE_ENTRY_FAILED),
        )
    is VenueEntryUiState.Ready -> {
      VenueEntryNavigation(
          authRepository,
          venueRepository,
          venuePictures,
          locationScreen = areaStep,
          createQuestFactory = createQuestFactory,
          venueQuestsFactory = venueQuestsFactory,
          dashboardFactory = dashboardFactory,
          venueProfileFactory = venueProfileFactory,
          start = state.start,
          navController = navController,
      )
    }
  }
}
