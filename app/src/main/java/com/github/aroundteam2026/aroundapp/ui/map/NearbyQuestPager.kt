// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerDefaults
import com.github.aroundteam2026.aroundapp.ui.map.marker.MarkerStyle
import com.github.aroundteam2026.aroundapp.ui.quest.QuestAvatar
import com.github.aroundteam2026.aroundapp.ui.quest.QuestChips
import com.github.aroundteam2026.aroundapp.ui.quest.QuestLine
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import kotlinx.coroutines.launch

/** The most dots the pager shows; past that, they slide along with the shown page. */
private const val MAX_DOTS = 5

// The pager's own geometry, from Figma (frame 2006:688); its gaps come from AroundSpacing
private val AVATAR_SIZE = 60.dp
private val LINE_SPACING = 3.dp
private val TITLE_SPACING = 2.dp
private val BUTTON_HEIGHT = 52.dp
private val CHEVRON_SIZE = 20.dp
private val CLOSE_SIZE = 36.dp
private val DOT_SIZE = 6.dp
private val SHOWN_DOT_WIDTH = 18.dp

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
 * @param style How the pages look; by default, as in Figma, in the theme's colours.
 * @param onClose Called when the explorer taps the close button, which shows only when given.
 * @param filters The filters [quests] already passed; while one is set, the counter names them in
 *   place of the order.
 * @param onFiltersChange Called with the filters the explorer picks. The filter button, which opens
 *   and closes the filters under the header, shows only when given.
 */
@Composable
fun NearbyQuestPager(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    onQuestClick: (NearbyQuest) -> Unit,
    onViewQuest: (NearbyQuest) -> Unit,
    modifier: Modifier = Modifier,
    style: MarkerStyle = NearbyQuestPagerDefaults.style(),
    onClose: (() -> Unit)? = null,
    filters: NearbyFilters = NearbyFilters(),
    onFiltersChange: ((NearbyFilters) -> Unit)? = null,
) {
  // Saved, so the filters stay open over rotation; they close with the pager
  var filtersOpen by rememberSaveable { mutableStateOf(false) }
  val summary = filterSummary(filters)
  val header: @Composable (counter: String?) -> Unit = { counter ->
    Header(counter, onClose, filtersOpen.takeIf { onFiltersChange != null }) {
      filtersOpen = !filtersOpen
    }
    if (filtersOpen && onFiltersChange != null) {
      NearbyFilterPanel(filters, locationOn, onFiltersChange)
    }
  }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AroundSpacing.SectionGap)) {
    if (quests.isEmpty()) {
      header(summary)
      LocationHint(locationOn)
      Text(
          stringResource(
              if (filters.isActive) R.string.map_nearby_no_match else R.string.map_nearby_empty
          ),
          Modifier.testTag(C.Tag.NEARBY_EMPTY),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = AroundTextStyles.BodyMedium,
      )
    } else {
      Pages(quests, locationOn, summary, header, onQuestClick, onViewQuest, style)
    }
  }
}

/** How the pager looks by default. */
object NearbyQuestPagerDefaults {
  /**
   * The quest card's chips and avatar, sized and set as in Figma. The pager sits on the app's
   * surface rather than on a card of its own, so it takes the theme's colours, light or dark.
   */
  @Composable
  fun style(): MarkerStyle {
    val colors = MaterialTheme.colorScheme
    return MarkerDefaults.style(
        colors =
            MarkerDefaults.colors()
                .copy(
                    accent = colors.primary,
                    onAccent = colors.onPrimary,
                    secondary = colors.tertiary,
                    onSecondary = colors.onTertiary,
                    surface = colors.surface,
                    onSurface = colors.onSurface,
                    onSurfaceVariant = colors.onSurfaceVariant,
                    outline = colors.outline,
                ),
        typography =
            MarkerDefaults.typography()
                .copy(
                    questTitle = AroundTextStyles.CardTitle,
                    venueName = AroundTextStyles.InlineBody,
                    chip = AroundTextStyles.Badge,
                    initials = AroundTextStyles.AvatarLarge,
                ),
        dimensions =
            MarkerDefaults.dimensions.copy(
                cardAvatarSize = AVATAR_SIZE,
                cardGap = AroundSpacing.InsetPadding,
                cardLineSpacing = LINE_SPACING,
                chipVerticalPadding = AroundSpacing.ExtraSmall,
            ),
    )
  }
}

/**
 * "Nearby quests" over [counter] when there is one, the filter button unless [filtersOpen] is null,
 * filled while they are open, and the close button when [onClose] is given.
 */
