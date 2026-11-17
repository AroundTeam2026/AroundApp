// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.venuepage.AcceptQuestButton
import com.github.aroundteam2026.aroundapp.ui.venuepage.AcceptStatus
import kotlinx.coroutines.launch

/** The most dots the pager shows; past that, they slide along with the shown page. */
private const val MAX_DOTS = 5

// The pager's own geometry, from Figma (frames 2006:688 and 5234:29428); its gaps come from
// AroundSpacing
private val AVATAR_SIZE = 60.dp
private val LINE_SPACING = 3.dp
private val TITLE_SPACING = 2.dp
private val PIN_SIZE = 52.dp
private val ICON_SIZE = 20.dp
private val SMALL_BUTTON_SIZE = 36.dp
private val DOT_SIZE = 6.dp
private val SHOWN_DOT_WIDTH = 18.dp

/**
 * The quests near the explorer, one per page in the order of [quests], with a counter and up to
 * five dots marking the shown page.
 *
 * Tapping a page opens its quest's details; under it, the pin shows the quest on the map instead,
 * and with [acceptState], "Accept quest" accepts it as the bar of its venue's page does. The
 * explorer swipes or uses the arrows either side of the dots, which wrap around. When [quests]
 * change, the shown quest stays shown wherever it moved; if it is gone, the one now in its place
 * shows, or the last one when the list got shorter, and quests arriving after it emptied start from
 * the first. Without [locationOn], a hint asks for the location, which orders the quests closest
 * first; with no quests, an empty state replaces the pages.
 *
 * @param quests The quests to page through, already ordered.
 * @param locationOn Whether the explorer's location is on, so [quests] are ordered closest first
 *   rather than by venue name.
 * @param onOpenQuest Called with a page's quest when the explorer taps the page.
 * @param onShowOnMap Called with a page's quest when the explorer taps its pin.
 * @param style How the pages look; by default, as in Figma, in the theme's colours.
 * @param onClose Called when the explorer taps the close button, which shows only when given.
 * @param filters The filters [quests] already passed; while one is set, the counter names them in
 *   place of the order.
 * @param onFiltersChange Called with the filters the explorer picks. The filter button, which opens
 *   and closes the filters under the header, shows only when given.
 * @param acceptState Where accepting each quest stands. "Accept quest" shows only when given.
 * @param onAccept Called with a page's quest when the explorer taps its "Accept quest".
 */
@Composable
fun NearbyQuestPager(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    onOpenQuest: (NearbyQuest) -> Unit,
    onShowOnMap: (NearbyQuest) -> Unit,
    modifier: Modifier = Modifier,
    style: MarkerStyle = NearbyQuestPagerDefaults.style(),
    onClose: (() -> Unit)? = null,
    filters: NearbyFilters = NearbyFilters(),
    onFiltersChange: ((NearbyFilters) -> Unit)? = null,
    acceptState: NearbyAcceptUiState? = null,
    onAccept: (NearbyQuest) -> Unit = {},
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
      Pages(quests, locationOn, summary, header) { quest ->
        QuestPage(
            quest,
            style,
            onOpen = { onOpenQuest(quest) },
            onShowOnMap = { onShowOnMap(quest) },
            accept =
                acceptState?.let { state ->
                  Accept(state.statusOf(quest.questId), state.available) { onAccept(quest) }
                },
        )
      }
    }
  }
}

/** How the pager looks by default. */
object NearbyQuestPagerDefaults {
  /**
   * The quest card's chips and avatar, sized and set as in Figma, in the theme's colours, light or
   * dark.
   */
  @Composable
  fun style(): MarkerStyle {
    return MarkerDefaults.style(
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
      RoundButton(
          R.drawable.ic_sliders,
          R.string.map_nearby_filters,
          SMALL_BUTTON_SIZE,
          SMALL_BUTTON_SIZE,
          selected = open,
          onClick = onToggleFilters,
      )
    }
    onClose?.let {
      RoundButton(
          R.drawable.ic_close,
          R.string.map_nearby_close,
          SMALL_BUTTON_SIZE,
          SMALL_BUTTON_SIZE,
          onClick = it,
      )
    }
  }
}

/**
 * The [header] with its counter, the pages of [quests], each drawn by [page], their dots and the
 * arrows. Composed only while there are quests, so quests arriving after an empty list start again
 * from the first.
 *
 * @param summary The filters in words, which the counter names in place of the order, or null.
 */
