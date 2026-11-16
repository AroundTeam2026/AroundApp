// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.VenueDashboardScreen
import com.github.aroundteam2026.aroundapp.ui.quest.list.VenueQuestsScreen
import com.github.aroundteam2026.aroundapp.ui.venue.VenueProfileScreen
import com.github.aroundteam2026.aroundapp.ui.venue.VenueProfileViewModel

/** The venue's top-level destinations, one per tab of its bottom bar. */
enum class VenueTab(
    override val route: String,
    @param:StringRes override val label: Int,
    @param:DrawableRes override val icon: Int,
    override val tabTag: String,
    override val screenTag: String,
) : BottomTab {
  DASHBOARD(
      "venue_home_dashboard",
      R.string.dashboard_title,
      R.drawable.ic_dashboard,
      C.Tag.VENUE_DASHBOARD_TAB,
      C.Tag.VENUE_DASHBOARD_SCREEN,
  ),
  QUESTS(
      "venue_home_quests",
      R.string.tab_quests,
      R.drawable.ic_quests,
      C.Tag.VENUE_QUESTS_TAB,
      C.Tag.VENUE_QUESTS_SCREEN,
  ),
  PROFILE(
      "venue_home_profile",
      R.string.tab_profile,
      R.drawable.ic_profile,
      C.Tag.VENUE_PROFILE_TAB,
      C.Tag.PROFILE_SCREEN,
  ),
}

/** The tab the venue home opens on, the leftmost one, and that Back returns to. */
private val VENUE_START_TAB = VenueTab.DASHBOARD

/** The tabs whose screens place the offline banner themselves, where Figma draws it. */
private val VENUE_INLINE_BANNER_TABS = setOf(VenueTab.DASHBOARD)

/**
 * The venue's home, where a signed-in venue lands: the selected tab's screen above a bottom bar of
 * Dashboard, Quests and Profile (see [BottomTabScaffold]).
 *
 * The Dashboard tab shows the venue's reservations and the proofs to review (see
 * [VenueDashboardScreen]); tapping a proof calls [onOpenProof]. The Quests tab lists the venue's
 * quests (see [VenueQuestsScreen]); its "New quest" buttons call [onCreateQuest], so the venue's
 * NavHost opens the create-quest form full screen above the home (see [venueDestinations]). Tapping
 * a quest calls [onEditQuest], which opens the same form to edit it.
 *
 * @param onCreateQuest opens the form where the venue creates a quest.
 * @param onEditQuest opens the form editing the quest of an id.
 * @param onSignOut signs the venue out, from the Profile tab.
 * @param venueQuestsFactory builds the Quests tab's ViewModel; tests pass one on fakes.
 * @param dashboardFactory builds the Dashboard tab's ViewModel; tests pass one on fakes.
 * @param venueProfileFactory builds the Profile tab's ViewModel; tests pass one on fakes.
 * @param onEditDetails opens the venue's details form, from "Edit details" on the Profile tab.
 * @param onOpenProof opens the Review proof screen of a completion's id.
 * @param screen draws the screen of a tab; tests pass their own to check the navigation alone.
 * @param connectivityRepository tells the shell when the device is offline; tests pass a fake.
 */
@Composable
fun VenueHome(
    onCreateQuest: () -> Unit,
    onSignOut: () -> Unit,
    venueQuestsFactory: ViewModelProvider.Factory,
    dashboardFactory: ViewModelProvider.Factory,
    onOpenProof: (String) -> Unit = {},
    onEditQuest: (String) -> Unit = {},
    venueProfileFactory: ViewModelProvider.Factory = VenueProfileViewModel.factory(),
    onEditDetails: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    screen: @Composable (VenueTab) -> Unit = {
      VenueTabScreen(
          it,
          onCreateQuest,
          onSignOut,
          venueQuestsFactory,
          dashboardFactory,
          onOpenProof,
          onEditQuest,
          venueProfileFactory,
          onEditDetails,
      )
    },
    connectivityRepository: ConnectivityRepository? = null,
) {
  BottomTabScaffold(
      tabs = VenueTab.entries,
      startTab = VENUE_START_TAB,
      navController = navController,
      navBarTag = C.Tag.VENUE_NAV_BAR,
      screen = screen,
      modifier = Modifier.testTag(C.Tag.VENUE_HOME),
      connectivityRepository = connectivityRepository,
      inlineBannerTabs = VENUE_INLINE_BANNER_TABS,
  )
}

/** The screen of [tab]. */
@Composable
private fun VenueTabScreen(
    tab: VenueTab,
    onCreateQuest: () -> Unit,
    onSignOut: () -> Unit,
    venueQuestsFactory: ViewModelProvider.Factory,
    dashboardFactory: ViewModelProvider.Factory,
    onOpenProof: (String) -> Unit,
    onEditQuest: (String) -> Unit,
    venueProfileFactory: ViewModelProvider.Factory,
    onEditDetails: () -> Unit,
) =
    when (tab) {
      VenueTab.DASHBOARD ->
          VenueDashboardScreen(
              viewModel(factory = dashboardFactory),
              Modifier.testTag(tab.screenTag),
              onOpenProof = onOpenProof,
          )
      VenueTab.QUESTS ->
          VenueQuestsScreen(
              viewModel(factory = venueQuestsFactory),
              onCreateQuest,
              Modifier.testTag(tab.screenTag),
              onEditQuest = onEditQuest,
          )
      VenueTab.PROFILE ->
          VenueProfileScreen(
              viewModel(factory = venueProfileFactory),
              onSignOut,
              Modifier.testTag(tab.screenTag),
              onEditDetails,
          )
    }
