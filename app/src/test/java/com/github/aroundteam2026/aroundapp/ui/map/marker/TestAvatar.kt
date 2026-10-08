// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** Stands in for a venue image: an avatar the tests can find by its [name]. */
data class TestAvatar(val name: String) : VenueAvatar {
  @Composable
  override fun Content(tint: Color, modifier: Modifier) {
    Box(modifier.testTag(tagOf(name)))
  }

  companion object {
    fun tagOf(name: String) = "test_avatar_$name"
  }
}
