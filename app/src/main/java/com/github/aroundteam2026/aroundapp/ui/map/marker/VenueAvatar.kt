// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * What a venue's pin shows for it. Each kind draws itself, so adding one, such as an icon for the
 * venue's category, needs no change to the pin.
 *
 * Implementations must be immutable with a meaningful `equals`: the map redraws a marker only when
 * its avatar stops being equal.
 */
@Stable
interface VenueAvatar {
  /** Draws the avatar to fill [modifier]; [tint] is the colour for single-colour avatars. */
  @Composable fun Content(tint: Color, modifier: Modifier)

  /** The quest flag, shown until venues have categories. */
  data object QuestFlag : VenueAvatar {
    @Composable
    override fun Content(tint: Color, modifier: Modifier) {
      Icon(
          painterResource(R.drawable.ic_quests),
          // The marker describes itself to screen readers
          contentDescription = null,
          modifier = modifier.testTag(C.Tag.QUEST_FLAG_AVATAR),
          tint = tint,
      )
    }
  }
}
