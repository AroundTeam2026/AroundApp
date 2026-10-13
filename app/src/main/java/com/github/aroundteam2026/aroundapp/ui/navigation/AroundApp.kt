// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.MapScreen
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewScreen
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileScreen
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import com.github.aroundteam2026.aroundapp.ui.venuepage.venuePageDestination

/** The app's top-level destinations, one per tab of the bottom bar. */
enum class Tab(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val tabTag: String,
    val screenTag: String,
) {
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
 * The app's root: the selected tab's screen above a bottom navigation bar.
 *
 * A screen opened from a tab, such as a venue's page, belongs to that tab: the tab stays selected
 * while it is shown, and reselecting the tab returns to the tab's own screen.
 *
 * @param onSignOut supplies the Profile sign-out action.
 * @param screen draws the screen of a tab; tests pass their own to check the navigation alone.
 */
@Composable
fun AroundApp(
    navController: NavHostController = rememberNavController(),
    onSignOut: () -> Unit,
    screen: @Composable (Tab) -> Unit = {
      TabScreen(it, navController::openVenueFromMap, onSignOut)
    },
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  // Recomputed whenever the shown screen changes, since it reads backStackEntry
  val currentTab = backStackEntry?.let { navController.tabOfCurrentScreen() }

  Scaffold(
      modifier = Modifier.testTag(C.Tag.APP),
      bottomBar = {
        NavigationBar(modifier = Modifier.testTag(C.Tag.NAV_BAR)) {
          Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == currentTab,
                onClick = { navController.navigateToTab(tab, currentTab) },
                // The label already names the tab, so the icon is decorative
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag(tab.tabTag),
            )
          }
        }
      },
  ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = START_TAB.route,
        modifier = Modifier.padding(innerPadding),
    ) {
      Tab.entries.forEach { tab -> composable(tab.route) { screen(tab) } }
      // Explorer screens only: the venue's screens belong to the venue's own NavHost
      venuePageDestination()
    }
  }
}

/**
 * Switches to [tab] the way bottom bars should: the back stack keeps at most the start tab below
 * the current one, so Back from any tab returns to Quests and then leaves the app, and each tab's
 * state, including screens opened from it, is saved when left and restored when reselected.
 *
 * Reselecting [currentTab], the tab in use, instead returns to its own screen, so a screen opened
 * from a tab can always be left through the bottom bar.
 */
private fun NavHostController.navigateToTab(tab: Tab, currentTab: Tab?) {
  if (tab == currentTab) {
    popBackStack(tab.route, inclusive = false)
    return
  }
  navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

/**
 * The tab the shown screen belongs to: the tab itself, or the tab it was opened from. Below the
 * shown screen, the back stack holds the start tab and at most one other tab (see [navigateToTab]),
 * so that other tab, if any, is the one in use.
 */
private fun NavHostController.tabOfCurrentScreen(): Tab? {
  fun inBackStack(tab: Tab) = runCatching { getBackStackEntry(tab.route) }.isSuccess
  return Tab.entries.firstOrNull { it != START_TAB && inBackStack(it) }
      ?: START_TAB.takeIf { inBackStack(it) }
}

/**
 * Opens the page of the venue with [venueId], when its card on the map is tapped. The venue page
 * loads the venue's name itself, so [venueName] is not passed on.
 */
@Suppress("UNUSED_PARAMETER")
internal fun NavHostController.openVenueFromMap(venueId: String, venueName: String) =
    navigateToVenuePage(venueId)

/**
 * The screen of [tab].
 *
 * @param onOpenVenue opens the page of the venue with this id and name, when its card on the map is
 *   tapped.
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
