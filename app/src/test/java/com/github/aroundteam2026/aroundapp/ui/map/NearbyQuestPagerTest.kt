// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import com.github.aroundteam2026.aroundapp.ui.quest.TestAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the nearby quest list: what each card shows, the list, the taps, and its two states. */
@RunWith(AndroidJUnit4::class)
class NearbyQuestPagerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /** The list's quests; tests change them to stand for live updates. */
  private var quests by mutableStateOf(emptyList<NearbyQuest>())
  private val opened = mutableListOf<NearbyQuest>()
  private val shownOnMap = mutableListOf<NearbyQuest>()
  private val accepted = mutableListOf<NearbyQuest>()
  /** The filters the list shows; a change from the list is applied, as the map does. */
  private var filters by mutableStateOf(NearbyFilters())
  /** Where accepting stands, as the pager shows it; a change redraws it, as the map does. */
  private var shownAcceptState by mutableStateOf<NearbyAcceptUiState?>(null)
  private val filterChanges = mutableListOf<NearbyFilters>()

  private fun row(
      id: String,
      title: String = "Quest $id",
      venueName: String = "Venue $id",
      reward: Reward? = null,
      minPartySize: Int = 1,
      exactPartySize: Boolean = false,
      distanceMeters: Double? = null,
  ) =
      NearbyQuest(
          venueId = "venue-$id",
          venueName = venueName,
          avatar = TestAvatar(id),
          questId = id,
          title = title,
          reward = reward,
          minPartySize = minPartySize,
          exactPartySize = exactPartySize,
          distanceMeters = distanceMeters,
      )

  private fun rows(count: Int) = (1..count).map { row("q$it") }

  private fun show(
      shown: List<NearbyQuest>,
      locationOn: Boolean = true,
      onClose: (() -> Unit)? = null,
      withFilters: Boolean = false,
      acceptState: NearbyAcceptUiState? = null,
  ) {
    quests = shown
    shownAcceptState = acceptState
    composeTestRule.setContent {
      NearbyQuestPager(
          quests = quests,
          locationOn = locationOn,
          onOpenQuest = { opened += it },
          onShowOnMap = { shownOnMap += it },
          onClose = onClose,
          filters = filters,
          onFiltersChange =
              if (withFilters) {
                {
                  filterChanges += it
                  filters = it
                }
              } else {
                null
              },
          acceptState = shownAcceptState,
          onAccept = { accepted += it },
      )
    }
  }

  private fun filterButton() =
      composeTestRule.onNodeWithContentDescription(context.getString(R.string.map_nearby_filters))

  private fun option(label: Int) = composeTestRule.onNodeWithText(context.getString(label))

  private fun close() =
      composeTestRule.onNodeWithContentDescription(context.getString(R.string.map_nearby_close))

  private fun node(tag: String): SemanticsNodeInteraction =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun list() = node(C.Tag.NEARBY_LIST)

  private fun title(text: String) = composeTestRule.onNodeWithText(text, useUnmergedTree = true)

  /** The single card's "Accept quest", merged with its label. */
  private fun acceptButton() = composeTestRule.onNodeWithTag(C.Tag.NEARBY_ACCEPT)

  /** Asserts the header counts [count] quests, ordered as [order] says. */
  private fun assertHeader(count: Int, @StringRes order: Int = R.string.map_nearby_closest_first) {
    node(C.Tag.NEARBY_COUNTER)
        .assertTextEquals(nearbyHeader(context, count, context.getString(order)))
  }

  @Test
  fun listsTheQuestsAndCountsThemAll() {
    show(rows(3))

    composeTestRule.onNodeWithText(context.getString(R.string.map_nearby_title)).assertIsDisplayed()
    title("Quest q1").assertIsDisplayed()
    assertHeader(3)
    node(C.Tag.NEARBY_EMPTY).assertDoesNotExist()
  }

  @Test
  fun aSingleQuestIsCountedAsOne() {
    show(rows(1))

    assertHeader(1)
  }

  @Test
  fun theQuestsAreListedInOrderOneUnderTheOther() {
    show(rows(3))

    val first = title("Quest q1").getBoundsInRoot()
    val second = title("Quest q2").getBoundsInRoot()
    assertTrue("q1 above q2", first.bottom < second.top)
  }

  @Test
  fun theListScrollsToTheLastQuest() {
    show(rows(6))
    // Only the cards near the top are composed at first
    title("Quest q6").assertDoesNotExist()

    list().performScrollToNode(hasText("Quest q6"))

    title("Quest q6").assertIsDisplayed()
  }

  @Test
  fun aCardFurtherDownCanBeOpenedShownOnTheMapAndAccepted() {
    val shown = rows(5)
    show(shown, acceptState = ACCEPTABLE)
    list().performScrollToNode(hasTestTag("${C.Tag.NEARBY_QUEST}_q4"))

    composeTestRule.inQuest("q4", C.Tag.NEARBY_CARD).performClick()
    composeTestRule.inQuest("q4", C.Tag.NEARBY_SHOW_ON_MAP).performClick()
    composeTestRule.inQuest("q4", C.Tag.NEARBY_ACCEPT).performClick()

    assertEquals(listOf(shown[3]), opened)
    assertEquals(listOf(shown[3]), shownOnMap)
    assertEquals(listOf(shown[3]), accepted)
  }

  @Test
  fun aQuestLeavingTheListDoesNotMoveTheOnesInView() {
    val shown = rows(6)
    show(shown)
    list().performScrollToIndex(2)
    val top = title("Quest q3").getBoundsInRoot().top

    // A quest above the ones in view goes, as when it is taken or filtered out
    quests = shown.drop(1)
    composeTestRule.waitForIdle()

    assertDpEquals("q3 top", top, title("Quest q3").getBoundsInRoot().top)
  }

  @Test
  fun aQuestArrivingAboveDoesNotMoveTheOnesInView() {
    val shown = rows(4)
    show(shown)
    list().performScrollToIndex(2)
    val top = title("Quest q3").getBoundsInRoot().top

    // A quest nearer than the ones in view appears above them
    quests = listOf(row("new")) + shown
    composeTestRule.waitForIdle()

    assertDpEquals("q3 top", top, title("Quest q3").getBoundsInRoot().top)
    assertHeader(5)
  }

  @Test
  fun questsArrivingAfterTheListEmptiedStartFromTheTop() {
    show(rows(4))
    list().performScrollToIndex(3)
    quests = emptyList()
    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()

    quests = rows(4)

    title("Quest q1").assertIsDisplayed()
    assertHeader(4)
    node(C.Tag.NEARBY_EMPTY).assertDoesNotExist()
  }

  @Test
  fun aCardShowsItsQuestVenueAvatarAndChips() {
    show(
        listOf(
            row(
                "fox",
                title = "Find the hidden fox",
                venueName = "Café Lumen",
                reward = Reward.Other("Free coffee"),
                minPartySize = 3,
                distanceMeters = 120.0,
            )
        )
    )

    node(C.Tag.NEARBY_QUEST_TITLE).assertTextEquals("Find the hidden fox")
    node(C.Tag.NEARBY_VENUE).assertTextEquals("Café Lumen")
    composeTestRule
        .onNode(
            hasTestTag(TestAvatar.tagOf("fox")) and hasAnyAncestor(hasTestTag(C.Tag.NEARBY_AVATAR)),
            useUnmergedTree = true,
        )
        .assertExists()
    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("Free coffee")
    node(C.Tag.QUEST_CHIP_PARTY)
        .assertTextEquals(context.getString(R.string.map_card_party_size, 3))
    node(C.Tag.QUEST_CHIP_DISTANCE)
        .assertTextEquals(context.getString(R.string.map_card_distance_meters, "120"))
  }

  @Test
  fun anExactQuestsChipHasNoPlus() {
    show(listOf(row("fox", minPartySize = 3, exactPartySize = true)))

    node(C.Tag.QUEST_CHIP_PARTY)
        .assertTextEquals(context.getString(R.string.map_card_exact_party_size, 3))
  }

  @Test
  fun theCloseButtonClosesTheList() {
    var closed = 0
    show(rows(2), onClose = { closed++ })

    close().performClick()

    assertEquals(1, closed)
  }

  @Test
  fun withNothingToCloseThereIsNoCloseButton() {
    show(rows(2))

    close().assertDoesNotExist()
  }

  @Test
  fun anEmptyListCanStillBeClosed() {
    // The empty state has its own header, so the button must be there too
    var closed = 0
    show(emptyList(), onClose = { closed++ })

    close().performClick()

    assertEquals(1, closed)
  }

  @Test
  fun tappingAnywhereOnTheCardOpensItsQuest() {
    val shown = rows(1)
    show(shown)

    // Away from the middle, on the venue's name, the line under the title
    node(C.Tag.NEARBY_VENUE).performClick()

    assertEquals(shown, opened)
    assertEquals(emptyList<NearbyQuest>(), shownOnMap)
  }

  @Test
  fun thePinShowsItsCardsQuestOnTheMap() {
    val shown = rows(1)
    show(shown)

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.map_nearby_show_on_map))
        .performClick()

    assertEquals(shown, shownOnMap)
    assertEquals(emptyList<NearbyQuest>(), opened)
  }

  @Test
  fun withoutAcceptStateThereIsNoAcceptButton() {
    show(rows(1))

    node(C.Tag.NEARBY_SHOW_ON_MAP).assertIsDisplayed()
    node(C.Tag.NEARBY_ACCEPT).assertDoesNotExist()
  }

  @Test
  fun acceptReportsItsCardsQuest() {
    val shown = rows(1)
    show(shown, acceptState = ACCEPTABLE)

    acceptButton()
        .assertTextEquals(context.getString(R.string.accept_quest))
        .assertIsEnabled()
        .performClick()

    assertEquals(shown, accepted)
    assertEquals(emptyList<NearbyQuest>(), opened)
  }

  @Test
  fun anAcceptedQuestSaysSoAndCantBeAcceptedAgain() {
    show(rows(1), acceptState = ACCEPTABLE.copy(acceptedQuestIds = setOf("q1")))

    acceptButton()
        .assertTextEquals(context.getString(R.string.accept_quest_accepted_short))
        .assertIsNotEnabled()
  }

  @Test
  fun aFailedQuestOffersToTryAgain() {
    show(rows(1), acceptState = ACCEPTABLE.copy(failedQuestIds = setOf("q1")))

    acceptButton().assertTextEquals(context.getString(R.string.try_again)).assertIsEnabled()
  }

  @Test
  fun aQuestBeingAcceptedShowsAPlaceholderThatCantBeTapped() {
    show(
        rows(1),
        acceptState = ACCEPTABLE.copy(acceptingQuestIds = setOf("q1")),
    )

    acceptButton()
        .assertContentDescriptionEquals(context.getString(R.string.accept_quest_accepting))
        .assertIsNotEnabled()
        .performClick()
    assertEquals(emptyList<NearbyQuest>(), accepted)
  }

  @Test
  fun whileAcceptingIsUnavailableTheButtonCantBeTapped() {
    show(rows(1), acceptState = NearbyAcceptUiState(available = false))

    acceptButton().assertIsNotEnabled()
  }

  @Test
  fun whileOfflineTheButtonCantBeTappedAndBackOnlineItCan() {
    show(rows(1), acceptState = ACCEPTABLE.copy(offline = true))

    acceptButton().assertIsNotEnabled().performClick()
    assertEquals(emptyList<NearbyQuest>(), accepted)

    shownAcceptState = ACCEPTABLE
    acceptButton().assertIsEnabled().performClick()
    assertEquals(quests, accepted)
  }

  @Test
  fun withoutLocationTheQuestsAreByVenueNameWithAHint() {
    show(rows(2), locationOn = false)

    assertHeader(2, R.string.map_nearby_by_name)
    node(C.Tag.NEARBY_LOCATION_HINT).assertIsDisplayed()
  }

  @Test
  fun withLocationThereIsNoHint() {
    show(rows(2), locationOn = true)

    node(C.Tag.NEARBY_LOCATION_HINT).assertDoesNotExist()
  }

  @Test
  fun noQuestsShowsTheEmptyStateAndNoList() {
    show(emptyList())

    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()
    node(C.Tag.NEARBY_COUNTER).assertDoesNotExist()
    list().assertDoesNotExist()
    node(C.Tag.NEARBY_SHOW_ON_MAP).assertDoesNotExist()
    node(C.Tag.NEARBY_LOCATION_HINT).assertDoesNotExist()
  }

  @Test
  fun noQuestsWithoutLocationShowsTheHintAndTheEmptyState() {
    show(emptyList(), locationOn = false)

    node(C.Tag.NEARBY_LOCATION_HINT).assertIsDisplayed()
    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()
  }

  @Test
  fun theCardTellsScreenReadersWhatATapDoes() {
    show(rows(1))

    composeTestRule
        .onNodeWithTag(C.Tag.NEARBY_CARD)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assert(
            SemanticsMatcher("taps open the quest's details") {
              it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                  context.getString(R.string.map_nearby_open_details)
            }
        )
  }

  @Test
  fun withoutFiltersTheHeaderHasNoFilterButton() {
    show(rows(2))

    filterButton().assertDoesNotExist()
  }

  @Test
  fun theFilterButtonOpensAndClosesTheFilters() {
    show(rows(2), withFilters = true)
    node(C.Tag.NEARBY_FILTERS).assertDoesNotExist()
    filterButton().assertIsNotSelected()

    filterButton().performClick()
    node(C.Tag.NEARBY_FILTERS).assertIsDisplayed()
    // Filled in purple while open
    filterButton().assertIsSelected()

    filterButton().performClick()
    node(C.Tag.NEARBY_FILTERS).assertDoesNotExist()
    filterButton().assertIsNotSelected()
  }

  @Test
  fun choosingAnOptionChangesThatFilterOnly() {
    show(rows(2), withFilters = true)
    filterButton().performClick()

    option(R.string.map_nearby_filter_1_km).performClick()
    option(R.string.map_nearby_filter_discount).performClick()

    assertEquals(
        listOf(
            NearbyFilters(distance = DistanceFilter.WITHIN_1_KM),
            NearbyFilters(DistanceFilter.WITHIN_1_KM, RewardFilter.DISCOUNT),
        ),
        filterChanges,
    )
    option(R.string.map_nearby_filter_1_km).assertIsSelected()
    option(R.string.map_nearby_filter_500_m).assertIsNotSelected()
    option(R.string.map_nearby_filter_discount).assertIsSelected()
    option(R.string.map_nearby_filter_other).assertIsNotSelected()
  }

  @Test
  fun withoutTheLocationOnlyTheRewardCanBeChosen() {
    show(rows(2), locationOn = false, withFilters = true)
    filterButton().performClick()

    option(R.string.map_nearby_filter_500_m).assertIsNotEnabled()
    option(R.string.map_nearby_filter_3_km).assertIsNotEnabled()
    option(R.string.map_nearby_filter_other).assertIsEnabled()
  }

  @Test
  fun activeFiltersReplaceTheOrderInTheHeader() {
    filters = NearbyFilters(DistanceFilter.WITHIN_3_KM, RewardFilter.DISCOUNT)
    show(rows(2), withFilters = true)

    val summary =
        context.getString(
            R.string.map_nearby_pair,
            context.getString(R.string.map_nearby_within_3_km),
            context.getString(R.string.map_nearby_discounts),
        )
    node(C.Tag.NEARBY_COUNTER).assertTextEquals(nearbyHeader(context, 2, summary))
  }

  @Test
  fun whenNothingMatchesTheListSaysSoAndKeepsTheFilters() {
    filters = NearbyFilters(reward = RewardFilter.OTHER)
    show(emptyList(), withFilters = true)

    node(C.Tag.NEARBY_EMPTY).assertTextEquals(context.getString(R.string.map_nearby_no_match))
    node(C.Tag.NEARBY_COUNTER)
        .assertTextEquals(context.getString(R.string.map_nearby_other_rewards))
    filterButton().performClick()
    option(R.string.map_nearby_filter_other).assertIsSelected()
  }
}

/** Where accepting stands once the explorer's reservations are known: a quest can be accepted. */
private val ACCEPTABLE = NearbyAcceptUiState(available = true)
