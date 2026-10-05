// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.location

import android.content.Context

/** Builds the app's [LocationRepository], so ViewModels never depend on an implementation. */
object LocationRepositoryProvider {
  /** The repository to use; it keeps only the application context, so it can't leak a screen. */
  fun repository(context: Context): LocationRepository =
      FusedLocationRepository(context.applicationContext)
}
