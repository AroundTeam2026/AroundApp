// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.overview.OverviewOnFakes
import com.github.aroundteam2026.aroundapp.ui.venuepage.navigateToVenuePage
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's own venue page, as [AroundApp] draws it when no test replaces it. The main dispatcher
 * is paused, so its ViewModel never starts listening to the real Firestore and the page stays
 * loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AroundAppVenuePageTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val app = ApplicationProvider.getApplicationContext<Application>()

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    // Another test in this process may have initialized the default app already
    if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app)
  }

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun aVenueOpensTheAppsVenuePage() {
    lateinit var navController: NavHostController
    compose.setContent {
      navController = rememberNavController()
      AroundApp(
          explorerUid = "explorer",
          navController = navController,
          onSignOut = {},
          overview = { OverviewOnFakes(it) },
      )
    }

    compose.runOnUiThread { navController.navigateToVenuePage("venue-1") }
    compose.waitForIdle()

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_LOADING).assertIsDisplayed()
  }
}
