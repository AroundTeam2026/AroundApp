// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorStorage
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the picture repository against the Auth and Storage emulators and `storage.rules`. */
@RunWith(AndroidJUnit4::class)
class UserPictureRepositoryStorageTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val repository = UserPictureRepositoryStorage(context, storage)
  private val files = mutableListOf<File>()

  @After
  fun deletePhotos() {
    files.forEach(File::delete)
  }

  /** A second picture gets a fresh path while both paths still return their original bytes. */
  @Test
  fun uploadAndDownloadTwoPicturesForTheOwner() = withTimeoutBlocking {
    val first = repository.upload(uid, photo(byteArrayOf(1, 2, 3))).getOrThrow()
    val second = repository.upload(uid, photo(byteArrayOf(4, 5, 6))).getOrThrow()

    assertNotEquals(first, second)
    assertTrue(first.startsWith("gs://${storage.reference.bucket}/user-pictures/$uid/"))
    assertArrayEquals(byteArrayOf(1, 2, 3), repository.download(first).getOrThrow())
    assertArrayEquals(byteArrayOf(4, 5, 6), repository.download(second).getOrThrow())
  }

  /** Invalid ids fail locally, before a Storage write can start. */
  @Test
  fun invalidUserIdDoesNotUpload() = withTimeoutBlocking {
    assertTrue(
        repository.upload("other/user", photo(byteArrayOf(1))).exceptionOrNull()
            is IllegalArgumentException
    )
  }

  private fun photo(bytes: ByteArray): Uri {
    val file = File(context.cacheDir, UUID.randomUUID().toString())
    files += file
    file.writeBytes(bytes)
    return Uri.fromFile(file)
  }

  companion object {
    private const val APP = "user-picture-test"
    private lateinit var uid: String
    private lateinit var storage: FirebaseStorage

    @BeforeClass
    @JvmStatic
    fun signIn() = runBlocking {
      withTimeout(SETUP_TIMEOUT_MS) { uid = signInNewUser(APP) }
      storage = emulatorStorage(APP)
    }
  }
}
