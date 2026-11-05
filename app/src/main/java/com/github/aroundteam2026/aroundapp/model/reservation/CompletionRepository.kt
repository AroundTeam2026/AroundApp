// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow

/** Reads and writes [Completion]s. ViewModels depend on this interface only. */
interface CompletionRepository {
  /**
   * Stores a new pending completion of the reservation [reservationId] under [completionId], with
   * its submission time set by the repository.
   *
   * An explorer submits at most one completion per reservation: if one is already stored, whatever
   * its status, this fails and changes nothing, so a retry can't create a second one.
   *
   * Waits a bounded time for the backend. A refusal by the security rules in that time is returned
   * as a failure. Without an answer, e.g. while offline, the write stays queued and this returns
   * success, so a later refusal is not reported.
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
   * Observes every completion of one reservation, so its party sees each other's submissions. Emits
   * the current list on collection, then again whenever one is added or changes.
   *
   * The security rules only let a joined member of the party run this, so the Firestore
   * implementation's flow fails for anyone else; a venue uses [observeForVenue] instead.
   *
   * @param reservationId id of the reservation whose completions to return.
   * @return all of that reservation's completions, whatever their status, in unspecified order.
   * @throws Exception from the flow if the backend stops the listener (e.g. permission denied after
   *   sign-out). Collectors must catch it, e.g. with `.catch`, or it crashes their scope.
   */
  fun observeForReservation(reservationId: String): Flow<List<Completion>>

  /**
   * Moves a pending or rejected completion to [CompletionStatus.APPROVED], removing any reject
   * reason and changing nothing else, so a venue can change its mind about a rejection. The
   * security rules only let the venue that owns it do so, so the Firestore implementation fails for
   * anyone else.
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
