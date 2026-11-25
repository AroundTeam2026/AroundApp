// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.notification.FakeNotificationRegistration
import com.github.aroundteam2026.aroundapp.model.notification.FakeNotificationTokenRepository
import com.github.aroundteam2026.aroundapp.model.notification.NotificationTokenRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofUploadQueue
import com.github.aroundteam2026.aroundapp.model.reservation.ProofUploadQueue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
  private val repository = FakeAuthRepository(EXPLORER_UID)
  private val queue = FakeProofUploadQueue()
  private val tokens = FakeNotificationTokenRepository()
  private val registration = FakeNotificationRegistration()
  private val store = ViewModelStore()

  @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  private fun viewModel(
      queue: ProofUploadQueue? = this.queue,
      tokens: NotificationTokenRepository = this.tokens,
  ): ProfileViewModel =
      ViewModelProvider(store, ProfileViewModel.factory(repository, queue, tokens, registration))[
          ProfileViewModel::class.java]

  /** Saves this phone's ID for the explorer, as the registrar does at sign-in. */
  private suspend fun savePhoneToken() {
    tokens.save(EXPLORER_UID, registration.phoneId).getOrThrow()
  }

  @Test
  fun signOutClearsTheSharedSession() {
    viewModel().signOut()

    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_cancelsEveryQueuedProofWhileStillSignedIn() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    var signedInWhileCancelling: String? = null
    val watched =
        object : ProofUploadQueue by queue {
          override suspend fun cancelAll(): Result<Unit> {
            signedInWhileCancelling = repository.currentUserId.value
            return queue.cancelAll()
          }
        }

    viewModel(watched).signOut()

    assertEquals(EXPLORER_UID, signedInWhileCancelling)
    assertEquals(1, queue.cancelAllCount)
    assertTrue(queue.waiting.first().isEmpty())
    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_whenCancellingTheQueueFails_stillSignsOut() = runTest {
    queue.enqueue(RESERVATION_ID, VENUE_ID, EXPLORER_UID, PHOTO)
    queue.cancelFailure = IllegalStateException(UNAVAILABLE)

    viewModel().signOut()

    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_whenClearedWhileCancellingTheQueue_stillSignsOut() {
    val stuck =
        object : ProofUploadQueue by queue {
          override suspend fun cancelAll(): Result<Unit> = awaitCancellation()
        }
    viewModel(stuck).signOut()

    store.clear()

    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_withoutAQueue_asForAVenue_signsOut() {
    viewModel(queue = null).signOut()

    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_deletesThePhonesTokenWhileStillSignedIn() = runTest {
    savePhoneToken()
    var signedInWhileDeleting: String? = null
    val watched =
        object : NotificationTokenRepository by tokens {
          override suspend fun delete(uid: String, token: String): Result<Unit> {
            signedInWhileDeleting = repository.currentUserId.value
            return tokens.delete(uid, token)
          }
        }

    viewModel(tokens = watched).signOut()

    assertEquals(EXPLORER_UID, signedInWhileDeleting)
    assertTrue(tokens.tokensOf(EXPLORER_UID).isEmpty())
    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_keepsTheUsersTokensOnOtherPhones() = runTest {
    savePhoneToken()
    tokens.save(EXPLORER_UID, OTHER_PHONE_ID).getOrThrow()

    viewModel().signOut()

    assertEquals(setOf(OTHER_PHONE_ID), tokens.tokensOf(EXPLORER_UID))
  }

  @Test
  fun signOut_whenDeletingTheTokenFails_stillSignsOut() = runTest {
    savePhoneToken()
    tokens.failure = IllegalStateException(UNAVAILABLE)

    viewModel().signOut()

    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_whenThePhonesIdIsUnavailable_stillSignsOut() = runTest {
    savePhoneToken()
    registration.idFailure = IllegalStateException(UNAVAILABLE)

    viewModel().signOut()

    assertEquals(setOf(registration.phoneId), tokens.tokensOf(EXPLORER_UID))
    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_whenNobodyIsSignedIn_deletesNoToken() = runTest {
    savePhoneToken()
    repository.signOut()

    viewModel().signOut()

    assertEquals(setOf(registration.phoneId), tokens.tokensOf(EXPLORER_UID))
    assertNull(repository.currentUserId.value)
  }

  @Test
  fun signOut_whenDeletingTheTokenNeverAnswers_signsOutAfterTheTimeout() = runTest {
    val offline =
        object : NotificationTokenRepository by tokens {
          override suspend fun delete(uid: String, token: String): Result<Unit> =
              awaitCancellation()
        }

    viewModel(tokens = offline).signOut()
    advanceTimeBy(ProfileViewModel.TOKEN_DELETE_TIMEOUT_MS - 1)
    runCurrent()
    assertNotNull(repository.currentUserId.value)

    advanceTimeBy(1)
    runCurrent()
    assertNull(repository.currentUserId.value)
  }

  private companion object {
    const val EXPLORER_UID = "explorer"
    const val RESERVATION_ID = "reservation-1"
    const val VENUE_ID = "cafe"
    const val UNAVAILABLE = "WorkManager unavailable"
    const val OTHER_PHONE_ID = "other-phone"
    val PHOTO = File("/data/user/0/app/files/proofs/proof-1.jpg")
  }
}
