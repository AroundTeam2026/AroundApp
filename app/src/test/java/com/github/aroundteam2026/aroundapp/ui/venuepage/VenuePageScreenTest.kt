// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.ScriptedQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class VenuePageScreenTest {
  @get:Rule val compose = createComposeRule()

  private var backCalls = 0
  private val tapped = mutableListOf<String>()
  private var retries = 0
  private val detailsOpened = mutableListOf<String>()
  private var detailsClosed = 0

  private fun show(
      state: VenuePageUiState,
      questRow: VenuePageQuestRow = DefaultQuestRow,
      bottomBar: @Composable (VenuePageQuest?) -> Unit = {},
  ) {
    compose.setContent {
      AroundAppTheme {
        VenuePageContent(
            state,
            onBack = { backCalls++ },
            onQuestTapped = { tapped += it },
            onRetry = { retries++ },
            onQuestDetails = { detailsOpened += it },
            onQuestDetailsClosed = { detailsClosed++ },
            questRow = questRow,
            bottomBar = bottomBar,
        )
      }
    }
  }

  @Test
  fun loadingShowsOnlyTheSpinner() {
    show(VenuePageUiState())

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_LOADING).assertIsDisplayed()
    listOf(C.Tag.VENUE_PAGE_HEADER, C.Tag.VENUE_PAGE_NOT_FOUND, C.Tag.VENUE_PAGE_FAILED).forEach {
      compose.onNodeWithTag(it).assertDoesNotExist()
    }
  }

  @Test
  fun notFoundSaysSo() {
    show(VenuePageUiState(status = VenuePageStatus.NOT_FOUND))

    compose
        .onNodeWithTag(C.Tag.VENUE_PAGE_NOT_FOUND)
        .assertTextEquals("This venue isn't on Around anymore.")
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_HEADER).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_RETRY).assertDoesNotExist()
  }

  @Test
  fun failedOffersARetry() {
    show(VenuePageUiState(status = VenuePageStatus.FAILED))

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_FAILED).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_HEADER).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_RETRY).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun showsTheVenuesHeader() {
    show(LOADED)

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_AVATAR).assertIsDisplayed()
    compose.onNodeWithText("CL").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_NAME).assertTextEquals("Café Lumen")
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_ADDRESS).assertTextEquals("Rue du Pont 1")
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_LOADING).assertDoesNotExist()
  }

  @Test
  fun aVenueWithoutAnAddressShowsNone() {
    show(LOADED.copy(address = null))

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_NAME).assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_ADDRESS).assertDoesNotExist()
  }

  @Test
  fun noQuestsShowsTheEmptyState() {
    show(LOADED.copy(quests = emptyList()))

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_HEADER).assertIsDisplayed()
    compose
        .onNodeWithTag(C.Tag.VENUE_PAGE_EMPTY)
        .assertTextEquals("No quests here right now. Check back soon.")
  }

  @Test
  fun theQuestsCardCountsTheQuests() {
    show(LOADED)

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_QUESTS_CARD).assertIsDisplayed()
    compose.onNodeWithText("Quests").assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.VENUE_PAGE_QUEST_COUNT).assertTextEquals("2 active")
  }

  @Test
  fun noQuestsShowsNoQuestsCard() {
    show(LOADED.copy(quests = emptyList()))

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_QUESTS_CARD).assertDoesNotExist()
  }

  @Test
  fun theQuestRowSlotDrawsEachQuestInOrderWithItsPick() {
    val drawn = mutableListOf<Pair<String, Boolean>>()
    show(
        LOADED.copy(selectedQuestId = "b"),
        questRow = { quest, selected, onClick, _ ->
          drawn += quest.questId to selected
          Text(quest.title, Modifier.testTag("row_${quest.questId}").clickable(onClick = onClick))
        },
    )

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_EMPTY).assertDoesNotExist()
    compose.onNodeWithTag("row_a").assertTextEquals("Quest a")
    compose.onNodeWithTag("row_b").assertTextEquals("Quest b")
    assertEquals(listOf("a" to false, "b" to true), drawn.distinct())

    compose.onNodeWithTag("row_a").performClick()
    assertEquals(listOf("a"), tapped)
  }

  @Test
  fun theDefaultRowsAreQuestRowsThatPickOnTap() {
    show(LOADED.copy(selectedQuestId = "b"))

    compose.onAllNodesWithTag(C.Tag.VENUE_QUEST_ROW).assertCountEquals(2)
    compose.onNodeWithText("Quest b").assertIsSelected()
    compose.onNodeWithText("Quest a").assertIsNotSelected().performClick()

    assertEquals(listOf("a"), tapped)
  }

  @Test
  fun theQuestRowsAreOneChoice() {
    show(LOADED)

    compose
        .onAllNodesWithTag(C.Tag.VENUE_QUEST_ROW)[0]
        .onParent()
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
  }

  @Test
  fun theBottomBarSitsUnderTheListWithThePickedQuest() {
    val given = mutableListOf<VenuePageQuest?>()
    show(
        LOADED.copy(selectedQuestId = "b"),
        bottomBar = {
          given += it
          Text("bar", Modifier.testTag("bar"))
        },
    )

    val list = compose.onNodeWithTag(C.Tag.VENUE_PAGE_QUESTS).fetchSemanticsNode().boundsInRoot
    val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
    assertEquals(list.bottom, bar.top)
    assertEquals("b", given.last()?.questId)
  }

  @Test
  fun theBottomBarIsLeftOutUntilTheVenueLoads() {
    var state by mutableStateOf(VenuePageUiState())
    val shownWhile = mutableSetOf<VenuePageStatus>()
    compose.setContent {
      AroundAppTheme {
        VenuePageContent(state, {}, {}, {}, bottomBar = { shownWhile += state.status })
      }
    }

    listOf(VenuePageStatus.NOT_FOUND, VenuePageStatus.FAILED, VenuePageStatus.LOADING).forEach {
      state = VenuePageUiState(status = it)
      compose.waitForIdle()
    }

    assertEquals(emptySet<VenuePageStatus>(), shownWhile)
  }

  @Test
  fun aRowsChevronAsksForItsDetails() {
    show(LOADED)

    compose.onNodeWithContentDescription("Details of Quest b").performClick()

    assertEquals(listOf("b"), detailsOpened)
    assertEquals(emptyList<String>(), tapped)
  }

  @Test
  fun theDetailQuestIsShownInASheet() {
    show(LOADED.copy(detailQuestId = "b"))

    compose
        .onNodeWithTag(C.Tag.QUEST_DETAIL_TITLE, useUnmergedTree = true)
        .assertTextEquals("Quest b")
  }

  @Test
  fun noDetailQuestShowsNoSheet() {
    show(LOADED)

    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).assertDoesNotExist()
  }

  @Test
  fun closingTheSheetSaysSo() {
    show(LOADED.copy(detailQuestId = "a"))

    compose.onNodeWithContentDescription("Close quest details").performClick()
    compose.waitForIdle()

    assertEquals(1, detailsClosed)
  }

  @Test
  fun theScreenOpensAndClosesTheDetailsThroughItsViewModel() {
    showScreen(activeQuestsByVenue = { flowOf(listOf(testQuest(id = "a", venueId = VENUE_ID))) })

    compose.onNodeWithContentDescription("Details of Order the secret menu").performClick()
    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).assertIsDisplayed()

    compose.onNodeWithContentDescription("Close quest details").performClick()
    compose.waitForIdle()
    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).assertDoesNotExist()
  }

  @Test
  fun theBackArrowCallsBack() {
    show(LOADED)

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_BACK).performClick()

    assertEquals(1, backCalls)
  }

  @Test
  fun theScreenPicksAndUnpicksThroughItsViewModel() {
    showScreen(activeQuestsByVenue = { flowOf(listOf(testQuest(id = "a", venueId = VENUE_ID))) })

    compose.onNodeWithText("Order the secret menu").performClick().assertIsSelected()
    compose.onNodeWithText("Order the secret menu").performClick().assertIsNotSelected()
  }

  @Test
  fun theScreenRetriesThroughItsViewModel() {
    var calls = 0
    showScreen(
        activeQuestsByVenue = {
          if (calls++ == 0) failing() else flowOf(listOf(testQuest(id = "a", venueId = VENUE_ID)))
        }
    )

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_RETRY).performClick()

    compose.onNodeWithTag(C.Tag.VENUE_PAGE_NAME).assertTextEquals("Café Lumen")
    compose.onNodeWithText("Order the secret menu").assertIsDisplayed()
  }

  private fun showScreen(activeQuestsByVenue: (String) -> Flow<List<Quest>>) {
    val viewModel =
        VenuePageViewModel(
            VENUE_ID,
            FakeVenueRepository(listOf(testVenue(id = VENUE_ID, name = "Café Lumen"))),
            ScriptedQuestRepository(activeQuestsByVenue = activeQuestsByVenue),
            FakeReservationRepository(),
            "explorer",
        )
    compose.setContent { AroundAppTheme { VenuePageScreen(viewModel, onBack = {}) } }
  }

  private fun <T> failing(): Flow<T> = flow { throw IOException("offline") }

  private companion object {
    const val VENUE_ID = "cafe"

    fun quest(id: String) =
        VenuePageQuest(
            questId = id,
            title = "Quest $id",
            description = "",
            requirements = "",
            proofType = ProofType.PHOTO,
            minPartySize = 1,
            reward = null,
        )

    val LOADED =
        VenuePageUiState(
            status = VenuePageStatus.LOADED,
            venueName = "Café Lumen",
            avatar = VenueAvatar.Initials("CL"),
            address = "Rue du Pont 1",
            quests = listOf(quest("a"), quest("b")),
        )
  }
}
