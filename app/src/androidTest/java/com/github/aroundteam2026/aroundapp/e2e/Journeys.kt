// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS

/** Waits for a node matching [matcher], including one inside a clickable row's merged node. */
fun ComposeTestRule.waitFor(matcher: SemanticsMatcher) =
    waitUntil(TIMEOUT_MS) {
      onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

/** Waits until no node is tagged [tag], e.g. once a screen closed. */
fun ComposeTestRule.waitUntilGone(tag: String) =
    waitUntil(TIMEOUT_MS) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

/** Opens the map's nearby quests and waits for the quest [questTitle] to be the one shown. */
fun ComposeTestRule.findTheQuestNearby(questTitle: String) {
  onNodeWithTag(C.Tag.MAP_TAB).performClick()
  onNodeWithTag(C.Tag.NEARBY_BUTTON).performClick()
  // "View quest" opens the venue of the page shown, so the quest must be the one shown
  val nearbyQuest = hasTestTag(C.Tag.NEARBY_QUEST_TITLE) and hasText(questTitle)
  waitFor(nearbyQuest)
  onNode(nearbyQuest, useUnmergedTree = true).assertIsDisplayed()
}

/**
 * Opens the shown quest's venue page and waits for it to be [venueName]'s, listing [questTitle].
 */
fun ComposeTestRule.viewTheVenuesPage(venueName: String, questTitle: String) {
  onNodeWithTag(C.Tag.NEARBY_VIEW).performClick()
  waitFor(hasTestTag(C.Tag.VENUE_PAGE_NAME) and hasText(venueName))
  waitFor(hasTestTag(C.Tag.VENUE_QUEST_ROW_TITLE) and hasText(questTitle))
}

/**
 * Opens the explorer's Quests tab and waits for the card of the reservation [reservationId] to show
 * [questTitle] and the status [status], a string resource such as
 * `R.string.overview_status_waiting`.
 */
fun ComposeTestRule.seeTheQuestsCard(
    reservationId: String,
    questTitle: String,
    @StringRes status: Int,
) {
  onNodeWithTag(C.Tag.QUESTS_TAB).performClick()
  // Only this reservation's card, whatever else the tab lists
  val inItsCard = hasAnyAncestor(hasTestTag("${C.Tag.OVERVIEW_ROW}_$reservationId"))
  waitFor(hasTestTag(C.Tag.OVERVIEW_ROW_TITLE) and hasText(questTitle) and inItsCard)
  val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(status)
  // The card shows its status in capitals
  val itsStatus =
      hasText(text, ignoreCase = true) and
          hasAnyAncestor(hasTestTag(C.Tag.OVERVIEW_ROW_STATUS)) and
          inItsCard
  waitFor(itsStatus)
  onNode(itsStatus, useUnmergedTree = true).assertIsDisplayed()
}
