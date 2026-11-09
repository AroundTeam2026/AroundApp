// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.location.LocationPermissions
import com.github.aroundteam2026.aroundapp.resources.C

/**
 * Says the location is off, so the map shows the quests around Lausanne rather than around the
 * explorer, with a "Turn on" button, as in Figma.
 *
 * @param onTurnOn Called when the explorer taps "Turn on"; see [rememberTurnOnLocation].
 */
@Composable
fun LocationBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
  MapBanner(
      icon = R.drawable.ic_location_off,
      title = stringResource(R.string.map_location_off),
      caption = stringResource(R.string.map_location_off_fallback),
      action = stringResource(R.string.map_location_turn_on),
      onAction = onTurnOn,
      actionTag = C.Tag.LOCATION_TURN_ON,
      modifier = modifier.testTag(C.Tag.LOCATION_BANNER),
  )
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
