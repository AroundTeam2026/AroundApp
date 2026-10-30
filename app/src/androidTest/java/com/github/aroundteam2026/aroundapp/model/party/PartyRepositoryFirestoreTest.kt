// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.reservation.ReservationStatus
import com.github.aroundteam2026.aroundapp.model.reservation.newPendingReservation
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorClient
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.github.aroundteam2026.aroundapp.testing.writeBypassingRules
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [PartyRepositoryFirestore] on the Auth and Firestore emulators, so firestore.rules apply.
 *
 * Three users take part, each on their own client from [emulatorClient]: the explorer who made the
 * reservation, a friend who joins it, and the venue that owns it. Each test signs in fresh users
 * and has the explorer reserve a fresh active quest of the venue, so tests never see each other's
 * memberships. Each test's KDoc names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class PartyRepositoryFirestoreTest {
  /** Fails a stuck test, including its setUp and tearDown, instead of blocking CI forever. */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  private lateinit var explorerDb: FirebaseFirestore
  private lateinit var friendDb: FirebaseFirestore
  private lateinit var venueDb: FirebaseFirestore

  /** Each user's repository; the explorer's and the friend's join at [JOINED_AT]. */
  private lateinit var explorer: PartyRepositoryFirestore
  private lateinit var friend: PartyRepositoryFirestore
  private lateinit var venue: PartyRepositoryFirestore

  private lateinit var explorerUid: String
  private lateinit var friendUid: String
  private lateinit var venueId: String

  /** The explorer's pending reservation, created by [setUp], with no membership yet. */
  private lateinit var reservationId: String

  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          explorerUid = signInNewUser(EXPLORER_APP, Role.EXPLORER)
          friendUid = signInNewUser(FRIEND_APP, Role.EXPLORER)
          venueId = signInNewUser(VENUE_APP, Role.VENUE)
          explorerDb = emulatorClient(EXPLORER_APP)
          friendDb = emulatorClient(FRIEND_APP)
          venueDb = emulatorClient(VENUE_APP)
          reservationId = createPendingReservation()
        }
        explorer = PartyRepositoryFirestore(explorerDb) { JOINED_AT }
        friend = PartyRepositoryFirestore(friendDb) { JOINED_AT }
        venue = PartyRepositoryFirestore(venueDb)
      }

  /** Terminates every client, so no listener or cached data leaks between tests. */
  @After
  fun tearDown() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          explorerDb.terminate().await()
          friendDb.terminate().await()
          venueDb.terminate().await()
        }
      }

  /**
   * Bug: a field is dropped or mis-mapped, the document is not under `{reservationId}_{uid}`, which
   * the rules look memberships up by, or the times are not stored as timestamps.
   */
  @Test
  fun join_storesAJoinedMembershipUnderTheComposedId() = withTimeoutBlocking {
    explorer.join(reservationId, explorerUid).getOrThrow()

    val document =
        explorerDb
            .collection(PARTY_USER)
            .document("${reservationId}_$explorerUid")
            .get(Source.SERVER)
            .await()
    assertTrue(document.get("joinedAt") is Timestamp)
    assertTrue(document.get("updatedAt") is Timestamp)
    assertEquals(
        listOf(
            PartyMember(reservationId, explorerUid, PartyMemberStatus.JOINED, JOINED_AT, JOINED_AT)
        ),
        venue.observeMembers(reservationId).first { it.isNotEmpty() },
    )
  }

  /** Bug: joining twice is reported as a success, or resets the first join's time. */
  @Test
  fun join_twice_returnsPermissionDeniedAndKeepsTheFirstJoin() = withTimeoutBlocking {
    explorer.join(reservationId, explorerUid).getOrThrow()

    val later = PartyRepositoryFirestore(explorerDb) { WITHDRAWN_AT }
    assertPermissionDenied(later.join(reservationId, explorerUid))
    assertEquals(JOINED_AT, venue.observeMembers(reservationId).first().single().joinedAt)
  }

  /** Bug: an explorer joins a party the venue has already answered. */
  @Test
  fun join_aReservationThatIsNoLongerPending_returnsPermissionDenied() = withTimeoutBlocking {
    ReservationRepositoryFirestore(venueDb)
        .updateStatus(reservationId, ReservationStatus.APPROVED)
        .getOrThrow()

    assertPermissionDenied(friend.join(reservationId, friendUid))
  }

  /**
   * Bug: join waits for the server without a limit, so it hangs while the device is offline, or
   * fails instead of leaving the write queued until the device is back online.
   */
  @Test
  fun join_whileOffline_succeedsAfterTheTimeoutAndIsSentOnceBackOnline() = withTimeoutBlocking {
    val repository =
        PartyRepositoryFirestore(explorerDb, confirmTimeoutMs = SHORT_TIMEOUT_MS) { JOINED_AT }
    explorerDb.disableNetwork().await()
    try {
      repository.join(reservationId, explorerUid).getOrThrow()
    } finally {
      explorerDb.enableNetwork().await()
    }
    explorerDb.waitForPendingWrites().await()

    assertEquals(
        listOf(explorerUid),
        venue.observeMembers(reservationId).first { it.isNotEmpty() }.map { it.uid },
    )
  }

  /**
   * Bug: withdrawing deletes the membership, so the party's history is lost, does not record when
   * it happened, or can happen twice.
   */
  @Test
  fun withdraw_keepsTheMembershipRecordsWhenAndHappensOnce() = withTimeoutBlocking {
    explorer.join(reservationId, explorerUid).getOrThrow()

    PartyRepositoryFirestore(explorerDb) { WITHDRAWN_AT }
        .withdraw(reservationId, explorerUid)
        .getOrThrow()

    val member =
        venue.observeMembers(reservationId).first { members ->
          members.any { it.status == PartyMemberStatus.WITHDRAWN }
        }
    assertEquals(
        listOf(
            PartyMember(
                reservationId,
                explorerUid,
                PartyMemberStatus.WITHDRAWN,
                JOINED_AT,
                WITHDRAWN_AT,
            )
        ),
        member,
    )
    assertTrue(
        explorer.withdraw(reservationId, explorerUid).exceptionOrNull() is IllegalStateException
    )
  }

  /**
   * Bug: withdraw throws or hangs without a membership instead of returning a failure. The read
   * rule cannot evaluate a missing document, so the server answers PERMISSION_DENIED.
   */
  @Test
  fun withdraw_withoutAMembership_failsWithPermissionDenied() = withTimeoutBlocking {
    assertPermissionDenied(friend.withdraw(reservationId, friendUid))
  }

  /** Bug: withdraw changes a document that cannot be mapped to a PartyMember. */
  @Test
  fun withdraw_aMalformedMembership_failsWithNoSuchElement() = withTimeoutBlocking {
    // No status or times: the rules refuse it, so it goes past them
    writeBypassingRules(
        "$PARTY_USER/${reservationId}_$explorerUid",
        mapOf("reservationId" to reservationId, "uid" to explorerUid),
    )

    assertTrue(
        explorer.withdraw(reservationId, explorerUid).exceptionOrNull() is NoSuchElementException
    )
  }

  /**
   * Bug: observeMembers reads once instead of listening, so a friend joining later never shows, or
   * the rules refuse a joined member's query of their party.
   */
  @Test
  fun observeMembers_byAMember_emitsAFriendJoiningAfterSubscribing() = withTimeoutBlocking {
    explorer.join(reservationId, explorerUid).getOrThrow()
    val emissions = Channel<List<PartyMember>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          explorer.observeMembers(reservationId).collect(emissions::send)
        }
    try {
      // The first emission proves the listener is attached before the friend joins
      emissions.receive()

      friend.join(reservationId, friendUid).getOrThrow()

      // A one-shot flow never emits again, so this loop fails the test on TIMEOUT_MS
      do {
        val latest = emissions.receive()
      } while (latest.none { it.uid == friendUid })
    } finally {
      collector.cancel()
    }
  }

  /**
   * Bug: the read rule lets someone outside the party, or a member who withdrew, read it, instead
   * of the flow ending with PERMISSION_DENIED as the KDoc promises.
   */
  @Test
  fun observeMembers_byAnOutsiderOrAWithdrawnMember_failsWithPermissionDenied() =
      withTimeoutBlocking {
        explorer.join(reservationId, explorerUid).getOrThrow()
        friend.join(reservationId, friendUid).getOrThrow()
        // A later clock, since the rules refuse a withdrawal that leaves updatedAt unchanged
        PartyRepositoryFirestore(friendDb) { WITHDRAWN_AT }
            .withdraw(reservationId, friendUid)
            .getOrThrow()
        signInNewUser(OUTSIDER_APP, Role.EXPLORER)
        val outsiderDb = emulatorClient(OUTSIDER_APP)
        try {
          val outsider = PartyRepositoryFirestore(outsiderDb)

          assertPermissionDenied(runCatching { outsider.observeMembers(reservationId).first() })
          assertPermissionDenied(runCatching { friend.observeMembers(reservationId).first() })
        } finally {
          outsiderDb.terminate().await()
        }
      }

  /** Bug: a malformed document crashes the listener or ends the flow instead of being skipped. */
  @Test
  fun observeMembers_skipsMalformedDocuments() = withTimeoutBlocking {
    writeBypassingRules(
        "$PARTY_USER/${reservationId}_malformed",
        mapOf("reservationId" to reservationId, "uid" to "malformed", "status" to "GONE"),
    )
    explorer.join(reservationId, explorerUid).getOrThrow()

    val members = venue.observeMembers(reservationId).first { it.isNotEmpty() }

    assertEquals(listOf(explorerUid), members.map { it.uid })
  }

  /**
   * Has the venue create an active quest and the explorer reserve it, then waits until the server
   * has both, since the rules read them.
   *
   * @return the reservation's id.
   */
  private suspend fun createPendingReservation(): String {
    val quest =
        Quest(
            id = "",
            venueId = venueId,
            radiusMeters = 50,
            title = "Title",
            description = "Description",
            requirements = "Requirements",
            proofType = ProofType.PHOTO,
            reward = null,
            status = QuestStatus.ACTIVE,
            createdAt = 0L,
            updatedAt = 0L,
        )
    val questId = QuestRepositoryFirestore(venueDb).createQuest(quest).getOrThrow()
    venueDb.waitForPendingWrites().await()
    val reservation =
        newPendingReservation(questId, venueId, explorerUid, EXPLORER_NAME, SLOT_START)
    val id = ReservationRepositoryFirestore(explorerDb).createReservation(reservation).getOrThrow()
    explorerDb.waitForPendingWrites().await()
    return id
  }

  /** Asserts [result] is a failure caused by the security rules refusing the write. */
  private fun assertPermissionDenied(result: Result<*>) {
    val error = result.exceptionOrNull()
    assertTrue("Expected a Firestore exception but got $error", error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
  }

  private companion object {
    const val PARTY_USER = "party_user"

    const val EXPLORER_APP = "party-test-explorer"
    const val FRIEND_APP = "party-test-friend"
    const val VENUE_APP = "party-test-venue"
    const val OUTSIDER_APP = "party-test-outsider"

    /** The display name signInNewUser gives every profile; the rules require it on reservations. */
    const val EXPLORER_NAME = "Test"

    /** Bounds a whole test: setUp, the test body and tearDown, plus a margin. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + SETUP_TIMEOUT_MS + 5_000L

    /** A confirmation timeout far below [TIMEOUT_MS], so the offline test outlasts it quickly. */
    const val SHORT_TIMEOUT_MS = 500L

    /** Join and withdrawal times; the non-zero ms catch precision loss. */
    const val JOINED_AT = 1_700_000_000_123L
    const val WITHDRAWN_AT = 1_700_000_100_456L

    /** When the reservation's slot starts, in epoch ms, in the future so the rules accept it. */
    const val SLOT_START = 1_800_000_000_456L
  }
}
