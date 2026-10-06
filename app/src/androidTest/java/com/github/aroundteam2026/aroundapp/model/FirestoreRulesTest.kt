// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks firestore.rules against the Firestore emulator, signed in as real emulator users. Every
 * test uses fresh users and documents, so tests never see each other's data.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {

  private val auth = Firebase.auth
  private val db = Firebase.firestore

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    auth.signOut()
  }

  // ---- users ----

  @Test
  fun userCreatesAndReadsOnlyTheirOwnProfile() {
    val alice = newUser()
    val bob = newUser()

    signInAs(bob)
    assertSucceeds(user(bob).set(profile(role = null)))
    assertDenied(user(alice).set(profile(role = null)))
    assertDenied(user(alice).get(Source.SERVER))
    assertEquals(EMAIL_FIELD_VALUE, assertSucceeds(user(bob).get(Source.SERVER)).getString("email"))
  }

  @Test
  fun roleCanBeChosenOnceAndNeverChanged() {
    val alice = newUser()
    signInAs(alice)
    assertSucceeds(user(alice).set(profile(role = null)))

    assertSucceeds(user(alice).update("role", "EXPLORER"))
    assertDenied(user(alice).update("role", "VENUE"))
    assertDenied(user(alice).update("role", null))
  }

  @Test
  fun roleMustBeExplorerOrVenue() {
    val alice = newUser()
    signInAs(alice)
    assertDenied(user(alice).set(profile(role = "ADMIN")))
  }

  // ---- venues ----

  @Test
  fun venueIsReadableBySignedInUsersAndWritableByItsOwnerOnly() {
    val venue = newUser()
    val explorer = newUser()

    signInAs(venue)
    assertSucceeds(db.collection("venues").document(venue.uid).set(mapOf("name" to "Café")))

    signInAs(explorer)
    assertSucceeds(db.collection("venues").document(venue.uid).get(Source.SERVER))
    assertDenied(
        db.collection("venues")
            .document(venue.uid)
            .set(mapOf("name" to "Taken"), SetOptions.merge())
    )

    auth.signOut()
    assertDenied(db.collection("venues").document(venue.uid).get(Source.SERVER))
  }

  // ---- quests ----

  @Test
  fun venueCreatesQuestsOnlyUnderItsOwnId() {
    val venue = newUser()
    val other = newUser()
    signInAs(venue)

    assertSucceeds(newQuest().set(quest(venueId = venue.uid)))
    assertDenied(newQuest().set(quest(venueId = other.uid)))
  }

  @Test
  fun questsAreReadableBySignedInUsersOnly() {
    val venue = newUser()
    val explorer = newUser()
    signInAs(venue)
    val quest = newQuest()
    assertSucceeds(quest.set(quest(venueId = venue.uid)))

    signInAs(explorer)
    assertSucceeds(quest.get(Source.SERVER))
    auth.signOut()
    assertDenied(quest.get(Source.SERVER))
  }

  @Test
  fun onlyTheOwningVenueEditsAQuestAndCannotReassignIt() {
    val venue = newUser()
    val other = newUser()
    signInAs(venue)
    val quest = newQuest()
    assertSucceeds(quest.set(quest(venueId = venue.uid)))

    assertSucceeds(quest.update("title", "New title"))
    assertDenied(quest.update("venueId", other.uid))

    signInAs(other)
    assertDenied(quest.update("title", "Hijacked"))
    assertDenied(quest.delete())
  }

  // ---- reservations ----

  @Test
  fun explorerCreatesOnlyPendingReservationsThatListThem() {
    val venue = newUser()
    val explorer = newUser()
    val friend = newUser()
    signInAs(explorer)

    assertSucceeds(newReservation().set(reservation(venue, listOf(explorer, friend))))
    assertDenied(newReservation().set(reservation(venue, listOf(friend))))
    assertDenied(newReservation().set(reservation(venue, listOf(explorer), status = "APPROVED")))
  }

  @Test
  fun reservationIsReadableByItsVenueAndPartyOnly() {
    val venue = newUser()
    val explorer = newUser()
    val outsider = newUser()
    signInAs(explorer)
    val reservation = newReservation()
    assertSucceeds(reservation.set(reservation(venue, listOf(explorer))))

    signInAs(venue)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(outsider)
    assertDenied(reservation.get(Source.SERVER))
  }

  @Test
  fun onlyTheVenueMovesAReservationAlongAllowedTransitions() {
    val venue = newUser()
    val explorer = newUser()
    signInAs(explorer)
    val reservation = newReservation()
    assertSucceeds(reservation.set(reservation(venue, listOf(explorer))))
    assertDenied(reservation.update("status", "APPROVED"))

    signInAs(venue)
    assertDenied(reservation.update(mapOf("status" to "APPROVED", "slotStart" to "changed")))
    assertSucceeds(reservation.update("status", "APPROVED"))
    assertDenied(reservation.update("status", "PENDING"))
    assertSucceeds(reservation.update("status", "CANCELLED"))
    assertDenied(reservation.update("status", "APPROVED"))
  }

  // ---- completions ----

  @Test
  fun completionsAreDeniedUntilTheyGetRules() {
    val explorer = newUser()
    signInAs(explorer)
    assertDenied(
        db.collection("completions")
            .document(UUID.randomUUID().toString())
            .set(mapOf("explorerUid" to explorer.uid))
    )
  }

  // ---- helpers ----

  private data class TestUser(val email: String, val uid: String)

  /** Creates an emulator user; signing up leaves them signed in. */
  private fun newUser(): TestUser {
    val email = "test-${UUID.randomUUID()}@around.test"
    val uid = assertSucceeds(auth.createUserWithEmailAndPassword(email, PASSWORD)).user!!.uid
    return TestUser(email, uid)
  }

  private fun signInAs(user: TestUser) {
    assertSucceeds(auth.signInWithEmailAndPassword(user.email, PASSWORD))
  }

  private fun user(user: TestUser) = db.collection("users").document(user.uid)

  private fun newQuest() = db.collection("quests").document(UUID.randomUUID().toString())

  private fun newReservation() =
      db.collection("reservations").document(UUID.randomUUID().toString())

  private fun profile(role: String?) =
      mapOf("email" to EMAIL_FIELD_VALUE, "displayName" to "Test", "role" to role)

  private fun quest(venueId: String) =
      mapOf("venueId" to venueId, "title" to "Find the hidden owl", "status" to "ACTIVE")

  private fun reservation(venue: TestUser, party: List<TestUser>, status: String = "PENDING") =
      mapOf(
          "questId" to "quest",
          "venueId" to venue.uid,
          "explorerUids" to party.map { it.uid },
          "status" to status,
      )

  private fun <T> assertSucceeds(task: Task<T>): T = Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS)

  private fun assertDenied(task: Task<*>) {
    val error =
        assertThrows(ExecutionException::class.java) {
          Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS)
        }
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error.cause as FirebaseFirestoreException).code,
    )
  }

  private companion object {
    const val PASSWORD = "password123"
    const val EMAIL_FIELD_VALUE = "test@around.test"
    const val TIMEOUT_S = 10L
  }
}
