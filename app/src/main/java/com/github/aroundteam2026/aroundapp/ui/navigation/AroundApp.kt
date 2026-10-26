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
 * @param venuePage draws a venue's page, given its id and back action; tests pass their own so the
 *   page doesn't read Firestore.
 */
@Composable
fun AroundApp(
    explorerUid: String,
    navController: NavHostController = rememberNavController(),
    onSignOut: () -> Unit,
    screen: @Composable (Tab) -> Unit = {
      TabScreen(it, navController::openVenueFromMap, onSignOut)
    },
    venuePage: @Composable (venueId: String, onBack: () -> Unit) -> Unit = { venueId, onBack ->
      VenuePage(venueId, explorerUid, onBack)
    },
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
    venuePageDestination(onBack = { navController.popBackStack() }, page = venuePage)
  }
}

/**
 * Opens the page of the venue with [venueId], when its card on the map is tapped. A tap that
 * arrives once a venue's page is open, such as the second of a fast double tap, opens nothing. The
 * venue page loads the venue's name itself, so [venueName] is not passed on.
 */
@Suppress("UNUSED_PARAMETER")
internal fun NavHostController.openVenueFromMap(venueId: String, venueName: String) {
  if (currentDestination?.route == Routes.VENUE_PAGE) return
  navigateToVenuePage(venueId)
}

/**
 * The screen of [tab].
 *
 * @param onOpenVenue opens the page of the venue with this id and name, when its card on the map or
 *   its quest's "View quest" is tapped.
 * @param onSignOut signs the user out from the Profile tab.
 */
@Composable
private fun TabScreen(
    tab: Tab,
    onOpenVenue: (venueId: String, venueName: String) -> Unit,
    onSignOut: () -> Unit,
) =
    when (tab) {
      Tab.MAP -> MapScreen(onOpenVenue = onOpenVenue)
      // The tab keeps its own tag, whatever the overview becomes
      Tab.QUESTS -> Box(Modifier.fillMaxSize().testTag(tab.screenTag)) { OverviewScreen() }
      Tab.PROFILE -> ProfileScreen(onSignOut)
    }
