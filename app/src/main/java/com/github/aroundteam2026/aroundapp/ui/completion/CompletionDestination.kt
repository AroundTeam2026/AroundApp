// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

private const val TAG = "CompletionDestination"

/**
 * Registers the screen where the explorer completes a reservation's quest, which takes the
 * reservation's id as [Routes.RESERVATION_ID] and the venue's name as [Routes.VENUE_NAME].
 *
 * Its back arrow, system back and a sent proof all close it, back to the screen that opened it.
 * Each closes only this screen, and only once: a second call, such as the sent proof showing again
 * as the screen leaves, does nothing, so it never closes the screen below too.
 *
 * @param navController The explorer's NavHost controller, which the screen is closed through.
 * @param screen Draws the screen of the reservation with the given id and venue name, with the
 *   action that closes it.
 */
fun NavGraphBuilder.completionDestination(
    navController: NavController,
    screen: @Composable (reservationId: String, venueName: String, close: () -> Unit) -> Unit,
) {
  composable(
      Routes.COMPLETE_QUEST,
      arguments =
          listOf(
              navArgument(Routes.RESERVATION_ID) { type = NavType.StringType },
              navArgument(Routes.VENUE_NAME) {
                type = NavType.StringType
                defaultValue = ""
              },
          ),
  ) { entry ->
    // The route only matches a non-blank id, so the argument is always there
    val reservationId = requireNotNull(entry.arguments?.getString(Routes.RESERVATION_ID))
    val venueName = entry.arguments?.getString(Routes.VENUE_NAME).orEmpty()
    screen(reservationId, venueName) { navController.close(entry) }
  }
}

/** Pops [entry], unless it isn't the screen on top any more because it was closed already. */
private fun NavController.close(entry: NavBackStackEntry) {
  if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/**
 * The completion screen as the app shows it to the explorer [explorerUid], with the app's
 * repositories.
 *
 * @param close Closes the screen, on its back arrow, system back, or once the proof is sent.
 * @param factory Builds the screen's ViewModel; the app's unless a test needs fakes.
 */
@Composable
fun Completion(
    reservationId: String,
    venueName: String,
    explorerUid: String,
    close: () -> Unit,
    factory: ViewModelProvider.Factory = CompletionViewModel.factory(reservationId, explorerUid),
) =
    CompletionScreen(
        viewModel(factory = factory),
        venueName,
        onBack = close,
        onSubmitted = { close() },
    )

/**
 * Opens the screen where the explorer completes the quest of the reservation [reservationId] at
 * [venueName]. A tap that arrives once the screen is open, such as the second of a fast double tap,
 * opens nothing.
 *
 * A blank [reservationId] matches no route, so it opens nothing and is logged instead of crashing.
 */
fun NavController.navigateToCompletion(reservationId: String, venueName: String) {
  if (reservationId.isBlank()) {
    Log.w(TAG, "Not completing a quest: the reservation id is blank")
    return
  }
  if (currentDestination?.route == Routes.COMPLETE_QUEST) return
  navigate(Routes.completeQuest(reservationId, venueName))
}
