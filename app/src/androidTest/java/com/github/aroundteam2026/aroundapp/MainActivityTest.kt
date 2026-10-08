// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.model.AppContainer
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.screen.MainScreen
import com.github.aroundteam2026.aroundapp.ui.map.awaitGoogleMap
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Launches the app on a device and walks through its tabs. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  // Inject before the activity launches; navigation tests never depend on a real auth session.
  @get:Rule(order = 0)
  val session =
      object : ExternalResource() {
        private lateinit var original: AppContainer
        private val app
          get() =
              InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                  as AroundApplication

        override fun before() {
          original = app.container
          app.container = AppContainer { FakeAuthRepository("device-test-user") }
        }

        override fun after() {
          app.container = original
        }
      }

  // Otherwise the Map tab's permission dialog covers the app
  @get:Rule(order = 1)
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  @get:Rule(order = 2) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun opensOnQuestsAndSwitchesTabs() = run {
    step("Start on the Quests tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        navBar { assertIsDisplayed() }
        questsTab { assertIsSelected() }
        questsScreen { assertIsDisplayed() }
      }
    }
    step("Open the Map tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        mapTab { performClick() }
        mapTab { assertIsSelected() }
        mapScreen { assertIsDisplayed() }
      }
    }
    step("Open the Profile tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        profileTab { performClick() }
        profileTab { assertIsSelected() }
        profileScreen { assertIsDisplayed() }
      }
    }
  }

  @Test
  fun activityRecreationKeepsTheSession() {
    ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
      profileTab { performClick() }
      profileScreen { assertIsDisplayed() }
    }
    composeTestRule.activityRule.scenario.recreate()
    ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
      navBar { assertIsDisplayed() }
      profileTab { assertIsSelected() }
      profileScreen { assertIsDisplayed() }
    }
  }

  @Test
  fun theMapTabRunsAGoogleMap() = run {
    step("Open the Map tab") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) {
        mapTab { performClick() }
        map { assertIsDisplayed() }
      }
    }
    step("The Maps SDK hands the map over, so it is set up in this build") {
      // Fails if the Maps SDK never provides the map
      composeTestRule.awaitGoogleMap()
    }
  }
}
