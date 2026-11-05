// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
  val currentSubmit by rememberUpdatedState(onSubmit)
  var refused by rememberSaveable { mutableStateOf(false) }
  var requested by rememberSaveable { mutableStateOf(false) }
  val fineGranted = {
    ContextCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
  }
  val launcher =
      rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        refused = !fineGranted()
        if (!refused) currentSubmit()
      }
  LifecycleResumeEffect(Unit) {
    if (fineGranted()) refused = false
    onPauseOrDispose {}
  }
  return CompletionPermission(
      refused = refused,
      submit = {
        if (fineGranted()) currentSubmit()
        else {
          requested = true
          launcher.launch(LocationPermissions.ALL)
        }
      },
      request = {
        if (refused && requested) {
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
    val submit: () -> Unit,
    val request: () -> Unit,
)
