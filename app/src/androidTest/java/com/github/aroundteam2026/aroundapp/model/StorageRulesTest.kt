// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.testing.SETUP_TIMEOUT_MS
import com.github.aroundteam2026.aroundapp.testing.emulatorStorage
import com.github.aroundteam2026.aroundapp.testing.signInNewUser
import com.github.aroundteam2026.aroundapp.testing.writeBypassingRules
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks storage.rules against the Storage emulator. Every test signs fresh users in, each on their
 * own `demo-project` app, and writes the reservation and memberships the rules read straight into
 * the Firestore emulator, past firestore.rules, so only storage.rules decide.
 *
 * Start the emulators with `firebase emulators:start --only auth,firestore,storage --project
 * demo-project`.
 */
@RunWith(AndroidJUnit4::class)
class StorageRulesTest {

  @Test
  fun aJoinedExplorerUploadsAProofThatTheirPartyAndTheVenueRead() {
    val venue = newAccount()
    val explorer = newAccount()
    val friend = newAccount()
    val reservation = reservationOf(venue)
    joinParty(reservation, explorer)
    joinParty(reservation, friend)
    val proof = proofPath(reservation)

    assertSucceeds(explorer.upload(proof))

    assertSucceeds(explorer.read(proof))
    assertSucceeds(friend.read(proof))
    assertSucceeds(venue.read(proof))
  }

  @Test
  fun nobodyOutsideThePartyUploadsOrReadsAProof() {
    val venue = newAccount()
    val explorer = newAccount()
    val reservation = reservationOf(venue)
    joinParty(reservation, explorer)
    val proof = proofPath(reservation)
    assertSucceeds(explorer.upload(proof))
    val outsider = newAccount()
    val otherVenue = newAccount()
    val signedOut = emulatorStorage("storage-${UUID.randomUUID()}")

    assertDenied(outsider.upload(proofPath(reservation)), "an outsider uploading")
    assertDenied(outsider.read(proof), "an outsider reading")
    assertDenied(otherVenue.read(proof), "another venue reading")
    assertDenied(venue.upload(proofPath(reservation)), "the venue uploading")
    assertDenied(signedOut.upload(proofPath(reservation)), "a signed-out upload")
    assertDenied(signedOut.read(proof), "a signed-out read")
  }

  @Test
  fun aWithdrawnMemberNeitherUploadsNorReadsAProof() {
    val venue = newAccount()
    val explorer = newAccount()
    val withdrawn = newAccount()
    val reservation = reservationOf(venue)
    joinParty(reservation, explorer)
    joinParty(reservation, withdrawn, status = "WITHDRAWN")
    val proof = proofPath(reservation)
    assertSucceeds(explorer.upload(proof))

    assertDenied(withdrawn.upload(proofPath(reservation)), "a withdrawn member uploading")
    assertDenied(withdrawn.read(proof), "a withdrawn member reading")
  }

  @Test
  fun onlyAnApprovedReservationTakesAProof() {
    val venue = newAccount()
    val explorer = newAccount()

    for (status in listOf("PENDING", "REJECTED", "CANCELLED")) {
      val reservation = reservationOf(venue, status)
      joinParty(reservation, explorer)
      assertDenied(explorer.upload(proofPath(reservation)), "a $status reservation")
    }
    val approved = reservationOf(venue)
    joinParty(approved, explorer)
    assertSucceeds(explorer.upload(proofPath(approved)))
  }

  @Test
  fun aProofIsAnImageUnderTheSizeLimit() {
    val venue = newAccount()
    val explorer = newAccount()
    val reservation = reservationOf(venue)
    joinParty(reservation, explorer)

    assertDenied(
        explorer.upload(proofPath(reservation), contentType = "application/pdf"),
        "a PDF",
    )
    assertDenied(
        explorer.upload(proofPath(reservation), bytes = ByteArray(MAX_PHOTO_BYTES)),
        "a photo at the size limit",
    )
    assertSucceeds(explorer.upload(proofPath(reservation), bytes = ByteArray(MAX_PHOTO_BYTES - 1)))
  }

