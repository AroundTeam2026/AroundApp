// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.content.ContextCompat

/** The location permissions the app asks for. */
object LocationPermissions {
  /** Requested together: the user can then choose between a precise and an approximate location. */
  val ALL = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

  /** Whether the user granted any of [ALL], so an approximate location is enough. */
  fun isGranted(context: Context): Boolean = ALL.any {
    ContextCompat.checkSelfPermission(context, it) == PERMISSION_GRANTED
  }
}
