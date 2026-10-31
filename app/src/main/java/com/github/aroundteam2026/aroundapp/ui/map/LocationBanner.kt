// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.location.LocationPermissions
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import com.github.aroundteam2026.aroundapp.ui.theme.AroundCorners
import com.github.aroundteam2026.aroundapp.ui.theme.AroundSpacing
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTextStyles
import com.github.aroundteam2026.aroundapp.ui.theme.AroundTheme

// The banner's own geometry, from Figma (frame 99:588); its gaps come from AroundSpacing
private val TILE_SIZE = 40.dp
private val ICON_SIZE = 20.dp
private val TEXT_SPACING = 2.dp
private val BUTTON_HEIGHT = 44.dp
private val BANNER_ELEVATION = 8.dp

/**
 * Says the location is off, so the map shows the quests around Lausanne rather than around the
 * explorer, with a "Turn on" button, as in Figma.
 *
 * @param onTurnOn Called when the explorer taps "Turn on"; see [rememberTurnOnLocation].
 */
@Composable
fun LocationBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier
          .testTag(C.Tag.LOCATION_BANNER)
          .fillMaxWidth()
          .shadow(
              BANNER_ELEVATION,
              AroundCorners.LocationBanner,
              ambientColor = AroundColors.BannerShadow,
              spotColor = AroundColors.BannerShadow,
          )
          .background(colors.surface, AroundCorners.LocationBanner)
          .padding(
              start = AroundSpacing.InsetPadding,
              top = AroundSpacing.Medium,
              end = AroundSpacing.Medium,
              bottom = AroundSpacing.Medium,
          ),
      horizontalArrangement = Arrangement.spacedBy(AroundSpacing.Medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        Modifier.size(TILE_SIZE).background(AroundTheme.colors.lavender, AroundCorners.InsetField),
        Alignment.Center,
    ) {
      // Decorative: the title says it
      Icon(
          painterResource(R.drawable.ic_location_off),
          contentDescription = null,
          Modifier.size(ICON_SIZE),
          tint = colors.onSurface,
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_SPACING)) {
      Text(
          stringResource(R.string.map_location_off),
          color = colors.onSurface,
          style = AroundTextStyles.Subtitle,
      )
      Text(
          stringResource(R.string.map_location_off_fallback),
          color = colors.onSurfaceVariant,
          style = AroundTextStyles.Caption,
      )
    }
    Button(
        onTurnOn,
        Modifier.testTag(C.Tag.LOCATION_TURN_ON).height(BUTTON_HEIGHT),
        shape = AroundCorners.InsetField,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.tertiary,
                contentColor = colors.onTertiary,
            ),
        contentPadding = PaddingValues(horizontal = AroundSpacing.InsetPadding),
    ) {
      Text(stringResource(R.string.map_location_turn_on), style = AroundTextStyles.FieldLabel)
    }
  }
}

/**
 * What "Turn on" does. While Android would still show its dialog, it asks for the location again
 * and reports the answer to [onResult]. Once the explorer told Android not to ask again, the dialog
 * can't show, so it opens the app's settings instead, where they can allow it; back on the map, its
 * own check sees the permission.
 */
@Composable
internal fun rememberTurnOnLocation(onResult: (granted: Boolean) -> Unit): () -> Unit {
  val activity = LocalActivity.current
  val context = LocalContext.current
  // A dismissed dialog answers with no grants at all, which counts as a refusal
  val launcher =
      rememberLauncherForActivityResult(RequestMultiplePermissions()) { grants ->
        onResult(grants.values.any { it })
      }
  return {
    val canAsk =
        activity != null &&
            LocationPermissions.ALL.any {
              ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
    if (canAsk) {
      launcher.launch(LocationPermissions.ALL)
    } else {
      context.startActivity(
          Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.fromParts("package", context.packageName, null),
          )
      )
    }
  }
}