  @Test
  fun nobodyOverwritesDeletesOrListsProofs() {
    val venue = newAccount()
    val explorer = newAccount()
    val reservation = reservationOf(venue)
    joinParty(reservation, explorer)
    val proof = proofPath(reservation)
    assertSucceeds(explorer.upload(proof))

    assertDenied(explorer.upload(proof), "overwriting a proof")
    assertDenied(explorer.storage.reference.child(proof).delete(), "the explorer deleting")
    assertDenied(venue.storage.reference.child(proof).delete(), "the venue deleting")
    assertDenied(
        venue.storage.reference.child("proofs/$reservation").listAll(),
        "the venue listing the proofs",
    )
  }

  @Test
  fun nothingIsStoredOutsideAReservationsProofs() {
    val venue = newAccount()
    val explorer = newAccount()
    val reservation = reservationOf(venue)
    joinParty(reservation, explorer)

    assertDenied(explorer.upload("photos/${UUID.randomUUID()}.jpg"), "another folder")
    assertDenied(explorer.upload("proofs/${UUID.randomUUID()}.jpg"), "a proof of no reservation")
    assertDenied(
        explorer.upload("proofs/$reservation/nested/${UUID.randomUUID()}.jpg"),
        "a proof in a subfolder",
    )
  }

  // ---- helpers ----

  /** A user signed in on their own app, and that app's Storage client. */
  private class Account(val uid: String, val storage: FirebaseStorage)

  /** Signs a fresh user in on a new app; a new app also gets a Storage client of its own. */
  private fun newAccount(): Account {
    val app = "storage-${UUID.randomUUID()}"
    val uid = runBlocking { withTimeout(SETUP_TIMEOUT_MS) { signInNewUser(app) } }
    return Account(uid, emulatorStorage(app))
  }

  /** Stores a reservation of [venue] with [status], past firestore.rules, and returns its id. */
  private fun reservationOf(venue: Account, status: String = "APPROVED"): String {
    val id = UUID.randomUUID().toString()
    writeBypassingRules("reservations/$id", mapOf("venueId" to venue.uid, "status" to status))
    return id
  }

  /** Makes [explorer] a member of the party of [reservationId], past firestore.rules. */
  private fun joinParty(reservationId: String, explorer: Account, status: String = "JOINED") =
      writeBypassingRules(
          "party_user/${reservationId}_${explorer.uid}",
          mapOf("reservationId" to reservationId, "uid" to explorer.uid, "status" to status),
      )

  /** Where a new proof photo of [reservationId] goes. */
  private fun proofPath(reservationId: String) = "proofs/$reservationId/${UUID.randomUUID()}.jpg"

  private fun Account.upload(
      path: String,
      bytes: ByteArray = PHOTO,
      contentType: String = "image/jpeg",
  ) = storage.upload(path, bytes, contentType)

  private fun FirebaseStorage.upload(
      path: String,
      bytes: ByteArray = PHOTO,
      contentType: String = "image/jpeg",
  ) =
      reference
          .child(path)
          .putBytes(bytes, StorageMetadata.Builder().setContentType(contentType).build())

  private fun Account.read(path: String) = storage.read(path)

  private fun FirebaseStorage.read(path: String) = reference.child(path).metadata

  private fun <T> assertSucceeds(task: Task<T>): T = Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS)

  /** Asserts the rules deny [task]; [why] names the case in the failure message. */
  private fun assertDenied(task: Task<*>, why: String) {
    val error =
        assertThrows(why, ExecutionException::class.java) {
          Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS)
        }
    assertEquals(
        why,
        StorageException.ERROR_NOT_AUTHORIZED,
        (error.cause as StorageException).errorCode,
    )
  }

  private companion object {
    /** The size limit of storage.rules: a proof must be smaller. */
    const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    /** The start of a JPEG file; the emulator never looks inside. */
    val PHOTO = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    const val TIMEOUT_S = 15L
  }
}
