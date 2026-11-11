// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.net.Uri
import com.github.aroundteam2026.aroundapp.model.user.FakeUserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileSetupViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val users = FakeUserRepository()
  private val pictures = FakeUserPictureRepository()
  private val uid = "explorer"

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private suspend fun create(name: String = "") {
    users.createUser(User(uid, name, Role.EXPLORER, 12L)).getOrThrow()
  }

  @Test
  fun blankSavedNameRequiresSetupAndANameAlreadySavedOpensHome() =
      runTest(dispatcher) {
        create()
        val model = ProfileSetupViewModel(uid, users, pictures)
        advanceUntilIdle()
        assertEquals(ProfileSetupDestination.SETUP, model.destination.value)
        users.updateProfile(uid, "Ada", null).getOrThrow()
        advanceUntilIdle()
        assertEquals(ProfileSetupDestination.HOME, model.destination.value)
      }

  @Test
  fun missingProfileDoesNotOpenHome() =
      runTest(dispatcher) {
        val model = ProfileSetupViewModel(uid, users, pictures)
        advanceUntilIdle()
        assertEquals(ProfileSetupDestination.ERROR, model.destination.value)
      }

  @Test
  fun retryAfterAFailedReadReachesSetup() =
      runTest(dispatcher) {
        create()
        var reads = 0
        val flaky =
            object : UserRepository by users {
              override fun observeUser(uid: String): Flow<User?> =
                  if (reads++ == 0) flow { throw IllegalStateException("offline") }
                  else users.observeUser(uid)
            }
        val model = ProfileSetupViewModel(uid, flaky, pictures)
        advanceUntilIdle()
        assertEquals(ProfileSetupDestination.ERROR, model.destination.value)

        model.retry()
        advanceUntilIdle()
        assertEquals(ProfileSetupDestination.SETUP, model.destination.value)
        assertEquals(2, reads)
      }

  @Test
  fun rejectsBlankAndOverlongNamesWithoutWriting() =
      runTest(dispatcher) {
        create()
        val model = ProfileSetupViewModel(uid, users, pictures)
        advanceUntilIdle()
        model.setName("  ")
        model.save()
        assertEquals(ProfileSetupError.NAME_REQUIRED, model.form.value.error)
        model.setName("x".repeat(101))
        model.save()
        assertEquals(ProfileSetupError.NAME_TOO_LONG, model.form.value.error)
        assertEquals("", users.getUser(uid)?.displayName)
      }

  @Test
  fun savesTrimmedNameWithoutPhotoAndPreservesRoleAndCreationTime() =
      runTest(dispatcher) {
        create()
        val model = ProfileSetupViewModel(uid, users, pictures)
        advanceUntilIdle()
        model.setName("  Ada  ")
        model.save()
        advanceUntilIdle()
        assertEquals(User(uid, "Ada", Role.EXPLORER, 12L), users.getUser(uid))
        assertEquals(ProfileSetupDestination.HOME, model.destination.value)
        assertFalse(model.form.value.saving)
      }

  @Test
  fun failedUploadKeepsTheFormForRetry() =
      runTest(dispatcher) {
        create()
        pictures.failure = IllegalStateException("offline")
        val model = ProfileSetupViewModel(uid, users, pictures)
        advanceUntilIdle()
        val photo = Uri.parse("content://photos/avatar")
        model.setName("Ada")
        model.setPhoto(photo)
        model.save()
        advanceUntilIdle()
        assertEquals(ProfileSetupError.SAVE_FAILED, model.form.value.error)
        assertEquals(photo, model.form.value.photo)
        assertEquals("", users.getUser(uid)?.displayName)
        pictures.failure = null
        model.save()
        advanceUntilIdle()
        assertEquals("Ada", users.getUser(uid)?.displayName)
        assertTrue(users.getUser(uid)?.pictureUrl?.contains("user-pictures/$uid/") == true)
      }

  @Test
  fun retryAfterProfileWriteFailureReusesTheUploadedPicture() =
      runTest(dispatcher) {
        create()
        var uploads = 0
        var refuseWrite = true
        val pictureRepository =
            object : UserPictureRepository by pictures {
              override suspend fun upload(uid: String, photo: Uri): Result<String> {
                uploads++
                return pictures.upload(uid, photo)
              }
            }
        val profileRepository =
            object : UserRepository by users {
              override suspend fun updateProfile(
                  uid: String,
                  displayName: String,
                  pictureUrl: String?,
              ): Result<Unit> =
                  if (refuseWrite) Result.failure(IllegalStateException("offline"))
                  else users.updateProfile(uid, displayName, pictureUrl)
            }
        val model = ProfileSetupViewModel(uid, profileRepository, pictureRepository)
        advanceUntilIdle()
        model.setName("Ada")
        model.setPhoto(Uri.parse("content://photos/avatar"))
        model.save()
        advanceUntilIdle()
        assertEquals(ProfileSetupError.SAVE_FAILED, model.form.value.error)
        assertNull(users.getUser(uid)?.pictureUrl)
        refuseWrite = false
        model.save()
        advanceUntilIdle()
        assertEquals(1, uploads)
        assertEquals(ProfileSetupDestination.HOME, model.destination.value)
      }

  @Test
  fun repeatedSaveWhileUploadIsPendingStartsOnlyOneUpload() =
      runTest(dispatcher) {
        create()
        val release = CompletableDeferred<Unit>()
        var uploads = 0
        val delayedPictures =
            object : UserPictureRepository by pictures {
              override suspend fun upload(uid: String, photo: Uri): Result<String> {
                uploads++
                release.await()
                return pictures.upload(uid, photo)
              }
            }
        val model = ProfileSetupViewModel(uid, users, delayedPictures)
        advanceUntilIdle()
        model.setName("Ada")
        model.setPhoto(Uri.parse("content://photos/avatar"))
        model.save()
        model.save()
        runCurrent()
        assertEquals(1, uploads)
        assertTrue(model.form.value.saving)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(ProfileSetupDestination.HOME, model.destination.value)
      }
}
