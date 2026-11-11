// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.proof

import com.github.aroundteam2026.aroundapp.model.reservation.Completion
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.user.observeProfiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * Who sent a party's proof, when another member did rather than the explorer looking at it: the
 * screens then name them, as the Figma frame "Quest details · proof sent by a member" does.
 *
 * @property name Their display name, or null while their profile can't be read; the screens then
 *   say the proof was sent without naming anyone.
 */
data class ProofSender(val name: String?)

/**
 * The member who sent each of [completions], by reservation id, from their [profiles] by uid. A
 * proof [explorerUid] sent themselves is left out, since it needs no name.
 */
internal fun proofSendersOf(
    completions: List<Completion>,
    explorerUid: String,
    profiles: Map<String, User?>,
): Map<String, ProofSender> =
    completions
        .filter { it.explorerUid != explorerUid }
        .associate {
          it.reservationId to ProofSender(profiles[it.explorerUid]?.displayName?.ifBlank { null })
        }

/**
 * Observes who sent each of [completions] other than [explorerUid], by reservation id, as
 * [proofSendersOf] gives them. Emits again when one of them renames; at once an empty map when the
 * explorer sent them all. A profile that can't be read only leaves its sender unnamed.
 */
internal fun UserRepository.observeProofSenders(
    completions: List<Completion>,
    explorerUid: String,
): Flow<Map<String, ProofSender>> =
    observeProfiles(completions.map { it.explorerUid }.filter { it != explorerUid }).map {
      proofSendersOf(completions, explorerUid, it)
    }

/**
 * Who sent the proofs [explorerUid] sees, as [observeProofSenders] gives them, without holding a
 * screen back for the senders' profiles, which can take a while offline. Until a profile is read,
 * its sender keeps the name given before, or is a member not named yet: never the explorer.
 *
 * Not thread-safe: use it from one thread, as a ViewModel's listener does.
 */
internal class ProofSenders(private val users: UserRepository, private val explorerUid: String) {
  /** The senders named so far, by uid. */
  private val named = mutableMapOf<String, ProofSender>()

  /** Observes who sent each of [completions] other than the explorer, by reservation id. */
  fun observe(completions: List<Completion>): Flow<Map<String, ProofSender>> =
      users
          .observeProofSenders(completions, explorerUid)
          .onEach { senders ->
            completions.forEach { proof ->
              senders[proof.reservationId]?.let { named[proof.explorerUid] = it }
            }
          }
          .onStart {
            emit(
                completions
                    .filter { it.explorerUid != explorerUid }
                    .associate { it.reservationId to (named[it.explorerUid] ?: ProofSender(null)) }
            )
          }
}
