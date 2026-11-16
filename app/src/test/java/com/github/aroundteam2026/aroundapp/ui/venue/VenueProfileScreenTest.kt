// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class VenueProfileScreenTest {
  @get:Rule val compose = createComposeRule()

  private var signOuts = 0
  private var retries = 0

  private fun show(state: VenueProfileUiState) {
    compose.setContent {
      AroundAppTheme {
        VenueProfileContent(state, onSignOut = { signOuts++ }, onRetry = { retries++ })
      }
    }
  }

  private fun signOut() {
    compose.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performClick()
    assertEquals(1, signOuts)
  }

  @Test
  fun showsTheVenuesNameAndAddressAndSignsOut() {
    show(VenueProfileUiState(VenueProfileStatus.LOADED, "Café Lumière", "Rue de Bourg 12"))

    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_NAME).assertTextEquals("Café Lumière")
    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_ADDRESS).assertTextEquals("Rue de Bourg 12")
    // The avatar shows the name's initials, as on the dashboard
    compose.onNodeWithText("CL").assertIsDisplayed()
    signOut()
  }

  @Test
  fun aVenueWithoutAnAddressSaysSo() {
    show(VenueProfileUiState(VenueProfileStatus.LOADED, "Café Lumière", null))

    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_ADDRESS).assertTextEquals("No address yet")
  }

  @Test
  fun whileLoadingTheVenueCanStillSignOut() {
    show(VenueProfileUiState())

    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_LOADING).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_NAME).assertDoesNotExist()
    signOut()
  }

  @Test
  fun aFailedLoadOffersTryAgainAndSignOut() {
    show(VenueProfileUiState(VenueProfileStatus.FAILED))

    compose.onNodeWithText("Couldn't load your venue.").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_RETRY).performClick()
    assertEquals(1, retries)
    signOut()
  }

  // Trying again can't make a missing venue appear, so there's no button for it
  @Test
  fun aMissingVenueSaysSoWithoutTryAgainButCanSignOut() {
    show(VenueProfileUiState(VenueProfileStatus.MISSING))

    compose
        .onNodeWithTag(C.Tag.VENUE_PROFILE_MISSING)
        .assertTextEquals("We couldn't find your venue. Sign out, then sign in again.")
    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_RETRY).assertDoesNotExist()
    signOut()
  }

  // On a short screen with large fonts, Sign out must still be reachable by scrolling
  @Test
  @Config(qualifiers = "en-w320dp-h320dp-mdpi", fontScale = 2f)
  fun signOutCanBeScrolledToOnASmallScreenWithLargeFonts() {
    show(VenueProfileUiState(VenueProfileStatus.FAILED))

    compose.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun thePreviewShowsALoadedVenue() {
    compose.setContent { VenueProfilePreview() }

    compose.onNodeWithTag(C.Tag.VENUE_PROFILE_NAME).assertTextEquals("Café Lumière")
  }
}
