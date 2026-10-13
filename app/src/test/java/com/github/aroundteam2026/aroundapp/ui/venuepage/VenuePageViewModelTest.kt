// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.lifecycle.viewmodel.CreationExtras
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.rewardExpiringAt
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepository
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenuePageViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  /** The test's clock: [START] plus the virtual time the test has advanced. */
  private val clock = { START + dispatcher.scheduler.currentTime }

  private val venue = testVenue(id = VENUE_ID, name = "Café Lumen", address = "Rue du Pont 1")
  private val venues = FakeVenueRepository(listOf(venue))
  private val quests = MutableStateFlow(listOf(testQuest(id = "a", venueId = VENUE_ID)))
  private var questRepository = ScriptedQuestRepository(questsByVenue = { quests })
  private var venueRepository: VenueRepository = venues

  private val viewModel by lazy {
    VenuePageViewModel(VENUE_ID, venueRepository, questRepository, clock)
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  private fun ids() = state.quests.map { it.questId }

  @Test fun startsLoading() = test { assertEquals(VenuePageUiState(), state) }

  @Test
  fun showsTheVenueAndItsQuests() = test {
    viewModel
    runCurrent()

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertEquals("Café Lumen", state.venueName)
    assertEquals(VenueAvatar.Initials("CL"), state.avatar)
    assertEquals("Rue du Pont 1", state.address)
    assertEquals(listOf("a"), ids())
  }

  @Test
  fun asksForTheQuestsOfItsOwnVenue() = test {
    val asked = mutableListOf<String>()
    questRepository =
        ScriptedQuestRepository(
            questsByVenue = {
              asked += it
              quests
            }
        )
    viewModel
    runCurrent()

    assertEquals(listOf(VENUE_ID), asked)
  }

  @Test
  fun aVenueWithoutValidQuestsIsLoadedWithAnEmptyList() = test {
    quests.value = emptyList()
    viewModel
    runCurrent()

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertTrue(state.quests.isEmpty())
  }

  @Test
  fun anUnknownVenueIsNotFound() = test {
    venueRepository = FakeVenueRepository()
    viewModel
    runCurrent()

    assertEquals(VenuePageUiState(status = VenuePageStatus.NOT_FOUND), state)
  }

  @Test
  fun followsChangesToTheVenue() = test {
    val shown = MutableStateFlow<Venue?>(venue)
    venueRepository = ScriptedVenueRepository(shown)
    viewModel
    runCurrent()

    shown.value = venue.copy(name = "Bar Nocturne", address = null)
    runCurrent()

    assertEquals("Bar Nocturne", state.venueName)
    assertEquals(VenueAvatar.Initials("BN"), state.avatar)
    assertNull(state.address)
  }

  @Test
  fun aVenueCreatedLaterIsShown() = test {
    val shown = MutableStateFlow<Venue?>(null)
    venueRepository = ScriptedVenueRepository(shown)
    viewModel
    runCurrent()
    assertEquals(VenuePageStatus.NOT_FOUND, state.status)

    shown.value = venue
    runCurrent()

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertEquals(listOf("a"), ids())
  }

  @Test
  fun aVenueThatDisappearsIsNotFound() = test {
    val shown = MutableStateFlow<Venue?>(venue)
    venueRepository = ScriptedVenueRepository(shown)
    viewModel
    runCurrent()

    shown.value = null
    runCurrent()

    // Nothing of the venue that was shown is left
    assertEquals(VenuePageUiState(status = VenuePageStatus.NOT_FOUND), state)
  }

  @Test
  fun followsChangesToTheQuests() = test {
    viewModel
    runCurrent()

    quests.value = quests.value + testQuest(id = "b", venueId = VENUE_ID, createdAt = 2_000)
    runCurrent()

    assertEquals(listOf("b", "a"), ids())
  }

  @Test
  fun theFeaturedQuestComesFirst() = test {
    venueRepository = FakeVenueRepository(listOf(venue.copy(featuredQuestId = "a")))
    quests.value = quests.value + testQuest(id = "b", venueId = VENUE_ID, createdAt = 2_000)
    viewModel
    runCurrent()

    assertEquals(listOf("a", "b"), ids())
  }

  @Test
  fun aQuestLeavesThePageWhenItsRewardExpires() = test {
    quests.value =
        listOf(
            testQuest(id = "a", venueId = VENUE_ID),
            testQuest(id = "b", venueId = VENUE_ID, reward = rewardExpiringAt(START + 60_000)),
        )
    viewModel
    runCurrent()
    assertEquals(listOf("a", "b"), ids())

    advanceTimeBy(59_999)
    runCurrent()
    assertEquals(listOf("a", "b"), ids())

    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf("a"), ids())
  }

  @Test
  fun aPickedQuestIsUnpickedWhenItsRewardExpires() = test {
    quests.value =
        listOf(testQuest(id = "a", venueId = VENUE_ID, reward = rewardExpiringAt(START + 1_000)))
    viewModel
    runCurrent()
    viewModel.onQuestSelected("a")

    advanceTimeBy(1_000)
    runCurrent()

    assertNull(state.selectedQuestId)
  }

  @Test
  fun aPickedQuestStaysPickedWhileTheQuestsChange() = test {
    viewModel
    runCurrent()
    viewModel.onQuestSelected("a")

    quests.value = quests.value + testQuest(id = "b", venueId = VENUE_ID)
    runCurrent()

    assertEquals("a", state.selectedQuestId)
  }

  @Test
  fun tappingAQuestPicksIt() = test {
    viewModel
    runCurrent()

    viewModel.onQuestSelected("a")
    assertEquals("a", state.selectedQuestId)

    viewModel.onQuestSelected("a")
    assertNull(state.selectedQuestId)
  }

  @Test
  fun failingQuestsFailThePage() = test {
    questRepository = ScriptedQuestRepository(questsByVenue = { failing() })
    viewModel
    runCurrent()

    assertEquals(VenuePageUiState(status = VenuePageStatus.FAILED), state)
  }

  @Test
  fun aFailingVenueFailsThePage() = test {
    venueRepository = ScriptedVenueRepository(failing())
    viewModel
    runCurrent()

    assertEquals(VenuePageStatus.FAILED, state.status)
  }

  @Test
  fun retryingAfterAFailureListensAgain() = test {
    var fails = true
    questRepository = ScriptedQuestRepository(questsByVenue = { if (fails) failing() else quests })
    viewModel
    runCurrent()
    assertEquals(VenuePageStatus.FAILED, state.status)

    fails = false
    viewModel.onRetry()
    assertEquals(VenuePageStatus.LOADING, state.status)
    runCurrent()

    assertEquals(VenuePageStatus.LOADED, state.status)
    assertEquals(listOf("a"), ids())
  }

  @Test
  fun aLoadedPageThatFailsIsClearedAndRetriesWithNothingPicked() = test {
    // Null stands for the connection dropping while the page is shown
    val live = MutableStateFlow<List<Quest>?>(quests.value)
    questRepository =
        ScriptedQuestRepository(
            questsByVenue = { live.map { it ?: throw IOException("connection lost") } }
        )
    viewModel
    runCurrent()
    viewModel.onQuestSelected("a")

    live.value = null
    runCurrent()
    assertEquals(VenuePageUiState(status = VenuePageStatus.FAILED), state)

    live.value = quests.value
    viewModel.onRetry()
    runCurrent()
    assertEquals(VenuePageStatus.LOADED, state.status)
    assertEquals(listOf("a"), ids())
    assertNull(state.selectedQuestId)
  }

  @Test
  fun retryingWithoutAFailureDoesNothing() = test {
    var listens = 0
    questRepository =
        ScriptedQuestRepository(
            questsByVenue = {
              listens++
              quests
            }
        )
    viewModel
    runCurrent()

    viewModel.onRetry()
    runCurrent()

    assertEquals(1, listens)
    assertEquals(VenuePageStatus.LOADED, state.status)
  }

  @Test
  fun theFactoryBuildsTheViewModelOfItsVenue() = test {
    val built =
        VenuePageViewModel.factory("no-such-venue")
            .create(VenuePageViewModel::class.java, CreationExtras.Empty)
    // Not advanceUntilIdle: the app's demo quests expire by the real clock, not the test's
    runCurrent()

    assertEquals(VenuePageStatus.NOT_FOUND, built.uiState.value.status)
  }

  private fun <T> failing(): Flow<T> = flow { throw IOException("offline") }

  private companion object {
    const val VENUE_ID = "cafe"

    /** When the tests start, in epoch milliseconds. */
    const val START = 1_800_000_000_000L
  }
}

/** A [VenueRepository] whose venue is whatever [venue] emits. */
private class ScriptedVenueRepository(private val venue: Flow<Venue?>) : VenueRepository {
  override fun observeVenue(venueId: String): Flow<Venue?> = venue

  override suspend fun getVenue(venueId: String): Venue? = null

  override suspend fun createVenue(venue: Venue) = Result.success(Unit)

  override suspend fun setArea(
      venueId: String,
      location: Location,
      radiusMeters: Int,
      address: String?,
  ) = Result.success(Unit)
}
