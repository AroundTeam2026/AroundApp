// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing

/**
 * Whether the device is offline, for a tab screen that places the offline banner itself, as Figma
 * draws it on some tabs. [BottomTabScaffold] provides it to the tabs' own screens.
 */
val LocalShowOfflineBanner = compositionLocalOf { false }

/** A tab of a bottom navigation bar. */
interface BottomTab {
  /** Route of the tab's own screen. */
  val route: String

  /** Name shown under the tab's icon. */
  @get:StringRes val label: Int

  /** Icon of the tab. */
  @get:DrawableRes val icon: Int

  /** Test tag of the tab in the bar. */
  val tabTag: String

  /** Test tag of the tab's own screen. */
  val screenTag: String
}

/**
 * The selected tab's screen above a bottom navigation bar of [tabs], shared by the explorer's and
 * the venue's homes.
 *
 * A screen opened from a tab, such as a venue's page, belongs to that tab: the tab stays selected
 * while it is shown, and reselecting the tab returns to the tab's own screen.
 *
 * @param tabs the tabs, in the order of the bar.
 * @param startTab the tab the home opens on, and that Back returns to before leaving the app.
 * @param navBarTag test tag of the bar.
 * @param screen draws the screen of a tab. Tabs in [inlineBannerTabs] read [LocalShowOfflineBanner]
 *   to place the offline banner themselves.
 * @param modifier modifier applied to the whole home.
 * @param connectivityRepository reports when the device goes offline; tests provide a fake.
 * @param inlineBannerTabs tabs whose own screens draw the banner, where Figma places it; on any
 *   other screen the scaffold draws it at the top.
 * @param destinations registers the screens the tabs open, besides the tabs' own screens.
 */
@Composable
internal fun <T : BottomTab> BottomTabScaffold(
    tabs: List<T>,
    startTab: T,
    navController: NavHostController,
    navBarTag: String,
    screen: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    connectivityRepository: ConnectivityRepository? = null,
    inlineBannerTabs: Set<T> = emptySet(),
    destinations: NavGraphBuilder.() -> Unit = {},
) {
  val offline: OfflineBannerViewModel =
      viewModel(
          factory = OfflineBannerViewModel.factory(LocalContext.current, connectivityRepository)
      )
  val showBanner by offline.showBanner.collectAsStateWithLifecycle()
  val backStackEntry by navController.currentBackStackEntryAsState()
  // Recomputed whenever the shown screen changes, since it reads backStackEntry
  val currentTab = backStackEntry?.let { navController.tabOfCurrentScreen(tabs, startTab) }

  Scaffold(
      modifier = modifier,
      topBar = {
        val drawsInline =
            currentTab in inlineBannerTabs &&
                backStackEntry?.destination?.route == currentTab?.route
        if (showBanner && !drawsInline) {
          OfflineBanner(
              // The top bar gets no window insets, so the banner clears the status bar itself
              Modifier.fillMaxWidth()
                  .windowInsetsPadding(WindowInsets.statusBars)
                  .padding(
                      horizontal = AroundSpacing.ScreenHorizontal,
                      vertical = AroundSpacing.Medium,
                  )
          )
        }
      },
      bottomBar = {
        NavigationBar(modifier = Modifier.testTag(navBarTag)) {
          tabs.forEach { tab ->
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
        startDestination = startTab.route,
        // Screens that read the insets themselves must not add the bars' room a second time
        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
    ) {
      tabs.forEach { tab ->
        composable(tab.route) {
          CompositionLocalProvider(LocalShowOfflineBanner provides showBanner) { screen(tab) }
        }
      }
      destinations()
    }
  }
}

/**
 * Switches to [tab] the way bottom bars should: the back stack keeps at most the start tab below
 * the current one, so Back from any tab returns to the start tab and then leaves the app, and each
 * tab's state, including screens opened from it, is saved when left and restored when reselected.
 *
 * Reselecting [currentTab], the tab in use, instead returns to its own screen, so a screen opened
 * from a tab can always be left through the bottom bar.
 */
private fun NavHostController.navigateToTab(tab: BottomTab, currentTab: BottomTab?) {
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
private fun <T : BottomTab> NavHostController.tabOfCurrentScreen(tabs: List<T>, startTab: T): T? {
  fun inBackStack(tab: T) = runCatching { getBackStackEntry(tab.route) }.isSuccess
  return tabs.firstOrNull { it != startTab && inBackStack(it) }
      ?: startTab.takeIf { inBackStack(it) }
}
