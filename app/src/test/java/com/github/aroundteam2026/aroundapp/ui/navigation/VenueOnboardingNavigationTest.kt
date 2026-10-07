// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenueOnboardingNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val repository = FakeVenueRepository()
  private val authRepository = FakeAuthRepository("owner-1")
  private lateinit var navController: NavHostController

  private fun launch() {
    compose.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = "signup") {
        // The auth owner invokes this callback after successful Venue sign-up.
        composable("signup") {
          Button(
              onClick = { navController.navigateToVenueInitialization() },
              modifier = Modifier.testTag("signup"),
          ) {
            Text("Continue")
          }
        }
        venueOnboardingDestinations(navController, authRepository, repository) { venueId, onBack ->
          Button(onClick = onBack, modifier = Modifier.testTag("location")) { Text(venueId) }
        }
      }
    }
    compose.onNodeWithTag("signup").performClick()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertIsDisplayed()
  }

  private fun submitName() {
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("My Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").assertIsDisplayed().assertTextContains("owner-1")
  }

  private fun pressSystemBack() {
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  @Test
  fun signupThenValidNameNavigatesWithSavedVenueId() {
    launch()
    submitName()
    assertEquals("My Cafe", runBlocking { repository.getVenue("owner-1") }?.name)
  }

  @Test
  fun invalidNameStaysOnInitializationWithoutSaving() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_ERROR).assertIsDisplayed()
    compose.onNodeWithTag("location").assertDoesNotExist()
    assertNull(runBlocking { repository.getVenue("owner-1") })
  }

  @Test
  fun backButtonReturnsToSignup() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_BACK).performClick()
    compose.onNodeWithTag("signup").assertIsDisplayed()
    assertNull(runBlocking { repository.getVenue("owner-1") })
  }

  @Test
  fun repeatedSignupHandoffDoesNotDuplicateInitialization() {
    launch()
    compose.runOnUiThread { navController.navigateToVenueInitialization() }
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_BACK).performClick()
    compose.onNodeWithTag("signup").assertIsDisplayed()
  }

  @Test
  fun systemBackReturnsToSignup() {
    launch()
    pressSystemBack()
    compose.onNodeWithTag("signup").assertIsDisplayed()
  }

  @Test
  fun locationBackPreservesNameWithoutNavigatingForwardAgain() {
    launch()
    submitName()
    compose.onNodeWithTag("location").performClick()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).assertTextContains("My Cafe")
    compose.onNodeWithTag("location").assertDoesNotExist()
    val saved = runBlocking { repository.getVenue("owner-1") }
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").assertIsDisplayed()
    assertEquals(saved, runBlocking { repository.getVenue("owner-1") })
  }

  @Test
  fun systemBackFromLocationPreservesInitialization() {
    launch()
    submitName()
    pressSystemBack()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).assertTextContains("My Cafe")
    compose.onNodeWithTag("location").assertDoesNotExist()
    pressSystemBack()
    compose.onNodeWithTag("signup").assertIsDisplayed()
  }
}
