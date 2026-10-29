// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.party.MAX_PARTY_SIZE
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
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
  fun explorerCreatesOnlyPendingReservationsWithTheirOwnMembership() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val friend = newUser(Role.EXPLORER)
    signInAs(explorer)

    assertDenied(newReservation().set(reservation(quest, venue)), "a reservation without a member")
    val reservation = newReservation()
    assertDenied(
        db.batch()
            .set(reservation, reservation(quest, venue))
            .set(membership(reservation.id, friend), member(reservation.id, friend))
            .commit(),
        "another explorer's membership",
    )
    assertDenied(
        createWithMembership(newReservation(), reservation(quest, venue, "APPROVED"), explorer),
        "an approved reservation",
    )
    assertSucceeds(createWithMembership(newReservation(), reservation(quest, venue), explorer))
  }

  @Test
  fun onlyExplorerAccountsCreateReservations() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    assertDenied(
        createWithMembership(newReservation(), reservation(quest, venue), venue),
        "a venue",
    )

    val withoutProfile = newUser()
    assertDenied(
        createWithMembership(newReservation(), reservation(quest, venue), withoutProfile),
        "an account without a profile",
    )

    val explorer = newUser(Role.EXPLORER)
    assertSucceeds(createWithMembership(newReservation(), reservation(quest, venue), explorer))
  }

  @Test
  fun reservationMustBeForAnActiveQuestOfItsVenue() {
    val venue = newUser(Role.VENUE)
    val otherVenue = newUser(Role.VENUE)
    val active = questOf(venue)
    val draft = questOf(venue, status = "DRAFT")
    val explorer = newUser(Role.EXPLORER)

    assertDenied(
        createWithMembership(newReservation(), reservation(draft, venue), explorer),
        "a draft",
    )
    assertDenied(
        createWithMembership(newReservation(), reservation("missing", venue), explorer),
        "a missing quest",
    )
    assertDenied(
        createWithMembership(newReservation(), reservation(active, otherVenue), explorer),
        "another venue than the quest's",
    )
    assertSucceeds(createWithMembership(newReservation(), reservation(active, venue), explorer))
  }

  @Test
  fun reservationMustFollowTheSchema() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val valid = reservation(quest, venue)

    assertOnlyValidReservationSucceeds(
        explorer,
        valid,
        mapOf(
            "no slotStart" to valid - "slotStart",
            "a slotStart that is not a timestamp" to valid + ("slotStart" to 1_800_000_000_000L),
            "a party listed on the reservation" to valid + ("explorerUids" to listOf(explorer.uid)),
            "an unknown field" to valid + ("leaderUid" to explorer.uid),
            "no explorerName" to valid - "explorerName",
            "an explorerName that is not a string" to valid + ("explorerName" to 42),
            "no joinedCount" to valid - "joinedCount",
            "a joinedCount above the creator alone" to valid + ("joinedCount" to 2),
        ),
    )
  }

  @Test
  fun reservationCarriesItsCreatorsOwnDisplayName() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val valid = reservation(quest, venue)

    assertOnlyValidReservationSucceeds(
        explorer,
        valid,
        mapOf(
            "another name than the profile's" to valid + ("explorerName" to "Someone else"),
            "an empty name for a named profile" to valid + ("explorerName" to ""),
        ),
    )
  }

  @Test
  fun reservationSlotCannotStartInThePast() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val valid = reservation(quest, venue)

    assertOnlyValidReservationSucceeds(
        explorer,
        valid,
        mapOf("a slot that started 10 minutes ago" to valid + ("slotStart" to minutesAgo(10))),
    )
    assertSucceeds(
        createWithMembership(newReservation(), valid + ("slotStart" to minutesAgo(1)), explorer)
    )
  }

  @Test
  fun reservationIsReadableByItsVenueAndJoinedMembersOnly() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    assertSucceeds(join(reservation.id, friend))
    val outsider = newUser(Role.EXPLORER)

    signInAs(venue)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(explorer)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(friend)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(outsider)
    assertDenied(reservation.get(Source.SERVER), "a non-member")

    signInAs(friend)
    assertSucceeds(withdraw(reservation.id, friend))
    assertDenied(reservation.get(Source.SERVER), "a withdrawn member")
  }

  /**
   * The app listens to a reservation and its party as soon as it creates them, maybe before the
   * server has them, so a missing reservation and its party, which is empty, must be readable.
   */
  @Test
  fun aMissingReservationAndItsPartyAreReadable() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val outsider = newUser(Role.EXPLORER)
    val missing = newReservation()

    assertEquals(false, assertSucceeds(missing.get(Source.SERVER)).exists())
    assertEquals(0, assertSucceeds(partyOf(missing.id).get(Source.SERVER)).size())
    assertDenied(partyOf(reservation.id).get(Source.SERVER), "another reservation's party")
  }

  @Test
  fun venueMovesAReservationAlongAllowedTransitionsOnly() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)

    signInAs(venue)
    assertDenied(reservation.update(mapOf("status" to "APPROVED", "slotStart" to "changed")))
    assertSucceeds(reservation.update("status", "APPROVED"))
    assertDenied(reservation.update("status", "PENDING"))
    assertSucceeds(reservation.update("status", "CANCELLED"))
    assertDenied(reservation.update("status", "APPROVED"))
  }

  @Test
  fun joinedMemberCancelsButNeverAnswers() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val pending = pendingReservationOf(explorer, venue)
    val approved = pendingReservationOf(explorer, venue)
    signInAs(venue)
    assertSucceeds(approved.update("status", "APPROVED"))
    val outsider = newUser(Role.EXPLORER)
    val friend = newUser(Role.EXPLORER)
    assertSucceeds(join(pending.id, friend))
    assertSucceeds(withdraw(pending.id, friend))

    assertDenied(pending.update("status", "CANCELLED"), "a withdrawn member cancelling")
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
        join(pending.id, friend, document = partyUser("${pending.id}-${friend.uid}")),
        "a wrong membership id",
    )
    assertDenied(join(pending.id, explorer), "another uid")
    assertDenied(join(approved.id, friend), "not pending")
    assertDenied(membership("missing", friend).set(member("missing", friend)), "no reservation")
    assertSucceeds(join(pending.id, friend))

    val otherVenue = newUser(Role.VENUE)
    assertDenied(join(pending.id, otherVenue), "a venue account")
  }

  @Test
  fun partyMemberMustFollowTheSchema() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    val valid = member(reservation.id, friend)
    val invalid =
        mapOf(
            "a withdrawn membership" to valid + ("status" to "WITHDRAWN"),
            "an unknown status" to valid + ("status" to "INVITED"),
            "no joinedAt" to valid - "joinedAt",
            "an updatedAt that is not a timestamp" to valid + ("updatedAt" to "now"),
            "an unknown field" to valid + ("role" to "LEADER"),
        )

    // Each join also counts the member, as the app does, so only the membership is wrong
    for ((why, data) in invalid) assertDenied(join(reservation.id, friend, data), why)
    assertSucceeds(join(reservation.id, friend, valid))
  }

  @Test
  fun onlyTheMemberWithdrawsAndNeverJoinsAgain() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val membership = membership(reservation.id, explorer)

    signInAs(venue)
    assertDenied(withdraw(reservation.id, explorer), "the venue withdrawing the explorer")
    signInAs(explorer)
    assertDenied(
        withdraw(reservation.id, explorer, withdrawal() + ("joinedAt" to Timestamp.now())),
        "a withdrawal that moves joinedAt",
    )
    assertDenied(
        withdraw(reservation.id, explorer, mapOf("status" to "WITHDRAWN")),
        "a withdrawal without updatedAt",
    )
    assertSucceeds(withdraw(reservation.id, explorer))
    assertDenied(membership.update("status", "JOINED"), "WITHDRAWN -> JOINED")
    assertDenied(join(reservation.id, explorer), "joining again")
    assertDenied(membership.delete(), "a delete")
  }

  @Test
  fun membershipIsReadableByTheMemberTheirJoinedPartyAndTheVenueOnly() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    val friendsMembership = membership(reservation.id, friend)
    assertSucceeds(join(reservation.id, friend))
    val outsider = newUser(Role.EXPLORER)

    signInAs(explorer)
    assertSucceeds(friendsMembership.get(Source.SERVER))
    signInAs(venue)
    assertSucceeds(friendsMembership.get(Source.SERVER))
    signInAs(outsider)
    assertDenied(friendsMembership.get(Source.SERVER), "an outsider")

    signInAs(explorer)
    assertSucceeds(withdraw(reservation.id, explorer))
    assertSucceeds(membership(reservation.id, explorer).get(Source.SERVER))
    assertDenied(friendsMembership.get(Source.SERVER), "a withdrawn member")
  }

  /** Rules can't count a party's memberships, so the reservation's joinedCount caps it. */
  @Test
  fun aFullPartyRefusesAJoinUntilAMemberWithdraws() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friends = List(MAX_PARTY_SIZE - 1) { newUser(Role.EXPLORER) }
    for (friend in friends) {
      signInAs(friend)
      assertSucceeds(join(reservation.id, friend))
    }
    val late = newUser(Role.EXPLORER)

    assertDenied(join(reservation.id, late), "an explorer joining a full party")
    signInAs(friends.first())
    assertSucceeds(withdraw(reservation.id, friends.first()))
    signInAs(late)
    assertSucceeds(join(reservation.id, late))
    signInAs(venue)
    assertEquals(
        MAX_PARTY_SIZE.toLong(),
        assertSucceeds(reservation.get(Source.SERVER)).getLong("joinedCount"),
    )
  }

  @Test
  fun joinedCountMovesByOneOnlyWithTheUsersOwnMembership() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)

    assertDenied(
        membership(reservation.id, friend).set(member(reservation.id, friend)),
        "a join that doesn't count",
    )
    assertDenied(countChange(reservation, 1), "a count without a join")
    assertDenied(
        db.batch()
            .set(membership(reservation.id, friend), member(reservation.id, friend))
            .update(reservation, "joinedCount", FieldValue.increment(2))
            .commit(),
        "a join that counts twice",
    )
    signInAs(venue)
    assertDenied(countChange(reservation, -1), "the venue changing the count")
    signInAs(explorer)
    assertDenied(countChange(reservation, -1), "a member lowering the count without withdrawing")
    assertDenied(
        membership(reservation.id, explorer).update(withdrawal()),
        "a withdrawal that doesn't count",
    )
    assertDenied(
        db.batch()
            .update(reservation, mapOf("status" to "CANCELLED", "joinedCount" to 0))
            .update(membership(reservation.id, explorer), withdrawal())
            .commit(),
        "a withdrawal that also cancels",
    )
    assertSucceeds(withdraw(reservation.id, explorer))
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

  private fun reservation(questId: String, venue: TestUser, status: String = "PENDING") =
      mapOf(
          "questId" to questId,
          "venueId" to venue.uid,
          "slotStart" to Timestamp.now(),
          "status" to status,
          "createdAt" to Timestamp.now(),
          "explorerName" to DISPLAY_NAME,
          "joinedCount" to 1,
      )

  /**
   * Has [creator] create [reservation] with [data], in one batch with their joined membership, as
   * the app does. Signs [creator] in.
   */
  private fun createWithMembership(
      reservation: DocumentReference,
      data: Map<String, Any?>,
      creator: TestUser,
  ): Task<Void> {
    signInAs(creator)
    return db.batch()
        .set(reservation, data)
        .set(membership(reservation.id, creator), member(reservation.id, creator))
        .commit()
  }

  /**
   * Has [explorer] reserve a new active quest of [venue], for a party of just them, and returns the
   * reservation. Leaves [explorer] signed in.
   */
  private fun pendingReservationOf(explorer: TestUser, venue: TestUser): DocumentReference {
    val quest = questOf(venue)
    val reservation = newReservation()
    assertSucceeds(createWithMembership(reservation, reservation(quest, venue), explorer))
    return reservation
  }

  private fun partyUser(id: String) = db.collection("party_user").document(id)

  private fun membership(reservationId: String, user: TestUser) =
      partyUser("${reservationId}_${user.uid}")

  /** The memberships of the party of [reservationId], as the app queries them. */
  private fun partyOf(reservationId: String) =
      db.collection("party_user").whereEqualTo("reservationId", reservationId)

  private fun reservationDocument(id: String) = db.collection("reservations").document(id)

  /**
   * Has the signed-in user write [data] to [document], their membership by default, in one batch
   * that adds 1 to the joinedCount of [reservationId], as the app joins.
   */
  private fun join(
      reservationId: String,
      user: TestUser,
      data: Map<String, Any?> = member(reservationId, user),
      document: DocumentReference = membership(reservationId, user),
  ): Task<Void> =
      db.batch()
          .set(document, data)
          .update(reservationDocument(reservationId), "joinedCount", FieldValue.increment(1))
          .commit()

  /**
   * Has the signed-in user update [user]'s membership with [changes] in one batch that takes 1 off
   * the joinedCount of [reservationId], as the app withdraws.
   */
  private fun withdraw(
      reservationId: String,
      user: TestUser,
      changes: Map<String, Any> = withdrawal(),
  ): Task<Void> =
      db.batch()
          .update(membership(reservationId, user), changes)
          .update(reservationDocument(reservationId), "joinedCount", FieldValue.increment(-1))
          .commit()

  /** Moves the joinedCount of [reservation] by [delta] alone. */
  private fun countChange(reservation: DocumentReference, delta: Long) =
      reservation.update("joinedCount", FieldValue.increment(delta))

  /** The fields a member changes to withdraw. */
  private fun withdrawal() = mapOf("status" to "WITHDRAWN", "updatedAt" to Timestamp.now())

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

  /**
   * Asserts that [creator] creating a reservation with each of [invalid], keyed by what is wrong
   * with it, with their membership, is denied, and that creating one with [valid] then succeeds.
   */
  private fun assertOnlyValidReservationSucceeds(
      creator: TestUser,
      valid: Map<String, Any?>,
      invalid: Map<String, Map<String, Any?>>,
  ) {
    for ((why, data) in invalid) {
      assertDenied(createWithMembership(newReservation(), data, creator), why)
    }
    assertSucceeds(createWithMembership(newReservation(), valid, creator))
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
