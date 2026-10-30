// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.dashboard.venueDashboardFactoryOnFakes
import com.github.aroundteam2026.aroundapp.ui.quest.list.venueQuestsFactoryOnFakes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [VenueHome]: its tabs, its "New quest" button and signing out. */
@RunWith(AndroidJUnit4::class)
class VenueHomeTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private var questsCreated = 0
  private var signOuts = 0

  @Before
  fun launch() {
    composeTestRule.setContent {
      VenueHome(
          onCreateQuest = { questsCreated++ },
          onSignOut = { signOuts++ },
          venueQuestsFactory = venueQuestsFactoryOnFakes(),
          dashboardFactory = venueDashboardFactoryOnFakes(),
      )
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
    composeTestRule.onNodeWithTag(C.Tag.DASHBOARD_EMPTY).assertIsDisplayed()
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
  }

  // VenueDestinationsTest checks that the venue's NavHost opens the form
  @Test
  fun newQuestAsksToOpenTheCreateQuestForm() {
    select(VenueTab.QUESTS)
    composeTestRule.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).performClick()

    assertEquals(1, questsCreated)
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
    composeTestRule.pressBack()

    assertShowing(VenueTab.DASHBOARD)
  }
}
