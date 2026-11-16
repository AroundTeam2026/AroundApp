// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.venueDashboardFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewOnFakes
import com.github.aroundteam2026.aroundapp.ui.quest.list.venueQuestsFactoryOnFakes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that both role shells show exactly one offline banner while the device is offline: the one
 * a tab places itself where Figma draws it, or else the shell's at the top. It hides again once the
 * device is back online.
 */
@RunWith(AndroidJUnit4::class)
class OfflineBannerShellTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val connectivity = FakeConnectivityRepository()

  @Test
  fun explorerShellShowsOneBannerOnEveryTabWhileOffline() {
    compose.setContent {
      AroundApp(
          explorerUid = "explorer",
          onSignOut = {},
          overview = { onOpenQuest, onJoinParty -> OverviewOnFakes(onOpenQuest, onJoinParty) },
          connectivityRepository = connectivity,
      )
    }

    banners().assertCountEquals(0)
    goOffline()
    // The Quests tab draws it under its title
    banners().assertCountEquals(1)
    compose.onNodeWithTag(C.Tag.OVERVIEW_TITLE).assertExists()
    // The Profile tab doesn't, so the shell does
    compose.onNodeWithTag(Tab.PROFILE.tabTag).performClick()
    banners().assertCountEquals(1)
    goOnline()
    banners().assertCountEquals(0)
  }

  @Test
  fun venueShellShowsOneBannerOnEveryTabWhileOffline() {
    compose.setContent {
      VenueHome(
          onCreateQuest = {},
          onSignOut = {},
          venueQuestsFactory = venueQuestsFactoryOnFakes(),
          dashboardFactory = venueDashboardFactoryOnFakes(),
          connectivityRepository = connectivity,
      )
    }

    banners().assertCountEquals(0)
    goOffline()
    // The dashboard draws it under its header
    banners().assertCountEquals(1)
    // The venue's Quests tab doesn't, so the shell does
    compose.onNodeWithTag(VenueTab.QUESTS.tabTag).performClick()
    banners().assertCountEquals(1)
    goOnline()
    banners().assertCountEquals(0)
  }

  private fun banners() = compose.onAllNodesWithTag(C.Tag.OFFLINE_BANNER)

  private fun goOffline() = compose.runOnIdle { connectivity.online.value = false }

  private fun goOnline() = compose.runOnIdle { connectivity.online.value = true }
}
