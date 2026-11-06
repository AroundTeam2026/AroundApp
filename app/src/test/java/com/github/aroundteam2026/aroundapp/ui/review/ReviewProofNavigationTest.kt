// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.review

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes
import com.github.aroundteam2026.aroundapp.ui.navigation.VenueTab
import com.github.aroundteam2026.aroundapp.ui.navigation.pressBack
import com.github.aroundteam2026.aroundapp.ui.navigation.venueDestinations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the way from a proof on the dashboard to its Review proof screen, and back. */
@RunWith(AndroidJUnit4::class)
class ReviewProofNavigationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val fakes = ReviewProofOnFakes()
  private lateinit var navController: NavHostController
  private lateinit var completionId: String

  /** The venue's NavHost on its dashboard, with one proof to review. */
  @Before
  fun launch() {
    completionId = runBlocking { fakes.sendProof() }
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = Routes.VENUE_HOME) {
        venueDestinations(
            navController,
            onSignOut = {},
            // Neither the form nor the Quests tab is opened here
            createQuestFactory = viewModelFactory {},
            venueQuestsFactory = viewModelFactory {},
            dashboardFactory = fakes.dashboardFactory(),
            reviewProofFactory = fakes::reviewProofFactory,
        )
      }
    }
  }

  private fun currentRoute() = composeTestRule.runOnUiThread {
    navController.currentDestination?.route
  }

  private fun openTheProof() {
    composeTestRule.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_$completionId").performClick()
    composeTestRule.waitForIdle()
  }

  private fun assertBackOnTheDashboard() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.REVIEW_PROOF_SCREEN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(VenueTab.DASHBOARD.tabTag).assertIsSelected()
    assertEquals(Routes.VENUE_HOME, currentRoute())
  }

  @Test
  fun tappingAProofOpensItsReviewScreenAboveTheHome() {
    openTheProof()

    composeTestRule.onNodeWithTag(C.Tag.REVIEW_PROOF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText(ReviewProofOnFakes.EXPLORER_NAME).assertIsDisplayed()
    composeTestRule.onNodeWithText(ReviewProofOnFakes.ANSWER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.VENUE_NAV_BAR).assertDoesNotExist()
    assertEquals(Routes.REVIEW_PROOF, currentRoute())
  }

  @Test
  fun systemBackReturnsToTheDashboard() {
    openTheProof()
    composeTestRule.pressBack()

    assertBackOnTheDashboard()
    composeTestRule.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_$completionId").assertIsDisplayed()
  }

  @Test
  fun theBackButtonReturnsToTheDashboard() {
    openTheProof()
    composeTestRule.onNodeWithTag(C.Tag.REVIEW_PROOF_BACK).performClick()

    assertBackOnTheDashboard()
  }

  @Test
  fun approvingReturnsToTheDashboardWithoutTheProof() {
    openTheProof()
    composeTestRule.onNodeWithTag(C.Tag.REVIEW_PROOF_APPROVE).performClick()

    assertBackOnTheDashboard()
    composeTestRule.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_$completionId").assertDoesNotExist()
  }

  @Test
  fun aBlankCompletionIdOpensNothing() {
    composeTestRule.runOnUiThread { navController.navigateToReviewProof(" ") }
    composeTestRule.waitForIdle()

    assertEquals(Routes.VENUE_HOME, currentRoute())
    assertThrows(IllegalArgumentException::class.java) { Routes.reviewProof("") }
  }
}
