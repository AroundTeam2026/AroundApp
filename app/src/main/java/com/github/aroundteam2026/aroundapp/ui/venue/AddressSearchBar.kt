// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import com.github.aroundteam2026.aroundapp.ui.theme.AroundFonts

// The Figma "Search" field and "Search results" card of the explorer search, in the second draft
private val FIELD_HEIGHT = 44.dp
private val FIELD_OUTLINE = 2.dp
private val FIELD_PADDING = 14.dp
private val FIELD_GAP = 10.dp
private val FIELD_ICON = 20.dp
private val CLEAR_SIZE = 22.dp
private val FIELD_SHADOW = 10.dp
private val CARD_GAP = 18.dp
private val CARD_RADIUS = 16.dp
private val ROW_HORIZONTAL_PADDING = 14.dp
private val ROW_VERTICAL_PADDING = 10.dp
private val ROW_GAP = 12.dp
private val AVATAR_SIZE = 36.dp
private val AVATAR_ICON = 20.dp
private val LINE_GAP = 2.dp
private val HAIRLINE = 1.dp

/** How visible a suggestion from the previous query stays while the next search runs. */
private const val STALE_ALPHA = 0.5f

private val QUERY_STYLE = TextStyle(fontFamily = AroundFonts.Body, fontSize = 15.sp)
private val TITLE_STYLE =
    TextStyle(fontFamily = AroundFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
private val SUBTITLE_STYLE = TextStyle(fontFamily = AroundFonts.Body, fontSize = 13.sp)

/**
 * The colours of the address search, from the Figma tokens.
 *
 * @property surface Fills the field and the results card.
 * @property outline Rings the field.
 * @property ink The query, the titles and the icons.
 * @property muted The placeholder, the second lines and the status messages.
 * @property line Rings the results card, divides its rows, and fills the clear button.
 * @property avatar Fills the round tile beside each suggestion.
 * @property onAvatar The pin on [avatar].
 * @property highlight The part of a title matching the query.
 * @property shadow The field's shadow.
 */
@Immutable
data class AddressSearchColors(
    val surface: Color,
    val outline: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val avatar: Color,
    val onAvatar: Color,
    val highlight: Color,
    val shadow: Color,
)

/** The address search's default colours: the Figma second draft's, light and dark. */
object AddressSearchDefaults {
  // The light tokens the app's colours already hold; the tile's teal is the second draft's
  // "tertiary", lighter than AroundColors.Teal, which came from the first draft
  private val LIGHT =
      AddressSearchColors(
          surface = AroundColors.Paper,
          outline = AroundColors.Purple,
          ink = AroundColors.Ink,
          muted = AroundColors.InkMuted,
          line = AroundColors.Line,
          avatar = Color(0xFF5EA89D),
          onAvatar = AroundColors.Cream,
          highlight = AroundColors.Purple,
          shadow = Color(0x266B5947),
      )

  // The app has no dark colours yet. The dark mode's highlight is its accent text colour, the same
  // as its ink, as in Figma
  private val DARK =
      LIGHT.copy(
          surface = Color(0xFF383838),
          ink = Color(0xFFFDFFE8),
          muted = Color(0xFFB5B6A6),
          line = Color(0xFF474547),
          highlight = Color(0xFFFDFFE8),
      )

  /** The colours for dark mode when [darkTheme], else for light mode. */
  fun colors(darkTheme: Boolean): AddressSearchColors = if (darkTheme) DARK else LIGHT

  /** The colours for the system's light or dark mode. */
  @Composable fun colors(): AddressSearchColors = colors(isSystemInDarkTheme())
}

/**
 * Where a venue searches its address: a rounded field showing [query], then a card listing
 * [state]'s suggestions, or a message saying it is searching or why there are none. Nothing shows
 * under the field while there is nothing to say. When the bar has less height than the card needs,
 * as above the keyboard, the card scrolls.
 *
 * Typing reports each change to [onQueryChange]; the keyboard's search key calls [onSearch]; the
 * clear button, shown once there is a query, calls [onClear]. Tapping a suggestion calls [onPick]
 * and hides the keyboard. While a search runs, the suggestions from the previous query stay in view
 * but can't be picked, as they may not match the query anymore.
 */
@Composable
fun AddressSearchBar(
    query: String,
    state: AddressSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onPick: (AddressSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    colors: AddressSearchColors = AddressSearchDefaults.colors(),
) {
  val focusManager = LocalFocusManager.current
  Column(modifier, verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
    SearchField(query, onQueryChange, onSearch, onClear, colors)
    val message = statusMessage(state)
    // Takes what height is left, without stretching, so a short space makes it scroll
    val cardModifier = Modifier.weight(1f, fill = false)
    if (state.suggestions.isNotEmpty()) {
      val pickable = state.status != AddressSearchStatus.SEARCHING
      ResultsCard(colors, cardModifier) {
        state.suggestions.forEachIndexed { index, suggestion ->
          if (index > 0) HorizontalDivider(thickness = HAIRLINE, color = colors.line)
          SuggestionRow(suggestion, query, colors, enabled = pickable) {
            focusManager.clearFocus()
            onPick(suggestion)
          }
        }
      }
    } else if (message != null) {
      ResultsCard(colors, cardModifier) {
        Text(
            message,
            Modifier.testTag(C.Tag.VENUE_ADDRESS_STATUS)
                .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
            color = colors.muted,
            style = SUBTITLE_STYLE,
        )
      }
    }
  }
}

/** What to say when there are no suggestions to list, or null to say nothing. */
@Composable
private fun statusMessage(state: AddressSearchUiState): String? =
    when (state.status) {
      AddressSearchStatus.IDLE -> null
      AddressSearchStatus.SEARCHING -> stringResource(R.string.venue_address_searching)
      AddressSearchStatus.NO_RESULTS -> stringResource(R.string.venue_address_no_results)
      AddressSearchStatus.FAILED -> stringResource(R.string.venue_address_failed)
      AddressSearchStatus.UNAVAILABLE -> stringResource(R.string.venue_address_unavailable)
    }

/** The rounded field: the magnifier, the query or its placeholder, and the clear button. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    colors: AddressSearchColors,
) {
  val shape = RoundedCornerShape(FIELD_HEIGHT / 2)
  BasicTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier = Modifier.testTag(C.Tag.VENUE_ADDRESS_FIELD).fillMaxWidth(),
      textStyle = QUERY_STYLE.copy(color = colors.ink),
      singleLine = true,
      cursorBrush = SolidColor(colors.outline),
      keyboardOptions =
          KeyboardOptions(
              capitalization = KeyboardCapitalization.Words,
              autoCorrectEnabled = false,
              imeAction = ImeAction.Search,
          ),
      keyboardActions = KeyboardActions(onSearch = { onSearch() }),
      decorationBox = { innerTextField ->
        Row(
            Modifier.testTag(C.Tag.VENUE_ADDRESS_FIELD_BOX)
                .fillMaxWidth()
                .height(FIELD_HEIGHT)
                .shadow(
                    FIELD_SHADOW,
                    shape,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .background(colors.surface, shape)
                .border(FIELD_OUTLINE, colors.outline, shape)
                .padding(horizontal = FIELD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        ) {
          Icon(
              painterResource(R.drawable.ic_search),
              // The placeholder says what the field is for
              contentDescription = null,
              modifier = Modifier.size(FIELD_ICON),
              tint = colors.ink,
          )
          Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
              Text(
                  stringResource(R.string.venue_address_placeholder),
                  color = colors.muted,
                  style = QUERY_STYLE,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
            }
            innerTextField()
          }
          if (query.isNotEmpty()) ClearButton(onClear, colors)
        }
      },
  )
}

/** The round button that empties the field. */
@Composable
private fun ClearButton(onClear: () -> Unit, colors: AddressSearchColors) {
  Box(
      Modifier.testTag(C.Tag.VENUE_ADDRESS_CLEAR)
          .size(CLEAR_SIZE)
          .clip(CircleShape)
          .background(colors.line)
          .clickable(role = Role.Button, onClick = onClear),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painterResource(R.drawable.ic_search_clear),
        contentDescription = stringResource(R.string.venue_address_clear),
        tint = colors.ink,
    )
  }
}

/** The card under the field, which scrolls when [modifier] leaves it too little height. */
@Composable
private fun ResultsCard(
    colors: AddressSearchColors,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(CARD_RADIUS)
  Column(
      modifier
          .testTag(C.Tag.VENUE_ADDRESS_RESULTS)
          .fillMaxWidth()
          .clip(shape)
          .background(colors.surface)
          .border(HAIRLINE, colors.line, shape)
          .verticalScroll(rememberScrollState())
  ) {
    content()
  }
}

/**
 * One suggestion: a pin on a round tile, then its title over its second line. Faded and inert
 * unless [enabled].
 */
@Composable
private fun SuggestionRow(
    suggestion: AddressSuggestion,
    query: String,
    colors: AddressSearchColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  Row(
      Modifier.testTag(C.Tag.VENUE_ADDRESS_RESULT)
          .fillMaxWidth()
          .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
          .alpha(if (enabled) 1f else STALE_ALPHA)
          .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
  ) {
    Box(
        Modifier.testTag(C.Tag.VENUE_ADDRESS_RESULT_AVATAR)
            .size(AVATAR_SIZE)
            .background(colors.avatar, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painterResource(R.drawable.ic_address_pin),
          // The row's text names the address
          contentDescription = null,
          modifier = Modifier.size(AVATAR_ICON),
          tint = colors.onAvatar,
      )
    }
    Column(Modifier.weight(1f).heightIn(min = AVATAR_SIZE), Arrangement.Center) {
      Text(
          highlighted(suggestion.title, query, colors.highlight),
          Modifier.testTag(C.Tag.VENUE_ADDRESS_RESULT_TITLE),
          color = colors.ink,
          style = TITLE_STYLE,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      suggestion.subtitle?.let {
        Spacer(Modifier.height(LINE_GAP))
        Text(
            it,
            color = colors.muted,
            style = SUBTITLE_STYLE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** [title] with its first match of the trimmed [query], in any case, drawn in [color]. */
internal fun highlighted(title: String, query: String, color: Color): AnnotatedString {
  val match = query.trim()
  val start = if (match.isEmpty()) -1 else title.indexOf(match, ignoreCase = true)
  if (start < 0) return AnnotatedString(title)
  return buildAnnotatedString {
    append(title.substring(0, start))
    withStyle(SpanStyle(color = color)) { append(title.substring(start, start + match.length)) }
    append(title.substring(start + match.length))
  }
}
