// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
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
  private fun open(questRepository: QuestRepository = quests, questId: String = QUEST_ID) {
    val venue = testVenue(id = VENUE_ID).copy(radiusMeters = 25)
    viewModel =
        CreateQuestViewModel(
            FakeAuthRepository(VENUE_ID),
            FakeVenueRepository(listOf(venue)),
            questRepository,
            clock,
            questId,
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

  @Test
  fun aQuestThatCantBeReadIsUnavailable() {
    open(ScriptedQuestRepository(quest = { throw IOException("offline") }))
    assertTrue(state.questUnavailable)
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
