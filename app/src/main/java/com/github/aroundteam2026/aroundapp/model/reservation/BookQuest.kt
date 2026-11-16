// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Books the quest [questId] of [venueId] for the explorer [explorerUid]: a pending reservation for
 * a slot starting at [now], with the explorer as its party's first member, under the display name
 * in their profile, which the venue sees.
 *
 * Fails without writing anything when the profile can't be read or doesn't exist.
 *
 * @param users Where the explorer's profile is read.
 * @return The new reservation's id, or the failure.
 */
suspend fun ReservationRepository.bookQuest(
    users: UserRepository,
    explorerUid: String,
    questId: String,
    venueId: String,
    now: Long,
): Result<String> {
  val name =
      try {
        users.getUser(explorerUid)?.displayName
            ?: return Result.failure(NoSuchElementException("No profile for $explorerUid"))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        return Result.failure(e)
      }
  return createReservation(newPendingReservation(questId, venueId, name, now), explorerUid)
}
