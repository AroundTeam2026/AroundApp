// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofUploadQueue
import com.github.aroundteam2026.aroundapp.model.reservation.ProofUploadQueue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
  private val repository = FakeAuthRepository(EXPLORER_UID)
  private val queue = FakeProofUploadQueue()
  private val store = ViewModelStore()

  @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  private fun viewModel(queue: ProofUploadQueue? = this.queue): ProfileViewModel =
      ViewModelProvider(store, ProfileViewModel.factory(repository, queue))[
          ProfileViewModel::class.java]

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
  fun signOut_withoutAQueue_asForAVenue_signsOut() {
    viewModel(queue = null).signOut()

    assertNull(repository.currentUserId.value)
  }

  private companion object {
    const val EXPLORER_UID = "explorer"
    const val RESERVATION_ID = "reservation-1"
    const val VENUE_ID = "cafe"
    const val UNAVAILABLE = "WorkManager unavailable"
    val PHOTO = File("/data/user/0/app/files/proofs/proof-1.jpg")
  }
}
