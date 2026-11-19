// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.await
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A [ProofUploadQueue] backed by WorkManager: each proof is a [ProofUploadWorker] run, enqueued as
 * unique work per reservation, which WorkManager keeps across app restarts. A reservation is
 * [waiting] while its work hasn't finished, so it leaves once the proof is submitted, or once the
 * worker stops or is cancelled.
 */
class ProofUploadQueueWorkManager(private val workManager: WorkManager) : ProofUploadQueue {

  override suspend fun enqueue(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      photo: File,
  ): Result<Unit> =
      try {
        workManager
            .enqueueUniqueWork(
                ProofUploadWorker.uniqueWorkName(reservationId),
                // A reservation has one proof waiting at a time, as ProofUploadQueue describes
                ExistingWorkPolicy.REPLACE,
                ProofUploadWorker.buildRequest(
                    reservationId,
                    venueId,
                    explorerUid,
                    Uri.fromFile(photo),
                ),
            )
            .await()
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  override val waiting: Flow<Set<String>> =
      workManager
          .getWorkInfosByTagFlow(ProofUploadWorker.TAG)
          .map { works ->
            works
                .filterNot { it.state.isFinished }
                .mapNotNull { work ->
                  work.tags.firstNotNullOfOrNull(ProofUploadWorker::reservationIdOf)
                }
                .toSet()
          }
          .distinctUntilChanged()
}
