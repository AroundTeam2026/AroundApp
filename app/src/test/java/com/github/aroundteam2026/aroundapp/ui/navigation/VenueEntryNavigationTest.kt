// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.create.createQuestFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.quest.list.venueQuestsFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenueEntryNavigationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val auth = FakeAuthRepository("venue-owner")
  private val venues = FakeVenueRepository()
  private var exits = 0

  private fun launch(
      restoration: StateRestorationTester? = null,
      start: VenueEntryStart = VenueEntryStart.Initialization,
  ) {
    compose.activity.onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            exits++
          }
        }
    )
    val content: @androidx.compose.runtime.Composable () -> Unit = {
      AroundAppTheme {
        VenueEntryNavigation(
            auth,
            venues,
            locationScreen = { uid, onSaved, onBack ->
              Column {
                Button(onClick = onSaved, modifier = Modifier.testTag("location")) { Text(uid) }
                Button(onClick = onBack, modifier = Modifier.testTag("location-back")) {
                  Text("Back")
                }
              }
            },
            createQuestFactory = createQuestFactoryOnFakes(),
            venueQuestsFactory = venueQuestsFactoryOnFakes(),
            start = start,
        )
      }
    }
    if (restoration == null) compose.setContent(content) else restoration.setContent(content)
  }

  private fun finishOnboarding() {
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").assertTextEquals("venue-owner").performClick()
    compose.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
  }

  private fun openQuestForm() {
    compose.onNodeWithTag(C.Tag.VENUE_QUESTS_TAB).performClick()
    compose.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).performClick()
  }

  /** Right after sign-up, setup is the root: Back signs out instead of trapping the owner. */
  @Test
  fun backArrowFromBusinessSetupSignsOut() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_BACK).performClick()
    compose.runOnIdle { assertEquals(null, auth.currentUserId.value) }
    assertEquals(0, exits)
  }

  @Test
  fun systemBackFromBusinessSetupSignsOut() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertIsDisplayed()
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.runOnIdle { assertEquals(null, auth.currentUserId.value) }
    assertEquals(0, exits)
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
  fun questFormClosesBackToHome() {
    launch()
    finishOnboarding()
    openQuestForm()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    compose.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
  }

  /** A lost location Back callback would trap the owner or discard the saved business. */
  @Test
  fun locationBackReturnsToTheSavedBusinessWithoutReopeningLocation() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location-back").performClick()
    compose
        .onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME)
        .assertTextContains("Cafe")
        .assertIsNotEnabled()
    compose.onNodeWithTag("location").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").assertTextEquals("venue-owner")
  }

  /** System Back must close the nested form, not leave the signed-in venue graph. */
  @Test
  fun systemBackFromQuestFormReturnsToHomeWithoutReopeningSetup() {
    launch()
    finishOnboarding()
    openQuestForm()
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
    compose.runOnIdle { assertEquals(0, exits) }
  }

  /** Recreation must restore the active nested destination, rather than restart onboarding. */
  @Test
  fun restoringAnOpenQuestFormKeepsItsDestinationAndCloseReturnsHome() {
    val restoration = StateRestorationTester(compose)
    launch(restoration)
    finishOnboarding()
    openQuestForm()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    compose.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
  }

  /** A venue resuming on its area gets it from the graph itself, above its saved name. */
  @Test
  fun resumingOnTheAreaOpensItAboveTheSavedName() {
    runBlocking {
      venues.createVenue(testVenue(id = "venue-owner", name = "Cafe", location = null))
    }
    launch(start = VenueEntryStart.Area("venue-owner"))
    compose.onNodeWithTag("location").assertTextEquals("venue-owner")
    compose.onNodeWithTag("location-back").performClick()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).assertTextContains("Cafe")
    compose.onNodeWithTag("location").assertDoesNotExist()
  }

  /** The area opens once: after Back to the name, recreation must not open it again. */
  @Test
  fun aResumedAreaDoesNotReopenAfterRecreation() {
    runBlocking {
      venues.createVenue(testVenue(id = "venue-owner", name = "Cafe", location = null))
    }
    val restoration = StateRestorationTester(compose)
    launch(restoration, start = VenueEntryStart.Area("venue-owner"))
    compose.onNodeWithTag("location-back").performClick()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertIsDisplayed()
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag("location").assertDoesNotExist()
  }

  /**
   * The real home's sign-out callback must reach the session gate and dispose protected content.
   */
  @Test
  fun realVenueHomeSignOutDisposesTheVenueGraph() {
    compose.setContent {
      AroundAppTheme {
        SessionNavigation(
            auth,
            mainScreen = {
              VenueEntryNavigation(
                  auth,
                  venues,
                  createQuestFactory = createQuestFactoryOnFakes(),
                  venueQuestsFactory = venueQuestsFactoryOnFakes(),
                  locationScreen = { uid, onSaved, _ ->
                    Button(onClick = onSaved, modifier = Modifier.testTag("location")) { Text(uid) }
                  },
              )
            },
        )
      }
    }
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").performClick()
    compose.onNodeWithTag(C.Tag.VENUE_DASHBOARD_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_TAB).performClick()
    compose.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_HOME).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
    compose.runOnIdle { assertEquals(null, auth.currentUserId.value) }
  }

  /**
   * The default form must use its supplied factory and return to home only after saving a quest.
   */
  @Test
  fun defaultQuestFormSavesThroughItsViewModelAndReturnsToHome() {
    val venue = testVenue(id = "venue-owner")
    val repository = FakeVenueRepository(listOf(venue))
    val quests = FakeQuestRepository()
    val form = CreateQuestViewModel(auth, repository, quests)
    compose.setContent {
      AroundAppTheme {
        VenueEntryNavigation(
            auth,
            repository,
            locationScreen = { uid, onSaved, _ ->
              Button(onClick = onSaved, modifier = Modifier.testTag("location")) { Text(uid) }
            },
            createQuestFactory = viewModelFactory { initializer { form } },
            venueQuestsFactory = venueQuestsFactoryOnFakes(),
        )
      }
    }
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag("location").performClick()
    openQuestForm()
    compose.runOnIdle {
      form.updateTitle("Find the mural")
      form.updateDescription("Visit the cafe")
      form.updateRequirements("Ask the owner")
    }
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).assertTextContains("Find the mural")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()
    compose.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
    val saved = runBlocking { quests.observeQuestsByVenue(venue.id).first().single() }
    assertEquals("Find the mural", saved.title)
    assertEquals(venue.id, saved.venueId)
    assertEquals(venue.location, saved.location)
  }
}
