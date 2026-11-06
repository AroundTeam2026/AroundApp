// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme
import com.github.aroundteam2026.aroundapp.ui.venue.AddressSearchBar
import com.github.aroundteam2026.aroundapp.ui.venue.AddressSearchStatus

/** What the quest's location does when the venue changes it; each does nothing by default. */
class QuestLocationActions(
    val onAtVenue: () -> Unit = {},
    val onElsewhere: () -> Unit = {},
    val onMove: (Location) -> Unit = {},
    val onAddressQueryChange: (String) -> Unit = {},
    val onAddressSearch: () -> Unit = {},
    val onAddressClear: () -> Unit = {},
    val onAddressPick: (AddressSuggestion) -> Unit = {},
)

/**
 * Where the quest happens: at the venue, or somewhere else, found by its address and placed on the
 * radius map below. "Use the venue's location" brings it back to the venue.
 */
@Composable
internal fun QuestLocationFields(state: CreateQuestUiState, actions: QuestLocationActions) {
  FieldLabel(stringResource(R.string.create_quest_location))
  Spacer(Modifier.height(AroundSpacing.CompactGap))
  Row(
      Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
  ) {
    LocationOption(
        R.drawable.ic_storefront,
        stringResource(R.string.create_quest_location_at_venue, state.venueName),
        selected = state.atVenue,
        onClick = actions.onAtVenue,
        tag = C.Tag.CREATE_QUEST_LOCATION_AT_VENUE,
    )
    LocationOption(
        R.drawable.ic_address_pin,
        stringResource(R.string.create_quest_location_elsewhere),
        selected = !state.atVenue,
        onClick = actions.onElsewhere,
        tag = C.Tag.CREATE_QUEST_LOCATION_ELSEWHERE,
    )
  }
  Spacer(Modifier.height(AroundSpacing.CompactGap))
  if (state.atVenue) {
    LocationHint(stringResource(R.string.create_quest_location_at_venue_hint))
    return
  }
  // The suggestions open under the field, where the keyboard may hide them, so the form scrolls
  // them into view as they come
  val searchInView = remember { BringIntoViewRequester() }
  val answered = state.addressSearch.status != AddressSearchStatus.IDLE
  val found = state.addressSearch.suggestions
  LaunchedEffect(answered, found) {
    if (answered || found.isNotEmpty()) searchInView.bringIntoView()
  }
  AddressSearchBar(
      query = state.addressQuery,
      state = state.addressSearch,
      onQueryChange = actions.onAddressQueryChange,
      onSearch = actions.onAddressSearch,
      onClear = actions.onAddressClear,
      onPick = actions.onAddressPick,
      // Bounded, so its suggestions can scroll inside the form, which scrolls itself
      modifier = Modifier.heightIn(max = SEARCH_MAX_HEIGHT).bringIntoViewRequester(searchInView),
      placeholder = stringResource(R.string.create_quest_location_search),
  )
  Spacer(Modifier.height(AroundSpacing.CompactGap))
  LocationHint(stringResource(R.string.create_quest_location_elsewhere_hint))
  TextButton(
      onClick = actions.onAtVenue,
      contentPadding = PaddingValues(0.dp),
      modifier = Modifier.testTag(C.Tag.CREATE_QUEST_LOCATION_USE_VENUE),
  ) {
    Text(
        stringResource(R.string.create_quest_location_use_venue, state.venueName),
        style = AroundTextStyles.Subtitle,
        color = AroundTheme.colors.accentText,
    )
  }
}

/** The line under the choices that says where explorers find the quest. */
@Composable
private fun LocationHint(text: String) {
  Text(
      text,
      style = AroundTextStyles.Caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(C.Tag.CREATE_QUEST_LOCATION_HINT),
  )
}

/** One place the quest can happen, an icon beside its name, half of the row wide. */
@Composable
private fun RowScope.LocationOption(
    @DrawableRes icon: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
  SelectableOption(
      selected = selected,
      onClick = onClick,
      tag = tag,
      shape = MaterialTheme.shapes.medium,
      padding =
          PaddingValues(horizontal = AroundSpacing.Medium, vertical = AroundSpacing.InsetPadding),
      modifier = Modifier.weight(1f).fillMaxHeight(),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val color = if (selected) AroundTheme.colors.accentText else MaterialTheme.colorScheme.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          painterResource(icon),
          contentDescription = null,
          tint = color,
          modifier = Modifier.size(ICON_SIZE),
      )
      Text(
          title,
          style = AroundTextStyles.Subtitle,
          color = color,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          // Shrinks before the icon, so a long venue name ends in an ellipsis
          modifier = Modifier.weight(1f, fill = false),
      )
    }
  }
}

private val ICON_SIZE: Dp = 18.dp

/** How tall the address search grows with its suggestions before they scroll. */
private val SEARCH_MAX_HEIGHT: Dp = 320.dp
