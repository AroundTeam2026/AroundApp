// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.aroundteam2026.aroundapp.model.location.LocationPermissions

/** Requests fine and coarse together; only a fine grant can continue a completion submission. */
@Composable
internal fun rememberCompletionPermission(onSubmit: () -> Unit): CompletionPermission {
  val context = LocalContext.current
  val locationManager = remember(context) { context.getSystemService(LocationManager::class.java) }
  var servicesEnabled by remember { mutableStateOf(locationManager.isLocationEnabled) }
  val currentSubmit by rememberUpdatedState(onSubmit)
  var refused by rememberSaveable { mutableStateOf(false) }
  var requested by rememberSaveable { mutableStateOf(false) }
  val fineGranted = {
    ContextCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
  }
  val continueWithLocation = {
    // Read again at the tap: services can change while this screen is open.
    servicesEnabled = locationManager.isLocationEnabled
    if (servicesEnabled) currentSubmit()
    else context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
  }
  val launcher =
      rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        refused = !fineGranted()
        if (!refused) continueWithLocation()
      }
  LifecycleResumeEffect(Unit) {
    servicesEnabled = locationManager.isLocationEnabled
    if (fineGranted()) refused = false
    onPauseOrDispose {}
  }
  return CompletionPermission(
      refused = refused,
      servicesEnabled = servicesEnabled,
      submit = {
        if (fineGranted()) continueWithLocation()
        else {
          requested = true
          launcher.launch(LocationPermissions.ALL)
        }
      },
      request = {
        servicesEnabled = locationManager.isLocationEnabled
        if (!servicesEnabled) {
          context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else if (fineGranted()) {
          currentSubmit()
        } else if (refused && requested) {
          context.startActivity(
              Intent(
                  Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.fromParts("package", context.packageName, null),
              )
          )
        } else {
          requested = true
          launcher.launch(LocationPermissions.ALL)
        }
      },
  )
}

internal data class CompletionPermission(
    val refused: Boolean,
    val servicesEnabled: Boolean,
    val submit: () -> Unit,
    val request: () -> Unit,
)
