// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import java.util.UUID

/** The password of every account [createAccount] creates; only the Auth emulator ever sees it. */
const val E2E_PASSWORD = "123456"

/**
 * Creates an explorer account through the app's own "Create account" form, from the auth screen
 * [E2eRule] starts on, and waits for the explorer's tabs.
 *
 * @param email unique by default, so the account is new on an emulator other tests already used.
 */
fun ComposeTestRule.createAccount(email: String = "e2e-${UUID.randomUUID()}@around.test") {
  onNodeWithTag(C.Tag.AUTH_SCREEN).assertIsDisplayed()
  onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).performScrollTo().performClick()
  onNodeWithTag(C.Tag.AUTH_EMAIL).performTextInput(email)
  onNodeWithTag(C.Tag.AUTH_PASSWORD).performTextInput(E2E_PASSWORD)
  onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performTextInput(E2E_PASSWORD)
  onNodeWithTag(C.Tag.AUTH_SUBMIT).performScrollTo().performClick()
  waitUntil(TIMEOUT_MS) { onAllNodes(hasTestTag(C.Tag.NAV_BAR)).fetchSemanticsNodes().isNotEmpty() }
  onNodeWithTag(C.Tag.NAV_BAR).assertIsDisplayed()
  onNodeWithTag(C.Tag.AUTH_SCREEN).assertDoesNotExist()
}
