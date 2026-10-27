// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.resources.C

/** Shared registration steps for Robolectric and T1/T2's device journeys, starting on Welcome. */
class RegistrationScreen(private val semantics: SemanticsNodeInteractionsProvider) {
  /** Creates an Explorer account through the normal Create account entry. */
  fun registerExplorer(email: String, password: String) = register(email, password, venue = false)

  /** Creates a Venue account through the Business choice. */
  fun registerVenue(email: String, password: String) = register(email, password, venue = true)

  private fun register(email: String, password: String, venue: Boolean) {
    click(C.Tag.AUTH_WELCOME_CREATE_ACCOUNT)
    click(if (venue) C.Tag.AUTH_BUSINESS_CARD else C.Tag.AUTH_EXPLORER_CARD)
    semantics.onNodeWithTag(C.Tag.AUTH_EMAIL).performScrollTo().performTextInput(email)
    semantics.onNodeWithTag(C.Tag.AUTH_PASSWORD).performScrollTo().performTextInput(password)
    semantics.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performScrollTo().performTextInput(password)
    click(C.Tag.AUTH_SUBMIT)
  }

  private fun click(tag: String) = semantics.onNodeWithTag(tag).performScrollTo().performClick()
}
