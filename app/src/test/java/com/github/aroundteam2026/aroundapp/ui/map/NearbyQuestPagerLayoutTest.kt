// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.assertDpEquals
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests where the pager puts things and how big they are, as in Figma (frame 2006:688). */
@RunWith(AndroidJUnit4::class)
class NearbyQuestPagerLayoutTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun show(count: Int = 3, withButtons: Boolean = false) {
    val quests =
        (1..count).map {
          NearbyQuest(
              venueId = "venue-$it",
              venueName = "Café Lumière",
              avatar = VenueAvatar.Initials("CL"),
              questId = "q$it",
              title = "Find the hidden fox",
              reward = Reward.Other("15% off hot drinks"),
              minPartySize = 1,
              distanceMeters = 120.0,
          )
        }
    composeTestRule.setContent {
      if (withButtons) NearbyQuestPager(quests, true, {}, {}, onClose = {}, onFiltersChange = {})
      else NearbyQuestPager(quests, true, {}, {})
    }
  }

  private fun bounds(tag: String): DpRect =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

  private fun button(description: Int): SemanticsNodeInteraction =
      composeTestRule.onNodeWithContentDescription(context.getString(description))

  @Test
  fun theFilterButtonIsThirtySixDpAndEightDpBeforeTheClose() {
    show(withButtons = true)

    val filter = button(R.string.map_nearby_filters).getBoundsInRoot()
    assertDpEquals("filter width", 36.dp, filter.right - filter.left)
    assertDpEquals("filter height", 36.dp, filter.bottom - filter.top)
    assertDpEquals(
        "filter to close",
        8.dp,
        button(R.string.map_nearby_close).getBoundsInRoot().left - filter.right,
    )
  }

  @Test
  fun theFiltersSitTwelveDpInsideTheirPanel() {
    show(withButtons = true)
    button(R.string.map_nearby_filters).performClick()

    val distance =
        composeTestRule
            .onNodeWithText(context.getString(R.string.map_nearby_filter_distance))
            .getBoundsInRoot()
    val panel = bounds(C.Tag.NEARBY_FILTERS)
    assertDpEquals("panel to label", 12.dp, distance.left - panel.left)
  }

  @Test
  fun theCounterSitsTwoDpUnderTheTitle() {
    show()

    val title =
        composeTestRule
            .onNodeWithText(context.getString(R.string.map_nearby_title))
            .getBoundsInRoot()
    assertDpEquals("title to counter", 2.dp, bounds(C.Tag.NEARBY_COUNTER).top - title.bottom)
  }

  @Test
  fun theSectionsAreTwentyDpApart() {
    show()

    val pages = bounds(C.Tag.NEARBY_PAGES)
    val dots = composeTestRule.onAllNodesWithTag(C.Tag.NEARBY_DOT, useUnmergedTree = true)[0]
    val previous = button(R.string.map_nearby_previous).getBoundsInRoot()
    assertDpEquals("header to page", 20.dp, pages.top - bounds(C.Tag.NEARBY_COUNTER).bottom)
    assertDpEquals("page to dots", 20.dp, dots.getBoundsInRoot().top - pages.bottom)
    assertDpEquals("dots to arrows", 20.dp, previous.top - dots.getBoundsInRoot().bottom)
  }

  @Test
  fun theAvatarIsSixtyDpAndFourteenDpBeforeTheText() {
    show()

    val avatar = bounds(C.Tag.NEARBY_AVATAR)
    assertDpEquals("avatar width", 60.dp, avatar.right - avatar.left)
    assertDpEquals("avatar height", 60.dp, avatar.bottom - avatar.top)
    assertDpEquals("avatar to text", 14.dp, bounds(C.Tag.NEARBY_QUEST_TITLE).left - avatar.right)
  }

  @Test
  fun theLinesAreThreeDpApartAndTheChipsFourMore() {
    show()

    val venue = bounds(C.Tag.NEARBY_VENUE)
    assertDpEquals("title to venue", 3.dp, venue.top - bounds(C.Tag.NEARBY_QUEST_TITLE).bottom)
    assertDpEquals("venue to chips", 7.dp, bounds(C.Tag.QUEST_CHIPS).top - venue.bottom)
  }

  @Test
  fun viewQuestFillsTheRowBetweenTwoFiftyTwoDpArrows() {
    show()

    val width = composeTestRule.onRoot().getBoundsInRoot().right
    val previous = button(R.string.map_nearby_previous).getBoundsInRoot()
    val next = button(R.string.map_nearby_next).getBoundsInRoot()
    val view = bounds(C.Tag.NEARBY_VIEW)
    assertDpEquals("previous width", 52.dp, previous.right - previous.left)
    assertDpEquals("previous height", 52.dp, previous.bottom - previous.top)
    assertDpEquals("next width", 52.dp, next.right - next.left)
    assertDpEquals("view height", 52.dp, view.bottom - view.top)
    assertDpEquals("previous at the start", 0.dp, previous.left)
    assertDpEquals("previous to view", 10.dp, view.left - previous.right)
    assertDpEquals("view to next", 10.dp, next.left - view.right)
    assertDpEquals("next at the end", width, next.right)
  }

  @Test
  fun theShownDotIsAnEighteenDpPillAndTheOthersSixDpDots() {
    show()
    button(R.string.map_nearby_next).performClick()

    val dots = composeTestRule.onAllNodesWithTag(C.Tag.NEARBY_DOT, useUnmergedTree = true)
    val first = dots[0].getBoundsInRoot()
    val shown = dots[1].getBoundsInRoot()
    assertDpEquals("shown width", 18.dp, shown.right - shown.left)
    assertDpEquals("shown height", 6.dp, shown.bottom - shown.top)
    assertDpEquals("other width", 6.dp, first.right - first.left)
    assertDpEquals("other height", 6.dp, first.bottom - first.top)
    assertDpEquals("dot spacing", 6.dp, shown.left - first.right)
  }
}
