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
import androidx.compose.ui.test.performScrollToNode
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

/** The card of the quest [questTitle] in the map's nearby quests, merged with its lines. */
private fun nearbyCard(questTitle: String) = hasTestTag(C.Tag.NEARBY_CARD) and hasText(questTitle)

/** Opens the map's nearby quests and scrolls their list until the quest [questTitle] shows. */
fun ComposeTestRule.findTheQuestNearby(questTitle: String) {
  onNodeWithTag(C.Tag.MAP_TAB).performClick()
  onNodeWithTag(C.Tag.NEARBY_BUTTON).performClick()
  waitFor(hasTestTag(C.Tag.NEARBY_LIST))
  // Other tests' quests can come first, and the quest can take a moment to arrive
  waitUntil(TIMEOUT_MS) {
    runCatching { onNodeWithTag(C.Tag.NEARBY_LIST).performScrollToNode(nearbyCard(questTitle)) }
        .isSuccess
  }
  onNode(nearbyCard(questTitle)).assertIsDisplayed()
}

/**
 * Taps the quest [questTitle], shown in the nearby quests, and waits for its details, at
 * [venueName], to show it.
 */
fun ComposeTestRule.openTheShownQuest(venueName: String, questTitle: String) {
  onNode(nearbyCard(questTitle)).performClick()
  waitFor(hasTestTag(C.Tag.QUEST_DETAIL_TITLE) and hasText(questTitle))
  waitFor(hasTestTag(C.Tag.QUEST_DETAIL_VENUE_NAME) and hasText(venueName))
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
