// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import java.util.UUID

/** The password of every account created here; only the Auth emulator ever sees it. */
const val E2E_PASSWORD = "123456"

/**
 * Creates an explorer account through the app's own "Create account" form, from the auth screen
 * [E2eRule] starts on, and waits for the explorer's tabs.
 *
 * @param email unique by default, so the account is new on an emulator other tests already used.
 */
fun ComposeTestRule.createAccount(email: String = "e2e-${UUID.randomUUID()}@around.test") =
    register(email, C.Tag.AUTH_EXPLORER_CARD, landsOn = C.Tag.NAV_BAR)

/**
 * Creates a venue account through the Explorer form's venue link, from the auth screen [E2eRule]
 * starts on, and waits for the venue's first onboarding step, its business name.
 *
 * @param email unique by default, so the account is new on an emulator other tests already used.
 */
fun ComposeTestRule.createVenueAccount(email: String = "e2e-${UUID.randomUUID()}@around.test") =
    register(
        email,
        C.Tag.AUTH_EXPLORER_CARD,
        landsOn = C.Tag.VENUE_INITIALIZATION_SCREEN,
        viaVenueLink = true,
    )

/**
 * Chooses [accountType], fills credentials, submits, and waits for the screen tagged [landsOn] in
 * place of the auth screen.
 */
private fun ComposeTestRule.register(
    email: String,
    accountType: String,
    landsOn: String,
    viaVenueLink: Boolean = false,
) {
  onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
  onNodeWithTag(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT).performClick()
  onNodeWithTag(accountType).performClick()
  if (viaVenueLink) {
    onNodeWithTag(C.Tag.AUTH_VENUE_LINK).performScrollTo().performClick()
    onNodeWithTag(C.Tag.AUTH_TITLE).assertTextEquals("Create venue account")
  }
  onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
  onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput(E2E_PASSWORD)
  onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput(E2E_PASSWORD)
  onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
  // Signing up creates the Auth account and writes the profile, which is slow on a cold emulator
  waitUntil(SETUP_TIMEOUT_MS) { onAllNodes(hasTestTag(landsOn)).fetchSemanticsNodes().isNotEmpty() }
  onNodeWithTag(landsOn).assertIsDisplayed()
  onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
}
