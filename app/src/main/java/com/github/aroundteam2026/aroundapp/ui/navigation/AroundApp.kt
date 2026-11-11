// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.completion.Completion
import com.github.aroundteam2026.aroundapp.ui.completion.CompletionViewModel
import com.github.aroundteam2026.aroundapp.ui.completion.completionDestination
import com.github.aroundteam2026.aroundapp.ui.completion.navigateToCompletion
import com.github.aroundteam2026.aroundapp.ui.joinparty.JoinParty
import com.github.aroundteam2026.aroundapp.ui.joinparty.JoinPartyViewModel
import com.github.aroundteam2026.aroundapp.ui.joinparty.joinPartyDestination
import com.github.aroundteam2026.aroundapp.ui.joinparty.navigateToJoinParty
import com.github.aroundteam2026.aroundapp.ui.map.MapScreen
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewScreen
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileScreen
import com.github.aroundteam2026.aroundapp.ui.venuepage.VenuePage
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import com.github.aroundteam2026.aroundapp.ui.venuepage.venuePageDestination

/** The explorer's top-level destinations, one per tab of the bottom bar. */
enum class Tab(
    override val route: String,
    @param:StringRes override val label: Int,
    @param:DrawableRes override val icon: Int,
    override val tabTag: String,
    override val screenTag: String,
) : BottomTab {
  QUESTS(
      "quests",
      R.string.tab_quests,
      R.drawable.ic_quests,
      C.Tag.QUESTS_TAB,
      C.Tag.QUESTS_SCREEN,
  ),
  MAP("map", R.string.tab_map, R.drawable.ic_map, C.Tag.MAP_TAB, C.Tag.MAP_SCREEN),
  PROFILE(
      "profile",
      R.string.tab_profile,
      R.drawable.ic_profile,
      C.Tag.PROFILE_TAB,
      C.Tag.PROFILE_SCREEN,
  ),
}

/** The tab the app opens on, and that Back returns to before leaving the app. */
private val START_TAB = Tab.QUESTS

/**
 * The explorer's home: the selected tab's screen above a bottom navigation bar (see
 * [BottomTabScaffold]).
 *
 * @param onSignOut supplies the Profile sign-out action.
 * @param screen draws the screen of a tab; tests pass their own to check the navigation alone.
 * @param explorerUid the signed-in explorer, who accepts quests on a venue's page.
 * @param venuePage draws a venue's page, given its id, the quest whose details it opens at once if
 *   any, its back action and what its "Submit proof" button opens; tests pass their own so the page
 *   doesn't read Firestore.
 * @param overview draws the Quests tab's overview, given what opens a quest's details on its
 *   venue's page and what opens the Join a party screen; tests pass their own so the overview
 *   doesn't read Firestore.
 * @param joinParty draws the screen where the explorer joins a party by its code, given what closes
 *   it; tests pass their own so it doesn't read Firestore.
 * @param completion draws the screen where the explorer completes a reservation's quest, given the
 *   reservation's id, the venue's name and what closes it; tests pass their own so it doesn't read
 *   Firestore.
 */
@Composable
fun AroundApp(
    explorerUid: String,
    navController: NavHostController = rememberNavController(),
    onSignOut: () -> Unit,
    venuePage:
        @Composable
        (
            venueId: String,
            questId: String?,
            onBack: () -> Unit,
            onSubmitProof: (reservationId: String, venueName: String) -> Unit,
        ) -> Unit =
        { venueId, questId, onBack, onSubmitProof ->
          VenuePage(venueId, questId, explorerUid, onBack, onSubmitProof)
        },
    completion: @Composable (reservationId: String, venueName: String, close: () -> Unit) -> Unit =
        { reservationId, venueName, close ->
          Completion(venueName, close, CompletionViewModel.factory(reservationId, explorerUid))
        },
    joinParty: @Composable (close: () -> Unit) -> Unit = { close ->
      JoinParty(close, JoinPartyViewModel.factory(explorerUid))
    },
    overview:
        @Composable
        (
            onOpenQuest: (venueId: String, questId: String) -> Unit,
            onJoinParty: () -> Unit,
        ) -> Unit =
        { onOpenQuest, onJoinParty ->
          OverviewScreen(explorerUid, onOpenQuest, onJoinParty)
        },
    screen: @Composable (Tab) -> Unit = { TabScreen(it, navController, overview, onSignOut) },
) {
  BottomTabScaffold(
      tabs = Tab.entries,
      startTab = START_TAB,
      navController = navController,
      navBarTag = C.Tag.NAV_BAR,
      screen = screen,
      modifier = Modifier.testTag(C.Tag.APP),
  ) {
    // Explorer screens only: the venue's screens belong to the venue's own NavHost
    venuePageDestination(
        onBack = { navController.popBackStack() },
        onSubmitProof = navController::navigateToCompletion,
        page = venuePage,
    )
    completionDestination(navController, screen = completion)
    joinPartyDestination(navController, screen = joinParty)
  }
}

/**
 * Opens the page of the venue with [venueId], when its card on the map is tapped. The venue page
 * loads the venue's name itself, so [venueName] is not passed on.
 */
@Suppress("UNUSED_PARAMETER")
internal fun NavHostController.openVenueFromMap(venueId: String, venueName: String) =
    openVenue(venueId)

/**
 * Opens the page of the venue with [venueId] from a tab, with the details of its quest [questId]
 * open if given. A tap that arrives once a venue's page is open, such as the second of a fast
 * double tap, opens nothing.
 */
internal fun NavHostController.openVenue(venueId: String, questId: String? = null) {
  if (currentDestination?.route == Routes.VENUE_PAGE) return
  navigateToVenuePage(venueId, questId)
}

/**
 * The screen of [tab].
 *
 * @param navController opens a venue's page when its card on the map is tapped, a quest's details
 *   on it when its row in the overview is, and the Join a party screen from the overview.
 * @param overview draws the Quests tab's overview, given what opens a quest's details and the Join
 *   a party screen.
 * @param onSignOut signs the user out from the Profile tab.
 */
@Composable
private fun TabScreen(
    tab: Tab,
    navController: NavHostController,
    overview:
        @Composable
        (
            onOpenQuest: (venueId: String, questId: String) -> Unit,
            onJoinParty: () -> Unit,
        ) -> Unit,
    onSignOut: () -> Unit,
) =
    when (tab) {
      Tab.MAP -> MapScreen(onOpenVenue = navController::openVenueFromMap)
      // The tab keeps its own tag, whatever the overview becomes
      Tab.QUESTS ->
          Box(Modifier.fillMaxSize().testTag(tab.screenTag)) {
            overview(navController::openVenue, navController::navigateToJoinParty)
          }
      Tab.PROFILE -> ProfileScreen(onSignOut)
    }
