// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.OFFLINE_HINT_DELAY_MS
import com.github.aroundteam2026.aroundapp.ui.dashboard.venueDashboardFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewOnFakes
import com.github.aroundteam2026.aroundapp.ui.quest.list.venueQuestsFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.venuepage.VenuePageOnFakes
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

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

  // The venue's page draws the banner under its top bar, so the shell doesn't
  @Test
  fun explorerShellShowsOneBannerOnAVenuePageWhileOffline() {
    openVenuePage(questId = null)

    goOffline()

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertExists()
    banners().assertCountEquals(1)
  }

  // As in Figma, the quest's details show one card, which says cancelling needs a connection
  @Test
  fun explorerShellShowsOneCardOnQuestDetailsWhileOffline() {
    openVenuePage(questId = QUEST)

    goOffline()
    // The page's ViewModel waits that long before it treats the device as offline
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(OFFLINE_HINT_DELAY_MS))
    compose.waitForIdle()

    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SCREEN).assertExists()
    banners().assertCountEquals(0)
    compose.onAllNodesWithTag(C.Tag.OFFLINE_HINT).assertCountEquals(1)
    compose.onNodeWithText("Cancelling needs a connection.").assertExists()
  }

  /**
   * Shows the explorer's shell on the page, on fakes, of a venue whose quest [QUEST] the explorer
   * reserved, with that quest's details open unless [questId] is null.
   */
  private fun openVenuePage(questId: String?) {
    lateinit var navController: NavHostController
    compose.setContent {
      navController = rememberNavController()
      AroundApp(
          explorerUid = "explorer",
          onSignOut = {},
          navController = navController,
          venuePage = { venueId, arrivalQuestId, _, onBack, onSubmitProof ->
            VenuePageOnFakes(
                venueId,
                onBack,
                onSubmitProof,
                approvedQuestId = QUEST,
                arrivalQuestId = arrivalQuestId,
                connectivity = connectivity,
            )
          },
          overview = { onOpenQuest, onJoinParty -> OverviewOnFakes(onOpenQuest, onJoinParty) },
          connectivityRepository = connectivity,
      )
    }
    compose.runOnUiThread { navController.navigateToVenuePage("venue-1", questId) }
    compose.waitForIdle()
  }

  private fun banners() = compose.onAllNodesWithTag(C.Tag.OFFLINE_BANNER)

  private fun goOffline() = compose.runOnIdle { connectivity.online.value = false }

  private fun goOnline() = compose.runOnIdle { connectivity.online.value = true }

  private companion object {
    const val QUEST = "fox"
  }
}
