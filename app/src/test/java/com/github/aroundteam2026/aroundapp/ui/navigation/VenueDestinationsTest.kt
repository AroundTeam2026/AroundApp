// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import com.github.aroundteam2026.aroundapp.ui.quest.list.VenueQuestsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [venueDestinations]: the venue home, and the create-quest form it opens full screen. */
@RunWith(AndroidJUnit4::class)
class VenueDestinationsTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private var signOuts = 0
  private lateinit var navController: NavHostController
  private val auth = FakeAuthRepository(VENUE_ID)
  /** Shared by the form and the Quests tab, so a saved quest reaches the list. */
  private val quests = FakeQuestRepository()
  private val form =
      CreateQuestViewModel(auth, FakeVenueRepository(listOf(testVenue(id = VENUE_ID))), quests)

  /** A venue NavHost that opens on the home of a placed venue without quests, on fakes. */
  @Before
  fun launch() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = Routes.VENUE_HOME) {
        venueDestinations(
            navController,
            { signOuts++ },
            createQuestFactory = viewModelFactory { initializer { form } },
            venueQuestsFactory =
                viewModelFactory { initializer { VenueQuestsViewModel(auth, quests) } },
        )
      }
    }
  }

  // The venue has no quests yet, so the empty state's button opens the form
  private fun openTheForm() {
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.tabTag).performClick()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_QUESTS_CREATE_FIRST).performClick()
    composeTestRule.waitForIdle()
  }

  private fun currentRoute() = composeTestRule.runOnUiThread {
    navController.currentDestination?.route
  }

  /** The form is gone, and the home shows the Quests tab it was opened from. */
  private fun assertBackOnTheQuestsTab() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.tabTag).assertIsSelected()
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.screenTag).assertIsDisplayed()
  }

  @Test
  fun theHomeOpensOnTheDashboard() {
    composeTestRule.onNodeWithTag(C.Tag.VENUE_HOME).assertIsDisplayed()
    composeTestRule.onNodeWithTag(VenueTab.DASHBOARD.tabTag).assertIsSelected()
  }

  // Full screen: the home's bottom bar would otherwise sit under the form's "Create quest" bar
  @Test
  fun newQuestOpensTheFormAboveTheHome() {
    openTheForm()

    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_HOME).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_NAV_BAR).assertDoesNotExist()
  }

  // Saving calls the same onClose; CreateQuestDestinationTest checks that it does
  @Test
  fun closingTheFormReturnsToTheQuestsTab() {
    openTheForm()
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()

    assertBackOnTheQuestsTab()
  }

  @Test
  fun closingTheFormLeavesOnlyTheHomeOnTheBackStack() {
    openTheForm()
    assertEquals(Routes.CREATE_QUEST, currentRoute())

    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    composeTestRule.waitForIdle()

    assertEquals(Routes.VENUE_HOME, currentRoute())
    assertNull(composeTestRule.runOnUiThread { navController.previousBackStackEntry })
  }

  @Test
  fun backFromTheFormReturnsToTheQuestsTab() {
    openTheForm()
    composeTestRule.pressBack()

    assertBackOnTheQuestsTab()
  }

  // The form stays on screen, with X enabled, while it fades out: the second tap must do nothing
  @Test
  fun aDoubleTapOnCloseStaysOnTheQuestsTab() {
    openTheForm()
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    composeTestRule.mainClock.advanceTimeBy(DOUBLE_TAP_GAP_MS)
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    composeTestRule.mainClock.autoAdvance = true

    assertBackOnTheQuestsTab()
  }

  @Test
  fun aSavedQuestIsListedOnTheQuestsTab() {
    openTheForm()
    composeTestRule.runOnIdle {
      form.updateTitle(QUEST_TITLE)
      form.updateDescription(QUEST_TITLE)
      form.updateRequirements(QUEST_TITLE)
    }
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()

    assertBackOnTheQuestsTab()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_QUEST_TITLE).assertTextEquals(QUEST_TITLE)
  }

  @Test
  fun theProfileTabSignsTheVenueOut() {
    composeTestRule.onNodeWithTag(VenueTab.PROFILE.tabTag).performClick()
    composeTestRule.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()

    assertEquals(1, signOuts)
  }

  private companion object {
    const val DOUBLE_TAP_GAP_MS = 100L
    const val VENUE_ID = "venue-1"
    const val QUEST_TITLE = "Find the fox"
  }
}
