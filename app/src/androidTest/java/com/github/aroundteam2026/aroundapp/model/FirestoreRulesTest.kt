// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
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
    signOut()
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
    assertEquals(
        DISPLAY_NAME,
        assertSucceeds(user(bob).get(Source.SERVER)).getString("displayName"),
    )
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

  @Test
  fun profileMustFollowTheSchema() {
    val alice = newUser()
    val valid = profile(role = null)

    assertOnlyValidWriteSucceeds(
        user(alice),
        valid,
        mapOf(
            "an unknown field" to valid + ("email" to alice.email),
            "no createdAt" to valid - "createdAt",
            "a displayName that is not a string" to valid + ("displayName" to 42),
        ),
    )
  }

  // ---- venues ----

  @Test
  fun venueIsReadableBySignedInUsersAndWritableByItsOwnerOnly() {
    val venue = newUser(Role.VENUE)
    val otherVenue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)

    signInAs(venue)
    assertSucceeds(venueOf(venue).set(venueData()))

    signInAs(explorer)
    assertSucceeds(venueOf(venue).get(Source.SERVER))

    // A venue account passes the role check, so only the owner check can deny it
    signInAs(otherVenue)
    assertDenied(venueOf(venue).set(mapOf("name" to "Taken"), SetOptions.merge()))
    assertDenied(venueOf(venue).delete())

    signOut()
    assertDenied(venueOf(venue).get(Source.SERVER))
  }

  @Test
  fun onlyVenueAccountsWriteTheirVenue() {
    val explorer = newUser(Role.EXPLORER)
    assertDenied(venueOf(explorer).set(venueData()), "an explorer")

    val withoutProfile = newUser()
    assertDenied(venueOf(withoutProfile).set(venueData()), "an account without a profile")

    val withoutRole = newUser()
    assertSucceeds(user(withoutRole).set(profile(role = null)))
    assertDenied(venueOf(withoutRole).set(venueData()), "an account that hasn't chosen a role")
  }

  @Test
  fun venueMustFollowTheSchema() {
    val venue = newUser(Role.VENUE)
    val valid = venueData()

    assertOnlyValidWriteSucceeds(
        venueOf(venue),
        valid,
        mapOf(
            "a name over 100 characters" to valid + ("name" to "x".repeat(101)),
            "a radius under 20 m" to valid + ("radiusMeters" to 19),
            "a radius over 200 m" to valid + ("radiusMeters" to 201),
            "a location that is not a GeoPoint" to valid + ("location" to "Lausanne"),
            "no createdAt" to valid - "createdAt",
            "an unknown field" to valid + ("ownerUid" to venue.uid),
        ),
    )
    assertDenied(venueOf(venue).update("radiusMeters", 500), "an update that breaks the schema")
    // Optional fields are omitted when null
    assertSucceeds(venueOf(venue).set(valid - listOf("location", "address", "featuredQuestId")))
  }

  // ---- quests ----

  @Test
  fun venueCreatesQuestsOnlyUnderItsOwnId() {
    val venue = newUser(Role.VENUE)
    val other = newUser(Role.VENUE)
    signInAs(venue)

    assertSucceeds(newQuest().set(questData(venue)))
    assertDenied(newQuest().set(questData(other)))
  }

  @Test
  fun onlyVenueAccountsWriteQuests() {
    val explorer = newUser(Role.EXPLORER)
    assertDenied(newQuest().set(questData(explorer)), "an explorer")

    val withoutProfile = newUser()
    assertDenied(newQuest().set(questData(withoutProfile)), "an account without a profile")
  }

  @Test
  fun questsAreReadableBySignedInUsersOnly() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser()
    signInAs(venue)
    val quest = newQuest()
    assertSucceeds(quest.set(questData(venue)))

    signInAs(explorer)
    assertSucceeds(quest.get(Source.SERVER))
    signOut()
    assertDenied(quest.get(Source.SERVER))
  }

  @Test
  fun onlyTheOwningVenueEditsAQuestAndCannotReassignIt() {
    val venue = newUser(Role.VENUE)
    val other = newUser(Role.VENUE)
    signInAs(venue)
    val quest = newQuest()
    assertSucceeds(quest.set(questData(venue)))

    assertSucceeds(quest.update("title", "New title"))
    assertDenied(quest.update("title", "x".repeat(61)), "an update that breaks the schema")
    assertDenied(quest.update("venueId", other.uid))

    signInAs(other)
    assertDenied(quest.update("title", "Hijacked"))
    assertDenied(quest.delete())
  }

  @Test
  fun questMustFollowTheSchema() {
    val venue = newUser(Role.VENUE)
    val valid = questData(venue)

    assertOnlyValidWriteSucceeds(
        newQuest(),
        valid,
        mapOf(
            "a title over 60 characters" to valid + ("title" to "x".repeat(61)),
            "a description over 500 characters" to valid + ("description" to "x".repeat(501)),
            "requirements over 300 characters" to valid + ("requirements" to "x".repeat(301)),
            "a radius over 200 m" to valid + ("radiusMeters" to 201),
            "a radius that is not an integer" to valid + ("radiusMeters" to "50"),
            "a party of 0" to valid + ("minPartySize" to 0),
            "an exactPartySize that is not a boolean" to valid + ("exactPartySize" to "yes"),
            "an unknown proof type" to valid + ("proofType" to "VIDEO"),
            "an unknown status" to valid + ("status" to "LIVE"),
            "a copied venueName" to valid + ("venueName" to "Café"),
            "a copied location" to valid + ("location" to GeoPoint(46.52, 6.57)),
            "an unknown field" to valid + ("rating" to 5),
        ),
    )
    assertSucceeds(newQuest().set(valid + ("exactPartySize" to true)))
  }

  @Test
  fun rewardMustMatchItsType() {
    val venue = newUser(Role.VENUE)
    val expiresAt = Timestamp.now()
    val validRewards =
        listOf(
            mapOf(
                "type" to "DISCOUNT",
                "amount" to 12.5,
                "unit" to "PERCENT",
                "specifics" to "On drinks",
                "expiresAt" to expiresAt,
            ),
            mapOf("type" to "DISCOUNT", "amount" to 150, "unit" to "CHF"),
            mapOf("type" to "FREE_ITEM", "name" to "Coffee", "specifics" to "Any size"),
            mapOf("type" to "OTHER", "description" to "A sticker", "expiresAt" to expiresAt),
        )
    val invalidRewards =
        mapOf(
            "a discount over 100 %" to
                mapOf("type" to "DISCOUNT", "amount" to 101, "unit" to "PERCENT"),
            "a discount over 1000 CHF" to
                mapOf("type" to "DISCOUNT", "amount" to 1001, "unit" to "CHF"),
            "a discount under 0.01" to mapOf("type" to "DISCOUNT", "amount" to 0, "unit" to "CHF"),
            "an amount that is not a number" to
                mapOf("type" to "DISCOUNT", "amount" to "ten", "unit" to "CHF"),
            "an unknown unit" to mapOf("type" to "DISCOUNT", "amount" to 5, "unit" to "EUR"),
            "a discount without an amount" to mapOf("type" to "DISCOUNT", "unit" to "CHF"),
            "an item name over 60 characters" to
                mapOf("type" to "FREE_ITEM", "name" to "x".repeat(61)),
            "specifics over 200 characters" to
                mapOf("type" to "FREE_ITEM", "name" to "Coffee", "specifics" to "x".repeat(201)),
            "a field of another type" to
                mapOf("type" to "OTHER", "description" to "A sticker", "amount" to 5),
            "an unknown type" to mapOf("type" to "MYSTERY", "description" to "Surprise"),
            "no type" to mapOf("description" to "Surprise"),
            "an expiry that is not a timestamp" to
                mapOf("type" to "OTHER", "description" to "A sticker", "expiresAt" to "tomorrow"),
        )

    for ((why, reward) in invalidRewards) {
      assertDenied(newQuest().set(questData(venue) + ("reward" to reward)), why)
    }
    for (reward in validRewards) {
      assertSucceeds(newQuest().set(questData(venue) + ("reward" to reward)))
    }
  }

  // ---- reservations ----

  @Test
  fun explorerCreatesOnlyPendingReservationsThatListThem() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val friend = newUser()
    signInAs(explorer)

    assertSucceeds(newReservation().set(reservation(quest, venue, listOf(explorer, friend))))
    assertDenied(newReservation().set(reservation(quest, venue, listOf(friend))))
    assertDenied(
        newReservation().set(reservation(quest, venue, listOf(explorer), status = "APPROVED"))
    )
  }

  @Test
  fun onlyExplorerAccountsCreateReservations() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    assertDenied(newReservation().set(reservation(quest, venue, listOf(venue))), "a venue")

    val withoutProfile = newUser()
    assertDenied(
        newReservation().set(reservation(quest, venue, listOf(withoutProfile))),
        "an account without a profile",
    )

    val explorer = newUser(Role.EXPLORER)
    assertSucceeds(newReservation().set(reservation(quest, venue, listOf(explorer))))
  }

  @Test
  fun reservationMustBeForAnActiveQuestOfItsVenue() {
    val venue = newUser(Role.VENUE)
    val otherVenue = newUser(Role.VENUE)
    val active = questOf(venue)
    val draft = questOf(venue, status = "DRAFT")
    val explorer = newUser(Role.EXPLORER)

    assertDenied(newReservation().set(reservation(draft, venue, listOf(explorer))), "a draft")
    assertDenied(
        newReservation().set(reservation("missing", venue, listOf(explorer))),
        "a missing quest",
    )
    assertDenied(
        newReservation().set(reservation(active, otherVenue, listOf(explorer))),
        "another venue than the quest's",
    )
    assertSucceeds(newReservation().set(reservation(active, venue, listOf(explorer))))
  }

  @Test
  fun reservationMustFollowTheSchema() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val valid = reservation(quest, venue, listOf(explorer))

    assertOnlyValidWriteSucceeds(
        newReservation(),
        valid,
        mapOf(
            "no slotStart" to valid - "slotStart",
            "a slotStart that is not a timestamp" to valid + ("slotStart" to 1_800_000_000_000L),
            "a party that is not a list" to valid + ("explorerUids" to explorer.uid),
            "an unknown field" to valid + ("leaderUid" to explorer.uid),
            "no explorerName" to valid - "explorerName",
            "an explorerName that is not a string" to valid + ("explorerName" to 42),
        ),
    )
  }

  @Test
  fun reservationCarriesItsCreatorsOwnDisplayName() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val valid = reservation(quest, venue, listOf(explorer))

    assertOnlyValidWriteSucceeds(
        newReservation(),
        valid,
        mapOf(
            "another name than the profile's" to valid + ("explorerName" to "Someone else"),
            "an empty name for a named profile" to valid + ("explorerName" to ""),
        ),
    )
  }

  @Test
  fun reservationLimitsThePartyAndTheSlotStart() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val valid = reservation(quest, venue, listOf(explorer))
    fun partyOf(size: Int) = listOf(explorer.uid) + List(size - 1) { "friend-$it" }

    assertOnlyValidWriteSucceeds(
        newReservation(),
        valid,
        mapOf(
            "the same explorer twice" to
                valid + ("explorerUids" to listOf(explorer.uid, explorer.uid)),
            "a party of 11" to valid + ("explorerUids" to partyOf(11)),
            "a slot that started 10 minutes ago" to valid + ("slotStart" to minutesAgo(10)),
        ),
    )
    assertSucceeds(newReservation().set(valid + ("explorerUids" to partyOf(10))))
    assertSucceeds(
        newReservation().set(valid + ("slotStart" to minutesAgo(1))),
    )
  }

  @Test
  fun reservationIsReadableByItsVenueAndPartyOnly() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val outsider = newUser()
    signInAs(explorer)
    val reservation = newReservation()
    assertSucceeds(reservation.set(reservation(quest, venue, listOf(explorer))))

    signInAs(venue)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(outsider)
    assertDenied(reservation.get(Source.SERVER))
  }

  @Test
  fun venueMovesAReservationAlongAllowedTransitionsOnly() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    signInAs(explorer)
    val reservation = newReservation()
    assertSucceeds(reservation.set(reservation(quest, venue, listOf(explorer))))

    signInAs(venue)
    assertDenied(reservation.update(mapOf("status" to "APPROVED", "slotStart" to "changed")))
    assertSucceeds(reservation.update("status", "APPROVED"))
    assertDenied(reservation.update("status", "PENDING"))
    assertSucceeds(reservation.update("status", "CANCELLED"))
    assertDenied(reservation.update("status", "APPROVED"))
  }

  @Test
  fun explorerOfThePartyCancelsButNeverAnswers() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val outsider = newUser()
    signInAs(explorer)
    val pending = newReservation()
    assertSucceeds(pending.set(reservation(quest, venue, listOf(explorer))))
    val approved = newReservation()
    assertSucceeds(approved.set(reservation(quest, venue, listOf(explorer))))
    signInAs(venue)
    assertSucceeds(approved.update("status", "APPROVED"))

    signInAs(outsider)
    assertDenied(pending.update("status", "CANCELLED"), "an outsider cancelling")
    signInAs(explorer)
    assertDenied(pending.update("status", "APPROVED"), "the explorer approving")
    assertDenied(pending.update("status", "REJECTED"), "the explorer rejecting")
    assertDenied(
        pending.update(mapOf("status" to "CANCELLED", "slotStart" to Timestamp.now())),
        "the explorer moving the slot while cancelling",
    )
    assertSucceeds(pending.update("status", "CANCELLED"))
    assertSucceeds(approved.update("status", "CANCELLED"))
  }

  // ---- party_user ----

  @Test
  fun explorerJoinsAPendingReservationOnlyAsThemselvesUnderTheirMembershipId() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val pending = pendingReservationOf(explorer, venue)
    val approved = pendingReservationOf(explorer, venue)
    signInAs(venue)
    assertSucceeds(approved.update("status", "APPROVED"))
    val friend = newUser(Role.EXPLORER)

    assertDenied(
        partyUser("${pending.id}-${friend.uid}").set(member(pending.id, friend)),
        "a wrong membership id",
    )
    assertDenied(membership(pending.id, explorer).set(member(pending.id, explorer)), "another uid")
    assertDenied(membership(approved.id, friend).set(member(approved.id, friend)), "not pending")
    assertDenied(membership("missing", friend).set(member("missing", friend)), "no reservation")
    assertSucceeds(membership(pending.id, friend).set(member(pending.id, friend)))

    val otherVenue = newUser(Role.VENUE)
    assertDenied(
        membership(pending.id, otherVenue).set(member(pending.id, otherVenue)),
        "a venue account",
    )
  }

  /** The creator joins in the same batch that creates the reservation, which getAfter sees. */
  @Test
  fun explorerJoinsAReservationCreatedInTheSameBatch() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val reservation = newReservation()

    val batch = db.batch()
    batch.set(reservation, reservation(quest, venue, listOf(explorer)))
    batch.set(membership(reservation.id, explorer), member(reservation.id, explorer))
    assertSucceeds(batch.commit())
  }

  @Test
  fun partyMemberMustFollowTheSchema() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val valid = member(reservation.id, explorer)

    assertOnlyValidWriteSucceeds(
        membership(reservation.id, explorer),
        valid,
        mapOf(
            "a withdrawn membership" to valid + ("status" to "WITHDRAWN"),
            "an unknown status" to valid + ("status" to "INVITED"),
            "no joinedAt" to valid - "joinedAt",
            "an updatedAt that is not a timestamp" to valid + ("updatedAt" to "now"),
            "an unknown field" to valid + ("role" to "LEADER"),
        ),
    )
  }

  @Test
  fun onlyTheMemberWithdrawsAndNeverJoinsAgain() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val membership = membership(reservation.id, explorer)
    assertSucceeds(membership.set(member(reservation.id, explorer)))
    val withdrawal = mapOf("status" to "WITHDRAWN", "updatedAt" to Timestamp.now())

    signInAs(venue)
    assertDenied(membership.update(withdrawal), "the venue withdrawing the explorer")
    signInAs(explorer)
    assertDenied(
        membership.update(withdrawal + ("joinedAt" to Timestamp.now())),
        "a withdrawal that moves joinedAt",
    )
    assertDenied(membership.update("status", "WITHDRAWN"), "a withdrawal without updatedAt")
    assertSucceeds(membership.update(withdrawal))
    assertDenied(membership.update("status", "JOINED"), "WITHDRAWN -> JOINED")
    assertDenied(membership.set(member(reservation.id, explorer)), "joining again")
    assertDenied(membership.delete(), "a delete")
  }

  @Test
  fun membershipIsReadableByTheMemberTheirJoinedPartyAndTheVenueOnly() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    assertSucceeds(membership(reservation.id, explorer).set(member(reservation.id, explorer)))
    val friend = newUser(Role.EXPLORER)
    val friendsMembership = membership(reservation.id, friend)
    assertSucceeds(friendsMembership.set(member(reservation.id, friend)))
    val outsider = newUser(Role.EXPLORER)

    signInAs(explorer)
    assertSucceeds(friendsMembership.get(Source.SERVER))
    signInAs(venue)
    assertSucceeds(friendsMembership.get(Source.SERVER))
    signInAs(outsider)
    assertDenied(friendsMembership.get(Source.SERVER), "an outsider")

    signInAs(explorer)
    assertSucceeds(
        membership(reservation.id, explorer)
            .update(mapOf("status" to "WITHDRAWN", "updatedAt" to Timestamp.now()))
    )
    assertSucceeds(membership(reservation.id, explorer).get(Source.SERVER))
    assertDenied(friendsMembership.get(Source.SERVER), "a withdrawn member")
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

  /**
   * Creates an emulator user; signing up leaves them signed in. With a [role], also saves their
   * profile with it, as the app does after sign-up.
   */
  private fun newUser(role: Role? = null): TestUser {
    val email = "test-${UUID.randomUUID()}@around.test"
    val uid = assertSucceeds(auth.createUserWithEmailAndPassword(email, PASSWORD)).user!!.uid
    awaitUserChange()
    val created = TestUser(email, uid)
    if (role != null) assertSucceeds(user(created).set(profile(role.name)))
    return created
  }

  private fun signInAs(user: TestUser) {
    assertSucceeds(auth.signInWithEmailAndPassword(user.email, PASSWORD))
    awaitUserChange()
  }

  private fun signOut() {
    auth.signOut()
    awaitUserChange()
  }

  /**
   * Waits until Firestore knows who is signed in. Auth tells it on the main thread, after the
   * sign-in task has completed, so a write issued in between is queued for the previous user and
   * never sent.
   */
  private fun awaitUserChange() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()

  private fun user(user: TestUser) = db.collection("users").document(user.uid)

  private fun venueOf(user: TestUser) = db.collection("venues").document(user.uid)

  private fun newQuest() = db.collection("quests").document(UUID.randomUUID().toString())

  private fun newReservation() =
      db.collection("reservations").document(UUID.randomUUID().toString())

  /** Has [venue] create a quest with [status] and returns its id. Leaves [venue] signed in. */
  private fun questOf(venue: TestUser, status: String = "ACTIVE"): String {
    signInAs(venue)
    val quest = newQuest()
    assertSucceeds(quest.set(questData(venue) + ("status" to status)))
    return quest.id
  }

  private fun profile(role: String?) =
      mapOf("displayName" to DISPLAY_NAME, "role" to role, "createdAt" to Timestamp.now())

  /** A venue with every optional field set. */
  private fun venueData() =
      mapOf(
          "name" to "Café",
          "location" to GeoPoint(46.52, 6.57),
          "radiusMeters" to 50,
          "address" to "Rue de Bourg 1, Lausanne",
          "createdAt" to Timestamp.now(),
          "featuredQuestId" to "quest-1",
      )

  /** An active quest of [venue] with only the required fields set. */
  private fun questData(venue: TestUser) =
      mapOf(
          "venueId" to venue.uid,
          "radiusMeters" to 50,
          "title" to "Find the hidden owl",
          "description" to "An owl hides somewhere in the café.",
          "requirements" to "Photograph it.",
          "proofType" to "PHOTO",
          "minPartySize" to 1,
          "status" to "ACTIVE",
          "createdAt" to Timestamp.now(),
          "updatedAt" to Timestamp.now(),
      )

  private fun reservation(
      questId: String,
      venue: TestUser,
      party: List<TestUser>,
      status: String = "PENDING",
  ) =
      mapOf(
          "questId" to questId,
          "venueId" to venue.uid,
          "explorerUids" to party.map { it.uid },
          "slotStart" to Timestamp.now(),
          "status" to status,
          "createdAt" to Timestamp.now(),
          "explorerName" to DISPLAY_NAME,
      )

  /**
   * Has [explorer] reserve a new active quest of [venue], for a party of just them, and returns the
   * reservation. Leaves [explorer] signed in.
   */
  private fun pendingReservationOf(explorer: TestUser, venue: TestUser): DocumentReference {
    val quest = questOf(venue)
    signInAs(explorer)
    val reservation = newReservation()
    assertSucceeds(reservation.set(reservation(quest, venue, listOf(explorer))))
    return reservation
  }

  private fun partyUser(id: String) = db.collection("party_user").document(id)

  private fun membership(reservationId: String, user: TestUser) =
      partyUser("${reservationId}_${user.uid}")

  /** A joined membership of [user] in the party of [reservationId]. */
  private fun member(reservationId: String, user: TestUser) =
      mapOf(
          "reservationId" to reservationId,
          "uid" to user.uid,
          "status" to "JOINED",
          "joinedAt" to Timestamp.now(),
          "updatedAt" to Timestamp.now(),
      )

  /** A time [minutes] before now, by this device's clock. */
  private fun minutesAgo(minutes: Int) = Timestamp(Timestamp.now().seconds - minutes * 60L, 0)

  /**
   * Asserts that writing each of [invalid], keyed by what is wrong with it, to [document] is
   * denied, and that writing [valid] then succeeds, so each denial comes from what is wrong.
   */
  private fun assertOnlyValidWriteSucceeds(
      document: DocumentReference,
      valid: Map<String, Any?>,
      invalid: Map<String, Map<String, Any?>>,
  ) {
    for ((why, data) in invalid) assertDenied(document.set(data), why)
    assertSucceeds(document.set(valid))
  }

  private fun <T> assertSucceeds(task: Task<T>): T = Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS)

  /** Asserts the rules deny [task]; [why] names the case in the failure message. */
  private fun assertDenied(task: Task<*>, why: String = "") {
    val error =
        assertThrows(why, ExecutionException::class.java) {
          Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS)
        }
    assertEquals(
        why,
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error.cause as FirebaseFirestoreException).code,
    )
  }

  private companion object {
    const val PASSWORD = "password123"
    const val DISPLAY_NAME = "Test"
    const val TIMEOUT_S = 10L
  }
}
