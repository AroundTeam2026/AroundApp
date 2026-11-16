// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.venueDashboardFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.quest.list.venueQuestsFactoryOnFakes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that both role shells show the app-wide banner when connectivity changes. */
@RunWith(AndroidJUnit4::class)
class OfflineBannerShellTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun explorerShellShowsAndHidesTheBanner() {
    val connectivity = FakeConnectivityRepository()
    compose.setContent {
      AroundApp(
          explorerUid = "explorer",
          onSignOut = {},
          screen = { Text(it.route) },
          connectivityRepository = connectivity,
      )
    }

    assertBannerFollows(connectivity)
  }

  @Test
  fun venueShellShowsAndHidesTheBanner() {
    val connectivity = FakeConnectivityRepository()
    compose.setContent {
      VenueHome(
          onCreateQuest = {},
          onSignOut = {},
          venueQuestsFactory = venueQuestsFactoryOnFakes(),
          dashboardFactory = venueDashboardFactoryOnFakes(),
          screen = { Text(it.route) },
          connectivityRepository = connectivity,
      )
    }

    assertBannerFollows(connectivity)
  }

  private fun assertBannerFollows(connectivity: FakeConnectivityRepository) {
    compose.onNodeWithTag(C.Tag.OFFLINE_BANNER).assertDoesNotExist()
    compose.runOnIdle { connectivity.online.value = false }
    compose.onNodeWithTag(C.Tag.OFFLINE_BANNER).assertIsDisplayed()
    compose.runOnIdle { connectivity.online.value = true }
    compose.onNodeWithTag(C.Tag.OFFLINE_BANNER).assertDoesNotExist()
  }
}
