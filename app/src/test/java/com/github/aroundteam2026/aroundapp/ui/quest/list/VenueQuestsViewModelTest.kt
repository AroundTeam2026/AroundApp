// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.testQuest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenueQuestsViewModelTest {
  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** The Quests tab of the signed-in [VENUE_ID], reading [quests]. */
  private fun viewModel(quests: QuestRepository) =
      VenueQuestsViewModel(FakeAuthRepository(VENUE_ID), quests)

  @Test
  fun listsOnlyTheVenuesQuestsNewestFirst() {
    val older = testQuest(id = "older", venueId = VENUE_ID, createdAt = OLDER)
    val newer =
        testQuest(
            id = "newer",
            venueId = VENUE_ID,
            status = QuestStatus.ARCHIVED,
            createdAt = NEWER,
        )
    val otherVenues = testQuest(id = "other", venueId = OTHER_VENUE_ID, createdAt = NEWER)
    val quests = FakeQuestRepository(initialQuests = listOf(older, otherVenues, newer))

    assertEquals(
        VenueQuestsUiState.Loaded(listOf(newer, older)),
        viewModel(quests).uiState.value,
    )
  }

  @Test
  fun aQuestSavedLaterIsListed() = runTest {
    val quests = FakeQuestRepository()
    val viewModel = viewModel(quests)
    assertEquals(VenueQuestsUiState.Loaded(emptyList()), viewModel.uiState.value)

    val id = quests.createQuest(testQuest(venueId = VENUE_ID)).getOrThrow()

    assertEquals(
        listOf(id),
        (viewModel.uiState.value as VenueQuestsUiState.Loaded).quests.map { it.id },
    )
  }

  @Test
  fun aListenerThatFailsShowsTheError() {
    val failing =
        object : QuestRepository by FakeQuestRepository() {
          override fun observeQuestsByVenue(venueId: String): Flow<List<Quest>> = flow {
            throw IOException()
          }
        }
    assertEquals(VenueQuestsUiState.Failed, viewModel(failing).uiState.value)
  }

  @Test
  fun nothingIsListedWithoutASignedInVenue() {
    val viewModel = VenueQuestsViewModel(FakeAuthRepository(), FakeQuestRepository())
    assertEquals(VenueQuestsUiState.Loading, viewModel.uiState.value)
  }

  private companion object {
    const val VENUE_ID = "venue-1"
    const val OTHER_VENUE_ID = "venue-2"
    const val OLDER = 1_000L
    const val NEWER = 2_000L
  }
}
