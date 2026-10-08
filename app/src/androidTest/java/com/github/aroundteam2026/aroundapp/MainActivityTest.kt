// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.screen.MainScreen
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the app on a device and walks through its tabs. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  // Otherwise the map's permission dialog covers the app, which opens on it
  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun opensOnExploreAndSwitchesTabs() = run {
    step("Start on the Explore tab, which shows the map") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        navBar { assertIsDisplayed() }
        exploreTab { assertIsSelected() }
        mapScreen { assertIsDisplayed() }
      }
    }
    step("Open the Quests tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        questsTab { performClick() }
        questsTab { assertIsSelected() }
        questsScreen { assertIsDisplayed() }
      }
    }
    step("Open the Friends tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        friendsTab { performClick() }
        friendsTab { assertIsSelected() }
        friendsScreen { assertIsDisplayed() }
      }
    }
    step("Open the Profile tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        profileTab { performClick() }
        profileTab { assertIsSelected() }
        profileScreen { assertIsDisplayed() }
      }
    }
    step("Go back to Explore") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        exploreTab { performClick() }
        exploreTab { assertIsSelected() }
        mapScreen { assertIsDisplayed() }
      }
    }
  }

  @Test
  fun theExploreTabRunsAGoogleMap() = run {
    step("The app opens on the map") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) { map { assertIsDisplayed() } }
    }
    step("The Maps SDK hands the map over, so it is set up in this build") {
      // Fails if the Maps SDK never provides the map
      composeTestRule.awaitGoogleMap()
    }
  }
}
