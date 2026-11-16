// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.ui.theme.AroundPillShape

/** Draws a circular profile photo with an outline visible on light and dark surfaces. */
@Composable
internal fun ProfilePhoto(picture: ImageBitmap, modifier: Modifier = Modifier) {
  Image(
      picture,
      contentDescription = null,
      modifier = modifier.clip(AroundPillShape).profileAvatarOutline(),
      contentScale = ContentScale.Crop,
  )
}

/** Adds the thin circular outline used for profile avatars. */
@Composable
internal fun Modifier.profileAvatarOutline(): Modifier =
    border(PROFILE_AVATAR_OUTLINE, MaterialTheme.colorScheme.outline, AroundPillShape)

private val PROFILE_AVATAR_OUTLINE = 1.dp
