// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateQuestViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var viewModel: CreateQuestViewModel
  private val state
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    viewModel = newViewModel()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A form for the signed-in [VENUE_ID], whose venue is read from [venues]. */
  private fun newViewModel(venues: VenueRepository = FakeVenueRepository()) =
      CreateQuestViewModel(FakeAuthRepository(VENUE_ID), venues)

  /** A form whose signed-in venue has [radiusMeters]; its radius is not loaded yet. */
  private fun newViewModelWithVenueRadius(radiusMeters: Int) =
      newViewModel(FakeVenueRepository(listOf(venue(radiusMeters))))

  private fun venue(radiusMeters: Int): Venue =
      testVenue(id = VENUE_ID).copy(radiusMeters = radiusMeters)

  /** Runs the venue load started when the form opened. */
  private fun loadVenue() = dispatcher.scheduler.runCurrent()

  private fun fillValid() {
    viewModel.updateTitle("Find the hidden fox")
    viewModel.updateDescription("A tiny ceramic fox hides somewhere in the café.")
    viewModel.updateRequirements("Order any drink.")
  }

  /** One field of the form: how to edit it, read its error, and its limit. */
  private class Field(
      val name: String,
      val update: (CreateQuestViewModel, String) -> Unit,
      val error: (CreateQuestUiState) -> QuestTextError?,
      val maxLength: Int,
  )

  private val fields =
      listOf(
          Field(
              "title",
              CreateQuestViewModel::updateTitle,
              { it.titleError },
              QuestLimits.TITLE_MAX_LENGTH,
          ),
          Field(
              "description",
              CreateQuestViewModel::updateDescription,
              { it.descriptionError },
              QuestLimits.DESCRIPTION_MAX_LENGTH,
          ),
          Field(
              "requirements",
              CreateQuestViewModel::updateRequirements,
              { it.requirementsError },
              QuestLimits.REQUIREMENTS_MAX_LENGTH,
          ),
      )

  @Test
  fun initialFormIsEmptyShowsNoErrorsAndCannotBeSubmitted() {
    assertEquals(CreateQuestUiState(), state)
    fields.forEach { assertNull(it.name, it.error(state)) }
    assertFalse(state.canSubmit)
  }

  @Test
  fun updatesKeepTheTypedTextUntrimmed() {
    viewModel.updateTitle("  Fox ")
    viewModel.updateDescription("Desc\n")
    viewModel.updateRequirements(" Req")
    assertEquals("  Fox ", state.title)
    assertEquals("Desc\n", state.description)
    assertEquals(" Req", state.requirements)
  }

  @Test
  fun validFormCanBeSubmitted() {
    fillValid()
    fields.forEach { assertNull(it.name, it.error(state)) }
    assertTrue(state.canSubmit)
  }

  @Test
  fun emptyOrWhitespaceFieldIsBlankOnceEdited() {
    fields.forEach { field ->
      fillValid()
      listOf("", " \t\n ").forEach { value ->
        field.update(viewModel, value)
        assertEquals(field.name, QuestTextError.BLANK, field.error(state))
        assertFalse(field.name, state.canSubmit)
      }
    }
  }

  @Test
  fun untouchedBlankFieldShowsNoErrorButBlocksSubmit() {
    viewModel.updateTitle("Find the hidden fox")
    assertNull(state.descriptionError)
    assertNull(state.requirementsError)
    assertFalse(state.canSubmit)
  }

  @Test
  fun fieldAtItsLimitIsValid() {
    fields.forEach { field ->
      fillValid()
      field.update(viewModel, "a".repeat(field.maxLength))
      assertNull(field.name, field.error(state))
      assertTrue(field.name, state.canSubmit)
    }
  }

  @Test
  fun fieldOverItsLimitIsTooLong() {
    fields.forEach { field ->
      fillValid()
      field.update(viewModel, "a".repeat(field.maxLength + 1))
      assertEquals(field.name, QuestTextError.TOO_LONG, field.error(state))
      assertFalse(field.name, state.canSubmit)
    }
  }

  @Test
  fun surroundingWhitespaceDoesNotCountTowardsTheLimit() {
    fields.forEach { field ->
      fillValid()
      field.update(viewModel, "  " + "a".repeat(field.maxLength) + " \n")
      assertNull(field.name, field.error(state))
      assertTrue(field.name, state.canSubmit)
    }
  }

  @Test
  fun tooLongShowsBeforeTheFieldIsTouched() {
    val state = CreateQuestUiState(title = "a".repeat(QuestLimits.TITLE_MAX_LENGTH + 1))
    assertEquals(QuestTextError.TOO_LONG, state.titleError)
  }

  @Test
  fun completionRulesDefaultToAPhotoAtTheDefaultRadiusForASoloVisitOfAtLeastOne() {
    loadVenue()
    assertEquals(ProofType.PHOTO, state.proofType)
    assertEquals(QuestLimits.DEFAULT_RADIUS_METERS, state.radiusMeters)
    assertEquals(QuestLimits.DEFAULT_RADIUS_METERS, state.selectedRadiusPreset)
    assertFalse(state.customRadius)
    assertEquals(QuestLimits.MIN_PARTY_SIZE, state.minPartySize)
    assertFalse(state.exactPartySize)
  }

  @Test
  fun updateProofTypeSelectsIt() {
    viewModel.updateProofType(ProofType.TEXT)
    assertEquals(ProofType.TEXT, state.proofType)
    viewModel.updateProofType(ProofType.PHOTO)
    assertEquals(ProofType.PHOTO, state.proofType)
  }

  @Test
  fun radiusStartsAtTheVenuesPresetRadius() {
    viewModel = newViewModelWithVenueRadius(100)
    loadVenue()
    assertEquals(100, state.radiusMeters)
    assertEquals(100, state.selectedRadiusPreset)
    assertFalse(state.customRadius)
  }

  @Test
  fun venueRadiusThatIsNotAPresetIsShownAsCustom() {
    viewModel = newViewModelWithVenueRadius(73)
    loadVenue()
    assertEquals(73, state.radiusMeters)
    assertTrue(state.customRadius)
    assertNull(state.selectedRadiusPreset)
  }

  @Test
  fun venueRadiusDoesNotOverwriteARadiusAlreadyChosenButItsLocationLoads() {
    viewModel = newViewModelWithVenueRadius(100)
    viewModel.selectRadiusPreset(25)
    loadVenue()
    assertEquals(25, state.radiusMeters)
    assertEquals(venue(100).location, state.venueLocation)
  }

  @Test
  fun venueLocationIsUnknownUntilTheVenueLoads() {
    viewModel = newViewModelWithVenueRadius(100)
    assertNull(state.venueLocation)
    loadVenue()
    assertEquals(venue(100).location, state.venueLocation)
  }

  @Test
  fun radiusStaysAtTheDefaultWhenNoVenueIsSignedIn() {
    viewModel = CreateQuestViewModel(FakeAuthRepository(), FakeVenueRepository(listOf(venue(100))))
    loadVenue()
    assertEquals(QuestLimits.DEFAULT_RADIUS_METERS, state.radiusMeters)
  }

  /** runTest fails on an uncaught exception, so the load must catch the failure. */
  @Test
  fun radiusStaysAtTheDefaultWhenTheVenueCannotBeRead() =
      runTest(dispatcher) {
        val failing =
            object : VenueRepository by FakeVenueRepository(listOf(venue(100))) {
              override suspend fun getVenue(venueId: String): Venue? = throw IOException("offline")
            }
        viewModel = newViewModel(failing)
        runCurrent()
        assertEquals(QuestLimits.DEFAULT_RADIUS_METERS, state.radiusMeters)
      }

  @Test
  fun selectingAPresetSetsItsRadiusAndLeavesCustom() {
    viewModel.selectCustomRadius()
    viewModel.updateRadius(130)
    viewModel.selectRadiusPreset(25)
    assertEquals(25, state.radiusMeters)
    assertEquals(25, state.selectedRadiusPreset)
    assertFalse(state.customRadius)
  }

  @Test
  fun customKeepsTheCurrentRadiusAndDeselectsThePreset() {
    viewModel.selectRadiusPreset(100)
    viewModel.selectCustomRadius()
    assertEquals(100, state.radiusMeters)
    assertTrue(state.customRadius)
    assertNull(state.selectedRadiusPreset)
  }

  @Test
  fun radiusWithinTheLimitsIsKept() {
    listOf(QuestLimits.MIN_RADIUS_METERS, 120, QuestLimits.MAX_RADIUS_METERS).forEach {
      viewModel.updateRadius(it)
      assertEquals(it, state.radiusMeters)
    }
  }

  @Test
  fun radiusOutsideTheLimitsIsClamped() {
    viewModel.updateRadius(QuestLimits.MIN_RADIUS_METERS - 1)
    assertEquals(QuestLimits.MIN_RADIUS_METERS, state.radiusMeters)
    viewModel.updateRadius(QuestLimits.MAX_RADIUS_METERS + 1)
    assertEquals(QuestLimits.MAX_RADIUS_METERS, state.radiusMeters)
  }

  @Test
  fun partySizeStepsUpAndDownByOne() {
    viewModel.incrementPartySize()
    viewModel.incrementPartySize()
    assertEquals(QuestLimits.MIN_PARTY_SIZE + 2, state.minPartySize)
    viewModel.decrementPartySize()
    assertEquals(QuestLimits.MIN_PARTY_SIZE + 1, state.minPartySize)
  }

  @Test
  fun partySizeNeverGoesBelowTheMinimum() {
    viewModel.decrementPartySize()
    assertEquals(QuestLimits.MIN_PARTY_SIZE, state.minPartySize)
  }

  @Test
  fun partySizeHasNoUpperLimit() {
    repeat(20) { viewModel.incrementPartySize() }
    assertEquals(QuestLimits.MIN_PARTY_SIZE + 20, state.minPartySize)
  }

  @Test
  fun updateExactPartySizeSetsIt() {
    viewModel.updateExactPartySize(true)
    assertTrue(state.exactPartySize)
    viewModel.updateExactPartySize(false)
    assertFalse(state.exactPartySize)
  }

  @Test
  fun completionRulesDoNotChangeWhetherTheFormCanBeSubmitted() {
    viewModel.updateProofType(ProofType.TEXT)
    viewModel.selectCustomRadius()
    viewModel.updateRadius(QuestLimits.MAX_RADIUS_METERS)
    viewModel.incrementPartySize()
    viewModel.updateExactPartySize(true)
    assertFalse(state.canSubmit)
    fillValid()
    assertTrue(state.canSubmit)
  }

  @Test
  fun switchingRewardTypeKeepsWhatWasTyped() {
    viewModel.selectRewardType(RewardType.FREE_ITEM)
    viewModel.updateRewardItemName("Coffee ")
    viewModel.updateRewardSpecifics("Any size")
    viewModel.selectRewardType(RewardType.OTHER)
    viewModel.updateRewardDescription("A tour")
    viewModel.selectRewardType(RewardType.FREE_ITEM)
    assertEquals(
        RewardFormState(
            type = RewardType.FREE_ITEM,
            itemName = "Coffee ",
            itemSpecifics = "Any size",
            description = "A tour",
            itemNameTouched = true,
            descriptionTouched = true,
        ),
        state.reward,
    )
  }

  @Test
  fun discountEditsSetTheAmountAsTypedAndItsUnit() {
    viewModel.updateDiscountAmount("12,5 ")
    viewModel.updateDiscountUnit(DiscountUnit.CHF)
    assertEquals("12,5 ", state.reward.discountAmount)
    assertTrue(state.reward.discountAmountTouched)
    assertEquals(DiscountUnit.CHF, state.reward.discountUnit)
  }

  @Test
  fun anIncompleteRewardBlocksSubmitUntilItIsFilledIn() {
    fillValid()
    viewModel.selectRewardType(RewardType.OTHER)
    assertFalse(state.canSubmit)
    viewModel.updateRewardDescription("A tour")
    assertTrue(state.canSubmit)
  }

  private companion object {
    const val VENUE_ID = "venue-1"
  }
}
