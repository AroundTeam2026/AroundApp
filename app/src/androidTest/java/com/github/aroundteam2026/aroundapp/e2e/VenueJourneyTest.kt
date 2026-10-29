// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.ui.map.tapMapUntil
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The visit radius the venue picks: not the default, so the saved one shows the slider was set. */
private const val RADIUS_METERS = 120

/**
 * A venue's first visit, through the app as it ships on the Auth and Firestore emulators: it
 * registers through the venue link, names the venue, places it on the map with a visit radius and
 * creates a quest from its home. Its role, its venue and the quest are then read back from
 * Firestore.
 */
@RunWith(AndroidJUnit4::class)
class VenueJourneyTest : TestCase() {
  @get:Rule val e2e = E2eRule()
  private val composeTestRule = e2e.composeTestRule

  private val id = UUID.randomUUID()
  private val venueName = "Journey venue $id"
  private val questTitle = "Journey quest $id"

  @Test
  fun aNewVenueSetsUpAndCreatesAQuest() = run {
    step("Register through the venue link") { composeTestRule.createVenueAccount() }
    step("Name the venue") {
      composeTestRule.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput(venueName)
      composeTestRule
          .onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE)
          .performScrollTo()
          .performClick()
      waitFor(hasTestTag(C.Tag.VENUE_AREA_SCREEN))
    }
    step("Place the venue on the map and set its visit radius") {
      // Saving needs a marker, which only a tap on the map places
      val canSave = hasTestTag(C.Tag.VENUE_AREA_SAVE) and isEnabled()
      composeTestRule.tapMapUntil(C.Tag.VENUE_AREA_MAP) { exists(canSave) }
      assertTrue("Tapping the map never placed the venue's marker", exists(canSave))
      composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SLIDER).performSemanticsAction(
          SemanticsActions.SetProgress
      ) {
        it(RADIUS_METERS.toFloat())
      }
      composeTestRule.onNodeWithTag(C.Tag.VENUE_AREA_SAVE).performClick()
      waitFor(hasTestTag(C.Tag.VENUE_HOME))
    }
    step("Create a quest from the venue's home") {
      composeTestRule.onNodeWithTag(C.Tag.VENUE_QUESTS_TAB).performClick()
      composeTestRule.onNodeWithTag(C.Tag.VENUE_NEW_QUEST).performClick()
      waitFor(hasTestTag(C.Tag.CREATE_QUEST_SCREEN))
      listOf(
              C.Tag.CREATE_QUEST_TITLE to questTitle,
              C.Tag.CREATE_QUEST_DESCRIPTION to "Find the hidden fox",
              C.Tag.CREATE_QUEST_REQUIREMENTS to "A photo of the fox",
          )
          .forEach { (tag, text) ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)
          }
      // Enabled once the form has read the venue's location, where the quest goes
      waitFor(hasTestTag(C.Tag.CREATE_QUEST_SUBMIT) and isEnabled())
      composeTestRule.onNodeWithTag(C.Tag.CREATE_QUEST_SUBMIT).performClick()
      // Saving closes the form, back to the Quests tab it was opened from
      composeTestRule.waitUntil(TIMEOUT_MS) { !exists(hasTestTag(C.Tag.CREATE_QUEST_SCREEN)) }
      composeTestRule.onNodeWithTag(C.Tag.VENUE_QUESTS_SCREEN).assertIsDisplayed()
    }
    step("Firestore holds the venue's role, its venue and the quest") {
      runBlocking {
        // The app's writes don't wait for the server, which would remove any the rules refused
        Firebase.firestore.waitForPendingWrites().await()
        val uid = checkNotNull(Firebase.auth.currentUser) { "The venue isn't signed in" }.uid
        assertEquals(Role.VENUE, UserRepositoryProvider.repository.getUser(uid)?.role)
        val venue = checkNotNull(VenueRepositoryProvider.repository.getVenue(uid))
        assertEquals(venueName, venue.name)
        assertEquals(RADIUS_METERS, venue.radiusMeters)
        assertNotNull("The area saved no location", venue.location)
        val quest = QuestRepositoryProvider.repository.observeQuestsByVenue(uid).first().single()
        assertEquals(questTitle, quest.title)
        assertEquals(QuestStatus.ACTIVE, quest.status)
      }
    }
  }

  private fun exists(matcher: SemanticsMatcher) =
      composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(matcher: SemanticsMatcher) =
      composeTestRule.waitUntil(TIMEOUT_MS) { exists(matcher) }
}
