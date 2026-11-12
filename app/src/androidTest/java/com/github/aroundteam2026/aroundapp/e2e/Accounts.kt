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
import androidx.test.espresso.Espresso
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
fun ComposeTestRule.createAccount(email: String = "e2e-${UUID.randomUUID()}@around.test") {
  register(email, C.Tag.AUTH_EXPLORER_CARD, landsOn = C.Tag.PROFILE_SETUP_SCREEN)
  onNodeWithTag(C.Tag.PROFILE_SETUP_NAME).performTextInput("Explorer")
  closeKeyboard()
  onNodeWithTag(C.Tag.PROFILE_SETUP_CONTINUE).performClick()
  waitUntil(SETUP_TIMEOUT_MS) {
    onAllNodes(hasTestTag(C.Tag.NAV_BAR)).fetchSemanticsNodes().isNotEmpty()
  }
  onNodeWithTag(C.Tag.NAV_BAR).assertIsDisplayed()
}

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
 * Signs in to the existing account [email] through the app's "Sign in" form, from the auth screen,
 * and waits for the screen tagged [landsOn], e.g. a set-up venue's dashboard.
 */
fun ComposeTestRule.signIn(email: String, landsOn: String) {
  onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
  onNodeWithTag(C.Tag.AUTH_WELCOME_SIGN_IN).performClick()
  onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
  onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput(E2E_PASSWORD)
  submitAuthForm()
  leaveAuthFor(landsOn)
}

/**
 * Signs out from the Profile tab, tagged [profileTab] in the explorer's and the venue's bars, and
 * waits for the auth screen.
 */
fun ComposeTestRule.signOut(profileTab: String = C.Tag.PROFILE_TAB) {
  onNodeWithTag(profileTab).performClick()
  onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()
  waitUntil(SETUP_TIMEOUT_MS) {
    onAllNodes(hasTestTag(C.Tag.AUTH_SCREEN)).fetchSemanticsNodes().isNotEmpty()
  }
}

/** Closes the soft keyboard, then taps the auth form's submit button, scrolled into view. */
fun ComposeTestRule.submitAuthForm() {
  closeKeyboard()
  onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
}

/**
 * Closes the soft keyboard and waits until it is hidden. Call it before every tap that follows
 * typing: while the keyboard slides in, a screen with imePadding is still catching up, so a button
 * can still be under the keyboard or moving when the tap lands, and the tap is lost (the auth form
 * in CI run 41899891610, the venue name's Continue in 41918593997).
 */
fun ComposeTestRule.closeKeyboard() {
  Espresso.closeSoftKeyboard()
}

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
  submitAuthForm()
  leaveAuthFor(landsOn)
}

/** Waits for the screen tagged [landsOn] to replace the auth screen after submitting its form. */
private fun ComposeTestRule.leaveAuthFor(landsOn: String) {
  // Signing up or in reads or writes the profile, which is slow on a cold emulator
  waitUntil(SETUP_TIMEOUT_MS) { onAllNodes(hasTestTag(landsOn)).fetchSemanticsNodes().isNotEmpty() }
  onNodeWithTag(landsOn).assertIsDisplayed()
  onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
}
