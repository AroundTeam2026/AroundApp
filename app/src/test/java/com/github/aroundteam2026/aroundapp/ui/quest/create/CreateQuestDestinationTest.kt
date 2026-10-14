// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CreateQuestDestinationTest {
  @get:Rule val compose = createComposeRule()
  private val quests = FakeQuestRepository()
  private val viewModel =
      CreateQuestViewModel(
          FakeAuthRepository(VENUE_ID),
          FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
          quests,
      )
  private lateinit var navController: NavHostController

  /** A venue screen that opened the form, which Back and saving return to. */
  @Before
  fun setUp() {
    compose.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = START) {
        composable(START) { Text("Venue home", Modifier.testTag(START)) }
        createQuestDestination(
            onClose = { navController.popBackStack() },
            factory = viewModelFactory { initializer { viewModel } },
        )
      }
    }
    compose.runOnIdle { navController.navigateToCreateQuest() }
  }

  /** Fills the form so it can be saved. */
  private fun fillValid() {
    compose.runOnIdle {
      viewModel.updateTitle("Find the fox")
      viewModel.updateDescription("A fox hides here.")
      viewModel.updateRequirements("Order a drink.")
    }
  }

  // Filled first, so a close that also saved would store the quest
  @Test
  fun closingTheFormReturnsWithoutSaving() {
    fillValid()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    compose.onNodeWithTag(START).assertIsDisplayed()
    assertNull(runBlocking { quests.getQuest("quest-1") })
  }

  @Test
  fun savingTheQuestReturnsToTheCaller() {
    fillValid()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()
    compose.onNodeWithTag(START).assertIsDisplayed()
    assertEquals("Find the fox", runBlocking { quests.getQuest("quest-1") }?.title)
  }

  private companion object {
    const val VENUE_ID = "venue-1"
    const val START = "start"
  }
}
