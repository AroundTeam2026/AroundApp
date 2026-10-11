// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.CardAvatar
import com.github.aroundteam2026.aroundapp.ui.map.marker.CardLine
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerStyle
import com.github.aroundteam2026.aroundapp.ui.map.marker.QuestChips
import kotlinx.coroutines.launch

/** The most dots the pager shows; past that, they slide along with the shown page. */
private const val MAX_DOTS = 5

/**
 * The quests near the explorer, one per page, in the order of [quests]: each page shows its venue's
 * avatar, the quest's title, the venue's name and the quest card's chips. A counter says which page
 * is shown and how the quests are ordered, and up to five dots mark it.
 *
 * The explorer swipes between pages, or pages with the arrows, which wrap around from the last
 * quest to the first and back. When [quests] change, the shown quest stays shown wherever it moved;
 * if it is gone, the quest now in its place is shown, or the last one when the list got shorter.
 * Quests arriving after the list emptied start again from the first.
 *
 * Without [locationOn], a hint asks for the location, which orders the quests closest first. With
 * no quests, an empty state stands in for the pages.
 *
 * @param quests The quests to page through, already ordered.
 * @param locationOn Whether the explorer's location is on, so [quests] are ordered closest first
 *   rather than by venue name.
 * @param onQuestClick Called with a page's quest when the explorer taps the page.
 * @param onViewQuest Called with the shown quest when the explorer taps "View quest".
 */
@Composable
fun NearbyQuestPager(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    onQuestClick: (NearbyQuest) -> Unit,
    onViewQuest: (NearbyQuest) -> Unit,
    modifier: Modifier = Modifier,
    style: MarkerStyle = MarkerDefaults.style(),
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(R.string.map_nearby_title), style = MaterialTheme.typography.titleLarge)
    if (quests.isEmpty()) {
      LocationHint(locationOn)
      Text(
          stringResource(R.string.map_nearby_empty),
          Modifier.testTag(C.Tag.NEARBY_EMPTY),
          style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      Pages(quests, locationOn, onQuestClick, onViewQuest, style)
    }
  }
}

/**
 * The counter, the pages of [quests], their dots and the arrows. Composed only while there are
 * quests, so quests arriving after an empty list start again from the first.
 */
@Composable
private fun Pages(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    onQuestClick: (NearbyQuest) -> Unit,
    onViewQuest: (NearbyQuest) -> Unit,
    style: MarkerStyle,
) {
  val pagerState = rememberPagerState { quests.size }
  // For a frame after the list shrinks, the pager's page can still be past its end
  val shown = pagerState.currentPage.coerceAtMost(quests.lastIndex)
  val order = if (locationOn) R.string.map_nearby_closest_first else R.string.map_nearby_by_name
  Text(
      stringResource(order, shown + 1, quests.size),
      Modifier.testTag(C.Tag.NEARBY_COUNTER),
      style = MaterialTheme.typography.bodySmall,
  )
  LocationHint(locationOn)
  // Keyed by quest, so the shown quest keeps its page when others come and go
  HorizontalPager(
      pagerState,
      Modifier.testTag(C.Tag.NEARBY_PAGES).fillMaxWidth(),
      key = { quests[it].questId },
  ) { page ->
    val quest = quests[page]
    QuestPage(quest, style, onClick = { onQuestClick(quest) })
  }
  if (quests.size > 1) Dots(shown, quests.size)
  Navigation(pagerState, onView = { onViewQuest(quests[shown]) })
}

/** Without [locationOn], a hint that the location orders the quests closest first. */
@Composable
private fun LocationHint(locationOn: Boolean) {
  if (locationOn) return
  Text(
      stringResource(R.string.map_nearby_location_off),
      Modifier.testTag(C.Tag.NEARBY_LOCATION_HINT),
      style = MaterialTheme.typography.bodyMedium,
  )
}

/** One quest's page: its venue's avatar, then its title, its venue's name and its chips. */
@Composable
private fun QuestPage(quest: NearbyQuest, style: MarkerStyle, onClick: () -> Unit) {
  // On the sheet, not on the card's own surface, so the text takes the theme's colours
  val colors = MaterialTheme.colorScheme
  Row(
      Modifier.testTag(C.Tag.NEARBY_PAGE)
          .fillMaxWidth()
          .clickable(
              onClickLabel = stringResource(R.string.map_nearby_show_on_map),
              role = Role.Button,
              onClick = onClick,
          ),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CardAvatar(quest.avatar, style, Modifier.testTag(C.Tag.NEARBY_AVATAR))
    Column(
        Modifier.padding(start = style.dimensions.cardGap).weight(1f),
        verticalArrangement = Arrangement.spacedBy(style.dimensions.cardLineSpacing),
    ) {
      CardLine(
          quest.title,
          C.Tag.NEARBY_QUEST_TITLE,
          style.typography.questTitle,
          colors.onSurface,
          maxLines = 2,
      )
      CardLine(
          quest.venueName,
          C.Tag.NEARBY_VENUE,
          style.typography.venueName,
          colors.onSurfaceVariant,
      )
      QuestChips(quest.reward, quest.minPartySize, quest.distanceMeters, style = style)
    }
  }
}

/** A dot for each page of the window around [current], which has the selected one. */
@Composable
private fun Dots(current: Int, count: Int) {
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
  ) {
    for (page in dotWindow(current, count)) {
      val selected = page == current
      Box(
          Modifier.testTag(C.Tag.NEARBY_DOT)
              .semantics { this.selected = selected }
              .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
              .background(
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
                  CircleShape,
              )
      )
    }
  }
}

/** The arrows, which wrap around, either side of "View quest". */
@Composable
private fun Navigation(pagerState: PagerState, onView: () -> Unit) {
  val scope = rememberCoroutineScope()
  val count = pagerState.pageCount
  fun pageBy(step: Int) {
    // From where a move under way is going, so quick taps each move a page
    scope.launch { pagerState.animateScrollToPage((pagerState.targetPage + step).mod(count)) }
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Arrow("‹", stringResource(R.string.map_nearby_previous), enabled = count > 1) { pageBy(-1) }
    Button(onView, Modifier.testTag(C.Tag.NEARBY_VIEW).weight(1f)) {
      Text(stringResource(R.string.map_nearby_view))
    }
    Arrow("›", stringResource(R.string.map_nearby_next), enabled = count > 1) { pageBy(1) }
  }
}

/** An arrow button that screen readers announce as [description]. */
@Composable
private fun Arrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
  IconButton(
      onClick,
      Modifier.semantics { contentDescription = description },
      enabled = enabled,
  ) {
    Text(glyph, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
  }
}

/**
 * The pages that get a dot when [current] of [count] pages is shown: all of them up to [MAX_DOTS],
 * else [MAX_DOTS] pages with [current] in the middle, slid to stay within the pages near either
 * end.
 */
internal fun dotWindow(current: Int, count: Int): IntRange {
  val first = (current - MAX_DOTS / 2).coerceIn(0, maxOf(0, count - MAX_DOTS))
  return first until minOf(count, first + MAX_DOTS)
}
