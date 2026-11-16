// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.OfflineHint
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing

/**
 * Says the device is offline, so the app shows what is saved on this phone, as the offline banner
 * in Figma (component 4579:9313). It is the screens' [OfflineHint] with an app-wide caption, so the
 * two look the same; the shells show it under their header while offline (#408).
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
  OfflineHint(R.string.offline_banner_caption, modifier, tag = C.Tag.OFFLINE_BANNER)
}

@Preview(name = "Light", widthDp = 413)
@Preview(name = "Dark", widthDp = 413, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun OfflineBannerPreview() {
  AroundAppTheme {
    // On the app's background, 16 dp from the screen's edges as in Figma, with room for the shadow
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(AroundSpacing.Large)) {
      OfflineBanner()
    }
  }
}
