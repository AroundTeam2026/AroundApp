// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.DEFAULT_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryFirestore
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Firebase app the venue saves its quest through; see [ExplorerJourneyTest.seedApp]. */
private const val SEED_APP = "explorer-journey-seed"

/** Where the device is during the journey, and where the venue and its quest are: EPFL. */
private val HERE = Location(46.5191, 6.5668)

/**
 * An explorer's first visit, through the app as it ships on the Auth and Firestore emulators: they
 * create an account, find a venue's quest saved in Firestore among the map's nearby quests and view
 * that venue's page.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerJourneyTest : TestCase() {
  @get:Rule val e2e = E2eRule()
  private val composeTestRule = e2e.composeTestRule

  private val id = UUID.randomUUID()
  private val venueName = "Journey venue $id"
  private val questTitle = "Journey quest $id"

  /**
   * Puts the device at [HERE] and saves a venue and its active quest there, as a fresh venue
   * account would: the nearby quests are those within 5 km of the explorer, so the app must find
   * the explorer where the test put the quest, whatever fix the emulator has.
   */
  @Before
  fun seedQuest(): Unit = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    withTimeout(SETUP_TIMEOUT_MS) {
      mockDeviceLocation(HERE)
      val app = seedApp(context)
      val auth = Firebase.auth(app)
      val db = Firebase.firestore(app)
      val uid =
          auth
              .createUserWithEmailAndPassword("venue-$id@around.test", E2E_PASSWORD)
              .await()
              .user!!
              .uid
      // The rules let only an account with the VENUE role write a venue and its quests
      UserRepositoryFirestore(db).createUser(User(uid, "Test", Role.VENUE, 0L)).getOrThrow()
      VenueRepositoryFirestore(db)
          .createVenue(Venue(uid, venueName, HERE, DEFAULT_RADIUS_METERS, null, createdAt = 0L))
          .getOrThrow()
      QuestRepositoryFirestore(db)
          .createQuest(
              Quest(
                  id = "",
                  venueId = uid,
                  venueName = venueName,
                  location = HERE,
                  radiusMeters = QuestLimits.DEFAULT_RADIUS_METERS,
                  title = questTitle,
                  description = "Find the hidden fox",
                  requirements = "A photo of the fox",
                  proofType = ProofType.PHOTO,
                  reward = null,
                  status = QuestStatus.ACTIVE,
                  createdAt = 0L,
                  updatedAt = 0L,
              )
          )
          .getOrThrow()
      // createQuest doesn't wait for the server, which is where the app reads the quest from
      db.waitForPendingWrites().await()
      auth.signOut()
    }
  }

  @After fun stopMockingLocation() = runBlocking { stopMockingDeviceLocation() }

  @Test
  fun aNewExplorerFindsTheSeededQuestAndOpensItsVenuesPage() = run {
    step("Register a new explorer") { composeTestRule.createAccount() }
    step("Find the quest in the map's nearby list") {
      composeTestRule.onNodeWithTag(C.Tag.MAP_TAB).performClick()
      composeTestRule.onNodeWithTag(C.Tag.NEARBY_BUTTON).performClick()
      // "View quest" opens the venue of the page shown, so the seeded quest must be the one shown
      val nearbyQuest = hasTestTag(C.Tag.NEARBY_QUEST_TITLE) and hasText(questTitle)
      waitFor(nearbyQuest)
      composeTestRule.onNode(nearbyQuest, useUnmergedTree = true).assertIsDisplayed()
    }
    step("View the venue's page, which lists the quest") {
      composeTestRule.onNodeWithTag(C.Tag.NEARBY_VIEW).performClick()
      waitFor(hasTestTag(C.Tag.VENUE_PAGE_NAME) and hasText(venueName))
      waitFor(hasTestTag(C.Tag.VENUE_QUEST_ROW_TITLE) and hasText(questTitle))
    }
  }

  /** Waits for a node matching [matcher], including one inside a clickable row's merged node. */
  private fun waitFor(matcher: SemanticsMatcher) =
      composeTestRule.waitUntil(TIMEOUT_MS) {
        composeTestRule
            .onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

  /**
   * A second Firebase app on the app's own project, so its data reaches the app, and on the
   * emulators. It has its own sign-in, so the venue account never signs the app in: the explorer
   * starts signed out, as [E2eRule] leaves the app.
   */
  private fun seedApp(context: Context): FirebaseApp =
      FirebaseApp.getApps(context).find { it.name == SEED_APP }
          ?: FirebaseApp.initializeApp(context, Firebase.app.options, SEED_APP).also {
            Firebase.auth(it).useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
            Firebase.firestore(it)
                .useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.FIRESTORE_PORT)
          }
}
