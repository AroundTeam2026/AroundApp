// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.work.WorkManager
import com.github.aroundteam2026.aroundapp.ui.completion.ProofPhotos

/** Builds the app's [ProofUploadQueue], so ViewModels never depend on an implementation. */
object ProofUploadQueueProvider {
  /** The queue to use; any context will do, since WorkManager keeps only the app's. */
  fun queue(context: Context): ProofUploadQueue =
      ProofUploadQueueWorkManager(WorkManager.getInstance(context), ProofPhotos(context)::discard)
}
