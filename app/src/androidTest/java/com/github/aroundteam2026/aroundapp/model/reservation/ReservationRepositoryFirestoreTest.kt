// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_CODE_ATTEMPTS
import com.github.aroundteam2026.aroundapp.model.party.PartyCodeNotFoundException
import com.github.aroundteam2026.aroundapp.model.party.PartyRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.party.isPartyCode
import com.github.aroundteam2026.aroundapp.model.party.newPartyCode
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorClient
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.withTimeoutBlocking
import com.github.aroundteam2026.aroundapp.testing.writeBypassingRules
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [ReservationRepositoryFirestore] on the Auth and Firestore emulators, started with
 * `firebase emulators:start --only auth,firestore --project demo-project`, so firestore.rules
 * apply.
 *
 * Two clients take part, each from [emulatorClient] on its own named [FirebaseApp] on the
 * `demo-project` id, never the default app, so the tests cannot reach the real project: the
 * explorer creates reservations and the venue answers them. Each client has its own cache, so what
 * the venue reads has been accepted by the server. Each test signs in fresh users, uses the venue's
 * uid as the venue id and has the venue create an active quest to reserve, as the rules require, so
 * tests never see each other's reservations. Each test's KDoc names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class ReservationRepositoryFirestoreTest {
  /**
   * Fails any test, including its setUp, that runs longer than this instead of blocking CI forever.
   * It is longer than the setUp and test body timeouts added together, so one of those reports
   * where the test was stuck before this rule cuts it off.
   */
  @get:Rule val timeout: Timeout = Timeout.millis(RULE_TIMEOUT_MS)

  /** The signed-in explorer's client, from [setUp]. */
  private lateinit var explorerDb: FirebaseFirestore

  /** The signed-in venue's client, from [setUp]. */
  private lateinit var venueDb: FirebaseFirestore

  /** Repository used by the signed-in explorer. */
  private lateinit var explorerRepository: ReservationRepositoryFirestore

  /** Repository used by the signed-in venue, which owns the reservations the tests create. */
  private lateinit var venueRepository: ReservationRepositoryFirestore

  /** Uid of the explorer signed in by [setUp], who creates every reservation and so joins it. */
  private lateinit var explorerUid: String

  /** Uid of the venue signed in by [setUp], used as the venue id of every reservation. */
  private lateinit var venueId: String

  /** Id of the venue's active quest, created by [setUp], which every reservation is for. */
  private lateinit var questId: String

  /**
   * Signs in a fresh explorer and a fresh venue, each on their own client of the emulator, and has
   * the venue create the quest to reserve.
   */
  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          explorerUid = signInNewUser(EXPLORER_APP, Role.EXPLORER)
          venueId = signInNewUser(VENUE_APP, Role.VENUE)
          explorerDb = emulatorClient(EXPLORER_APP)
          venueDb = emulatorClient(VENUE_APP)
          questId = createActiveQuest()
        }
        explorerRepository = ReservationRepositoryFirestore(explorerDb) { FIXED_NOW }
        venueRepository = ReservationRepositoryFirestore(venueDb) { FIXED_NOW }
      }

  /**
   * Bug: a field is dropped or mis-mapped on write or read (e.g. slotStart and createdAt swapped,
   * the status mapped to the wrong enum, the id not taken from the document id).
   */
  @Test
  fun createReservation_thenObserveForVenue_roundTripsEveryField() = withTimeoutBlocking {
    val id = createAndSync()

    val stored = fetchAsVenue(id)

    assertEquals(
        Reservation(
            id = id,
            questId = questId,
            venueId = venueId,
            slotStart = SLOT_START,
            status = ReservationStatus.PENDING,
            createdAt = FIXED_NOW,
            explorerName = EXPLORER_NAME,
            // Drawn at random; createReservation_writesAPartyCodeThatLeadsBackToIt checks it
            partyCode = stored.partyCode,
        ),
        stored,
    )
  }

  /**
   * Bug: a reservation is created without a party code, with one that is not a code, or without the
   * `party_codes` document that leads back to it, so nobody can join by its code.
   */
  @Test
  fun createReservation_writesAPartyCodeThatLeadsBackToIt() = withTimeoutBlocking {
    val id = createAndSync()

    val code = fetchAsVenue(id).partyCode
    assertTrue("Not a party code: $code", code != null && isPartyCode(code))
    assertEquals(id, storedCodeTarget(code!!))
  }

  /**
   * Bug: a party code that is already taken makes creating fail, or is written over, instead of a
   * new code being drawn.
   */
  @Test
  fun createReservation_withATakenPartyCode_drawsAnother() = withTimeoutBlocking {
    val taken = newPartyCode()
    val first = createAndSync(repository = repositoryDrawing(taken))
    val fresh = newPartyCode()

    val second = createAndSync(repository = repositoryDrawing(taken, fresh))

    assertEquals(fresh, fetchAsVenue(second).partyCode)
    assertEquals(first, storedCodeTarget(taken))
    assertEquals(second, storedCodeTarget(fresh))
  }

  /**
   * Bug: creating draws codes forever, reports success, or fails with a Firebase type a ViewModel
   * can't name, when every code it draws is taken.
   */
  @Test
  fun createReservation_whenEveryPartyCodeIsTaken_failsWithIllegalStateAfterTheLastAttempt() =
      withTimeoutBlocking {
        val taken = newPartyCode()
        createAndSync(repository = repositoryDrawing(taken))
        var draws = 0
        val repository =
            ReservationRepositoryFirestore(
                explorerDb,
                newCode = {
                  draws++
                  taken
                },
            ) {
              FIXED_NOW
            }

        val error = repository.createReservation(reservation(), explorerUid).exceptionOrNull()

        assertTrue(
            "Expected an IllegalStateException but got $error",
            error is IllegalStateException,
        )
        assertEquals(MAX_PARTY_CODE_ATTEMPTS, draws)
      }

  /**
   * Bug: every refusal is taken for a taken code, so a reservation the rules refuse for another
   * reason is written again with new codes.
   */
  @Test
  fun createReservation_refusedForAnotherReasonThanItsCode_drawsOnlyOneCode() =
      withTimeoutBlocking {
        var draws = 0
        val repository =
            ReservationRepositoryFirestore(
                explorerDb,
                newCode = {
                  draws++
                  newPartyCode()
                },
            ) {
              FIXED_NOW
            }

        assertPermissionDenied(
            repository.createReservation(reservation().copy(questId = "missing"), explorerUid)
        )
        assertEquals(1, draws)
      }

  /**
   * Bug: the reservation an explorer makes by accepting a quest ([newPendingReservation], what the
   * venue page creates) does not pass the deployed rules, so accepting fails on the server after
   * the call has returned: a solo party of the signed-in explorer, pending, for an active quest.
   */
  @Test
  fun createReservation_forAnAcceptedQuest_isAcceptedByTheRules() = withTimeoutBlocking {
    val accepted = newPendingReservation(questId, venueId, EXPLORER_NAME, SLOT_START)

    val id = createAndSync(accepted)

    val stored = fetchAsVenue(id)
    assertEquals(
        accepted.copy(id = id, createdAt = FIXED_NOW, partyCode = stored.partyCode),
        stored,
    )
  }

  /**
   * Bug: the document does not follow the schema (a time stored as a Long, the status stored as an
   * ordinal, the party still stored on the reservation) or the id is also stored as a field. A
   * round trip cannot see any of these.
   */
  @Test
  fun createReservation_storesSchemaTypes() = withTimeoutBlocking {
    val id = createAndSync()

    val document = venueDb.collection(RESERVATIONS).document(id).get(Source.SERVER).await()

    assertEquals(
        setOf(
            "questId",
            "venueId",
            "slotStart",
            "status",
            "createdAt",
            "explorerName",
            "joinedCount",
            "partyCode",
        ),
        document.data!!.keys,
    )
    assertTrue(document.get("partyCode") is String)
    assertTrue(document.get("slotStart") is Timestamp)
    assertTrue(document.get("createdAt") is Timestamp)
    assertEquals("PENDING", document.get("status"))
    assertEquals(EXPLORER_NAME, document.get("explorerName"))
    assertEquals(1L, document.get("joinedCount"))
  }

  /**
   * Bug: the creator's membership is not written, is written under another id than the rules look
   * up, or with another time than the reservation's, so the creator can't read their own
   * reservation and the party looks empty.
   */
  @Test
  fun createReservation_makesTheCreatorAJoinedMemberAtTheCreationTime() = withTimeoutBlocking {
    val id = createAndSync()

    val membership =
        venueDb.collection(PARTY_USER).document("${id}_$explorerUid").get(Source.SERVER).await()

    assertEquals(id, membership.getString("reservationId"))
    assertEquals(explorerUid, membership.getString("uid"))
    assertEquals("JOINED", membership.getString("status"))
    assertEquals(FIXED_NOW, membership.getTimestamp("joinedAt")!!.toDate().time)
    assertEquals(FIXED_NOW, membership.getTimestamp("updatedAt")!!.toDate().time)
  }

  /**
   * Bug: a reservation stored before `explorerName` and `partyCode` existed is treated as
   * malformed, so it vanishes from the venue's list instead of showing without a name or a code.
   */
  @Test
  fun observe_readsAReservationWithoutAnExplorerNameOrPartyCodeAsUnnamedWithoutCode() =
      withTimeoutBlocking {
        val id = explorerDb.collection(RESERVATIONS).document().id
        writeBypassingRules(
            "$RESERVATIONS/$id",
            mapOf(
                "questId" to questId,
                "venueId" to venueId,
                "slotStart" to Timestamp(1_800_000_000L, 0),
                "status" to "PENDING",
                "createdAt" to Timestamp(1_700_000_000L, 0),
            ),
        )

        val stored = fetchAsVenue(id)
        assertEquals("", stored.explorerName)
        assertNull(stored.partyCode)
      }

  /**
   * Bug: the caller's id, status or creation time are kept, so a second reservation overwrites the
   * first, a caller can create an already approved one, or the clock is not used.
   */
  @Test
  fun createReservation_ignoresTheCallersIdStatusAndCreatedAt() = withTimeoutBlocking {
    val input =
        reservation().copy(id = "caller", status = ReservationStatus.APPROVED, createdAt = 1L)

    val first = createAndSync(input)
    val second = createAndSync(input)

    assertNotEquals("caller", first)
    assertNotEquals(first, second)
    val stored = fetchAsVenue(first)
    assertEquals(ReservationStatus.PENDING, stored.status)
    assertEquals(FIXED_NOW, stored.createdAt)
    assertEquals(second, fetchAsVenue(second).id)
  }

  /**
   * Bug: a reservation the rules refuse is reported as created, so the explorer sees it accepted
   * until it silently vanishes, or it is stored anyway. The rules only let an explorer reserve as
   * themselves, joining the party.
   */
  @Test
  fun createReservation_forAnotherCreatorThanTheSignedInUser_returnsPermissionDenied() =
      withTimeoutBlocking {
        val result = explorerRepository.createReservation(reservation(), FRIEND_UID)

        assertPermissionDenied(result)
        assertTrue(venueRepository.observeForVenue(venueId).first().isEmpty())
      }

  /** Bug: a reservation for a quest that doesn't exist is reported as created. */
  @Test
  fun createReservation_forAMissingQuest_returnsPermissionDenied() = withTimeoutBlocking {
    val result =
        explorerRepository.createReservation(reservation().copy(questId = "missing"), explorerUid)

    assertPermissionDenied(result)
  }

  /**
   * Bug: a reservation for a quest the venue has just archived is reported as created. This is the
   * case of the venue deactivating the quest while an explorer is accepting it: the rules only let
   * an explorer reserve an active quest.
   */
  @Test
  fun createReservation_forAQuestTheVenueArchived_returnsPermissionDenied() = withTimeoutBlocking {
    venueDb.collection(QUESTS).document(questId).update("status", "ARCHIVED").await()

    val result = explorerRepository.createReservation(reservation(), explorerUid)

    assertPermissionDenied(result)
  }

  /**
   * Bug: createReservation returns before the server has the reservation, so a success says nothing
   * about whether the write was accepted.
   */
  @Test
  fun createReservation_returnsOnceTheServerHasTheReservation() = withTimeoutBlocking {
    val id = explorerRepository.createReservation(reservation(), explorerUid).getOrThrow()

    // No waitForPendingWrites: the server must already have it
    assertTrue(venueDb.collection(RESERVATIONS).document(id).get(Source.SERVER).await().exists())
  }

  /**
   * Bug: createReservation waits for the server without a limit, so it hangs while the device is
   * offline, or fails instead of leaving the write queued until the device is back online.
   */
  @Test
  fun createReservation_whileOffline_succeedsAfterTheTimeoutAndIsSentOnceBackOnline() =
      withTimeoutBlocking {
        val repository =
            ReservationRepositoryFirestore(explorerDb, confirmTimeoutMs = SHORT_TIMEOUT_MS) {
              FIXED_NOW
            }
        explorerDb.disableNetwork().await()
        val id: String
        try {
          id = repository.createReservation(reservation(), explorerUid).getOrThrow()
        } finally {
          explorerDb.enableNetwork().await()
        }
        explorerDb.waitForPendingWrites().await()

        assertEquals(id, fetchAsVenue(id).id)
      }

  /** Bug: an exception escapes createReservation instead of becoming a failure. */
  @Test
  fun createReservation_returnsFailureWhenTheClientIsTerminated() = withTimeoutBlocking {
    val terminated = emulatorClient(TERMINATED_APP)
    terminated.terminate().await()

    val result =
        ReservationRepositoryFirestore(terminated).createReservation(reservation(), explorerUid)

    assertTrue(result.isFailure)
  }

  /**
   * Bug: a code typed in lower case or with a space between its groups is not found, or only a
   * member of the party can find it, though the explorer looking it up hasn't joined yet. A friend,
   * another explorer outside the party, looks it up, as when joining by code.
   */
  @Test
  fun findReservationIdByCode_typedInLowerCaseWithSpaces_findsItForAnExplorerOutsideTheParty() =
      withTimeoutBlocking {
        val id = createAndSync()
        val code = fetchAsVenue(id).partyCode!!
        val typed = code.lowercase().chunked(TYPED_GROUP_SIZE).joinToString(" ")
        signInNewUser(FRIEND_APP, Role.EXPLORER)
        val friendRepository = ReservationRepositoryFirestore(emulatorClient(FRIEND_APP))

        assertEquals(id, friendRepository.findReservationIdByCode(typed).getOrThrow())
      }

  /** Bug: an unknown or malformed code fails like being offline, or finds some reservation. */
  @Test
  fun findReservationIdByCode_forAnUnknownOrMalformedCode_failsWithPartyCodeNotFound() =
      withTimeoutBlocking {
        val unknown = newPartyCode()
        // Each typed code and the code the failure reports: normalized when it is a code
        val reported = mapOf(unknown.lowercase() to unknown, NOT_A_PARTY_CODE to NOT_A_PARTY_CODE)
        for ((typed, code) in reported) {
          val error = explorerRepository.findReservationIdByCode(typed).exceptionOrNull()
          assertTrue("$typed: $error", error is PartyCodeNotFoundException)
          assertEquals(code, (error as PartyCodeNotFoundException).code)
        }
      }

  /** Bug: offline, a code is reported as unknown, or found in the cache rather than the server. */
  @Test
  fun findReservationIdByCode_whileOffline_failsWithAnotherErrorThanNotFound() =
      withTimeoutBlocking {
        val code = fetchAsVenue(createAndSync()).partyCode!!
        explorerDb.disableNetwork().await()
        val error =
            try {
              explorerRepository.findReservationIdByCode(code).exceptionOrNull()
            } finally {
              explorerDb.enableNetwork().await()
            }

        assertTrue("Expected a failure but got $error", error != null)
        assertFalse(error is PartyCodeNotFoundException)
      }

  /**
   * Bug: an explorer who hasn't joined the party can't read its reservation, or the count of joined
   * members is not the stored one, so joining by a party code can't show who they would join.
   */
  @Test
  fun getPartyPreview_byAnExplorerOutsideTheParty_returnsTheReservationAndItsJoinedCount() =
      withTimeoutBlocking {
        val id = createAndSync()
        val friendUid = signInNewUser(FRIEND_APP, Role.EXPLORER)
        val friendDb = emulatorClient(FRIEND_APP)
        val friendRepository = ReservationRepositoryFirestore(friendDb)

        val alone = friendRepository.getPartyPreview(id).getOrThrow()
        PartyRepositoryFirestore(friendDb).join(id, friendUid).getOrThrow()
        friendDb.waitForPendingWrites().await()
        val together = venueRepository.getPartyPreview(id).getOrThrow()

        assertEquals(fetchAsVenue(id), alone.reservation)
        assertEquals(1, alone.joinedCount)
        assertEquals(2, together.joinedCount)
      }

  /** Bug: an unknown id fails like being offline, or finds some reservation. */
  @Test
  fun getPartyPreview_forAnUnknownId_failsWithNoSuchElement() = withTimeoutBlocking {
    val error = explorerRepository.getPartyPreview("missing").exceptionOrNull()

    assertTrue("Expected NoSuchElementException but got $error", error is NoSuchElementException)
  }

  /**
   * Bug: a reservation without its joinedCount reads as an empty party, so the join screen shows "0
   * joined" for a party that always has its creator.
   */
  @Test
  fun getPartyPreview_forAReservationWithoutAJoinedCount_failsWithNoSuchElement() =
      withTimeoutBlocking {
        val id = explorerDb.collection(RESERVATIONS).document().id
        writeBypassingRules(
            "$RESERVATIONS/$id",
            mapOf(
                "questId" to questId,
                "venueId" to venueId,
                "slotStart" to Timestamp(1_800_000_000L, 0),
                "status" to "PENDING",
                "createdAt" to Timestamp(1_700_000_000L, 0),
                "explorerName" to EXPLORER_NAME,
            ),
        )

        val error = explorerRepository.getPartyPreview(id).exceptionOrNull()

        assertTrue(
            "Expected NoSuchElementException but got $error",
            error is NoSuchElementException,
        )
      }

  /**
   * Bug: cancelling the read is reported as a failed one, so the caller goes on with an error it
   * never asked for, instead of the cancellation reaching it.
   */
  @Test
  fun getPartyPreview_whenCancelled_rethrowsTheCancellationInsteadOfReturningIt() =
      withTimeoutBlocking {
        val id = createAndSync()
        var returned: Result<PartyPreview>? = null
        val read =
            launch(start = CoroutineStart.UNDISPATCHED) {
              returned = explorerRepository.getPartyPreview(id)
            }

        read.cancelAndJoin()

        assertNull("The cancelled read returned $returned", returned)
      }

  /** Bug: offline, the party is reported unknown, or read from the cache rather than the server. */
  @Test
  fun getPartyPreview_whileOffline_failsWithAnotherErrorThanNoSuchElement() = withTimeoutBlocking {
    val id = createAndSync()
    // A listener keeps the document in the memory cache, so a read allowed to use the cache would
    // succeed offline
    val cached = CompletableDeferred<Unit>()
    val registration =
        explorerDb.collection(RESERVATIONS).document(id).addSnapshotListener { snapshot, _ ->
          if (snapshot?.exists() == true) cached.complete(Unit)
        }
    val error =
        try {
          cached.await()
          explorerDb.disableNetwork().await()
          explorerRepository.getPartyPreview(id).exceptionOrNull()
        } finally {
          registration.remove()
          explorerDb.enableNetwork().await()
        }

    assertTrue("Expected a failure but got $error", error != null)
    assertFalse(error is NoSuchElementException)
  }

  /** Bug: observeForVenue does not filter on the venue, or also filters on the status. */
  @Test
  fun observeForVenue_returnsEveryReservationOfThatVenueWhateverTheirStatus() =
      withTimeoutBlocking {
        val pending = createAndSync()
        val approved = createAndSync()
        venueRepository.updateStatus(approved, ReservationStatus.APPROVED).getOrThrow()
        val ownIds = setOf(pending, approved)

        val reservations =
            venueRepository.observeForVenue(venueId).first { list ->
              list.map { it.id }.containsAll(ownIds)
            }

        assertEquals(ownIds, reservations.map { it.id }.toSet())
        assertEquals(
            ReservationStatus.APPROVED,
            reservations.first { it.id == approved }.status,
        )
      }

  /** Bug: the flow reads once (get()) instead of listening, so later reservations never arrive. */
  @Test
  fun observeForVenue_emitsAReservationCreatedAfterSubscribing() = withTimeoutBlocking {
    val emissions = Channel<List<Reservation>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          venueRepository.observeForVenue(venueId).collect(emissions::send)
        }
    try {
      // The first emission proves the listener is attached before the write.
      emissions.receive()

      val id = createAndSync()

      // No assertion needed: a one-shot flow never emits again, so this loop never ends and the
      // test fails on TIMEOUT_MS.
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id })
    } finally {
      collector.cancel()
    }
  }

  /**
   * Bug: observeForExplorer only finds the reservations the explorer created, also finds those
   * whose party they never joined, or keeps those they withdrew from.
   */
  @Test
  fun observeForExplorer_returnsTheReservationsTheExplorerJoinedAndDidNotLeave() =
      withTimeoutBlocking {
        val friendUid = signInNewUser(FRIEND_APP, Role.EXPLORER)
        val friendDb = emulatorClient(FRIEND_APP)
        try {
          val friendRepository = ReservationRepositoryFirestore(friendDb) { FIXED_NOW }
          val own = createAndSync()
          val joined = friendRepository.createReservation(reservation(), friendUid).getOrThrow()
          // The creator can't leave their own party, so the explorer leaves the friend's
          val left = friendRepository.createReservation(reservation(), friendUid).getOrThrow()
          friendRepository.createReservation(reservation(), friendUid).getOrThrow()
          val party = PartyRepositoryFirestore(explorerDb)
          party.join(joined, explorerUid).getOrThrow()
          party.join(left, explorerUid).getOrThrow()
          party.withdraw(left, explorerUid).getOrThrow()

          val reservations =
              explorerRepository.observeForExplorer(explorerUid).first { list ->
                list.map { it.id }.toSet() == setOf(own, joined)
              }

          assertEquals(setOf(own, joined), reservations.map { it.id }.toSet())
        } finally {
          friendDb.terminate().await()
        }
      }

  /**
   * Bug: observeForExplorer reads the memberships or a reservation once instead of listening, so a
   * withdrawal or the venue's answer never shows.
   */
  @Test
  fun observeForExplorer_followsTheVenuesAnswerAndAWithdrawal() = withTimeoutBlocking {
    val friendUid = signInNewUser(FRIEND_APP, Role.EXPLORER)
    val friendDb = emulatorClient(FRIEND_APP)
    val party = PartyRepositoryFirestore(explorerDb)
    // The creator can't leave their own party, so the explorer joins and leaves the friend's
    val id =
        ReservationRepositoryFirestore(friendDb) { FIXED_NOW }
            .createReservation(reservation(), friendUid)
            .getOrThrow()
    party.join(id, explorerUid).getOrThrow()
    val emissions = Channel<List<Reservation>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          explorerRepository.observeForExplorer(explorerUid).collect(emissions::send)
        }
    try {
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id })

      venueRepository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id && it.status == ReservationStatus.APPROVED })

      party.withdraw(id, explorerUid).getOrThrow()
      do {
        val latest = emissions.receive()
      } while (latest.any { it.id == id })
    } finally {
      collector.cancel()
    }
  }

  /**
   * Bug: the rules refuse to read a reservation the server doesn't have yet, so the listener
   * observeForExplorer attaches right after an offline creation fails and the reservation never
   * follows the venue's answer.
   */
  @Test
  fun observeForExplorer_ofAReservationCreatedOffline_followsItOnceOnline() = withTimeoutBlocking {
    val repository =
        ReservationRepositoryFirestore(explorerDb, confirmTimeoutMs = SHORT_TIMEOUT_MS) {
          FIXED_NOW
        }
    val emissions = Channel<List<Reservation>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          repository.observeForExplorer(explorerUid).collect(emissions::send)
        }
    try {
      explorerDb.disableNetwork().await()
      val id: String
      try {
        id = repository.createReservation(reservation(), explorerUid).getOrThrow()
        // The local copy, listed before the server has it
        do {
          val latest = emissions.receive()
        } while (latest.none { it.id == id })
      } finally {
        explorerDb.enableNetwork().await()
      }
      explorerDb.waitForPendingWrites().await()

      venueRepository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()

      // A listener the server refused is left out, so this loop fails the test on TIMEOUT_MS
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id && it.status == ReservationStatus.APPROVED })
    } finally {
      collector.cancel()
    }
  }

  /**
   * Bug: observeForVenue or observeForExplorer lets a malformed reservation or membership crash the
   * listener or end the flow instead of skipping it.
   */
  @Test
  fun observe_skipsMalformedDocuments() = withTimeoutBlocking {
    // No questId, slotStart or createdAt: the rules refuse it, so it goes past them
    val malformedId = explorerDb.collection(RESERVATIONS).document().id
    writeBypassingRules(
        "$RESERVATIONS/$malformedId",
        mapOf("venueId" to venueId, "status" to "PENDING"),
    )
    writeBypassingRules(
        "$PARTY_USER/${malformedId}_$explorerUid",
        mapOf(
            "reservationId" to malformedId,
            "uid" to explorerUid,
            "status" to "JOINED",
            "joinedAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now(),
        ),
    )
    // A membership without its times, of a reservation that doesn't exist
    writeBypassingRules(
        "$PARTY_USER/missing_$explorerUid",
        mapOf("reservationId" to "missing", "uid" to explorerUid, "status" to "JOINED"),
    )
    val validId = createAndSync()

    val forVenue =
        venueRepository.observeForVenue(venueId).first { list -> list.any { it.id == validId } }
    val forExplorer =
        explorerRepository.observeForExplorer(explorerUid).first { list ->
          list.any { it.id == validId }
        }

    assertEquals(listOf(validId), forVenue.map { it.id })
    assertEquals(listOf(validId), forExplorer.map { it.id })
  }

  /**
   * Bug: updateStatus writes more than the status (the rules refuse that), or writes a different
   * status than asked.
   */
  @Test
  fun updateStatus_byTheVenue_changesOnlyTheStatus() = withTimeoutBlocking {
    val id = createAndSync()
    val before = fetchAsVenue(id)

    venueRepository.updateStatus(id, ReservationStatus.APPROVED).getOrThrow()

    val after = fetchAsVenue(id) { it.status == ReservationStatus.APPROVED }
    assertEquals(before.copy(status = ReservationStatus.APPROVED), after)
  }

  /**
   * Bug: updateStatus does not check canTransition, so a final status can be reopened, or it
   * reports the refusal with the wrong exception.
   */
  @Test
  fun updateStatus_refusesAForbiddenTransitionAndLeavesTheStatusUnchanged() = withTimeoutBlocking {
    val id = createAndSync()
    venueRepository.updateStatus(id, ReservationStatus.CANCELLED).getOrThrow()

    val result = venueRepository.updateStatus(id, ReservationStatus.APPROVED)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals("CANCELLED", storedStatus(id))
  }

  /**
   * Bug: updateStatus throws, hangs or writes for an id with no document instead of returning a
   * failure. The rules let anyone signed in find that an id has no reservation, so the transaction
   * sees it missing.
   */
  @Test
  fun updateStatus_forAnUnknownId_failsWithNoSuchElement() = withTimeoutBlocking {
    val result = venueRepository.updateStatus("unknown", ReservationStatus.APPROVED)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  /**
   * Bug: updateStatus changes a document that cannot be mapped to a Reservation, or reports it with
   * another exception than NoSuchElementException.
   */
  @Test
  fun updateStatus_forAMalformedDocument_failsWithNoSuchElement() = withTimeoutBlocking {
    val id = explorerDb.collection(RESERVATIONS).document().id
    // No questId, slotStart or createdAt: the rules refuse it, so it goes past them. The venue may
    // read it because the venueId matches.
    writeBypassingRules(
        "$RESERVATIONS/$id",
        mapOf("venueId" to venueId, "status" to "PENDING"),
    )

    val result = venueRepository.updateStatus(id, ReservationStatus.APPROVED)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertEquals("PENDING", storedStatus(id))
  }

  /**
   * Bug: the KDoc and the rules disagree: a joined member of the party may cancel their reservation
   * (E21) but not answer it in the venue's place, and that refusal must come back as a failure that
   * leaves the reservation untouched.
   */
  @Test
  fun updateStatus_byTheExplorer_cancelsButCannotApprove() = withTimeoutBlocking {
    val id = createAndSync()

    assertPermissionDenied(explorerRepository.updateStatus(id, ReservationStatus.APPROVED))
    assertEquals("PENDING", storedStatus(id))

    explorerRepository.updateStatus(id, ReservationStatus.CANCELLED).getOrThrow()
    assertEquals("CANCELLED", storedStatus(id))
  }

  /**
   * Bug: the venue approves a party smaller than its quest asks for, or the refusal isn't returned
   * as a failure and leaves the reservation changed.
   */
  @Test
  fun updateStatus_approvingAPartyBelowItsQuestsSize_returnsPermissionDenied() =
      withTimeoutBlocking {
        val group = createActiveQuest(minPartySize = GROUP_SIZE)
        val id = createAndSync(reservation().copy(questId = group))

        assertPermissionDenied(venueRepository.updateStatus(id, ReservationStatus.APPROVED))
        assertEquals("PENDING", storedStatus(id))
      }

  /**
   * A reservation for the venue's quest. `id`, `status` and `createdAt` are set by the repository
   * and ignored.
   */
  private fun reservation() =
      Reservation(
          id = "",
          questId = questId,
          venueId = venueId,
          slotStart = SLOT_START,
          status = ReservationStatus.PENDING,
          createdAt = 1L,
          explorerName = EXPLORER_NAME,
      )

  /**
   * Has the venue create an active quest and waits until the server has it, since the rules read
   * the quest when a reservation for it is created.
   *
   * @param minPartySize how many explorers the quest needs.
   * @return the quest's id.
   */
  private suspend fun createActiveQuest(minPartySize: Int = QuestLimits.MIN_PARTY_SIZE): String {
    val quest =
        Quest(
            id = "",
            venueId = venueId,
            radiusMeters = 50,
            title = "Title",
            description = "Description",
            requirements = "Requirements",
            proofType = ProofType.PHOTO,
            minPartySize = minPartySize,
            reward = null,
            status = QuestStatus.ACTIVE,
            createdAt = 0L,
            updatedAt = 0L,
        )
    val id = QuestRepositoryFirestore(venueDb).createQuest(quest).getOrThrow()
    venueDb.waitForPendingWrites().await()
    return id
  }

  /**
   * Creates [reservation] as the explorer and waits until the server has answered the write, so
   * that the venue can read it. [ReservationRepositoryFirestore.createReservation] itself does not
   * wait for the server. This also returns once the server has rejected the write.
   */
  private suspend fun createAndSync(
      reservation: Reservation = reservation(),
      repository: ReservationRepositoryFirestore = explorerRepository,
  ): String {
    val id = repository.createReservation(reservation, explorerUid).getOrThrow()
    explorerDb.waitForPendingWrites().await()
    return id
  }

  /** The explorer's repository, drawing [codes] in turn as party codes. */
  private fun repositoryDrawing(vararg codes: String): ReservationRepositoryFirestore {
    val next = codes.iterator()
    return ReservationRepositoryFirestore(explorerDb, newCode = next::next) { FIXED_NOW }
  }

  /** The reservation id the server stores for the party code [code], read as the venue. */
  private suspend fun storedCodeTarget(code: String): String? =
      venueDb
          .collection(PARTY_CODES)
          .document(code)
          .get(Source.SERVER)
          .await()
          .getString("reservationId")

  /**
   * Reads the reservation with [id] through the venue's client, once [until] accepts it. The venue
   * client has no local copy of the explorer's writes, so the reservation comes from the server.
   * The listener may first deliver an older snapshot, hence [until] for checks after an update.
   */
  private suspend fun fetchAsVenue(
      id: String,
      until: (Reservation) -> Boolean = { true },
  ): Reservation =
      venueRepository
          .observeForVenue(venueId)
          .first { list -> list.any { it.id == id && until(it) } }
          .first { it.id == id }

  /** The status stored on the server for [id], read as the venue so the rules allow it. */
  private suspend fun storedStatus(id: String): String? =
      venueDb.collection(RESERVATIONS).document(id).get(Source.SERVER).await().getString("status")

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
    /**
     * The Firestore collection the repository stores reservations in; some tests use it directly.
     */
    const val RESERVATIONS = "reservations"

    /** The Firestore collection of party codes, which some tests read directly. */
    const val PARTY_CODES = "party_codes"

    /** How many characters an explorer types before a space when typing a party code. */
    const val TYPED_GROUP_SIZE = 3

    /** Not a party code: too short, and with characters that are easy to mistake. */
    const val NOT_A_PARTY_CODE = "0O1IL"

    /** The Firestore collection of quests, which a test changes directly as the venue. */
    const val QUESTS = "quests"

    /** The Firestore collection of memberships, which some tests read or write directly. */
    const val PARTY_USER = "party_user"

    /** The app of the explorer that [setUp] signs in. */
    const val EXPLORER_APP = "reservation-test-explorer"

    /** The app of the venue that [setUp] signs in. */
    const val VENUE_APP = "reservation-test-venue"

    /** The app of a friend, another explorer, whom some tests sign in. */
    const val FRIEND_APP = "reservation-test-friend"

    /** The app whose client a test terminates on purpose, to make the write fail. */
    const val TERMINATED_APP = "reservation-test-terminated"

    /** The display name signInNewUser gives every profile; the rules require it on reservations. */
    const val EXPLORER_NAME = "Test"

    /** An explorer who never signs in, whom the signed-in explorer can't reserve for. */
    const val FRIEND_UID = "friend-uid"

    /** Bounds a whole test: setUp and the test body, plus a margin. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + 5_000L

    /** A confirmation timeout far below [TIMEOUT_MS], so the offline test outlasts it quickly. */
    const val SHORT_TIMEOUT_MS = 500L

    /** The time the test clock returns, in epoch ms; the non-zero ms catch precision loss. */
    const val FIXED_NOW = 1_700_000_000_123L

    /** The size of a quest for more than one explorer, so its creator alone can't be approved. */
    const val GROUP_SIZE = 2

    /** When every reservation's slot starts, in epoch ms; the non-zero ms catch precision loss. */
    const val SLOT_START = 1_800_000_000_456L
  }
}
