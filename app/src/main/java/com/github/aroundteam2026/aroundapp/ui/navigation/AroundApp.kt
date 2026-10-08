// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

/** The app's top-level destinations, one per tab of the bottom bar, in the bar's order. */
enum class Tab(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val tabTag: String,
    val screenTag: String,
) {
  EXPLORE(
      "explore",
      R.string.tab_explore,
      R.drawable.ic_tab_explore,
      C.Tag.EXPLORE_TAB,
      C.Tag.MAP_SCREEN,
  ),
  QUESTS(
      "quests",
      R.string.tab_quests,
      R.drawable.ic_tab_quests,
      C.Tag.QUESTS_TAB,
      C.Tag.QUESTS_SCREEN,
  ),
  FRIENDS(
      "friends",
      R.string.tab_friends,
      R.drawable.ic_tab_friends,
      C.Tag.FRIENDS_TAB,
      C.Tag.FRIENDS_SCREEN,
  ),
  PROFILE(
      "profile",
      R.string.tab_profile,
      R.drawable.ic_tab_profile,
      C.Tag.PROFILE_TAB,
      C.Tag.PROFILE_SCREEN,
  ),
}

/**
 * The app's root: the selected tab's screen above the [AroundBottomBar]. It opens on Explore.
 *
 * @param screen draws the screen of a tab; tests pass their own to check the navigation alone.
 */
@Composable
fun AroundApp(
    navController: NavHostController = rememberNavController(),
    screen: @Composable (Tab) -> Unit = { TabScreen(it) },
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  Scaffold(
      modifier = Modifier.testTag(C.Tag.APP),
      bottomBar = {
        AroundBottomBar(
            selectedTab = Tab.entries.find { it.route == currentRoute },
            onTabClick = { navController.navigateToTab(it) },
        )
      },
  ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = Tab.EXPLORE.route,
        modifier = Modifier.padding(innerPadding),
    ) {
      Tab.entries.forEach { tab -> composable(tab.route) { screen(tab) } }
    }
  }
}

/**
 * Switches to [tab] the way bottom bars should: the back stack keeps at most the start tab below
 * the current one, so Back from any tab returns to Explore and then leaves the app, and each tab's
 * state is saved when left and restored when reselected.
 */
private fun NavHostController.navigateToTab(tab: Tab) =
    navigate(tab.route) {
      popUpTo(graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }

@Composable
private fun TabScreen(tab: Tab) =
    when (tab) {
      Tab.EXPLORE -> MapScreen()
      else -> PlaceholderScreen(tab)
    }

// TODO (Issue #20): a stand-in for each tab, until it gets its real screen in its own task
@Composable
private fun PlaceholderScreen(tab: Tab) {
  Box(Modifier.fillMaxSize().testTag(tab.screenTag), contentAlignment = Alignment.Center) {
    Text(stringResource(tab.label), style = MaterialTheme.typography.headlineMedium)
  }
}
