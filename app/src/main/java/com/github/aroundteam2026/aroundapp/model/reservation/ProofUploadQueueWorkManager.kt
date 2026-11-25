// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.github.aroundteam2026.aroundapp.worker.ProofUploadWorker
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [ProofUploadQueue] backed by WorkManager: each proof is a [ProofUploadWorker] run, enqueued as
 * unique work per reservation, which WorkManager keeps across app restarts. A reservation is
 * [waiting] while its work hasn't finished, so it leaves once the proof is submitted, or once the
 * worker stops or is cancelled. It is [failed] while its work is in [WorkInfo.State.FAILED], until
 * WorkManager prunes finished works, about a day later.
 *
 * [enqueue], [retry], [cancel] and [cancelAll] run one at a time, across every instance, so one
 * never acts on what another has just replaced.
 *
 * @param discard Deletes the copy of a proof that a newer one replaced or that was cancelled, since
 *   its work never runs again to delete it.
 */
class ProofUploadQueueWorkManager(
    private val workManager: WorkManager,
    private val discard: suspend (Uri) -> Boolean,
) : ProofUploadQueue {

  private val uploads: Flow<List<WorkInfo>> =
      workManager.getWorkInfosByTagFlow(ProofUploadWorker.TAG)

  override val waiting: Flow<Set<String>> =
      uploads
          .map { works ->
            works.filterNot { it.state.isFinished }.mapNotNull(::reservationOf).toSet()
          }
          .distinctUntilChanged()

  override val failed: Flow<Map<String, UploadFailure>> =
      uploads
          .map { works ->
            works
                .mapNotNull { work ->
                  val reservationId = reservationOf(work)
                  val failure = failureOf(work)
                  if (reservationId == null || failure == null) null else reservationId to failure
                }
                .toMap()
          }
          .distinctUntilChanged()

  override suspend fun enqueue(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      photo: File,
  ): Result<Unit> = lock.withLock { replace(reservationId, venueId, explorerUid, photo) }

  override suspend fun retry(reservationId: String): Result<Unit> = lock.withLock {
    try {
      val tags =
          works(reservationId).first().firstOrNull { failureOf(it) != null }?.tags
              ?: throw NoSuchElementException("No failed proof for $reservationId")
      val venueId = tags.firstNotNullOfOrNull(ProofUploadWorker::venueIdOf)
      val explorerUid = tags.firstNotNullOfOrNull(ProofUploadWorker::explorerUidOf)
      val photo = tags.firstNotNullOfOrNull(ProofUploadWorker::photoOf)?.path?.let(::File)
      // A work queued before uploads were tagged with their venue and sender can't be rebuilt
      if (venueId == null || explorerUid == null || photo == null) {
        error("The failed proof of $reservationId can't be rebuilt")
      }
      if (!photo.exists()) throw FileNotFoundException("${photo.path} is no longer kept")
      replace(reservationId, venueId, explorerUid, photo)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun cancel(reservationId: String): Result<Unit> {
    val name = ProofUploadWorker.uniqueWorkName(reservationId)
    return cancelAndDiscard(works(reservationId)) {
      // WorkManager can't delete one finished work, but REPLACE does, so a failed proof leaves
      // failed too; the placeholder that takes its place is cancelled before it could run
      workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, placeholder()).await()
      workManager.cancelUniqueWork(name).await()
    }
  }

  override suspend fun cancelAll(): Result<Unit> =
      cancelAndDiscard(uploads) {
        workManager.cancelAllWorkByTag(ProofUploadWorker.TAG).await()
        // Deletes every finished work, so the failed proofs leave failed too
        workManager.pruneWork().await()
      }

  /**
   * Runs [cancelWorks] on the proofs in [works], then deletes the copies they kept.
   *
   * @return success once they are cancelled, or the failure that kept them from being, in which
   *   case no copy is deleted.
   */
  private suspend fun cancelAndDiscard(
      works: Flow<List<WorkInfo>>,
      cancelWorks: suspend () -> Unit,
  ): Result<Unit> = lock.withLock {
    try {
      val cancelled = keptPhotos(works)
      cancelWorks()
      cancelled.forEach { discard(it) }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Queues [photo] as [reservationId]'s proof in place of any other, and deletes the copy of the
   * one it replaces. Callers hold [lock].
   */
  private suspend fun replace(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      photo: File,
  ): Result<Unit> =
      try {
        val replaced = keptPhotos(works(reservationId))
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
        replaced.filter { it != Uri.fromFile(photo) }.forEach { discard(it) }
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /** The works of [reservationId]'s proof upload. */
  private fun works(reservationId: String): Flow<List<WorkInfo>> =
      workManager.getWorkInfosForUniqueWorkFlow(ProofUploadWorker.uniqueWorkName(reservationId))

  /**
   * The copies [works] still keep, as they are now: a waiting one's, and a failed one's for
   * [retry].
   */
  private suspend fun keptPhotos(works: Flow<List<WorkInfo>>): List<Uri> =
      works
          .first()
          .filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }
          .flatMap { work -> work.tags.mapNotNull(ProofUploadWorker::photoOf) }

  private companion object {
    /** Shared by every instance, since the provider may build more than one. */
    val lock = Mutex()

    /** Long enough that a placeholder is always cancelled before it could run. */
    const val PLACEHOLDER_DELAY_DAYS = 365L

    /** The id of the reservation whose upload [work] is, or null if it names none. */
    fun reservationOf(work: WorkInfo): String? =
        work.tags.firstNotNullOfOrNull(ProofUploadWorker::reservationIdOf)

    /** Why [work] failed for good, or null if it hasn't or its completion is already approved. */
    fun failureOf(work: WorkInfo): UploadFailure? =
        if (work.state != WorkInfo.State.FAILED) null
        else
            when (work.outputData.getString(ProofUploadWorker.KEY_ERROR)) {
              ProofUploadWorker.ERROR_ALREADY_APPROVED -> null
              ProofUploadWorker.ERROR_UPLOAD,
              ProofUploadWorker.ERROR_SUBMIT -> UploadFailure.RETRYABLE
              ProofUploadWorker.ERROR_PHOTO_MISSING -> UploadFailure.PHOTO_MISSING
              else -> UploadFailure.REFUSED
            }

    /** An untagged work that never runs, which [cancel] swaps in for a reservation's works. */
    fun placeholder(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ProofUploadWorker>()
            .setInitialDelay(PLACEHOLDER_DELAY_DAYS, TimeUnit.DAYS)
            .build()
  }
}
