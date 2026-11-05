// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context

/** Builds the app's [ProofPhotoRepository], so ViewModels never depend on an implementation. */
object ProofPhotoRepositoryProvider {
  /** The repository to use; any context will do, since the repository keeps only the app's. */
  fun repository(context: Context): ProofPhotoRepository = ProofPhotoRepositoryStorage(context)
}
