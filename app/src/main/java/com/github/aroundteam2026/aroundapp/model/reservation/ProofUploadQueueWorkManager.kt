// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A [ProofUploadQueue] backed by WorkManager: each proof is a [ProofUploadWorker] run, enqueued as
 * unique work per reservation, which WorkManager keeps across app restarts. A reservation is
 * [waiting] while its work hasn't finished, so it leaves once the proof is submitted, or once the
 * worker stops or is cancelled.
 *
 * @param discard Deletes the copy of a proof that a newer one replaced or that was cancelled, since
 *   its cancelled work never runs to delete it.
 */
class ProofUploadQueueWorkManager(
    private val workManager: WorkManager,
    private val discard: suspend (Uri) -> Boolean,
) : ProofUploadQueue {

  override suspend fun enqueue(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      photo: File,
  ): Result<Unit> =
      try {
        val name = ProofUploadWorker.uniqueWorkName(reservationId)
        val replaced = waitingPhotos(name)
        workManager
            .enqueueUniqueWork(
                name,
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
        replaced.filter { it != Uri.fromFile(photo) }.forEach { discard(it) }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  override suspend fun cancel(reservationId: String): Result<Unit> {
    val name = ProofUploadWorker.uniqueWorkName(reservationId)
    return cancelAndDiscard(workManager.getWorkInfosForUniqueWorkFlow(name)) {
      workManager.cancelUniqueWork(name).await()
    }
  }

  override suspend fun cancelAll(): Result<Unit> =
      cancelAndDiscard(workManager.getWorkInfosByTagFlow(ProofUploadWorker.TAG)) {
        workManager.cancelAllWorkByTag(ProofUploadWorker.TAG).await()
      }

  /**
   * Runs [cancelWorks] on the proofs in [works], then deletes the copies of those that were still
   * waiting, since their cancelled work never runs to delete them.
   *
   * @return success once they are cancelled, or the failure that kept them from being, in which
   *   case no copy is deleted.
   */
  private suspend fun cancelAndDiscard(
      works: Flow<List<WorkInfo>>,
      cancelWorks: suspend () -> Unit,
  ): Result<Unit> =
      try {
        val cancelled = waitingPhotos(works)
        cancelWorks()
        cancelled.forEach { discard(it) }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /** The photos of the unique work [name] that hasn't finished yet. */
  private suspend fun waitingPhotos(name: String): List<Uri> =
      waitingPhotos(workManager.getWorkInfosForUniqueWorkFlow(name))

  /** The photos of the [works] that haven't finished yet, as they are now. */
  private suspend fun waitingPhotos(works: Flow<List<WorkInfo>>): List<Uri> =
      works
          .first()
          .filterNot { it.state.isFinished }
          .flatMap { work -> work.tags.mapNotNull(ProofUploadWorker::photoOf) }

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
