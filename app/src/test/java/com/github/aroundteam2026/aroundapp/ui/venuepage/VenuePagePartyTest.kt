// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.github.aroundteam2026.aroundapp.model.party.FakePartyRepository
import com.github.aroundteam2026.aroundapp.model.party.PartyMemberStatus
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeCompletionRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.testQuest
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests who the venue page shows in the party of an accepted quest. */
@OptIn(ExperimentalCoroutinesApi::class)
class VenuePagePartyTest {
  private val dispatcher = StandardTestDispatcher()

  /**
   * The time both fakes read. The creator's membership is written at the reservation's `createdAt`,
   * which is how `createdParty` tells them apart; each later join is a millisecond after the last.
   */
  private var time = 0L
  private val party = FakePartyRepository { time }
  private val reservations = FakeReservationRepository(now = { time }, party = party)
  private val users =
      FakeUserRepository().apply {
        runBlocking {
          createUser(User(ME, "Ada Lovelace", Role.EXPLORER, createdAt = 0L))
          createUser(
              User(FRIEND, "Bea Smith", Role.EXPLORER, createdAt = 0L, pictureUrl = BEA_PICTURE)
          )
          createUser(User(OTHER, "Cleo Martin", Role.EXPLORER, createdAt = 0L))
        }
      }

  /** The profile of [slowUid], if set, arrives only once a test emits it here. */
  private var slowUid: String? = null
  private val slowProfile = MutableSharedFlow<User?>()
  private val userRepository =
      object : UserRepository by users {
        override fun observeUser(uid: String): Flow<User?> =
            if (uid == slowUid) slowProfile else users.observeUser(uid)
      }

  private val picture = mockk<ImageBitmap>()
  private val downloads = mutableListOf<String>()

  /**
   * Whether downloads fail, as offline; the bytes aren't an image; the page gets pictures at all.
   */
  private var downloadFails = false
  private var notAPicture = false
  private var withPictures = true

  private val pictures =
      object : UserPictureRepository {
        override suspend fun upload(uid: String, photo: Uri): Result<String> =
            Result.failure(UnsupportedOperationException("Not needed here"))

        override suspend fun download(location: String): Result<ByteArray> {
          downloads += location
          return if (downloadFails) Result.failure(IOException("offline"))
          else Result.success(byteArrayOf(1))
        }
      }

  private val viewModel by lazy {
    VenuePageViewModel(
        VENUE,
        FakeVenueRepository(listOf(testVenue(id = VENUE))),
        FakeQuestRepository(initialQuests = listOf(testQuest(id = QUEST, venueId = VENUE))),
        reservations,
        userRepository,
        party,
        FakeCompletionRepository(),
        ME,
        clock = { NOW },
        pictureRepository = pictures.takeIf { withPictures },
        decodePicture = { picture.takeUnless { notAPicture } },
    )
  }

  private val state
    get() = viewModel.uiState.value

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

  /** A reservation of [QUEST] made by [creator], whose party [joiners] then join in this order. */
  private fun reservationBy(creator: String, vararg joiners: String): String = runBlocking {
    time++
    val id =
        reservations
            .createReservation(newPendingReservation(QUEST, VENUE, "Someone", NOW), creator)
            .getOrThrow()
    joiners.forEach {
      time++
      party.join(id, it).getOrThrow()
    }
    id
  }

  /** Loads the page and opens the details of [QUEST]. */
  private fun TestScope.openDetails() {
    viewModel
    runCurrent()
    viewModel.onQuestDetailsOpened(QUEST)
    runCurrent()
  }

  private fun uids() = state.members.map { it.uid }

  @Test
  fun showsEveryJoinedMemberTheCreatorFirst() = test {
    reservationBy(FRIEND, ME)
    openDetails()

    assertEquals(listOf(FRIEND, ME), uids())
    assertEquals(listOf("Bea Smith", "Ada Lovelace"), state.members.map { it.name })
    assertEquals(listOf(true, false), state.members.map { it.isCreator })
    assertEquals(listOf(false, true), state.members.map { it.isMe })
  }

  @Test
  fun showsNoMembersBeforeTheQuestIsAccepted() = test {
    openDetails()

    assertTrue(state.members.isEmpty())
  }

