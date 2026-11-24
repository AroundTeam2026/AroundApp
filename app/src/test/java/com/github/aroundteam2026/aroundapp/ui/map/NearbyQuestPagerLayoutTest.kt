// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/**
 * Tests where the list puts things and how big they are, as in Figma (frames 2006:688 and
 * 5488:31322).
 */
@RunWith(AndroidJUnit4::class)
class NearbyQuestPagerLayoutTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun show(count: Int = 1, withButtons: Boolean = false) {
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
              exactPartySize = false,
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

  /** The bounds of the [index]th node tagged [tag], from the top of the list. */
  private fun bounds(tag: String, index: Int): DpRect =
      composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true)[index].getBoundsInRoot()

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
  fun theFiftyTwoDpPinSitsTwentyDpUnderTheCard() {
    show()

    val pin = bounds(C.Tag.NEARBY_SHOW_ON_MAP)
    assertDpEquals("pin width", 52.dp, pin.right - pin.left)
    assertDpEquals("pin height", 52.dp, pin.bottom - pin.top)
    assertDpEquals("card to pin", 20.dp, pin.top - bounds(C.Tag.NEARBY_CARD).bottom)
  }

  @Test
  fun theListSitsTwentyDpUnderTheHeader() {
    show()

    assertDpEquals(
        "header to list",
        20.dp,
        bounds(C.Tag.NEARBY_LIST).top - bounds(C.Tag.NEARBY_COUNTER).bottom,
    )
  }

  @Test
  fun theCardsAreTwentyFourDpApart() {
    show(count = 3)

    assertDpEquals(
        "pin to next card",
        24.dp,
        bounds(C.Tag.NEARBY_CARD, 1).top - bounds(C.Tag.NEARBY_SHOW_ON_MAP, 0).bottom,
    )
  }

  @Test
  fun manyQuestsScrollInAListThreeHundredAndTwoDpTall() {
    show(count = 5)

    val list = bounds(C.Tag.NEARBY_LIST)
    assertDpEquals("list height", 302.dp, list.bottom - list.top)
  }

  @Test
  fun aSingleQuestsListIsOnlyAsTallAsItsCard() {
    show()

    assertDpEquals(
        "list to pin bottom",
        bounds(C.Tag.NEARBY_SHOW_ON_MAP).bottom,
        bounds(C.Tag.NEARBY_LIST).bottom,
    )
  }
}