@Composable
private fun Pages(
    quests: List<NearbyQuest>,
    locationOn: Boolean,
    summary: String?,
    header: @Composable (counter: String) -> Unit,
    page: @Composable (NearbyQuest) -> Unit,
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
  ) {
    page(quests[it])
  }
  Navigation(pagerState, shown)
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

/**
 * A quest's "Accept quest": where accepting it [status] stands, whether it [enabled], and what a
 * tap does.
 */
private class Accept(val status: AcceptStatus, val enabled: Boolean, val onAccept: () -> Unit)

/**
 * One quest's page: its venue's avatar, then its title, its venue's name and its chips, which open
 * the quest's details when tapped, as the chevron at the end says. Under them, its pin shows the
 * quest on the map, beside "Accept quest" when [accept] is given.
 */
@Composable
private fun QuestPage(
    quest: NearbyQuest,
    style: MarkerStyle,
    onOpen: () -> Unit,
    onShowOnMap: () -> Unit,
    accept: Accept?,
) {
  val colors = style.colors
  Column(verticalArrangement = Arrangement.spacedBy(AroundSpacing.SectionGap)) {
    Row(
        Modifier.testTag(C.Tag.NEARBY_PAGE)
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.map_nearby_open_details),
                role = Role.Button,
                onClick = onOpen,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      QuestAvatar(quest.avatar, style.avatarStyle, Modifier.testTag(C.Tag.NEARBY_AVATAR))
      Column(
          Modifier.padding(horizontal = style.dimensions.cardGap).weight(1f),
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
            quest.exactPartySize,
            // Figma sets the chips a little further below the venue's name than the lines' gap
            Modifier.padding(top = AroundSpacing.ExtraSmall),
        )
      }
      Icon(
          painterResource(R.drawable.ic_chevron_right),
          contentDescription = null,
          Modifier.size(ICON_SIZE),
          tint = colors.onSurfaceVariant,
      )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.CompactGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      RoundButton(
          R.drawable.ic_address_pin,
          R.string.map_nearby_show_on_map,
          PIN_SIZE,
          ICON_SIZE,
          Modifier.testTag(C.Tag.NEARBY_SHOW_ON_MAP),
          onClick = onShowOnMap,
      )
      accept?.let {
        AcceptQuestButton(
            it.status,
            it.enabled,
            it.onAccept,
            Modifier.weight(1f),
            tag = C.Tag.NEARBY_ACCEPT,
            acceptedLabel = R.string.accept_quest_accepted_short,
        )
      }
    }
  }
}

/** A dot for each page of the window around [current]: a longer pill for the shown one. */
@Composable
private fun Dots(current: Int, count: Int, modifier: Modifier = Modifier) {
  Row(
      modifier,
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

/**
 * The arrows, which wrap around, either side of the dots of the [shown] page, which show only when
 * there is more than one.
 */
@Composable
private fun Navigation(pagerState: PagerState, shown: Int) {
  val scope = rememberCoroutineScope()
  val count = pagerState.pageCount
  fun pageBy(step: Int) {
    // From where a move under way is going, so quick taps each move a page
    scope.launch { pagerState.animateScrollToPage((pagerState.targetPage + step).mod(count)) }
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    RoundButton(
        R.drawable.ic_chevron_left,
        R.string.map_nearby_previous,
        SMALL_BUTTON_SIZE,
        ICON_SIZE,
        enabled = count > 1,
    ) {
      pageBy(-1)
    }
    if (count > 1) Dots(shown, count, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    RoundButton(
        R.drawable.ic_chevron_right,
        R.string.map_nearby_next,
        SMALL_BUTTON_SIZE,
        ICON_SIZE,
        enabled = count > 1,
    ) {
      pageBy(1)
    }
  }
}

/**
 * A round, outlined [size] button showing [icon] at [iconSize], which screen readers announce as
 * [label]: the arrows, the pin, the close button and the filter button. A toggle passes [selected],
 * which fills it in purple while on; the others leave it null.
 */
@Composable
private fun RoundButton(
    icon: Int,
    label: Int,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val filled = selected == true
  OutlinedIconButton(
      onClick,
      modifier.size(size).semantics { selected?.let { this.selected = it } },
      enabled = enabled,
      shape = CircleShape,
      colors =
          IconButtonDefaults.outlinedIconButtonColors(
              containerColor = if (filled) colors.primary else colors.background,
              contentColor = if (filled) colors.onPrimary else colors.onSurface,
          ),
      border = if (filled) null else BorderStroke(1.dp, colors.outline),
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
