// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Reads and writes [Completion]s. ViewModels depend on this interface only. */
interface CompletionRepository {
  /**
   * Stores a new pending completion of the reservation [reservationId] under [completionId], with
   * its submission time set by the repository. It is the proof of the whole party.
   *
   * A party submits at most one completion per reservation, whichever member sends it: if one is
   * already stored, whatever its status, this fails and changes nothing, so neither a retry nor a
   * second member can create a second one.
   *
   * Waits a bounded time for the backend. A refusal by the security rules in that time is returned
   * as a failure. Without an answer, e.g. while offline, the write stays queued and this returns
   * success, so a later refusal is not reported.
   *
   * @param reservationId id of the approved reservation the proof belongs to.
   * @param venueId id of the venue that owns the reservation's quest.
   * @param explorerUid uid of the party member submitting the proof.
   * @param proof what the member submits.
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
   * Observes the completions an explorer submitted themselves, not those another member of their
   * party sent. Emits the current list on collection, then again whenever one is added or changes.
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
   * Observes the completion of one reservation, so every member of its party sees the party's proof
   * and its result. Emits the current list on collection, then again whenever one is added or
   * changes.
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
   * @param reason why the venue rejects the proof; not blank, at most [MAX_REJECT_REASON_LENGTH]
   *   characters. Invalid reasons fail before any backend request.
   * @return success, or a failure if [reason] is invalid ([IllegalArgumentException]), if there is
   *   no completion with this id ([NoSuchElementException]), if [canTransition] forbids the change
   *   ([IllegalStateException]), or if the write failed.
   */
  suspend fun reject(id: String, reason: String): Result<Unit>
}

/**
 * Observes the completions of every reservation of [reservationIds], so a party member sees the
 * party's proof whichever member sent it. Emits once every reservation has been read, then again
 * whenever one changes.
 *
 * @return the completions of those reservations, in unspecified order; an empty list at once when
 *   there are none.
 * @throws Exception from the flow if the listener of any reservation fails, as
 *   [CompletionRepository.observeForReservation] does.
 */
fun CompletionRepository.observeForReservations(
    reservationIds: Collection<String>
): Flow<List<Completion>> {
  val ids = reservationIds.distinct()
  if (ids.isEmpty()) return flowOf(emptyList())
  return combine(ids.map(::observeForReservation)) { it.toList().flatten() }
}
