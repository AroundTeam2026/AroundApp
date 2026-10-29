// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.DEFAULT_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a venue's page in the app as it ships, on the Auth and Firestore emulators: the page the
 * route draws by default, its ViewModel's factory and the app's venue and quest providers, down to
 * Firestore. Unit tests replace the page with one on in-memory repositories, so only this test runs
 * that path.
 */
@RunWith(AndroidJUnit4::class)
class AroundAppVenuePageDeviceTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController
  private lateinit var venue: Venue
  private lateinit var quest: Quest
  private lateinit var explorerUid: String

  /**
   * Signs in a fresh venue account and saves its venue and one active quest: the rules let only a
   * signed-in user whose profile has the VENUE role write them, as registration sets up in the app.
   * Then it signs in a fresh explorer, who opens the page, as a real explorer does.
   */
  @Before
  fun setUp(): Unit = runBlocking {
    FirebaseEmulator.connect()
    withTimeout(SETUP_TIMEOUT_MS) {
      val email = "venue-page-${UUID.randomUUID()}@around.test"
      val uid =
          Firebase.auth.createUserWithEmailAndPassword(email, "password123").await().user!!.uid
      UserRepositoryProvider.repository.createUser(User(uid, "Test", Role.VENUE, 0L)).getOrThrow()
      venue = Venue(uid, "Venue $uid", null, DEFAULT_RADIUS_METERS, null, createdAt = 0L)
      VenueRepositoryProvider.repository.createVenue(venue).getOrThrow()
      quest =
          Quest(
              id = "",
              venueId = uid,
              radiusMeters = QuestLimits.DEFAULT_RADIUS_METERS,
              title = "Quest $uid",
              description = "Find the hidden fox",
              requirements = "A photo of the fox",
              proofType = ProofType.PHOTO,
              reward = null,
              status = QuestStatus.ACTIVE,
              createdAt = 0L,
              updatedAt = 0L,
          )
      // Saved past the app's provider, so the page can only show it if the provider reads Firestore
      QuestRepositoryFirestore(Firebase.firestore).createQuest(quest).getOrThrow()
      // createQuest doesn't wait for the server, so without this the page could show the quest from
      // the local cache even if the rules refused it
      Firebase.firestore.waitForPendingWrites().await()

      Firebase.auth.signOut()
      explorerUid =
          Firebase.auth
              .createUserWithEmailAndPassword(
                  "explorer-page-${UUID.randomUUID()}@around.test",
                  "password123",
              )
              .await()
              .user!!
              .uid
      UserRepositoryProvider.repository
          .createUser(User(explorerUid, "Explorer", Role.EXPLORER, 0L))
          .getOrThrow()
    }
  }

  @After fun tearDown() = Firebase.auth.signOut()

  @Test
  fun aVenuesPageShowsTheVenueSavedInFirestore() {
    openVenuePage()

    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(venue.name).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun aVenuesPageListsItsQuestSavedInFirestore() {
    openVenuePage()

    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(quest.title).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun openVenuePage() {
    composeTestRule.setContent {
      navController = rememberNavController()
      // The app opens on the Quests tab; an empty overview keeps its listener off this test
      AroundApp(
          explorerUid = explorerUid,
          onSignOut = {},
          navController = navController,
          overview = {},
      )
    }
    composeTestRule.runOnUiThread { navController.navigateToVenuePage(venue.id) }
  }
}
