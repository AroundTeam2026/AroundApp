// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Where the signed-in user's profile stands after [ProfileSetup.finish]. */
sealed interface ProfileStatus {
  /** The profile exists with [role]; the app can open that role's home. */
  data class Ready(val role: Role) : ProfileStatus

  /** No role is saved and none was chosen, so the user must finish registration first. */
  data object NeedsAccountSetup : ProfileStatus
}

/**
 * Saves the account type chosen at registration as the user's profile (`users/{uid}`).
 *
 * The role is recorded with [choose], and [finish] turns it into a profile once the user is signed
 * in. The record is cleared only once the profile has a role, so a save interrupted by a failure or
 * by process death is finished by the next [finish] with the chosen role. It never falls back to
 * [Role.EXPLORER]: roles are write-once, so a wrong one would stick for good.
 *
 * Both ways of becoming a venue are supported: choosing before sign-up (a dedicated venue entry),
 * and choosing once signed in to an account without a role (any user who signed up). A choice made
 * while signed in belongs to that account and is never applied to another one.
 *
 * @param now Clock used for a new profile's `createdAt`, in epoch milliseconds.
 */
class ProfileSetup(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val pendingRole: PendingRoleStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

  /**
   * Records [role] as the account type to save, then call [finish] once signed in.
   *
   * Call it right before sign-up, or while signed in to an account without a role; a choice made
   * while signed in is bound to that account. If it fails, don't go on: without the record, an
   * interrupted save couldn't tell which type was chosen.
   */
  suspend fun choose(role: Role): Result<Unit> = storeCall {
    pendingRole.save(PendingRole(role, auth.currentUserId.value))
  }

  /**
   * Forgets a choice made before sign-in. Call it after every sign-in that isn't part of a
   * registration, before [finish]: that choice may come from an abandoned sign-up, and would
   * otherwise become this account's role for good. A choice bound to an account is kept: it is
   * either this account's own, or one that [finish] never applies to another account.
   */
  suspend fun discardChoice(): Result<Unit> = storeCall {
    if (pendingRole.load()?.uid == null) pendingRole.clear()
  }

  /**
   * Makes sure the signed-in user's profile has a role, saving the recorded one if needed.
   *
   * Creates the profile if it is absent, then sets the recorded role once. A profile or role saved
   * meanwhile by another device counts as success, and the role it holds is the one reported. Safe
   * to call again after a failure, which keeps the record.
   *
   * @return [ProfileStatus.Ready] with the saved role, [ProfileStatus.NeedsAccountSetup] when no
   *   role is saved or recorded (nothing is created then), or a failure if nobody is signed in or a
   *   read or write failed.
   */
  suspend fun finish(): Result<ProfileStatus> {
    val uid =
        auth.currentUserId.value
            ?: return Result.failure(IllegalStateException("Nobody is signed in"))
    return try {
      Result.success(finish(uid))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private suspend fun finish(uid: String): ProfileStatus {
    // A choice bound to another account is left for that account.
    val pending = pendingRole.load()?.takeIf { it.uid == null || it.uid == uid }
    val user = users.getUser(uid)
    user?.role?.let {
      return ready(it, pending)
    }
    val role = pending?.role ?: return ProfileStatus.NeedsAccountSetup
    if (user == null) {
      users.createUser(User(uid, displayName = "", role = null, createdAt = now())).orAlreadyDone()
    }
    if (!users.setRole(uid, role).orAlreadyDone()) {
      // Another device set the role first; report the one it saved.
      return ready(
          checkNotNull(users.getUser(uid)?.role) { "Role vanished after being set" },
          pending,
      )
    }
    return ready(role, pending)
  }

  /** Clears [pending], the record this account used, if any, and reports [role]. */
  private suspend fun ready(role: Role, pending: PendingRole?): ProfileStatus {
    if (pending != null) pendingRole.clear()
    return ProfileStatus.Ready(role)
  }

  private suspend fun storeCall(call: suspend () -> Unit): Result<Unit> =
      try {
        Result.success(call())
      } catch (e: IOException) {
        Result.failure(e)
      }

  /**
   * Returns true on success, or false if the write was already done elsewhere, which the repository
   * reports as [IllegalStateException]. Throws any other failure.
   */
  private fun Result<Unit>.orAlreadyDone(): Boolean =
      exceptionOrNull().let { error ->
        when (error) {
          null -> true
          // A subclass of IllegalStateException, but it means "stop", not "already done".
          is CancellationException -> throw error
          is IllegalStateException -> false
          else -> throw error
        }
      }
}
