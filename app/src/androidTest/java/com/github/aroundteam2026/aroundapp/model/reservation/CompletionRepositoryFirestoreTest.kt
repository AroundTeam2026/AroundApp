// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Tests [CompletionRepositoryFirestore] on the Auth and Firestore emulators, started with `firebase
 * emulators:start --only auth,firestore --project demo-project`, so firestore.rules apply.
 *
 * Two clients take part, each from [emulatorClient] on its own named [FirebaseApp] on the
 * `demo-project` id, never the default app, so the tests cannot reach the real project: the
 * explorer submits completions and the venue answers them. Each client has its own cache, so what
 * the venue reads has been accepted by the server. Each test signs in fresh users and has them make
 * an approved reservation of a new quest, as the rules require, so tests never see each other's
 * completions. Each test's KDoc names the production bug it catches.
 */
@RunWith(AndroidJUnit4::class)
class CompletionRepositoryFirestoreTest {
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

  /** Repository used by the signed-in explorer, who submits every completion. */
  private lateinit var explorerRepository: CompletionRepositoryFirestore

  /** Repository used by the signed-in venue, which answers the completions. */
  private lateinit var venueRepository: CompletionRepositoryFirestore

  /** Uid of the explorer signed in by [setUp]. */
  private lateinit var explorerUid: String

  /** Uid of the venue signed in by [setUp], used as the venue id of every reservation. */
  private lateinit var venueId: String

  /** Signs in a fresh explorer and a fresh venue, each on their own client of the emulator. */
  @Before
  fun setUp() =
      runBlocking<Unit> {
        withTimeout(SETUP_TIMEOUT_MS) {
          explorerUid = signInNewUser(EXPLORER_APP, Role.EXPLORER)
          venueId = signInNewUser(VENUE_APP, Role.VENUE)
          explorerDb = emulatorClient(EXPLORER_APP)
          venueDb = emulatorClient(VENUE_APP)
        }
        explorerRepository = CompletionRepositoryFirestore(explorerDb) { FIXED_NOW }
        venueRepository = CompletionRepositoryFirestore(venueDb) { FIXED_NOW }
      }

  /**
   * Bug: a field is dropped or mis-mapped on write or read (e.g. venueId and explorerUid swapped,
   * submittedAt not taken from the clock, the id not taken from the document id).
   */
  @Test
  fun submit_thenObserveForVenue_roundTripsEveryField() = withTimeoutBlocking {
    val reservationId = approvedReservation()

    val id = submit(reservationId)

    assertEquals(
        Completion(
            id = completionId(reservationId, explorerUid),
            reservationId = reservationId,
            venueId = venueId,
            explorerUid = explorerUid,
            proof = TEXT_PROOF,
            status = CompletionStatus.PENDING,
            rejectReason = null,
            submittedAt = FIXED_NOW,
        ),
        fetchAsVenue(id),
    )
  }

  /** Bug: a photo is stored or read back as a text answer, or the rules refuse a photo proof. */
  @Test
  fun submit_ofAPhotoProof_roundTripsTheProof() = withTimeoutBlocking {
    val id = submit(approvedReservation(ProofType.PHOTO), PHOTO_PROOF)

    assertEquals(PHOTO_PROOF, fetchAsVenue(id).proof)
  }

  /** Bug: a time is stored as a number, or an absent proof or reason as null (the rules refuse). */
  @Test
  fun submit_storesSchemaTypes() = withTimeoutBlocking {
    val stored = stored(submit(approvedReservation()))

    assertEquals(Timestamp(Date(FIXED_NOW)), stored["submittedAt"])
    assertEquals("PENDING", stored["status"])
    assertFalse(stored.contains("photoUrl"))
    assertFalse(stored.contains("rejectReason"))
  }

  /** Bug: submit returns before the server has the completion, so a success proves nothing. */
  @Test
  fun submit_returnsOnceTheServerHasTheCompletion() = withTimeoutBlocking {
    val id = submit(approvedReservation())

    // No waitForPendingWrites: the server must already have it
    assertTrue(stored(id).exists())
  }

  /**
   * Bug: a retry replaces the first proof, or comes back as a plain refusal that the ViewModel
   * can't tell from any other. It must fail with [IllegalStateException] and keep the first.
   */
  @Test
  fun submit_twice_failsWithIllegalStateAndKeepsTheFirst() = withTimeoutBlocking {
    val reservationId = approvedReservation()
    val id = submit(reservationId)

    val result =
        explorerRepository.submit(reservationId, venueId, explorerUid, Proof.Text("Another answer"))

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(TEXT_PROOF.answer, stored(id).getString("textAnswer"))
  }

  /** Bug: another explorer's submission goes through, or isn't reported as the refusal it is. */
  @Test
  fun submit_forAnotherExplorerThanTheSignedInUser_returnsPermissionDenied() = withTimeoutBlocking {
    val reservationId = approvedReservation()

    assertPermissionDenied(
        explorerRepository.submit(reservationId, venueId, FRIEND_UID, TEXT_PROOF)
    )
  }

  /**
   * Bug: the read that follows a refusal lets its own refusal escape, or reports another explorer's
   * completion as this explorer's second submission, when neither may read it.
   */
  @Test
  fun submit_overACompletionTheCallerCannotRead_returnsPermissionDenied() = withTimeoutBlocking {
    val reservationId = approvedReservation()
    writeBypassingRules(
        "$COMPLETIONS/${completionId(reservationId, FRIEND_UID)}",
        validFields() + ("explorerUid" to FRIEND_UID),
    )

    assertPermissionDenied(
        explorerRepository.submit(reservationId, venueId, FRIEND_UID, TEXT_PROOF)
    )
  }

  /** Bug: a completion of a reservation the venue hasn't approved is reported as stored. */
  @Test
  fun submit_forAReservationNotApproved_returnsPermissionDenied() = withTimeoutBlocking {
    val reservationId = pendingReservation()

    assertPermissionDenied(
        explorerRepository.submit(reservationId, venueId, explorerUid, TEXT_PROOF)
    )
    assertFalse(stored(completionId(reservationId, explorerUid)).exists())
  }

  /**
   * Bug: submit waits for the server without a limit, so it hangs while the device is offline, or
   * fails instead of leaving the write queued until the device is back online.
   */
  @Test
  fun submit_whileOffline_succeedsAfterTheTimeoutAndIsSentOnceBackOnline() = withTimeoutBlocking {
    val reservationId = approvedReservation()
    val repository =
        CompletionRepositoryFirestore(explorerDb, confirmTimeoutMs = SHORT_TIMEOUT_MS) { FIXED_NOW }
    explorerDb.disableNetwork().await()
    val id: String
    try {
      id = repository.submit(reservationId, venueId, explorerUid, TEXT_PROOF).getOrThrow()
    } finally {
      explorerDb.enableNetwork().await()
    }
    explorerDb.waitForPendingWrites().await()

    assertEquals(id, fetchAsVenue(id).id)
  }

  /** Bug: an exception escapes submit instead of becoming a failure. */
  @Test
  fun submit_returnsFailureWhenTheClientIsTerminated() = withTimeoutBlocking {
    val terminated = emulatorClient(TERMINATED_APP)
    terminated.terminate().await()

    val result =
        CompletionRepositoryFirestore(terminated).submit("r", venueId, explorerUid, TEXT_PROOF)

    assertTrue(result.isFailure)
  }

  /** Bug: observeForExplorer does not filter on the explorer, or also filters on the status. */
  @Test
  fun observeForExplorer_returnsEveryCompletionOfThatExplorerWhateverItsStatus() =
      withTimeoutBlocking {
        val answered = submit(approvedReservation())
        venueRepository.approve(answered).getOrThrow()
        val pending = submit(approvedReservation())
        writeBypassingRules(
            "$COMPLETIONS/${newId()}",
            validFields() + ("explorerUid" to FRIEND_UID),
        )

        val ids =
            explorerRepository
                .observeForExplorer(explorerUid)
                .first { list ->
                  list.any { it.id == answered && it.status == CompletionStatus.APPROVED } &&
                      list.any { it.id == pending }
                }
                .map { it.id }

        assertEquals(setOf(answered, pending), ids.toSet())
      }

  /** Bug: observeForVenue does not filter on the venue, or also filters on the status. */
  @Test
  fun observeForVenue_returnsEveryCompletionOfThatVenueWhateverItsStatus() = withTimeoutBlocking {
    val answered = submit(approvedReservation())
    venueRepository.reject(answered, REASON).getOrThrow()
    val pending = submit(approvedReservation())

    val ids =
        venueRepository
            .observeForVenue(venueId)
            .first { list ->
              list.any { it.id == answered && it.status == CompletionStatus.REJECTED } &&
                  list.any { it.id == pending }
            }
            .map { it.id }

    assertEquals(setOf(answered, pending), ids.toSet())
  }

  /** Bug: observeForReservation misses another member's completion, or leaks another party's. */
  @Test
  fun observeForReservation_returnsThePartysCompletionsOnly() = withTimeoutBlocking {
    val reservationId = approvedReservation()
    val mine = submit(reservationId)
    val friends = completionId(reservationId, FRIEND_UID)
    writeBypassingRules(
        "$COMPLETIONS/$friends",
        validFields() + ("reservationId" to reservationId) + ("explorerUid" to FRIEND_UID),
    )
    submit(approvedReservation())

    val ids =
        explorerRepository
            .observeForReservation(reservationId)
            .first { list -> list.size == 2 }
            .map { it.id }

    assertEquals(setOf(mine, friends), ids.toSet())
  }

  /** Bug: the flow reads once (get()) instead of listening, so later completions never arrive. */
  @Test
  fun observeForVenue_emitsACompletionSubmittedAfterSubscribing() = withTimeoutBlocking {
    val reservationId = approvedReservation()
    val emissions = Channel<List<Completion>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          venueRepository.observeForVenue(venueId).collect(emissions::send)
        }
    try {
      // The first emission proves the listener is attached before the write.
      emissions.receive()

      val id = submit(reservationId)

      // No assertion needed: a one-shot flow never emits again, so this loop never ends and the
      // test fails on TIMEOUT_MS.
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id })
    } finally {
      collector.cancel()
    }
  }

  /** Bug: the explorer's flow misses the venue's answer, so the overview keeps showing pending. */
  @Test
  fun observeForExplorer_followsTheVenuesAnswer() = withTimeoutBlocking {
    val id = submit(approvedReservation())
    val emissions = Channel<List<Completion>>(Channel.UNLIMITED)
    val collector =
        launch(start = CoroutineStart.UNDISPATCHED) {
          explorerRepository.observeForExplorer(explorerUid).collect(emissions::send)
        }
    try {
      // The first emission proves the listener is attached before the write.
      emissions.receive()

      venueRepository.reject(id, REASON).getOrThrow()

      // No assertion needed: without the answer this loop never ends and the test fails on
      // TIMEOUT_MS.
      do {
        val latest = emissions.receive()
      } while (latest.none { it.id == id && it.rejectReason == REASON })
    } finally {
      collector.cancel()
    }
  }

  /** Bug: a malformed completion crashes the listener or ends the flow instead of being skipped. */
  @Test
  fun observe_skipsMalformedDocuments() = withTimeoutBlocking {
    val malformed =
        mapOf(
            "no proof" to validFields() - "textAnswer",
            "two proofs" to validFields() + ("photoUrl" to PHOTO_PROOF.location),
            "no submittedAt" to validFields() - "submittedAt",
            "an unknown status" to validFields() + ("status" to "LOST"),
            "a reason while pending" to validFields() + ("rejectReason" to REASON),
        )
    malformed.values.forEach { writeBypassingRules("$COMPLETIONS/${newId()}", it) }
    val validId = submit(approvedReservation())

    val forVenue =
        venueRepository.observeForVenue(venueId).first { list -> list.any { it.id == validId } }
    val forExplorer =
        explorerRepository.observeForExplorer(explorerUid).first { list ->
          list.any { it.id == validId }
        }

    assertEquals(listOf(validId), forVenue.map { it.id })
    assertEquals(listOf(validId), forExplorer.map { it.id })
  }

  /** Bug: approve writes more than the status (the rules refuse that), or the wrong status. */
  @Test
  fun approve_byTheVenue_changesOnlyTheStatus() = withTimeoutBlocking {
    val id = submit(approvedReservation())
    val before = fetchAsVenue(id)

    venueRepository.approve(id).getOrThrow()

    val after = fetchAsVenue(id) { it.status == CompletionStatus.APPROVED }
    assertEquals(before.copy(status = CompletionStatus.APPROVED), after)
  }

  /** Bug: reject loses the reason, or changes more than the status and the reason. */
  @Test
  fun reject_byTheVenue_changesOnlyTheStatusAndTheReason() = withTimeoutBlocking {
    val id = submit(approvedReservation())
    val before = fetchAsVenue(id)

    venueRepository.reject(id, REASON).getOrThrow()

    val after = fetchAsVenue(id) { it.status == CompletionStatus.REJECTED }
    assertEquals(before.copy(status = CompletionStatus.REJECTED, rejectReason = REASON), after)
  }

  /** Bug: the rejection's reason stays on the document, which the rules and [Completion] refuse. */
  @Test
  fun approve_aRejectedCompletion_removesTheReason() = withTimeoutBlocking {
    val id = submit(approvedReservation())
    val before = fetchAsVenue(id)
    venueRepository.reject(id, REASON).getOrThrow()

    venueRepository.approve(id).getOrThrow()

    val after = fetchAsVenue(id) { it.status == CompletionStatus.APPROVED }
    assertEquals(before.copy(status = CompletionStatus.APPROVED), after)
    assertFalse(stored(id).contains("rejectReason"))
  }

  /** Bug: a blank reason reaches the transaction, where [Completion] or the rules would refuse. */
  @Test
  fun reject_withABlankReason_failsAndChangesNothing() = withTimeoutBlocking {
    val id = submit(approvedReservation())

    val result = venueRepository.reject(id, " ")

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("PENDING", stored(id).getString("status"))
  }

  @Test
  fun rejectAccepts300CharactersButRefuses301WithoutChangingTheProof() = withTimeoutBlocking {
    val id = submit(approvedReservation())
    val before = stored(id).data

    assertTrue(
        venueRepository.reject(id, "x".repeat(301)).exceptionOrNull() is IllegalArgumentException
    )
    assertEquals(before, stored(id).data)

    val reason = "x".repeat(300)
    venueRepository.reject(id, reason).getOrThrow()
    assertEquals(reason, fetchAsVenue(id) { it.status == CompletionStatus.REJECTED }.rejectReason)
  }

  /**
   * Bug: an answer does not check canTransition, so a final status can change, or the refusal comes
   * back with the wrong exception. Both answers go through it on the stored status.
   */
  @Test
  fun answer_toAnAnsweredCompletion_failsWithIllegalStateAndKeepsTheAnswer() = withTimeoutBlocking {
    val id = submit(approvedReservation())
    venueRepository.approve(id).getOrThrow()

    val rejected = venueRepository.reject(id, REASON)
    val approved = venueRepository.approve(id)

    assertTrue(rejected.exceptionOrNull() is IllegalStateException)
    assertTrue(approved.exceptionOrNull() is IllegalStateException)
    assertEquals("APPROVED", stored(id).getString("status"))
    assertFalse(stored(id).contains("rejectReason"))
  }

  /**
   * Bug: an answer throws, hangs or writes for an id with no document instead of returning a
   * failure. The rules let anyone signed in find that an id has no completion, so the transaction
   * sees it missing.
   */
  @Test
  fun answer_forAnUnknownId_failsWithNoSuchElement() = withTimeoutBlocking {
    assertTrue(venueRepository.approve("unknown").exceptionOrNull() is NoSuchElementException)
    assertTrue(
        venueRepository.reject("unknown", REASON).exceptionOrNull() is NoSuchElementException
    )
  }

  /** Bug: an answer changes a document that cannot be mapped to a Completion. */
  @Test
  fun answer_forAMalformedDocument_failsWithNoSuchElement() = withTimeoutBlocking {
    val id = newId()
    writeBypassingRules("$COMPLETIONS/$id", validFields() - "textAnswer")

    val result = venueRepository.approve(id)

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertEquals("PENDING", stored(id).getString("status"))
  }

  /** Bug: the explorer's answer goes through, or isn't reported as the refusal it is. */
  @Test
  fun answer_byTheExplorer_returnsPermissionDeniedAndChangesNothing() = withTimeoutBlocking {
    val id = submit(approvedReservation())

    assertPermissionDenied(explorerRepository.approve(id))
    assertEquals("PENDING", stored(id).getString("status"))
  }

  /** A fresh document id, so documents written past the rules never meet those of another run. */
  private fun newId(): String = explorerDb.collection(COMPLETIONS).document().id

  /** The fields of a valid pending completion of the explorer, for [writeBypassingRules]. */
  private fun validFields(): Map<String, Any> =
      mapOf(
          "reservationId" to "r",
          "venueId" to venueId,
          "explorerUid" to explorerUid,
          "textAnswer" to TEXT_PROOF.answer,
          "status" to "PENDING",
          "submittedAt" to Timestamp.now(),
      )

  /**
   * Has the venue create an active quest asking for [proofType], and the explorer reserve it.
   *
   * @return the pending reservation's id.
   */
  private suspend fun pendingReservation(proofType: ProofType = ProofType.TEXT): String {
    val quest =
        Quest(
            id = "",
            venueId = venueId,
            radiusMeters = 50,
            title = "Title",
            description = "Description",
            requirements = "Requirements",
            proofType = proofType,
            reward = null,
            status = QuestStatus.ACTIVE,
            createdAt = 0L,
            updatedAt = 0L,
        )
    val questId = QuestRepositoryFirestore(venueDb).createQuest(quest).getOrThrow()
    venueDb.waitForPendingWrites().await()
    return ReservationRepositoryFirestore(explorerDb)
        .createReservation(
            Reservation(
                id = "",
                questId = questId,
                venueId = venueId,
                slotStart = SLOT_START,
                status = ReservationStatus.PENDING,
                createdAt = 0L,
                explorerName = EXPLORER_NAME,
            ),
            explorerUid,
        )
        .getOrThrow()
  }

  /** Same as [pendingReservation], with the venue approving it. */
  private suspend fun approvedReservation(proofType: ProofType = ProofType.TEXT): String {
    val id = pendingReservation(proofType)
    ReservationRepositoryFirestore(venueDb)
        .updateStatus(id, ReservationStatus.APPROVED)
        .getOrThrow()
    return id
  }

  /** Submits [proof] as the explorer and returns the completion's id. */
  private suspend fun submit(reservationId: String, proof: Proof = TEXT_PROOF): String =
      explorerRepository.submit(reservationId, venueId, explorerUid, proof).getOrThrow()

  /**
   * Reads the completion with [id] through the venue's client, once [until] accepts it. The
   * listener may first deliver an older snapshot, hence [until] for checks after an answer.
   */
  private suspend fun fetchAsVenue(
      id: String,
      until: (Completion) -> Boolean = { true },
  ): Completion =
      venueRepository
          .observeForVenue(venueId)
          .first { list -> list.any { it.id == id && until(it) } }
          .first { it.id == id }

  /** The document stored on the server for [id], read as the venue so the rules allow it. */
  private suspend fun stored(id: String): DocumentSnapshot =
      venueDb.collection(COMPLETIONS).document(id).get(Source.SERVER).await()

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
    /** Name of the Firestore collection under test. */
    const val COMPLETIONS = "completions"

    /** Name of the app the explorer signs in on. */
    const val EXPLORER_APP = "completion-test-explorer"

    /** Name of the app the venue signs in on. */
    const val VENUE_APP = "completion-test-venue"

    /** Name of the app whose client is terminated on purpose. */
    const val TERMINATED_APP = "completion-test-terminated"

    /** The display name [signInNewUser] saves, which a reservation must carry. */
    const val EXPLORER_NAME = "Test"

    /** Uid of an explorer who isn't signed in. */
    const val FRIEND_UID = "friend-uid"

    /** Why the tests' venue rejects a proof. */
    const val REASON = "Blurry photo"

    /** Longer than the setUp and test body timeouts added together. */
    const val RULE_TIMEOUT_MS = SETUP_TIMEOUT_MS + TIMEOUT_MS + 5_000L

    /** Short wait for a server that can't answer, as the device is offline. */
    const val SHORT_TIMEOUT_MS = 500L

    /** Fixed submission time, with milliseconds so a truncation would show. */
    const val FIXED_NOW = 1_700_000_000_123L

    /** A slot far in the future, so the rules accept it whenever the tests run. */
    const val SLOT_START = 1_800_000_000_456L

    /** The proof of a `TEXT` quest. */
    val TEXT_PROOF = Proof.Text("The answer")

    /** The proof of a `PHOTO` quest. */
    val PHOTO_PROOF = Proof.Photo("https://example.test/proof.jpg")
  }
}
