// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.resources.C

/** Shared registration steps for Robolectric and T1/T2's device journeys, starting on sign-in. */
class RegistrationScreen(private val semantics: SemanticsNodeInteractionsProvider) {
  /** Creates an Explorer account through the normal Create account entry. */
  fun registerExplorer(email: String, password: String) = register(email, password, venue = false)

  /** Fills the same form and creates a Venue account through the venue hyperlink. */
  fun registerVenue(email: String, password: String) = register(email, password, venue = true)

  private fun register(email: String, password: String, venue: Boolean) {
    click(C.Tag.AUTH_SWITCH_MODE)
    semantics.onNodeWithTag(C.Tag.AUTH_EMAIL).performScrollTo().performTextInput(email)
    semantics.onNodeWithTag(C.Tag.AUTH_PASSWORD).performScrollTo().performTextInput(password)
    semantics.onNodeWithTag(C.Tag.AUTH_CONFIRMATION).performScrollTo().performTextInput(password)
    click(if (venue) C.Tag.AUTH_VENUE_LINK else C.Tag.AUTH_SUBMIT)
  }

  private fun click(tag: String) = semantics.onNodeWithTag(tag).performScrollTo().performClick()
}
