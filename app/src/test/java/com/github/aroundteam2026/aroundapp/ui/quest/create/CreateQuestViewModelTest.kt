// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
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
  private val quests = FakeQuestRepository(now = { SAVED_AT })
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

  /**
   * A form for the signed-in [VENUE_ID], whose venue is read from [venues], saved to
   * [questRepository] and dated by [clock].
   */
  private fun newViewModel(
      venues: VenueRepository = FakeVenueRepository(),
      questRepository: QuestRepository = quests,
      clock: Clock = Clock.systemUTC(),
  ) = CreateQuestViewModel(FakeAuthRepository(VENUE_ID), venues, questRepository, clock)

  /** A form whose signed-in venue is placed and loaded, saving to [questRepository]. */
  private fun newLoadedViewModel(
      questRepository: QuestRepository = quests,
      clock: Clock = Clock.systemUTC(),
  ): CreateQuestViewModel {
    viewModel = newViewModel(FakeVenueRepository(listOf(venue(50))), questRepository, clock)
    loadVenue()
    return viewModel
  }

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
    viewModel =
        CreateQuestViewModel(
            FakeAuthRepository(),
            FakeVenueRepository(listOf(venue(100))),
            quests,
        )
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
  fun expiryBeforeTodayIsInThePastButTodayIsNot() {
    val today = LocalDate.of(2026, 10, 12)
    val clock = Clock.fixed(today.atTime(23, 59).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    viewModel = newViewModel(clock = clock)

    viewModel.updateExpiryDate(today.minusDays(1))
    assertTrue(state.reward.expiryInPast)
    viewModel.updateExpiryDate(today)
    assertEquals(today, state.reward.expiryDate)
    assertFalse(state.reward.expiryInPast)
    viewModel.updateExpiryDate(null)
    assertNull(state.reward.expiryDate)
  }

  @Test
  fun anIncompleteRewardBlocksSubmitUntilItIsFilledIn() {
    fillValid()
    viewModel.selectRewardType(RewardType.OTHER)
    assertFalse(state.canSubmit)
    viewModel.updateRewardDescription("A tour")
    assertTrue(state.canSubmit)
  }

  @Test
  fun submitSavesTheTrimmedFormAsAnActiveQuestOfTheVenue() =
      runTest(dispatcher) {
        val today = LocalDate.of(2026, 10, 12)
        newLoadedViewModel(clock = Clock.fixed(today.atStartOfDay(ZONE).toInstant(), ZONE))
        viewModel.updateTitle(" Find the fox ")
        viewModel.updateDescription(" A fox hides here. ")
        viewModel.updateRequirements(" Order a drink. ")
        viewModel.updateProofType(ProofType.TEXT)
        viewModel.selectRadiusPreset(100)
        viewModel.incrementPartySize()
        viewModel.updateExactPartySize(true)
        viewModel.selectRewardType(RewardType.OTHER)
        viewModel.updateRewardDescription(" A tour ")
        viewModel.updateExpiryDate(today)

        viewModel.submit()
        runCurrent()

        assertEquals(SaveState.Saved("quest-1"), state.saveState)
        val endOfToday = today.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()
        assertEquals(
            Quest(
                id = "quest-1",
                venueId = VENUE_ID,
                venueName = venue(50).name,
                location = requireNotNull(venue(50).location),
                radiusMeters = 100,
                title = "Find the fox",
                description = "A fox hides here.",
                requirements = "Order a drink.",
                proofType = ProofType.TEXT,
                minPartySize = 2,
                exactPartySize = true,
                reward = Reward.Other("A tour", endOfToday),
                status = QuestStatus.ACTIVE,
                createdAt = SAVED_AT,
                updatedAt = SAVED_AT,
            ),
            quests.getQuest("quest-1"),
        )
      }

  @Test
  fun aSavedQuestIsNotSavedAgain() =
      runTest(dispatcher) {
        newLoadedViewModel()
        fillValid()
        viewModel.submit()
        runCurrent()
        viewModel.submit()
        runCurrent()
        assertEquals(SaveState.Saved("quest-1"), state.saveState)
        assertNull(quests.getQuest("quest-2"))
      }

  @Test
  fun aSaveInProgressBlocksAnotherUntilItEnds() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<Result<String>>()
        var calls = 0
        val slow =
            object : QuestRepository by quests {
              override suspend fun createQuest(quest: Quest): Result<String> {
                calls++
                return answer.await()
              }
            }
        newLoadedViewModel(slow)
        fillValid()

        viewModel.submit()
        runCurrent()
        assertEquals(SaveState.Saving, state.saveState)
        assertFalse(state.canSave)
        viewModel.submit()
        answer.complete(Result.success("quest-9"))
        runCurrent()

        assertEquals(1, calls)
        assertEquals(SaveState.Saved("quest-9"), state.saveState)
      }

  @Test
  fun clearingTheSaveErrorForgetsOnlyAFailedSave() =
      runTest(dispatcher) {
        newLoadedViewModel()
        fillValid()
        quests.forcedFailure = IOException("offline")
        viewModel.submit()
        runCurrent()
        viewModel.clearSaveError()
        assertEquals(SaveState.Idle, state.saveState)

        quests.forcedFailure = null
        viewModel.submit()
        runCurrent()
        viewModel.clearSaveError()
        assertEquals(SaveState.Saved("quest-1"), state.saveState)
      }

  @Test
  fun editsWhileSavingAreIgnored() =
      runTest(dispatcher) {
        val answer = CompletableDeferred<Result<String>>()
        newLoadedViewModel(
            object : QuestRepository by quests {
              override suspend fun createQuest(quest: Quest): Result<String> = answer.await()
            }
        )
        fillValid()
        viewModel.submit()
        runCurrent()
        val saving = state

        viewModel.updateTitle("Another title")
        viewModel.incrementPartySize()
        viewModel.selectRewardType(RewardType.OTHER)
        assertEquals(saving, state)

        answer.complete(Result.failure(IOException("offline")))
        runCurrent()
        viewModel.updateTitle("Another title")
        assertEquals("Another title", state.title)
      }

  @Test
  fun aFailedSaveKeepsTheFormAndCanBeRetried() =
      runTest(dispatcher) {
        newLoadedViewModel()
        fillValid()
        viewModel.incrementPartySize()
        viewModel.selectRewardType(RewardType.OTHER)
        viewModel.updateRewardDescription("A tour")
        val before = state
        quests.forcedFailure = IOException("offline")
        viewModel.submit()
        runCurrent()
        assertEquals(before.copy(saveState = SaveState.Failed), state)
        assertTrue(state.canSave)

        quests.forcedFailure = null
        viewModel.submit()
        runCurrent()
        assertEquals(SaveState.Saved("quest-1"), state.saveState)
      }

  /** runTest fails on an uncaught exception, so the save must catch it. */
  @Test
  fun aRepositoryThatThrowsFailsTheSave() =
      runTest(dispatcher) {
        val throwing =
            object : QuestRepository by quests {
              override suspend fun createQuest(quest: Quest): Result<String> =
                  throw IOException("offline")
            }
        newLoadedViewModel(throwing)
        fillValid()
        viewModel.submit()
        runCurrent()
        assertEquals(SaveState.Failed, state.saveState)
      }

  @Test
  fun nothingIsSavedBeforeTheVenueLoads() =
      runTest(dispatcher) {
        viewModel = newViewModelWithVenueRadius(50)
        fillValid()
        assertFalse(state.canSave)
        viewModel.submit()
        assertNothingSaved()
      }

  @Test
  fun nothingIsSavedForAVenueNotPlacedOnTheMap() =
      runTest(dispatcher) {
        viewModel =
            newViewModel(FakeVenueRepository(listOf(testVenue(id = VENUE_ID, location = null))))
        loadVenue()
        fillValid()
        assertFalse(state.canSave)
        viewModel.submit()
        assertNothingSaved()
      }

  @Test
  fun nothingIsSavedWhileTheFormIsInvalid() =
      runTest(dispatcher) {
        newLoadedViewModel()
        assertFalse(state.canSave)
        viewModel.submit()
        assertNothingSaved()
      }

  /** Runs what the last submit started, and checks that it saved nothing. */
  private suspend fun assertNothingSaved() {
    dispatcher.scheduler.runCurrent()
    assertEquals(SaveState.Idle, state.saveState)
    assertNull(quests.getQuest("quest-1"))
  }

  @Test
  fun aPastExpiryLeftFromAnotherRewardDoesNotBlockSavingNoReward() =
      runTest(dispatcher) {
        val today = LocalDate.of(2026, 10, 12)
        val clock = MovableClock(today.atTime(23, 59).atZone(ZONE).toInstant())
        newLoadedViewModel(clock = clock)
        fillValid()
        viewModel.selectRewardType(RewardType.OTHER)
        viewModel.updateExpiryDate(today)
        viewModel.selectRewardType(null)

        clock.instant = clock.instant.plusSeconds(120)
        viewModel.submit()
        runCurrent()

        assertEquals(SaveState.Saved("quest-1"), state.saveState)
        assertNull(quests.getQuest("quest-1")?.reward)
      }

  @Test
  fun anExpiryThatPassedSincePickingItBlocksTheSave() =
      runTest(dispatcher) {
        val today = LocalDate.of(2026, 10, 12)
        val clock = MovableClock(today.atTime(23, 59).atZone(ZONE).toInstant())
        newLoadedViewModel(clock = clock)
        fillValid()
        viewModel.selectRewardType(RewardType.OTHER)
        viewModel.updateRewardDescription("A tour")
        viewModel.updateExpiryDate(today)

        clock.instant = clock.instant.plusSeconds(120)
        viewModel.submit()
        runCurrent()

        assertTrue(state.reward.expiryInPast)
        assertEquals(SaveState.Idle, state.saveState)
        assertNull(quests.getQuest("quest-1"))
      }

  /** A clock at [instant], which a test can move forward. */
  private class MovableClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZONE

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant
  }

  private companion object {
    const val VENUE_ID = "venue-1"
    const val SAVED_AT = 5_000L
    val ZONE: ZoneId = ZoneOffset.UTC
  }
}
