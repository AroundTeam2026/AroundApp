// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.ui.SaveState
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests [CreateQuestViewModel] editing an existing quest. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditQuestViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC)
  private val quest =
      testQuest(
              id = QUEST_ID,
              venueId = VENUE_ID,
              title = "Find the hidden fox",
              proofType = ProofType.PHOTO,
              status = QuestStatus.ARCHIVED,
              reward = Reward.FreeItem("Coffee", "Any size"),
          )
          .copy(radiusMeters = 100)
  private val quests = FakeQuestRepository(initialQuests = listOf(quest))
  private val reservations = FakeReservationRepository()
  private lateinit var viewModel: CreateQuestViewModel
  private val state
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A form editing [questId] from [questRepository], for a venue whose own radius is 25 m. */
  private fun open(
      questRepository: QuestRepository = quests,
      questId: String = QUEST_ID,
      reservationRepository: ReservationRepository = reservations,
  ) {
    val venue = testVenue(id = VENUE_ID).copy(radiusMeters = 25)
    viewModel =
        CreateQuestViewModel(
            FakeAuthRepository(VENUE_ID),
            FakeVenueRepository(listOf(venue)),
            questRepository,
            FakeAddressSearchRepository(),
            clock,
            questId,
            reservationRepository,
        )
    dispatcher.scheduler.advanceUntilIdle()
  }

  // Its values would overwrite them once read
  @Test
  fun editsBeforeTheQuestIsReadAreIgnored() {
    viewModel =
        CreateQuestViewModel(
            FakeAuthRepository(VENUE_ID),
            FakeVenueRepository(),
            quests,
            FakeAddressSearchRepository(),
            clock,
            QUEST_ID,
        )
    assertTrue(state.editing)
    viewModel.updateTitle("Typed too early")
    assertEquals("", state.title)

    dispatcher.scheduler.advanceUntilIdle()
    assertEquals("Find the hidden fox", state.title)
    viewModel.updateTitle("Find the golden fox")
    assertEquals("Find the golden fox", state.title)
  }

  @Test
  fun theFormOpensFilledInWithTheQuest() {
    open()

    assertEquals("Find the hidden fox", state.title)
    assertEquals(quest.description, state.description)
    assertEquals(quest.requirements, state.requirements)
    assertEquals(ProofType.PHOTO, state.proofType)
    // The venue is read too, and must not reset the radius to its own
    assertEquals(100, state.radiusMeters)
    assertEquals(RewardType.FREE_ITEM, state.reward.type)
    assertEquals("Coffee", state.reward.itemName)
    assertEquals("Any size", state.reward.itemSpecifics)
    assertFalse(state.questUnavailable)
    assertTrue(state.canSave)
  }

  @Test
  fun savingUpdatesTheQuestKeepingItsIdAndStatus() = runTest {
    open()
    viewModel.updateTitle("Find the golden fox")
    viewModel.updateProofType(ProofType.TEXT)
    viewModel.submit()
    assertEquals(SaveState.Saving, state.saveState)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(SaveState.Saved(QUEST_ID), state.saveState)
    val saved = quests.getQuest(QUEST_ID)!!
    assertEquals("Find the golden fox", saved.title)
    assertEquals(ProofType.TEXT, saved.proofType)
    assertEquals(QuestStatus.ARCHIVED, saved.status)
    // Updated in place, not created as a new quest
    assertEquals(listOf(QUEST_ID), quests.observeQuestsByVenue(VENUE_ID).first().map { it.id })
  }

  // Saving must not move a quest placed away from the venue back to it
  @Test
  fun savingKeepsWhereTheQuestIs() = runTest {
    val elsewhere = Location(46.5191, 6.5668)
    val placed = quest.copy(location = elsewhere)
    val scripted = ScriptedQuestRepository(quest = { placed })
    open(scripted)
    assertEquals(elsewhere, state.questLocation)

    viewModel.updateTitle("Find the golden fox")
    viewModel.submit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(elsewhere, scripted.updated.single().location)
  }

  @Test
  fun aQuestMovedWhileEditedIsSavedAtItsNewPlace() = runTest {
    val moved = Location(46.5300, 6.6400)
    open()
    viewModel.placeElsewhere()
    viewModel.moveQuest(moved)
    viewModel.submit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(moved, quests.getQuest(QUEST_ID)!!.location)
  }

  @Test
  fun aFailedSaveKeepsTheFormAndCanBeRetried() {
    open()
    quests.forcedFailure = IOException("offline")
    viewModel.updateTitle("Find the golden fox")
    viewModel.submit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(SaveState.Failed, state.saveState)
    assertEquals("Find the golden fox", state.title)
    assertTrue(state.canSave)

    quests.forcedFailure = null
    viewModel.submit()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(SaveState.Saved(QUEST_ID), state.saveState)
  }

  @Test
  fun anUpdateThatThrowsIsAFailedSave() {
    val scripted =
        ScriptedQuestRepository(quest = { quest }, update = { throw IOException("offline") })
    open(scripted)
    viewModel.submit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(SaveState.Failed, state.saveState)
    assertEquals(QUEST_ID, scripted.updated.single().id)
    assertTrue(scripted.created.isEmpty())
  }

  @Test
  fun aQuestThatNoLongerExistsIsUnavailable() {
    open(questId = "gone")
    assertTrue(state.questUnavailable)
  }

  // A network blip must not close the form
  @Test
  fun aQuestThatCantBeReadCanBeReadAgain() {
    val scripted = ScriptedQuestRepository(quest = { throw IOException("offline") })
    open(scripted)
    assertTrue(state.questLoadFailed)
    assertFalse(state.questUnavailable)

    scripted.quest = { quest }
    viewModel.retryQuest()
    dispatcher.scheduler.advanceUntilIdle()
    assertFalse(state.questLoadFailed)
    assertEquals("Find the hidden fox", state.title)
  }

  // Its update would be rejected by the rules yet look saved
  @Test
  fun anotherVenuesQuestIsUnavailable() {
    open(ScriptedQuestRepository(quest = { quest.copy(venueId = "other-venue") }))
    assertTrue(state.questUnavailable)
  }

  // The rules check a pending party against the quest as it is when the venue approves
  @Test
  fun pendingReservationsLockThePartySize() = runTest {
    val id =
        reservations
            .createReservation(newPendingReservation(QUEST_ID, VENUE_ID, "Ana", 0), "explorer")
            .getOrThrow()
    open()
    assertTrue(state.partySizeLocked)
    viewModel.incrementPartySize()
    viewModel.updateExactPartySize(true)
    assertEquals(quest.minPartySize, state.minPartySize)
    assertFalse(state.exactPartySize)

    reservations.updateStatus(id, ReservationStatus.REJECTED)
    dispatcher.scheduler.advanceUntilIdle()
    assertFalse(state.partySizeLocked)
    viewModel.incrementPartySize()
    assertEquals(quest.minPartySize + 1, state.minPartySize)
  }

  @Test
  fun aReservationArrivingPutsTheQuestsPartySizeBack() = runTest {
    open()
    viewModel.incrementPartySize()
    reservations.createReservation(newPendingReservation(QUEST_ID, VENUE_ID, "Ana", 0), "explorer")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(state.partySizeLocked)
    assertEquals(quest.minPartySize, state.minPartySize)
  }

  @Test
  fun thePartySizeStaysLockedIfReservationsCantBeRead() {
    val failing =
        object : ReservationRepository by reservations {
          override fun observeForVenue(venueId: String) = flow<Nothing> { throw IOException() }
        }
    open(reservationRepository = failing)
    assertTrue(state.partySizeLocked)
  }

  // Nothing to save over: an empty edit form must never create a quest
  @Test
  fun anUnavailableQuestIsNeverSaved() {
    val scripted = ScriptedQuestRepository(quest = { null })
    open(scripted)
    viewModel.updateTitle("Title")
    viewModel.updateDescription("Description")
    viewModel.updateRequirements("Requirements")
    viewModel.submit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(SaveState.Idle, state.saveState)
    assertTrue(scripted.created.isEmpty())
    assertTrue(scripted.updated.isEmpty())
  }

  // The venue must pick a new expiry, or remove the reward's, before saving
  @Test
  fun aRewardThatHasExpiredBlocksSaving() {
    val lastDay = LocalDate.of(2026, 11, 1)
    val expired =
        RewardFormState(type = RewardType.OTHER, description = "A tour", expiryDate = lastDay)
            .toReward(ZoneOffset.UTC)
    val scripted = ScriptedQuestRepository(quest = { quest.copy(reward = expired) })
    open(scripted)

    assertTrue(state.reward.expiryInPast)
    assertFalse(state.canSave)
    viewModel.submit()
    assertTrue(scripted.updated.isEmpty())

    viewModel.updateExpiryDate(null)
    assertTrue(state.canSave)
  }

  private companion object {
    const val VENUE_ID = "venue-1"
    const val QUEST_ID = "quest-1"
  }
}
