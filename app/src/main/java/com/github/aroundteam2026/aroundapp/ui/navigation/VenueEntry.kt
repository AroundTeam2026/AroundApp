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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.dashboardDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest
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

/** Route of the venue home inside the venue's NavHost. */
internal const val VENUE_HOME_ROUTE = "venue_home"

/**
 * Where a signed-in venue user enters the app: C2's `venueEntry` slot, which role routing fills
 * with the user's uid. It reads the user's venue, then opens the venue's own NavHost at the first
 * onboarding step left (see [startFor]): the business name, the area, or the venue home. Saving the
 * area opens the venue home in place of onboarding.
 *
 * The venue's NavHost also registers the venue-only routes ([Routes.VENUE]), so an explorer, whose
 * NavHost is [AroundApp], can't reach them.
 *
 * @param userId uid of the signed-in venue user, which is also its venue's id.
 * @param authRepository the session, used by the business-name step.
 * @param venueRepository where the venue is read and saved.
 * @param onSignOut signs the venue out, from its home or when its venue can't be read.
 * @param viewModel chooses where the venue starts.
 * @param navController the venue's NavHost controller; tests pass their own to read it.
 * @param areaStep draws the area step; a [VenueAreaScreen] unless replaced, e.g. by tests.
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
    is VenueEntryUiState.Ready ->
        VenueNavHost(
            userId,
            state.start,
            authRepository,
            venueRepository,
            onSignOut,
            navController,
            areaStep,
        )
  }
}

/**
 * The venue's NavHost, opened at [start]. Onboarding sits under a venue resuming on the area, so
 * Back from the area returns to its business name, as for a venue that just gave it.
 */
@Composable
private fun VenueNavHost(
    userId: String,
    start: VenueEntryStart,
    authRepository: AuthRepository,
    venueRepository: VenueRepository,
    onSignOut: () -> Unit,
    navController: NavHostController,
    areaStep: VenueAreaStep,
) {
  NavHost(
      navController,
      startDestination =
          if (start == VenueEntryStart.HOME) VENUE_HOME_ROUTE
          else VenueOnboardingRoutes.INITIALIZATION,
  ) {
    venueOnboardingDestinations(navController, authRepository, venueRepository) { venueId, onBack ->
      areaStep(venueId, { navController.openVenueHome() }, onBack)
    }
    composable(VENUE_HOME_ROUTE) {
      VenueHome(onCreateQuest = navController::navigateToCreateQuest, onSignOut = onSignOut)
    }
    createQuestDestination()
    dashboardDestination()
  }
  // Once only, and not again after recreation: the back stack is saved with the area on top
  var resumed by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (start == VenueEntryStart.AREA && !resumed) {
      resumed = true
      navController.navigate(VenueOnboardingRoutes.location(userId)) { launchSingleTop = true }
    }
  }
}

/** Opens the venue home, clearing onboarding so Back can't return to it. */
private fun NavHostController.openVenueHome() =
    navigate(VENUE_HOME_ROUTE) {
      popUpTo(graph.id) { inclusive = true }
      launchSingleTop = true
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
