// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VenueEntryNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val auth = FakeAuthRepository("venue-owner")
  private val venues = FakeVenueRepository()
  private var exits = 0

  private fun launch() {
    compose.activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            exits++
          }
        }
    )
    compose.setContent {
      AroundAppTheme {
        VenueEntryNavigation(
            auth,
            venues,
            locationScreen = { uid, onSaved, _ ->
              Button(onClick = onSaved, modifier = Modifier.testTag("location")) { Text(uid) }
            },
            homeScreen = { onCreate, onSignOut ->
              androidx.compose.foundation.layout.Column {
                Button(onClick = onCreate, modifier = Modifier.testTag("home")) { Text("Create") }
                Button(onClick = onSignOut, modifier = Modifier.testTag("signout")) {
                  Text("Sign out")
                }
              }
            },
            createQuestScreen = { onClose ->
              Button(onClick = onClose, modifier = Modifier.testTag("create")) { Text("Close") }
            },
        )
      }
    }
  }

  private fun finishOnboarding() {
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").assertTextEquals("venue-owner").performClick()
    compose.onNodeWithTag("home").assertIsDisplayed()
  }

  @Test
  fun savedLocationReplacesOnboardingAndBackLeavesTheFlow() {
    launch()
    finishOnboarding()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(1, exits)
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag("location").assertDoesNotExist()
  }

  @Test
  fun questFormClosesBackToHomeAndProfileActionEndsSession() {
    launch()
    finishOnboarding()
    compose.onNodeWithTag("home").performClick()
    compose.onNodeWithTag("create").assertIsDisplayed().performClick()
    compose.onNodeWithTag("home").assertIsDisplayed()
    compose.onNodeWithTag("signout").performClick()
    compose.runOnIdle { assertEquals(null, auth.currentUserId.value) }
  }
}
