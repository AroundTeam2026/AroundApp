// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A [ProofUploadQueue] backed by WorkManager: each proof is a [ProofUploadWorker] run, enqueued as
 * unique work per reservation, which WorkManager keeps across app restarts. A reservation is
 * [waiting] while its work hasn't finished, so it leaves once the proof is submitted, or once the
 * worker stops or is cancelled. It is [failed] while its work is in [WorkInfo.State.FAILED], until
 * WorkManager prunes finished works, about a day later.
 *
 * @param discard Deletes the copy of a proof that a newer one replaced, since its cancelled work
 *   never runs to delete it.
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
        val replaced =
            workManager
                .getWorkInfosForUniqueWorkFlow(name)
                .first()
                .filterNot { it.state.isFinished }
                .flatMap { work -> work.tags.mapNotNull(ProofUploadWorker::photoOf) }
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

  override val waiting: Flow<Set<String>> = reservationsWhere { !it.state.isFinished }

  override val failed: Flow<Set<String>> = reservationsWhere(::failedForGood)

  override suspend fun retry(reservationId: String): Result<Unit> {
    val tags =
        try {
          workManager
              .getWorkInfosForUniqueWorkFlow(ProofUploadWorker.uniqueWorkName(reservationId))
              .first()
              .firstOrNull(::failedForGood)
              ?.tags
              .orEmpty()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          return Result.failure(e)
        }
    val venueId = tags.firstNotNullOfOrNull(ProofUploadWorker::venueIdOf)
    val explorerUid = tags.firstNotNullOfOrNull(ProofUploadWorker::explorerUidOf)
    val photo = tags.firstNotNullOfOrNull(ProofUploadWorker::photoOf)?.path?.let(::File)
    return when {
      venueId == null || explorerUid == null || photo == null ->
          Result.failure(NoSuchElementException("No failed proof for $reservationId"))
      !photo.exists() -> Result.failure(FileNotFoundException("${photo.path} is no longer kept"))
      else -> enqueue(reservationId, venueId, explorerUid, photo)
    }
  }

  /** The ids of the reservations of the proof uploads that match [keep], on each change. */
  private fun reservationsWhere(keep: (WorkInfo) -> Boolean): Flow<Set<String>> =
      workManager
          .getWorkInfosByTagFlow(ProofUploadWorker.TAG)
          .map { works ->
            works
                .filter(keep)
                .mapNotNull { work ->
                  work.tags.firstNotNullOfOrNull(ProofUploadWorker::reservationIdOf)
                }
                .toSet()
          }
          .distinctUntilChanged()

  /** Whether [work] failed without sending a proof that is still needed. */
  private fun failedForGood(work: WorkInfo): Boolean =
      work.state == WorkInfo.State.FAILED &&
          // The venue approved the completion, so the explorer has nothing left to send
          work.outputData.getString(ProofUploadWorker.KEY_ERROR) !=
              ProofUploadWorker.ERROR_ALREADY_APPROVED
}
