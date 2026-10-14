// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.create.CreateQuestViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [VenueHome]: its tabs, the create-quest form it opens and signing out. */
@RunWith(AndroidJUnit4::class)
class VenueHomeTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private var signOuts = 0

  @Before
  fun launch() {
    composeTestRule.setContent {
      VenueHome(onSignOut = { signOuts++ }, createQuestFactory = formFactory)
    }
  }

  private fun select(tab: VenueTab) {
    composeTestRule.onNodeWithTag(tab.tabTag).performClick()
  }

  /** Only [tab] is selected, and only its screen is shown. */
  private fun assertShowing(tab: VenueTab) {
    composeTestRule.waitForIdle()
    VenueTab.entries.forEach {
      val tabNode = composeTestRule.onNodeWithTag(it.tabTag)
      val screenNode = composeTestRule.onNodeWithTag(it.screenTag)
      if (it == tab) {
        tabNode.assertIsSelected()
        screenNode.assertIsDisplayed()
      } else {
        tabNode.assertIsNotSelected()
        screenNode.assertDoesNotExist()
      }
    }
  }

  @Test
  fun opensOnTheDashboard() {
    assertShowing(VenueTab.DASHBOARD)
    composeTestRule.onNodeWithTag(C.Tag.DASHBOARD_STUB).assertIsDisplayed()
  }

  @Test
  fun eachTabShowsItsScreen() {
    VenueTab.entries.reversed().forEach {
      select(it)
      assertShowing(it)
    }
  }

  @Test
  fun theQuestsTabShowsANewQuestButton() {
    select(VenueTab.QUESTS)

    composeTestRule.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_QUESTS_STUB).assertIsDisplayed()
  }

  private fun openTheForm() {
    select(VenueTab.QUESTS)
    composeTestRule.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).performClick()
  }

  private fun pressBack() {
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
  }

  // The form belongs to the Quests tab, so the tab stays selected above it
  @Test
  fun newQuestOpensTheCreateQuestForm() {
    openTheForm()

    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(VenueTab.QUESTS.tabTag).assertIsSelected()
  }

  // Saving calls the same onClose; CreateQuestDestinationTest checks that it does
  @Test
  fun closingTheFormReturnsToTheQuestsTab() {
    openTheForm()
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()

    assertShowing(VenueTab.QUESTS)
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
  }

  @Test
  fun backFromTheFormReturnsToTheQuestsTab() {
    openTheForm()
    pressBack()

    assertShowing(VenueTab.QUESTS)
    composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SCREEN).assertDoesNotExist()
  }

  @Test
  fun theProfileTabSignsTheVenueOut() {
    select(VenueTab.PROFILE)
    composeTestRule.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()

    assertEquals(1, signOuts)
  }

  @Test
  fun backFromAnyTabReturnsToTheDashboard() {
    select(VenueTab.QUESTS)
    select(VenueTab.PROFILE)
    pressBack()

    assertShowing(VenueTab.DASHBOARD)
  }

  private companion object {
    /** The create-quest form on fakes, so opening it needs no Firebase. */
    val formFactory = viewModelFactory {
      initializer {
        CreateQuestViewModel(FakeAuthRepository(), FakeVenueRepository(), FakeQuestRepository())
      }
    }
  }
}
