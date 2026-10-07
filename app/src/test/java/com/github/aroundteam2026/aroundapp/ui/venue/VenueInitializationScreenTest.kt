// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.venue

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VenueInitializationScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val repository = ScreenVenueRepository()
  private val viewModel = VenueInitializationViewModel(FakeAuthRepository("owner-1"), repository)
  private val navigatedIds = mutableListOf<String>()
  private var backCalls = 0
  private val recompositions = mutableIntStateOf(0)

  private fun launch() {
    compose.setContent {
      recompositions.intValue
      VenueInitializationScreen(
          viewModel,
          onBack = { backCalls++ },
          onContinueToLocation = { navigatedIds.add(it) },
      )
    }
  }

  @Test
  fun emptyNameShowsValidationWithoutSaving() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose
        .onNodeWithTag(C.Tag.VENUE_INITIALIZATION_ERROR)
        .assertTextContains("Enter your business name.")
    assertEquals(0, repository.saveCalls)
    assertTrue(navigatedIds.isEmpty())
  }

  @Test
  fun whitespaceNameShowsValidationAndEditingClearsIt() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("   ")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_ERROR).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_ERROR).assertDoesNotExist()
    assertEquals(0, repository.saveCalls)
  }

  @Test
  fun validSubmissionSavesAndContinuesOnceAcrossRecomposition() {
    launch()
    // A review artifact, generated from the real screen rather than the Figma screenshot.
    val screenshot = compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_SCREEN).captureToImage()
    val output = File("build/reports/venue-initialization.png")
    output.parentFile?.mkdirs()
    output.outputStream().use {
      screenshot.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }

    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("  Café Lumière  ")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.waitForIdle()
    assertEquals(listOf("owner-1"), navigatedIds)
    assertEquals(1, repository.saveCalls)
    val saved = runBlocking { repository.getVenue("owner-1") }!!
    assertEquals("Café Lumière", saved.name)
    assertNull(saved.location)
    assertNull(saved.address)
    assertEquals(VenueLimits.DEFAULT_RADIUS_METERS, saved.radiusMeters)
    compose.runOnIdle { recompositions.intValue++ }
    compose.waitForIdle()
    assertEquals(listOf("owner-1"), navigatedIds)
  }

  @Test
  fun savingDisablesContinueAndNameUntilCompleted() {
    repository.gate = CompletableDeferred()
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose
        .onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE)
        .assertIsNotEnabled()
        .assertTextContains("Saving", substring = true)
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).assertIsNotEnabled()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_BACK).assertIsNotEnabled()
    assertTrue(navigatedIds.isEmpty())
    compose.runOnIdle { repository.gate!!.complete(Unit) }
    compose.waitForIdle()
    assertEquals(listOf("owner-1"), navigatedIds)
  }

  @Test
  fun failedSaveShowsErrorAndPreservesNameForRetry() {
    repository.failSave = true
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("My Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose
        .onNodeWithTag(C.Tag.VENUE_INITIALIZATION_ERROR)
        .assertTextContains("Could not save your venue.", substring = true)
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).assertTextContains("My Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).assertIsEnabled()
    assertTrue(navigatedIds.isEmpty())
    compose.runOnIdle { repository.failSave = false }
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.waitForIdle()
    assertEquals(listOf("owner-1"), navigatedIds)
  }

  @Test
  fun existingVenueContinuesWithoutReplacingProfile() {
    val original = Venue("owner-1", "Original", null, 75, "Address", 1L)
    runBlocking { repository.seed(original) }
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Replacement")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_CONTINUE).performClick()
    compose.waitForIdle()
    assertEquals(listOf("owner-1"), navigatedIds)
    assertEquals(0, repository.saveCalls)
    assertEquals(original, runBlocking { repository.getVenue("owner-1") })
  }

  @Test
  fun backButtonCallsNavigationOwnerWithoutSaving() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_BACK).performClick()
    assertEquals(1, backCalls)
    assertEquals(0, repository.saveCalls)
  }

  @Test
  @Config(qualifiers = "w320dp-h480dp-mdpi")
  fun keyboardDoneSubmitsOnSmallScreen() {
    launch()
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performTextInput("Small Cafe")
    compose.onNodeWithTag(C.Tag.VENUE_INITIALIZATION_NAME).performImeAction()
    compose.waitForIdle()
    assertEquals(listOf("owner-1"), navigatedIds)
    assertEquals(1, repository.saveCalls)
  }

  private class ScreenVenueRepository : VenueRepository {
    private val delegate = FakeVenueRepository()
    var saveCalls = 0
    var gate: CompletableDeferred<Unit>? = null
    var failSave = false

    override fun observeVenue(venueId: String) = delegate.observeVenue(venueId)

    override suspend fun getVenue(venueId: String) = delegate.getVenue(venueId)

    override suspend fun createVenue(venue: Venue): Result<Unit> {
      saveCalls++
      gate?.await()
      if (failSave) return Result.failure(IllegalStateException("Offline"))
      return delegate.createVenue(venue)
    }

    suspend fun seed(venue: Venue) {
      delegate.createVenue(venue).getOrThrow()
    }
  }
}
