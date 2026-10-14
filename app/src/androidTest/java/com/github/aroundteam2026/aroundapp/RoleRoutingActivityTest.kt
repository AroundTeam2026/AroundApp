// Co-authored-by: OpenAI Codex <noreply@openai.com>
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
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.resources.C
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
    compose.waitUntil(15_000) {
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
    compose.waitUntil(15_000) {
      compose.onAllNodes(hasTestTag(C.Tag.AUTH_EMAIL)).fetchSemanticsNodes().isEmpty() &&
          compose.onAllNodes(hasTestTag(C.Tag.AUTH_SUBMIT)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
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
    awaitTag(C.Tag.AUTH_EMAIL)
    compose.onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
    compose.onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput("123456")
    compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
    awaitTag(C.Tag.VENUE_INITIALIZATION_SCREEN)
    compose.onNodeWithTag(C.Tag.NAV_BAR).assertDoesNotExist()
    compose.activityRule.scenario.recreate()
    awaitTag(C.Tag.VENUE_INITIALIZATION_SCREEN)
    compose.onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
  }
}
