// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileScreen
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestDestination
import com.github.aroundteam2026.aroundapp.ui.quest.create.navigateToCreateQuest
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing

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

/**
 * The venue's home, where a signed-in venue lands: the selected tab's screen above a bottom bar of
 * Dashboard, Quests and Profile (see [BottomTabScaffold]).
 *
 * The dashboard and the list of quests are "Coming soon" stubs until their own tasks build them.
 * The Quests tab's "New quest" button opens the create-quest form above the Quests tab; closing the
 * form, or saving its quest, returns to the tab.
 *
 * @param onSignOut signs the venue out, from the Profile tab.
 * @param createQuestFactory builds the create-quest form's ViewModel; tests pass one on fakes.
 * @param screen draws the screen of a tab; tests pass their own to check the navigation alone.
 */
@Composable
fun VenueHome(
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
    createQuestFactory: ViewModelProvider.Factory = CreateQuestViewModel.factory(),
    screen: @Composable (VenueTab) -> Unit = {
      VenueTabScreen(it, navController::navigateToCreateQuest, onSignOut)
    },
) {
  BottomTabScaffold(
      tabs = VenueTab.entries,
      startTab = VENUE_START_TAB,
      navController = navController,
      navBarTag = C.Tag.VENUE_NAV_BAR,
      screen = screen,
      modifier = Modifier.testTag(C.Tag.VENUE_HOME),
  ) {
    createQuestDestination(onClose = { navController.popBackStack() }, factory = createQuestFactory)
  }
}

/** The screen of [tab]. */
@Composable
private fun VenueTabScreen(tab: VenueTab, onCreateQuest: () -> Unit, onSignOut: () -> Unit) =
    when (tab) {
      // The tab keeps its own tag, whatever the dashboard becomes
      VenueTab.DASHBOARD ->
          Box(Modifier.fillMaxSize().testTag(tab.screenTag)) {
            ComingSoonScreen(R.string.dashboard_title, C.Tag.DASHBOARD_STUB)
          }
      VenueTab.QUESTS -> VenueQuestsTab(onCreateQuest, Modifier.testTag(tab.screenTag))
      VenueTab.PROFILE -> ProfileScreen(onSignOut)
    }

/**
 * The Quests tab: the venue's quests under a heading with a "New quest" button. Until the list of
 * quests is built, a "Coming soon" note stands in for it.
 *
 * @param onCreateQuest opens the form where the venue creates a quest.
 * @param modifier modifier applied to the tab's screen.
 */
@Composable
private fun VenueQuestsTab(onCreateQuest: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize().padding(AroundSpacing.ScreenHorizontal)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
          stringResource(R.string.venue_quests_title),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.weight(1f),
      )
      FilledIconButton(
          onClick = onCreateQuest,
          modifier = Modifier.testTag(C.Tag.VENUE_NEW_QUEST),
      ) {
        Icon(
            painterResource(R.drawable.ic_plus),
            contentDescription = stringResource(R.string.create_quest_heading),
        )
      }
    }
    Box(
        Modifier.fillMaxWidth().weight(1f).testTag(C.Tag.VENUE_QUESTS_STUB),
        contentAlignment = Alignment.Center,
    ) {
      Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
    }
  }
}
