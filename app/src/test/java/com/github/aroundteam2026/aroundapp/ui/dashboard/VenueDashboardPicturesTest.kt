// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The dashboard's profile pictures: downloaded once, shown on their members, initials otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VenueDashboardPicturesTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private val quests =
      FakeQuestRepository(initialQuests = listOf(testQuest(id = "owl", venueId = VENUE_ID)))
  private val party = FakePartyRepository { 0L }
  private val reservations = FakeReservationRepository({ 0L }, party, quests)
  private val users = FakeUserRepository()
  private val pictures = CountingPictures()
  private val decoded = ImageBitmap(2, 2)

  private val viewModel by lazy {
    VenueDashboardViewModel(
        FakeAuthRepository(VENUE_ID),
        reservations,
        quests,
        FakeVenueRepository(listOf(testVenue(id = VENUE_ID))),
        party,
        FakeCompletionRepository(),
        users,
        pictures,
        decode = { decoded },
    )
  }

  private val members
    get() = viewModel.uiState.value.needsAnswer.single().members

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  private fun TestScope.watch() {
    backgroundScope.launch { viewModel.uiState.collect {} }
  }

  /** Stores a reservation by "explorer", whose profile has [picture]. Returns its id. */
  private suspend fun reserveWithPicture(picture: String?): String {
    users.createUser(User("explorer", "Ada L.", Role.EXPLORER, 0L, picture)).getOrThrow()
    return reservations
        .createReservation(newPendingReservation("owl", VENUE_ID, "Ada", 0L), "explorer")
        .getOrThrow()
  }

  @Test
  fun aMembersPictureIsShownOnceDownloaded() = test {
    reserveWithPicture(PICTURE)
    watch()

    assertSame(decoded, members.single().picture)
    assertEquals(listOf(PICTURE), pictures.downloaded)
  }

  @Test
  fun aPictureIsDownloadedOnceWhateverChangesAfter() = test {
    val id = reserveWithPicture(PICTURE)
    watch()

    // A new member and an answer give new dashboards, with the same picture
    party.join(id, "friend").getOrThrow()
    viewModel.decline(id)
    reserveAgain()

    assertEquals(listOf(PICTURE), pictures.downloaded)
  }

  @Test
  fun aMemberWithoutAPictureShowsNone() = test {
    reserveWithPicture(null)
    watch()

    assertNull(members.single().picture)
    assertEquals(emptyList<String>(), pictures.downloaded)
  }

  @Test
  fun aPictureThatFailsToDownloadLeavesTheInitials() = test {
    pictures.failure = IOException("offline")
    reserveWithPicture(PICTURE)
    watch()

    assertNull(members.single().picture)
    assertEquals("Ada L.", members.single().name)
  }

  @Test
  fun aPictureThatFailedIsTriedAgainOnRetry() = test {
    pictures.failure = IOException("offline")
    reserveWithPicture(PICTURE)
    watch()

    pictures.failure = null
    viewModel.retry()

    assertSame(decoded, members.single().picture)
    assertEquals(listOf(PICTURE, PICTURE), pictures.downloaded)
  }

  @Test
  fun decodeAvatarShrinksABigPictureToTheAvatarsSize() = test {
    val big = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
    val png = ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val avatar = checkNotNull(decodeAvatar(png.toByteArray(), maxEdge = 128, dispatcher))

    assertEquals(128, avatar.width)
    assertEquals(64, avatar.height)
  }

  @Test
  fun decodeAvatarGivesNullForBytesThatArentAnImage() = test {
    assertNull(decodeAvatar(byteArrayOf(1, 2, 3), dispatcher = dispatcher))
  }

  /** Stores another reservation by the same explorer, so the same picture shows again. */
  private suspend fun reserveAgain() {
    reservations
        .createReservation(newPendingReservation("owl", VENUE_ID, "Ada", 1L), "explorer")
        .getOrThrow()
  }

  /** Gives [BYTES] for every picture, or [failure], recording each location downloaded. */
  private class CountingPictures : UserPictureRepository {
    val downloaded = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun upload(uid: String, photo: Uri): Result<String> = error("Not used")

    override suspend fun download(location: String): Result<ByteArray> {
      downloaded += location
      return failure?.let { Result.failure(it) } ?: Result.success(BYTES)
    }
  }

  private companion object {
    const val VENUE_ID = "cafe"
    const val PICTURE = "gs://bucket/user-pictures/explorer/1"
    val BYTES = byteArrayOf(1)
  }
}
