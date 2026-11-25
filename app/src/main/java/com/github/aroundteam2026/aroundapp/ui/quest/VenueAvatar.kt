// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.ProfilePhoto
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// NOSONAR below: Sonar's S6517 asks for a fun interface, but a lambda avatar has no meaningful
// equals, so the map would redraw its marker every time
/**
 * What a venue's pin, card and nearby page show for it. Each kind draws itself, so adding one, such
 * as an image the venue uploads, needs no change to them.
 *
 * Implementations must be immutable with a meaningful `equals`: the map redraws a marker only when
 * its avatar stops being equal.
 */
@Stable
interface VenueAvatar { // NOSONAR
  /** Draws the avatar to fill [modifier]; [tint] is the colour for single-colour avatars. */
  @Composable fun Content(tint: Color, modifier: Modifier)

  /** Whether the avatar fills its whole circle, with no padding around it, as a photo does. */
  val fillsCircle: Boolean
    get() = false

  /**
   * A venue's [initials] in the current text style, as the card and the pager show until venues
   * have images.
   */
  data class Initials(val initials: String) : VenueAvatar {
    @Composable
    override fun Content(tint: Color, modifier: Modifier) {
      Box(modifier, contentAlignment = Alignment.Center) {
        Text(initials, color = tint, style = LocalTextStyle.current, maxLines = 1)
      }
    }
  }

  /** The [picture] the venue uploaded, cropped to its circle. */
  data class Picture(val picture: ImageBitmap) : VenueAvatar {
    override val fillsCircle: Boolean
      get() = true

    @Composable
    override fun Content(tint: Color, modifier: Modifier) {
      ProfilePhoto(picture, modifier.testTag(C.Tag.VENUE_PICTURE_AVATAR))
    }
  }

  /** The quest flag, shown when a venue has no picture of its own. */
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

/**
 * [bytes] as a picture at most [maxEdge] pixels on each side, decoded on [dispatcher], or null if
 * they aren't an image. An avatar is small, so it never keeps a full-size photo in memory.
 */
internal suspend fun decodeAvatar(
    bytes: ByteArray,
    maxEdge: Int = AVATAR_EDGE_PX,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): ImageBitmap? =
    withContext(dispatcher) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

/** The longest side an avatar is decoded at: its 32 dp even on the densest screens. */
private const val AVATAR_EDGE_PX = 128
