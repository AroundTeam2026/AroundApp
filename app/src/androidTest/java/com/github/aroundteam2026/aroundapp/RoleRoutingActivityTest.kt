// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.e2e.E2eRule
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises MainActivity's production providers against Firebase emulators, never the project. */
@RunWith(AndroidJUnit4::class)
class RoleRoutingActivityTest {
  @get:Rule val e2e = E2eRule()
  private val compose
    get() = e2e.composeTestRule

  private fun awaitTag(tag: String) {
    compose.waitUntil(SETUP_TIMEOUT_MS) {
      compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun existingAccountWithoutProfileChoosesAndPersistsExplorerRole() {
    val uid = runBlocking {
      AuthRepositoryProvider.repository
          .signUpWithEmail("routing-${UUID.randomUUID()}@around.test", "123456")
          .getOrThrow()
    }
    compose.waitUntil(SETUP_TIMEOUT_MS) {
      compose.onAllNodes(hasTestTag(C.Tag.AUTH_EMAIL)).fetchSemanticsNodes().isEmpty() &&
          compose
              .onAllNodes(hasTestTag(C.Tag.AUTH_EXPLORER_CARD))
              .fetchSemanticsNodes()
              .isNotEmpty()
    }
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_EXPLORER_CARD).performScrollTo().performClick()
    awaitTag(C.Tag.NAV_BAR)
    assertEquals(
        Role.EXPLORER,
        runBlocking { UserRepositoryProvider.repository.getUser(uid) }?.role,
    )
    compose.activityRule.scenario.recreate()
    awaitTag(C.Tag.NAV_BAR)
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  @Test
  fun signingInWithSavedVenueRoleOpensVenueEntryAfterRecreation() {
    val email = "routing-${UUID.randomUUID()}@around.test"
    runBlocking {
      val auth = AuthRepositoryProvider.repository
      val uid = auth.signUpWithEmail(email, "123456").getOrThrow()
      UserRepositoryProvider.repository
          .createUser(User(uid, "Venue owner", Role.VENUE, 1L))
          .getOrThrow()
      auth.signOut()
    }
    awaitTag(C.Tag.AUTH_WELCOME_SIGN_IN)
    compose.onNodeWithTag(C.Tag.AUTH_WELCOME_SIGN_IN).performClick()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    awaitTag(C.Tag.VENUE_INITIALIZATION_SCREEN)
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.activityRule.scenario.recreate()
    awaitTag(C.Tag.VENUE_INITIALIZATION_SCREEN)
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }

  /**
   * Guards the production role-to-home wiring: a venue already set up skips onboarding. Each
   * fixture uses a fresh owner, and writes finish before UI sign-in.
   */
  @Test
  fun aSetUpVenueOpensItsHome() {
    val email = "venue-flow-${UUID.randomUUID()}@around.test"
    runBlocking {
      val owner = AuthRepositoryProvider.repository.signUpWithEmail(email, "123456").getOrThrow()
      UserRepositoryProvider.repository
          .createUser(User(owner, "Owner", Role.VENUE, 1L))
          .getOrThrow()
      VenueRepositoryProvider.repository
          .createVenue(Venue(owner, "Fixture Cafe", Location(46.52, 6.57), 50, null, 1L))
          .getOrThrow()
    }
    compose.runOnIdle { AuthRepositoryProvider.repository.signOut() }
    awaitTag(C.Tag.AUTH_WELCOME_SIGN_IN)
    compose.onNodeWithTag(C.Tag.AUTH_WELCOME_SIGN_IN).performClick()
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    awaitTag(C.Tag.VENUE_DASHBOARD_SCREEN)
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_AREA_SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }
}
