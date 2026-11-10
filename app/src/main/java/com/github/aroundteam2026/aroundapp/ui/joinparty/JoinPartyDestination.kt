// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.joinparty

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

/**
 * Registers the Join a party screen, which opens from the Quests tab.
 *
 * Its back arrow, system back and a joined party all close it, back to the Quests tab. Each closes
 * only this screen, and only once: a second call, such as the joined party showing again as the
 * screen leaves, does nothing, so it never closes the screen below too.
 *
 * @param navController The explorer's NavHost controller, which the screen is closed through.
 * @param openQuest Opens a quest's details on its venue's page, given the venue and the quest, when
 *   the card of the party found is tapped.
 * @param screen Draws the screen, given the action that closes it and [openQuest].
 */
fun NavGraphBuilder.joinPartyDestination(
    navController: NavController,
    openQuest: (venueId: String, questId: String) -> Unit,
    screen:
        @Composable
        (close: () -> Unit, openQuest: (venueId: String, questId: String) -> Unit) -> Unit,
) {
  composable(Routes.JOIN_PARTY) { entry -> screen({ navController.close(entry) }, openQuest) }
}

/** Pops [entry], unless it isn't the screen on top any more because it was closed already. */
private fun NavController.close(entry: NavBackStackEntry) {
  if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/**
 * The Join a party screen.
 *
 * @param close Closes the screen, on its back arrow, system back, or once the party is joined.
 * @param openQuest Opens a quest's details on its venue's page.
 * @param factory Builds the screen's ViewModel: [JoinPartyViewModel.factory] in the app, fakes in
 *   tests.
 */
@Composable
fun JoinParty(
    close: () -> Unit,
    openQuest: (venueId: String, questId: String) -> Unit,
    factory: ViewModelProvider.Factory,
) =
    JoinPartyScreen(
        viewModel(factory = factory),
        onBack = close,
        onOpenQuest = openQuest,
        onJoined = close,
    )

/**
 * Opens the Join a party screen. A tap that arrives once the screen is open, such as the second of
 * a fast double tap, opens nothing.
 */
fun NavController.navigateToJoinParty() {
  if (currentDestination?.route == Routes.JOIN_PARTY) return
  navigate(Routes.JOIN_PARTY)
}
