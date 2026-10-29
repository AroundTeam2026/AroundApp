// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.FlakyVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.list.venueQuestsFactoryOnFakes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests where a signed-in venue enters the app, with fake repositories and a stand-in for the area
 * screen, whose map Robolectric can't draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenueEntryTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val auth = FakeAuthRepository(USER)
  private var venues: VenueRepository = FakeVenueRepository()
  private lateinit var navController: NavHostController
  private var signOuts = 0
  private val quests = FakeQuestRepository()
  private var createQuest: CreateQuestViewModel? = null

  /** Bumped to sign the venue in again: a new entry, ViewModel and NavHost. */
  private var signIns by mutableIntStateOf(0)

  /** Shows the entry of [USER], reading [venues], as when the venue signs in. */
  private fun enter() {
    compose.setContent { key(signIns) { Entry() } }
    compose.waitForIdle()
  }

  /** Signs the venue in again, as after the app was killed: everything is read afresh. */
  private fun signInAgain() {
    compose.runOnUiThread { signIns++ }
    compose.waitForIdle()
  }

  @Composable
  private fun Entry() {
    navController = rememberNavController()
    VenueEntry(
        USER,
        auth,
        venues,
        onSignOut = { signOuts++ },
        viewModel = remember { VenueEntryViewModel(USER, venues) },
        navController = navController,
        areaStep = { venueId, onSaved, onBack ->
          Column(Modifier.testTag(AREA)) {
            Text(venueId, Modifier.testTag(AREA_VENUE))
            Button(onClick = onSaved, modifier = Modifier.testTag(AREA_SAVE)) { Text("Save") }
            Button(onClick = onBack, modifier = Modifier.testTag(AREA_BACK)) { Text("Back") }
          }
        },
        createQuestFactory =
            viewModelFactory {
              initializer { CreateQuestViewModel(auth, venues, quests).also { createQuest = it } }
            },
        venueQuestsFactory = venueQuestsFactoryOnFakes(),
    )
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag)

  private fun pressSystemBack() {
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private fun giveName(name: String = "My Cafe") {
    node(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput(name)
    node(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.waitForIdle()
  }

  @Test
  fun aNewVenueStartsByGivingItsName() {
    enter()

    node(C.Tag.VENUE_INITIALIZATION_SCREEN).assertIsDisplayed()
    node(AREA).assertDoesNotExist()
    node(C.Tag.VENUE_HOME).assertDoesNotExist()
  }

  @Test
  fun aVenueWithoutALocationResumesOnItsArea() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER, location = null)))
    enter()

    node(AREA_VENUE).assertIsDisplayed().assertTextEquals(USER)
  }

  @Test
  fun aSetUpVenueOpensItsHome() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER)))
    enter()

    node(C.Tag.VENUE_HOME).assertIsDisplayed()
    node(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
  }

  @Test
  fun aNewVenueGoesThroughSetupInOneGo() {
    enter()

    giveName()
    node(AREA_VENUE).assertIsDisplayed().assertTextEquals(USER)
    node(AREA_SAVE).performClick()
    compose.waitForIdle()

    node(C.Tag.VENUE_HOME).assertIsDisplayed()
    assertEquals("My Cafe", runBlocking { venues.getVenue(USER) }?.name)
  }

  @Test
  fun savingTheAreaLeavesOnboardingBehind() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER, location = null)))
    enter()

    node(AREA_SAVE).performClick()
    compose.waitForIdle()

    node(C.Tag.VENUE_HOME).assertIsDisplayed()
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun backFromAResumedAreaShowsTheSavedNameWithoutGoingForward() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER, name = "Café Lumen", location = null)))
    enter()

    node(AREA_BACK).performClick()
    compose.waitForIdle()

    node(C.Tag.VENUE_INITIALIZATION_NAME).assertTextContains("Café Lumen").assertIsNotEnabled()
    node(AREA).assertDoesNotExist()
  }

  @Test
  fun systemBackFromTheAreaReturnsToTheName() {
    enter()
    giveName()

    pressSystemBack()

    node(C.Tag.VENUE_INITIALIZATION_NAME).assertTextContains("My Cafe")
    node(AREA).assertDoesNotExist()
  }

  @Test
  fun aVenueThatLeftAfterItsNameResumesOnTheAreaWhenItSignsInAgain() {
    enter()
    giveName()

    signInAgain()

    node(AREA).assertIsDisplayed()
    node(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
  }

  /** Opens the create-quest form from the "New quest" of a set-up venue's My quests. */
  private fun openCreateQuest() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER)))
    enter()
    node(C.Tag.VENUE_QUESTS_TAB).performClick()
    node(C.Tag.VENUE_NEW_QUEST).performClick()
    compose.waitForIdle()
  }

  @Test
  fun newQuestOpensTheCreateQuestFormFromTheHome() {
    openCreateQuest()

    node(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
    node(C.Tag.VENUE_HOME).assertDoesNotExist()
  }

  @Test
  fun closingTheCreateQuestFormReturnsToMyQuests() {
    openCreateQuest()

    node(C.Tag.CREATE_QUEST_CLOSE).performClick()
    compose.waitForIdle()

    node(C.Tag.VENUE_NEW_QUEST).assertIsDisplayed()
    node(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
  }

  @Test
  fun savingAQuestReturnsToMyQuests() {
    openCreateQuest()
    compose.runOnIdle {
      createQuest!!.updateTitle("Find the fox")
      createQuest!!.updateDescription("A fox hides here.")
      createQuest!!.updateRequirements("Order a drink.")
    }

    node(C.Tag.CREATE_QUEST_SUBMIT).performClick()
    compose.waitForIdle()

    node(C.Tag.VENUE_NEW_QUEST).assertIsDisplayed()
    assertEquals("Find the fox", runBlocking { quests.getQuest("quest-1") }?.title)
  }

  // The dashboard is the home's own tab, so it has no route of its own
  @Test
  fun onlyTheCreateQuestRouteIsRegistered() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER)))
    enter()

    compose.runOnUiThread {
      assertEquals(Routes.CREATE_QUEST, navController.graph.findNode(Routes.CREATE_QUEST)?.route)
      assertNull(navController.graph.findNode(Routes.DASHBOARD))
    }
  }

  @Test
  fun signingOutFromTheHomeSignsOut() {
    venues = FakeVenueRepository(listOf(testVenue(id = USER)))
    enter()

    node(C.Tag.VENUE_PROFILE_TAB).performClick()
    node(C.Tag.PROFILE_SIGN_OUT).performClick()

    assertNull(auth.currentUserId.value)
  }

  @Test
  fun aFailedReadOffersRetryAndSignOut() {
    venues = FlakyVenueRepository(failures = 1, venue = testVenue(id = USER))
    enter()

    node(C.Tag.VENUE_ENTRY_FAILED).assertIsDisplayed()
    node(C.Tag.VENUE_ENTRY_SIGN_OUT).performClick()
    assertEquals(1, signOuts)

    node(C.Tag.VENUE_ENTRY_RETRY).performClick()
    compose.waitForIdle()
    node(C.Tag.VENUE_HOME).assertIsDisplayed()
  }

  private companion object {
    const val USER = "owner-1"
    const val AREA = "area"
    const val AREA_SAVE = "area_save"
    const val AREA_BACK = "area_back"
    const val AREA_VENUE = "area_venue"
  }
}