@Composable
private fun Header(
    counter: String?,
    onClose: (() -> Unit)?,
    filtersOpen: Boolean?,
    onToggleFilters: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TITLE_SPACING)) {
      Text(
          stringResource(R.string.map_nearby_title),
          color = MaterialTheme.colorScheme.onSurface,
          style = AroundTextStyles.OverlayTitle,
      )
      counter?.let {
        Text(
            it,
            Modifier.testTag(C.Tag.NEARBY_COUNTER),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = AroundTextStyles.Caption,
        )
      }
    }
    filtersOpen?.let { open ->
      val description = stringResource(R.string.map_nearby_filters)
      FilterChip(
          selected = open,
          onClick = onToggleFilters,
          label = { Text(stringResource(R.string.map_nearby_filters_label)) },
          modifier = Modifier.semantics { contentDescription = description },
      )
    }
    onClose?.let {
      RoundButton(
          R.drawable.ic_close,
          R.string.map_nearby_close,
          CLOSE_SIZE,
          CLOSE_SIZE,
          onClick = it,
      )
    }
  }
}

/**
 * The [header] with its counter, the pages of [quests], their dots and the arrows. Composed only
 * while there are quests, so quests arriving after an empty list start again from the first.
 *
 * @param summary The filters in words, which the counter names in place of the order, or null.
 */
@Composable
private fun Pages(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    summary: String?,
    header: @Composable (counter: String) -> Unit,
    onQuestClick: (NearbyQuest) -> Unit,
    onViewQuest: (NearbyQuest) -> Unit,
    style: MarkerStyle,
) {
  val pagerState = rememberPagerState { quests.size }
  // For a frame after the list shrinks, the pager's page can still be past its end
  val shown = pagerState.currentPage.coerceAtMost(quests.lastIndex)
  val order = if (locationOn) R.string.map_nearby_closest_first else R.string.map_nearby_by_name
  header(
      if (summary != null)
          stringResource(R.string.map_nearby_filtered, shown + 1, quests.size, summary)
      else stringResource(order, shown + 1, quests.size)
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
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = AroundTextStyles.BodyMedium,
  )
}

/** One quest's page: its venue's avatar, then its title, its venue's name and its chips. */
@Composable
private fun QuestPage(quest: NearbyQuest, style: MarkerStyle, onClick: () -> Unit) {
  val colors = style.colors
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
    QuestAvatar(quest.avatar, style.avatarStyle, Modifier.testTag(C.Tag.NEARBY_AVATAR))
    Column(
        Modifier.padding(start = style.dimensions.cardGap).weight(1f),
        verticalArrangement = Arrangement.spacedBy(style.dimensions.cardLineSpacing),
    ) {
      QuestLine(
          quest.title,
          C.Tag.NEARBY_QUEST_TITLE,
          style.typography.questTitle,
          colors.onSurface,
          maxLines = 2,
      )
      QuestLine(
          quest.venueName,
          C.Tag.NEARBY_VENUE,
          style.typography.venueName,
          colors.onSurfaceVariant,
      )
      QuestChips(
          quest.reward,
          quest.minPartySize,
          quest.distanceMeters,
          style.chipStyle,
          // Figma sets the chips a little further below the venue's name than the lines' gap
          Modifier.padding(top = AroundSpacing.ExtraSmall),
      )
    }
  }
}

/** A dot for each page of the window around [current]: a longer pill for the shown one. */
@Composable
private fun Dots(current: Int, count: Int) {
  Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement =
          Arrangement.spacedBy(AroundSpacing.SegmentGap, Alignment.CenterHorizontally),
  ) {
    for (page in dotWindow(current, count)) {
      val selected = page == current
      Box(
          Modifier.testTag(C.Tag.NEARBY_DOT)
              .semantics { this.selected = selected }
              .size(width = if (selected) SHOWN_DOT_WIDTH else DOT_SIZE, height = DOT_SIZE)
              .background(
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline,
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
  Row(
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    RoundButton(
        R.drawable.ic_chevron_left,
        R.string.map_nearby_previous,
        BUTTON_HEIGHT,
        CHEVRON_SIZE,
        enabled = count > 1,
    ) {
      pageBy(-1)
    }
    Button(
        onView,
        Modifier.testTag(C.Tag.NEARBY_VIEW).weight(1f).height(BUTTON_HEIGHT),
        shape = AroundPillShape,
    ) {
      Text(stringResource(R.string.map_nearby_view), style = AroundTextStyles.Button)
    }
    RoundButton(
        R.drawable.ic_chevron_right,
        R.string.map_nearby_next,
        BUTTON_HEIGHT,
        CHEVRON_SIZE,
        enabled = count > 1,
    ) {
      pageBy(1)
    }
  }
}

/**
 * A round, outlined [size] button showing [icon] at [iconSize], which screen readers announce as
 * [label]: the arrows and the close button.
 */
@Composable
private fun RoundButton(
    icon: Int,
    label: Int,
    size: Dp,
    iconSize: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  OutlinedIconButton(
      onClick,
      Modifier.size(size),
      enabled = enabled,
      shape = CircleShape,
      colors =
          IconButtonDefaults.outlinedIconButtonColors(
              containerColor = colors.background,
              contentColor = colors.onSurface,
          ),
      border = BorderStroke(1.dp, colors.outline),
  ) {
    Icon(painterResource(icon), stringResource(label), Modifier.size(iconSize))
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
