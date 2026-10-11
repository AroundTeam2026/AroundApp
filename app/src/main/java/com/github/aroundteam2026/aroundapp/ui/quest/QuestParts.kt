// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** A venue's avatar on its background, in the initials' text style. */
@Composable
internal fun QuestAvatar(
    avatar: VenueAvatar,
    style: QuestAvatarStyle,
    modifier: Modifier = Modifier,
) {
  Box(modifier.size(style.size).background(style.background, style.shape).padding(style.padding)) {
    CompositionLocalProvider(LocalTextStyle provides style.textStyle) {
      avatar.Content(tint = style.content, modifier = Modifier.fillMaxSize())
    }
  }
}

/** One line of a quest's text, cut off with an ellipsis past [maxLines]. */
@Composable
internal fun QuestLine(
    text: String,
    tag: String,
    style: TextStyle,
    color: Color,
    maxLines: Int = 1,
) {
  Text(
      text,
      Modifier.testTag(tag),
      color = color,
      style = style,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
  )
}
