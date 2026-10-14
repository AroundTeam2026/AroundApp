// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * route draws by default, its ViewModel's factory and the app's venue provider, down to Firestore.
 * Unit tests replace the page with one on in-memory repositories, so only this test runs that path.
 */
@RunWith(AndroidJUnit4::class)
class AroundAppVenuePageDeviceTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController
  private lateinit var venue: Venue

  /**
   * Signs in a fresh venue account and saves its venue: the rules let only a signed-in user whose
   * profile has the VENUE role write a venue, as registration sets up in the app.
   */
  @Before
  fun setUp() = runBlocking {
    FirebaseEmulator.connect()
    withTimeout(SETUP_TIMEOUT_MS) {
      val email = "venue-page-${UUID.randomUUID()}@around.test"
      val uid =
          Firebase.auth.createUserWithEmailAndPassword(email, "password123").await().user!!.uid
      UserRepositoryProvider.repository.createUser(User(uid, "Test", Role.VENUE, 0L)).getOrThrow()
      venue = Venue(uid, "Café Lumen", null, DEFAULT_RADIUS_METERS, null, createdAt = 0L)
      VenueRepositoryProvider.repository.createVenue(venue).getOrThrow()
    }
  }

  @After fun tearDown() = Firebase.auth.signOut()

  @Test
  fun aVenuesPageShowsTheVenueSavedInFirestore() {
    composeTestRule.setContent {
      navController = rememberNavController()
      AroundApp(onSignOut = {}, navController = navController)
    }
    composeTestRule.runOnUiThread { navController.navigateToVenuePage(venue.id) }

    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(venue.name).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
