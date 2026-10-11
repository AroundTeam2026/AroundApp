// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.TestAvatar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the nearby quest pager: what each page shows, paging, the taps, and its two states. */
@RunWith(AndroidJUnit4::class)
class NearbyQuestPagerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /** The pager's quests; tests change them to stand for live updates. */
  private var quests by mutableStateOf(emptyList<NearbyQuest>())
  private val clicked = mutableListOf<NearbyQuest>()
  private val viewed = mutableListOf<NearbyQuest>()

  private fun row(
      id: String,
      title: String = "Quest $id",
      venueName: String = "Venue $id",
      reward: Reward? = null,
      minPartySize: Int = 1,
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
          distanceMeters = distanceMeters,
      )

  private fun rows(count: Int) = (1..count).map { row("q$it") }

  private fun show(shown: List<NearbyQuest>, locationOn: Boolean = true) {
    quests = shown
    composeTestRule.setContent {
      NearbyQuestPager(
          quests = quests,
          locationOn = locationOn,
          onQuestClick = { clicked += it },
          onViewQuest = { viewed += it },
      )
    }
  }

  private fun node(tag: String): SemanticsNodeInteraction =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun previous() =
      composeTestRule.onNodeWithContentDescription(context.getString(R.string.map_nearby_previous))

  private fun next() =
      composeTestRule.onNodeWithContentDescription(context.getString(R.string.map_nearby_next))

  /** Asserts the page of the [page]th quest (from 1) of [count] is the one on screen. */
  private fun assertShows(title: String, page: Int, count: Int, locationOn: Boolean = true) {
    composeTestRule.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
    val counter = if (locationOn) R.string.map_nearby_closest_first else R.string.map_nearby_by_name
    node(C.Tag.NEARBY_COUNTER).assertTextEquals(context.getString(counter, page, count))
  }

  @Test
  fun opensOnTheFirstQuest() {
    show(rows(3))

    composeTestRule.onNodeWithText(context.getString(R.string.map_nearby_title)).assertIsDisplayed()
    assertShows("Quest q1", page = 1, count = 3)
    node(C.Tag.NEARBY_EMPTY).assertDoesNotExist()
  }

  @Test
  fun aPageShowsItsQuestVenueAvatarAndChips() {
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
  fun nextShowsTheFollowingQuest() {
    show(rows(3))

    next().performClick()

    assertShows("Quest q2", page = 2, count = 3)
  }

  @Test
  fun previousGoesBackAQuest() {
    show(rows(3))
    next().performClick()

    previous().performClick()

    assertShows("Quest q1", page = 1, count = 3)
  }

  @Test
  fun previousOnTheFirstQuestWrapsToTheLast() {
    show(rows(3))

    previous().performClick()

    assertShows("Quest q3", page = 3, count = 3)
  }

  @Test
  fun nextOnTheLastQuestWrapsToTheFirst() {
    show(rows(3))
    previous().performClick()
    assertShows("Quest q3", page = 3, count = 3)

    next().performClick()

    assertShows("Quest q1", page = 1, count = 3)
  }

  @Test
  fun swipingShowsTheNextQuest() {
    show(rows(3))

    node(C.Tag.NEARBY_PAGES).performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()

    assertShows("Quest q2", page = 2, count = 3)
  }

  @Test
  fun aSingleQuestHasNothingToPageTo() {
    show(rows(1))

    previous().assertIsNotEnabled()
    next().assertIsNotEnabled()
    composeTestRule.onAllNodesWithTag(C.Tag.NEARBY_DOT, useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun severalQuestsCanBePaged() {
    // The opposite of a single quest, so the arrows can't be disabled for good
    show(rows(2))

    previous().assertIsEnabled()
    next().assertIsEnabled()
  }

  @Test
  fun tappingThePageReportsItsQuest() {
    val shown = rows(3)
    show(shown)
    next().performClick()

    node(C.Tag.NEARBY_PAGE).performClick()

    assertEquals(listOf(shown[1]), clicked)
    assertEquals(emptyList<NearbyQuest>(), viewed)
  }

  @Test
  fun viewQuestReportsTheShownQuest() {
    val shown = rows(3)
    show(shown)
    next().performClick()

    node(C.Tag.NEARBY_VIEW).performClick()

    assertEquals(listOf(shown[1]), viewed)
    assertEquals(emptyList<NearbyQuest>(), clicked)
  }

  @Test
  fun withoutLocationTheQuestsAreByVenueNameWithAHint() {
    show(rows(2), locationOn = false)

    assertShows("Quest q1", page = 1, count = 2, locationOn = false)
    node(C.Tag.NEARBY_LOCATION_HINT).assertIsDisplayed()
  }

  @Test
  fun withLocationThereIsNoHint() {
    show(rows(2), locationOn = true)

    node(C.Tag.NEARBY_LOCATION_HINT).assertDoesNotExist()
  }

  @Test
  fun noQuestsShowsTheEmptyStateAndNothingToPage() {
    show(emptyList())

    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()
    node(C.Tag.NEARBY_COUNTER).assertDoesNotExist()
    node(C.Tag.NEARBY_PAGES).assertDoesNotExist()
    node(C.Tag.NEARBY_VIEW).assertDoesNotExist()
    previous().assertDoesNotExist()
    next().assertDoesNotExist()
    node(C.Tag.NEARBY_LOCATION_HINT).assertDoesNotExist()
  }

  @Test
  fun noQuestsWithoutLocationShowsTheHintAndTheEmptyState() {
    show(emptyList(), locationOn = false)

    node(C.Tag.NEARBY_LOCATION_HINT).assertIsDisplayed()
    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()
  }

  @Test
  fun questsArrivingAfterTheListEmptiedArePagedFromTheFirst() {
    show(rows(3))
    previous().performClick()
    assertShows("Quest q3", page = 3, count = 3)
    quests = emptyList()
    node(C.Tag.NEARBY_EMPTY).assertIsDisplayed()

    // The quest that was shown comes back with others: the pager starts over, closest first
    quests = rows(4)

    assertShows("Quest q1", page = 1, count = 4)
    node(C.Tag.NEARBY_EMPTY).assertDoesNotExist()
  }

  @Test
  fun quickTapsOnNextEachMoveAQuest() {
    show(rows(4))
    composeTestRule.mainClock.autoAdvance = false

    next().performClick()
    // The first move is under way, but its page isn't the current one yet
    composeTestRule.mainClock.advanceTimeByFrame()
    next().performClick()
    composeTestRule.mainClock.autoAdvance = true

    assertShows("Quest q3", page = 3, count = 4)
  }

  @Test
  fun quickTapsOnPreviousEachMoveAQuest() {
    show(rows(4))
    composeTestRule.mainClock.autoAdvance = false

    previous().performClick()
    composeTestRule.mainClock.advanceTimeByFrame()
    previous().performClick()
    composeTestRule.mainClock.autoAdvance = true

    assertShows("Quest q3", page = 3, count = 4)
  }

  @Test
  fun theCounterNeverPassesTheCountWhileTheLastQuestGoes() {
    val (a, b, c) = rows(3)
    show(listOf(a, b, c))
    previous().performClick()
    assertShows("Quest q3", page = 3, count = 3)
    composeTestRule.mainClock.autoAdvance = false

    quests = listOf(a, b)
    // With the clock paused, the change must be announced for the next frame to recompose
    Snapshot.sendApplyNotifications()
    // The frame where the pager hasn't yet moved back to the new last page
    composeTestRule.mainClock.advanceTimeByFrame()

    node(C.Tag.NEARBY_COUNTER)
        .assertTextEquals(context.getString(R.string.map_nearby_closest_first, 2, 2))
  }

  @Test
  fun thePageTellsScreenReadersWhatATapDoes() {
    show(rows(1))

    composeTestRule
        .onNodeWithTag(C.Tag.NEARBY_PAGE)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assert(
            SemanticsMatcher("taps show the quest on the map") {
              it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                  context.getString(R.string.map_nearby_show_on_map)
            }
        )
  }

  @Test
  fun theShownQuestStaysShownWhenOthersArriveBeforeIt() {
    val (a, b, c) = rows(3)
    show(listOf(a, b, c))
    next().performClick()
    assertShows("Quest q2", page = 2, count = 3)

    // A quest nearer than the shown one appears, pushing it back a page
    quests = listOf(row("new"), a, b, c)

    assertShows("Quest q2", page = 3, count = 4)
  }

  @Test
  fun whenTheShownQuestGoesTheNextOneTakesItsPlace() {
    val (a, b, c) = rows(3)
    show(listOf(a, b, c))
    next().performClick()

    quests = listOf(a, c)

    assertShows("Quest q3", page = 2, count = 2)
  }

  @Test
  fun whenTheLastQuestGoesTheNewLastIsShown() {
    val (a, b, c) = rows(3)
    show(listOf(a, b, c))
    previous().performClick()
    assertShows("Quest q3", page = 3, count = 3)

    quests = listOf(a, b)

    assertShows("Quest q2", page = 2, count = 2)
  }

  @Test
  fun aFewQuestsHaveADotEachAndTheShownOneIsSelected() {
    show(rows(3))
    next().performClick()

    val dots = composeTestRule.onAllNodesWithTag(C.Tag.NEARBY_DOT, useUnmergedTree = true)
    dots.assertCountEquals(3)
    dots[0].assertIsNotSelected()
    dots[1].assertIsSelected()
    dots[2].assertIsNotSelected()
  }

  @Test
  fun manyQuestsShowFiveDotsWithTheLastSelectedOnTheLastQuest() {
    show(rows(12))

    previous().performClick()

    assertShows("Quest q12", page = 12, count = 12)
    val dots = composeTestRule.onAllNodesWithTag(C.Tag.NEARBY_DOT, useUnmergedTree = true)
    dots.assertCountEquals(5)
    dots[4].assertIsSelected()
    dots[0].assertIsNotSelected()
  }
}
