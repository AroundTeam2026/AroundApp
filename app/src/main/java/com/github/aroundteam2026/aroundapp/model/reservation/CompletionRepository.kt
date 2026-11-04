// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow

/** Reads and writes [Completion]s. ViewModels depend on this interface only. */
interface CompletionRepository {
  /**
   * Stores a new completion of the reservation [reservationId]. The repository, not the caller,
   * sets the rest: it stores it under [completionId], sets the submission time, and starts the
   * completion as [CompletionStatus.PENDING], without a reject reason. An explorer submits at most
   * one completion per reservation: if one is already stored under that id, whatever its status,
   * this fails and changes nothing, so a retry can't create a second one.
   *
   * Waits for the backend to confirm the write, for a bounded time. The security rules only let an
   * explorer of an approved reservation submit, as themselves, a proof of the type its quest asks
   * for, so the Firestore implementation's server can refuse the write where the fake doesn't
   * check; a refusal that arrives in time is returned as a failure and nothing is stored. When no
   * answer arrives in time, e.g. while offline, the write stays queued, is sent once the device is
   * back online, and this returns success: a refusal after that is not reported here.
   *
   * @param reservationId id of the approved reservation the proof belongs to.
   * @param venueId id of the venue that owns the reservation's quest.
   * @param explorerUid uid of the explorer submitting the proof.
   * @param proof what the explorer submits.
   * @return the new completion's id, or a failure if one is already stored under it
   *   ([IllegalStateException]), if the write could not be issued, or if the backend refused it in
   *   time.
   */
  suspend fun submit(
      reservationId: String,
      venueId: String,
      explorerUid: String,
      proof: Proof,
  ): Result<String>

  /**
   * Observes the completions an explorer submitted. Emits the current list on collection, then
   * again whenever one is added or changes.
   *
   * @param uid uid of the explorer.
   * @return all of that explorer's completions, whatever their status, in unspecified order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeForExplorer(uid: String): Flow<List<Completion>>

  /**
   * Observes the completions submitted for one venue's quests. Emits the current list on
   * collection, then again whenever one is added or changes.
   *
   * @param venueId id of the venue whose completions to return.
   * @return all of that venue's completions, whatever their status, in unspecified order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeForVenue(venueId: String): Flow<List<Completion>>

  /**
   * Moves a completion to [CompletionStatus.APPROVED], changing nothing else. The security rules
   * only let the venue that owns it do so, so the Firestore implementation fails for anyone else.
   *
   * @param id id of the completion.
   * @return success, or a failure if there is no completion with this id
   *   ([NoSuchElementException]), if [canTransition] forbids the change ([IllegalStateException]),
   *   or if the write failed.
   */
  suspend fun approve(id: String): Result<Unit>

  /**
   * Moves a completion to [CompletionStatus.REJECTED] with [reason], which the explorer sees,
   * changing nothing else. The security rules only let the venue that owns it do so, so the
   * Firestore implementation fails for anyone else.
   *
   * @param id id of the completion.
   * @param reason why the venue rejects the proof; must not be blank.
   * @return success, or a failure if [reason] is blank ([IllegalArgumentException]), if there is no
   *   completion with this id ([NoSuchElementException]), if [canTransition] forbids the change
   *   ([IllegalStateException]), or if the write failed.
   */
  suspend fun reject(id: String, reason: String): Result<Unit>
}
