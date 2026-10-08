// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import java.util.Locale

/**
 * What a venue's pin and card show for it. Each kind draws itself, so adding one, such as an image
 * the venue uploads, needs no change to the pin or the card.
 *
 * Implementations must be immutable with a meaningful `equals`: the map redraws a marker only when
 * its avatar stops being equal.
 */
@Stable
interface VenueAvatar {
  /** Draws the avatar to fill [modifier]; [tint] is the colour for single-colour avatars. */
  @Composable fun Content(tint: Color, modifier: Modifier)

  /** A venue's [initials] in the current text style, as the card shows until venues have images. */
  data class Initials(val initials: String) : VenueAvatar {
    @Composable
    override fun Content(tint: Color, modifier: Modifier) {
      Box(modifier, contentAlignment = Alignment.Center) {
        Text(initials, color = tint, style = LocalTextStyle.current, maxLines = 1)
      }
    }
  }

  /** The quest flag, shown until venues can choose their own image. */
  data object QuestFlag : VenueAvatar {
    @Composable
    override fun Content(tint: Color, modifier: Modifier) {
      Icon(
          painterResource(R.drawable.ic_quests),
          // The card names the venue; on the map, the marker describes itself
          contentDescription = null,
          modifier = modifier.testTag(C.Tag.QUEST_FLAG_AVATAR),
          tint = tint,
      )
    }
  }
}

private val WHITESPACE = Regex("""\s+""")

/**
 * Up to two initials for [name]: the first letter or digit of its first and last words, in
 * uppercase whatever the device's language. Words without a letter or digit are skipped; a name
 * without any gives "?".
 */
fun initialsOf(name: String): String {
  val firstChars =
      name.split(WHITESPACE).mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
  val initials = listOfNotNull(firstChars.firstOrNull(), firstChars.drop(1).lastOrNull())
  return if (initials.isEmpty()) "?" else initials.joinToString("").uppercase(Locale.ROOT)
}
