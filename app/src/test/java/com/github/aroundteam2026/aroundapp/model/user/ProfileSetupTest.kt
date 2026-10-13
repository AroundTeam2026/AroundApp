// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileSetupTest {

  private val auth = FakeAuthRepository(signedInUserId = UID)
  private val users = ScriptedUserRepository()
  private val store = FakePendingRoleStore()

  /** A fresh [ProfileSetup] over the same auth, users and disk: what the app builds on a start. */
  private fun setup() = ProfileSetup(auth, users, store, now = { NOW })

  /** Records [role] as chosen before sign-up, the way a dedicated venue entry does. */
  private fun recordBeforeSignUp(role: Role) {
    store.pending = PendingRole(role, uid = null)
  }

  @Test
  fun choiceWhileSignedIn_isBoundToTheAccount_thenSavedOnceAsANewProfile() = runTest {
    val setup = setup()

    setup.choose(Role.EXPLORER).getOrThrow()
    assertEquals(PendingRole(Role.EXPLORER, UID), store.pending)
    val status = setup.finish().getOrThrow()

    assertEquals(ProfileStatus.Ready(Role.EXPLORER), status)
    assertEquals(User(UID, displayName = "", role = Role.EXPLORER, createdAt = NOW), getUser())
    assertEquals(listOf(Role.EXPLORER), users.rolesSet)
    assertNull(store.pending)
  }

  @Test
  fun choose_whenTheRecordCantBeWritten_failsSoSignUpDoesNotStart() = runTest {
    store.failSave = true

    val error = setup().choose(Role.VENUE).exceptionOrNull()

    assertTrue(error is IOException)
    assertNull(store.pending)
  }

  @Test
  fun venueChosenBeforeSignUp_whenTheAppDiesBeforeTheSave_nextStartCreatesAVenue() = runTest {
    auth.signOut()
    setup().choose(Role.VENUE).getOrThrow()
    assertEquals(PendingRole(Role.VENUE, uid = null), store.pending)
    val uid = auth.signUpWithEmail("venue@around.test", PASSWORD).getOrThrow()
    // The process dies here: signed up, but finish() never ran.

    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.Ready(Role.VENUE), status)
    assertEquals(Role.VENUE, users.getUser(uid)?.role)
    assertNull(store.pending)
  }

  @Test
  fun venueRecorded_whenTheAppDiesAfterCreatingTheProfile_nextStartOnlySetsTheRole() = runTest {
    recordBeforeSignUp(Role.VENUE)
    users.delegate.createUser(User(UID, displayName = "", role = null, createdAt = 1L))

    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.Ready(Role.VENUE), status)
    assertEquals(User(UID, displayName = "", role = Role.VENUE, createdAt = 1L), getUser())
    assertEquals(emptyList<User>(), users.created)
  }

  @Test
  fun noProfileAndNothingRecorded_needsAccountSetup_andCreatesNothing() = runTest {
    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.NeedsAccountSetup, status)
    assertNull(getUser())
    assertEquals(emptyList<User>(), users.created)
  }

  @Test
  fun profileWithoutRoleAndNothingRecorded_needsAccountSetup_andSetsNoRole() = runTest {
    users.delegate.createUser(User(UID, displayName = "", role = null, createdAt = 1L))

    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.NeedsAccountSetup, status)
    assertNull(getUser()?.role)
    assertEquals(emptyList<Role>(), users.rolesSet)
  }

  @Test
  fun staleChoiceFromAnAbandonedSignUp_isDiscardedOnSignIn_soNoRoleIsSet() = runTest {
    // Tapped the venue entry and gave up, then signed in with Google to a new, role-less account.
    recordBeforeSignUp(Role.VENUE)
    val setup = setup()

    setup.discardChoice().getOrThrow()
    val status = setup.finish().getOrThrow()

    assertEquals(ProfileStatus.NeedsAccountSetup, status)
    assertEquals(emptyList<Role>(), users.rolesSet)
    assertNull(getUser())
    assertNull(store.pending)
  }

  @Test
  fun discardChoice_keepsAChoiceBoundToTheSignedInAccount() = runTest {
    store.pending = PendingRole(Role.VENUE, UID)
    val setup = setup()

    setup.discardChoice().getOrThrow()

    assertEquals(ProfileStatus.Ready(Role.VENUE), setup.finish().getOrThrow())
  }

  @Test
  fun discardChoice_whenTheClearFails_failsAndKeepsTheRecord() = runTest {
    recordBeforeSignUp(Role.VENUE)
    store.failNextClear = true

    val error = setup().discardChoice().exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals(Role.VENUE, store.pending?.role)
  }

  @Test
  fun choiceBoundToAnotherAccount_isNeverAppliedToThisOne() = runTest {
    store.pending = PendingRole(Role.VENUE, OTHER_UID)

    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.NeedsAccountSetup, status)
    assertNull(getUser())
    assertEquals(PendingRole(Role.VENUE, OTHER_UID), store.pending)
  }

  @Test
  fun choiceBoundToAnotherAccount_survivesThisOnesFinish_andStillAppliesToItsOwn() = runTest {
    val other = auth.addAccount("other@around.test", PASSWORD)
    store.pending = PendingRole(Role.VENUE, other)
    users.delegate.createUser(User(UID, displayName = "", role = Role.EXPLORER, createdAt = 1L))

    assertEquals(ProfileStatus.Ready(Role.EXPLORER), setup().finish().getOrThrow())
    assertEquals(PendingRole(Role.VENUE, other), store.pending)

    auth.signInWithEmail("other@around.test", PASSWORD).getOrThrow()
    assertEquals(ProfileStatus.Ready(Role.VENUE), setup().finish().getOrThrow())
    assertEquals(Role.VENUE, users.getUser(other)?.role)
  }

  @Test
  fun savedRole_winsOverADifferentRecord_withoutWrites_andTheRecordIsCleared() = runTest {
    // Another device finished the profile before this one read it.
    users.delegate.createUser(User(UID, displayName = "", role = Role.EXPLORER, createdAt = 1L))
    recordBeforeSignUp(Role.VENUE)

    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.Ready(Role.EXPLORER), status)
    assertEquals(emptyList<Role>(), users.rolesSet)
    assertNull(store.pending)
  }

  @Test
  fun anotherDeviceSavingFirst_duringTheSave_isSuccess_reportsItsRole_andClears() = runTest {
    recordBeforeSignUp(Role.VENUE)
    // Lands between this device's read and its writes, so both writes find the work done.
    users.beforeCreate = {
      users.delegate.createUser(User(UID, displayName = "", role = null, createdAt = 1L))
      users.delegate.setRole(UID, Role.EXPLORER)
    }

    val status = setup().finish().getOrThrow()

    assertEquals(ProfileStatus.Ready(Role.EXPLORER), status)
    assertEquals(listOf(Role.VENUE), users.rolesSet)
    assertEquals(Role.EXPLORER, getUser()?.role)
    assertNull(store.pending)
  }

  @Test
  fun failedCreate_failsAndKeepsTheRecord_thenRetrySucceeds() = runTest {
    val failure = IOException("offline")
    users.nextCreateFailure = failure
    recordBeforeSignUp(Role.VENUE)
    val setup = setup()

    assertSame(failure, setup.finish().exceptionOrNull())
    assertEquals(Role.VENUE, store.pending?.role)
    assertNull(getUser())
    assertEquals(emptyList<Role>(), users.rolesSet)

    assertEquals(ProfileStatus.Ready(Role.VENUE), setup.finish().getOrThrow())
    assertEquals(Role.VENUE, getUser()?.role)
    assertNull(store.pending)
  }

  @Test
  fun failedSetRole_failsAndKeepsTheRecord_thenRetrySetsItWithoutRecreating() = runTest {
    val failure = IOException("offline")
    users.nextSetRoleFailure = failure
    recordBeforeSignUp(Role.VENUE)
    val setup = setup()

    assertSame(failure, setup.finish().exceptionOrNull())
    assertEquals(Role.VENUE, store.pending?.role)
    assertNull(getUser()?.role)

    assertEquals(ProfileStatus.Ready(Role.VENUE), setup.finish().getOrThrow())
    assertEquals(Role.VENUE, getUser()?.role)
    assertEquals(1, users.created.size)
    assertNull(store.pending)
  }

  @Test
  fun failedClearAfterTheSave_fails_thenRetryClearsWithoutSettingTheRoleAgain() = runTest {
    recordBeforeSignUp(Role.VENUE)
    store.failNextClear = true
    val setup = setup()

    assertTrue(setup.finish().exceptionOrNull() is IOException)
    assertEquals(Role.VENUE, store.pending?.role)

    assertEquals(ProfileStatus.Ready(Role.VENUE), setup.finish().getOrThrow())
    assertEquals(listOf(Role.VENUE), users.rolesSet)
    assertNull(store.pending)
  }

  @Test
  fun failedRead_withNothingRecorded_failsRatherThanAskingForAccountSetup() = runTest {
    // A profile that couldn't be read may well have a role: account setup would be wrong for it.
    val failure = IOException("offline")
    users.nextGetFailure = failure

    assertSame(failure, setup().finish().exceptionOrNull())
  }

  @Test
  fun cancellationDuringTheSave_propagates_andKeepsTheRecord() = runTest {
    users.nextSetRoleFailure = CancellationException("left the screen")
    recordBeforeSignUp(Role.VENUE)

    try {
      setup().finish()
      fail("finish() turned the cancellation into a result")
    } catch (_: CancellationException) {}

    assertEquals(Role.VENUE, store.pending?.role)
  }

  @Test
  fun nobodySignedIn_failsAndKeepsTheRecord() = runTest {
    auth.signOut()
    recordBeforeSignUp(Role.VENUE)

    val error = setup().finish().exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertEquals(Role.VENUE, store.pending?.role)
    assertEquals(emptyList<User>(), users.created)
  }

  private suspend fun getUser() = users.getUser(UID)

  private companion object {
    const val UID = "uid-1"
    const val OTHER_UID = "uid-2"
    const val PASSWORD = "password1"
    const val NOW = 42_000L
  }
}