  @Test
  fun showsAPictureOnceItArrivesAndDownloadsItOnce() = test {
    val id = reservationBy(FRIEND, ME)
    openDetails()

    assertSame(picture, state.members.first().picture)
    assertNull(state.members.last().picture)

    time++
    party.join(id, OTHER)
    runCurrent()

    assertSame(picture, state.members.first().picture)
    assertEquals(listOf(BEA_PICTURE), downloads)
  }

  @Test
  fun followsMembersWhoJoinAndLeave() = test {
    val id = reservationBy(FRIEND, ME)
    openDetails()

    time++
    party.join(id, OTHER)
    runCurrent()
    assertEquals(listOf(FRIEND, ME, OTHER), uids())
    assertEquals(3, state.acceptedParties[QUEST])

    party.withdraw(id, OTHER)
    runCurrent()
    assertEquals(listOf(FRIEND, ME), uids())
    assertEquals(2, state.acceptedParties[QUEST])
  }

  @Test
  fun followsAMemberWhoRenames() = test {
    reservationBy(FRIEND, ME)
    openDetails()

    users.updateProfile(FRIEND, "Bea", pictureUrl = null)
    runCurrent()

    assertEquals("Bea", state.members.first().name)
    assertNull(state.members.first().picture)
  }

  @Test
  fun aMemberWithoutAProfileHasNoName() = test {
    reservationBy(ME, "ghost")
    openDetails()

    assertEquals(listOf("Ada Lovelace", ""), state.members.map { it.name })
  }

  @Test
  fun aPictureThatFailsToDownloadLeavesTheInitialsAndIsTriedAgain() = test {
    downloadFails = true
    val id = reservationBy(FRIEND, ME)
    openDetails()

    assertNull(state.members.first().picture)

    downloadFails = false
    time++
    party.join(id, OTHER)
    runCurrent()

    assertSame(picture, state.members.first().picture)
    assertTrue(downloads.count { it == BEA_PICTURE } >= 2)
  }

  @Test
  fun bytesThatArentAPictureLeaveTheInitials() = test {
    notAPicture = true
    reservationBy(FRIEND, ME)
    openDetails()

    assertNull(state.members.first().picture)
    assertEquals("Bea Smith", state.members.first().name)
  }

  @Test
  fun withoutAPictureRepositoryNothingIsDownloaded() = test {
    withPictures = false
    reservationBy(FRIEND, ME)
    openDetails()

    assertTrue(state.members.all { it.picture == null })
    assertTrue(downloads.isEmpty())
  }

  @Test
  fun theReservationsDontWaitForTheMembersProfiles() = test {
    slowUid = FRIEND
    reservationBy(FRIEND, ME)
    openDetails()

    assertEquals(2, state.acceptedParties[QUEST])
    assertTrue(state.members.isEmpty())

    slowProfile.emit(users.getUser(FRIEND))
    runCurrent()

    assertEquals(listOf(FRIEND, ME), uids())
  }

  @Test
  fun aMemberWhoCancelsLeavesThePartyInstead() = test {
    val id = reservationBy(FRIEND, ME)
    openDetails()
    assertTrue(state.leavesParty)

    viewModel.cancel()
    runCurrent()

    val me = party.observeMembers(id).first().single { it.uid == ME }
    assertEquals(PartyMemberStatus.WITHDRAWN, me.status)
    val reservation = reservations.observeForExplorer(FRIEND).first().single()
    assertEquals(ReservationStatus.PENDING, reservation.status)
    assertNull(state.reservationIds[QUEST])
  }

  @Test
  fun theCreatorCancelsTheReservation() = test {
    val id = reservationBy(ME, FRIEND)
    openDetails()
    assertFalse(state.leavesParty)

    viewModel.cancel()
    runCurrent()

    val reservation = reservations.observeForExplorer(FRIEND).first().single { it.id == id }
    assertEquals(ReservationStatus.CANCELLED, reservation.status)
    assertTrue(party.observeMembers(id).first().all { it.status == PartyMemberStatus.JOINED })
  }

  private companion object {
    const val VENUE = "venue"
    const val QUEST = "quest"
    const val ME = "explorer"
    const val FRIEND = "friend"
    const val OTHER = "other"
    const val BEA_PICTURE = "gs://bucket/user-pictures/friend/1"
    const val NOW = 1_000_000L
  }
}
