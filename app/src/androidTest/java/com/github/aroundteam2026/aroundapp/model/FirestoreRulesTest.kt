// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
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
import com.google.firebase.firestore.WriteBatch
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
  fun signedInUsersReadProfilesButOnlyOwnersWriteThem() {
    val alice = newUser()
    val bob = newUser()

    signInAs(bob)
    assertSucceeds(user(bob).set(profile(role = null)))
    assertDenied(user(alice).set(profile(role = null)))
    signInAs(alice)
    assertSucceeds(user(alice).set(profile(role = null)))
    signInAs(bob)
    assertEquals(
        DISPLAY_NAME,
        assertSucceeds(user(alice).get(Source.SERVER)).getString("displayName"),
    )
    assertEquals(
        DISPLAY_NAME,
        assertSucceeds(user(bob).get(Source.SERVER)).getString("displayName"),
    )
    assertDenied(db.collection("users").get(Source.SERVER))
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
  fun profileOwnerEditsNameAndPictureWithoutChangingTheRoleOrCreatedAt() {
    val alice = newUser()
    val bob = newUser()
    signInAs(alice)
    assertSucceeds(user(alice).set(profile(role = "EXPLORER")))

    assertSucceeds(
        user(alice)
            .update(
                "displayName",
                "Grace",
                "pictureUrl",
                "gs://bucket/user-pictures/${alice.uid}/1",
            )
    )
    assertDenied(user(alice).update("pictureUrl", 42))
    assertDenied(user(alice).update("pictureUrl", "gs://bucket/user-pictures/${bob.uid}/1"))
    assertDenied(user(alice).update("pictureUrl", "gs://bucket/user-pictures/${alice.uid}/1/extra"))
    val picturePrefix = "gs://bucket/user-pictures/${alice.uid}/"
    assertDenied(
        user(alice).update("pictureUrl", picturePrefix + "x".repeat(2001 - picturePrefix.length))
    )
    assertSucceeds(user(alice).update("displayName", "x".repeat(100)))
    assertDenied(user(alice).update("createdAt", Timestamp.now()))
    assertDenied(user(bob).update("displayName", "Other"))
    assertEquals(
        "x".repeat(100),
        assertSucceeds(user(alice).get(Source.SERVER)).getString("displayName"),
    )
    signOut()
    assertDenied(user(alice).get(Source.SERVER))
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
            "a displayName over the limit" to valid + ("displayName" to "x".repeat(101)),
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

    // Another venue's update is checked by
    // onlyTheOwningVenueUpdatesAWholeQuestAndKeepsItsCreationTime
    signInAs(other)
    assertDenied(quest.delete())
  }

  @Test
  fun onlyTheOwningVenueUpdatesAWholeQuestAndKeepsItsCreationTime() {
    val venue = newUser(Role.VENUE)
    val other = newUser(Role.VENUE)
    signInAs(venue)
    val quest = newQuest()
    assertSucceeds(quest.set(questData(venue)))
    // What QuestRepositoryFirestore.updateQuest sends: every editable field and updatedAt
    val edit =
        mapOf(
            "radiusMeters" to 80,
            "title" to "Find the hidden fox",
            "description" to "A fox hides somewhere in the café.",
            "requirements" to "Describe it.",
            "proofType" to "TEXT",
            "minPartySize" to 2,
            "exactPartySize" to true,
            "status" to "ARCHIVED",
            "reward" to FieldValue.delete(),
            "updatedAt" to Timestamp.now(),
        )

    assertSucceeds(quest.update(edit))
    assertDenied(quest.update(edit + ("createdAt" to Timestamp.now())), "a changed createdAt")

    signInAs(other)
    assertDenied(quest.update(edit), "another venue")
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
            "a location that is not a GeoPoint" to valid + ("location" to "Lausanne"),
            "a {lat, lng} map as location" to
                valid + ("location" to mapOf("lat" to 46.52, "lng" to 6.57)),
            "an unknown field" to valid + ("rating" to 5),
        ),
    )
    assertSucceeds(newQuest().set(valid + ("exactPartySize" to true)))
  }

  // #430: a quest may have a location of its own, away from its venue
  @Test
  fun questMayHaveALocationOfItsOwn() {
    val venue = newUser(Role.VENUE)
    signInAs(venue)
    val quest = newQuest()

    assertSucceeds(quest.set(questData(venue) + ("location" to GeoPoint(46.52, 6.57))))
    assertSucceeds(quest.update("location", GeoPoint(46.51, 6.63)))
    assertDenied(quest.update("location", "Lausanne"), "a location that is not a GeoPoint")
    assertSucceeds(quest.update("location", FieldValue.delete()))
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

    assertDenied(
        withPartyCode(newReservation(), reservation(quest, venue)).commit(),
        "a reservation without a member",
    )
    val reservation = newReservation()
    assertDenied(
        withPartyCode(reservation, reservation(quest, venue))
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

  /**
   * An explorer given a party code reads the party's reservation, which they haven't joined, to see
   * its quest, status and joinedCount before joining. A venue reads its own reservations and lists
   * them; nobody else lists them, so an id or a code is needed to read one.
   */
  @Test
  fun reservationIsReadableByExplorersAndItsVenueAndListedByItsVenueOnly() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    assertSucceeds(join(reservation.id, friend))
    val outsider = newUser(Role.EXPLORER)
    val otherVenue = newUser(Role.VENUE)
    val ofTheVenue = db.collection("reservations").whereEqualTo("venueId", venue.uid)

    signInAs(venue)
    assertSucceeds(reservation.get(Source.SERVER))
    assertEquals(1, assertSucceeds(ofTheVenue.get(Source.SERVER)).size())
    signInAs(explorer)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(friend)
    assertSucceeds(reservation.get(Source.SERVER))
    signInAs(outsider)
    assertSucceeds(reservation.get(Source.SERVER))
    assertDenied(ofTheVenue.get(Source.SERVER), "another explorer listing a venue's reservations")
    assertDenied(db.collection("reservations").get(Source.SERVER), "listing every reservation")

    signInAs(friend)
    assertSucceeds(withdraw(reservation.id, friend))
    assertSucceeds(reservation.get(Source.SERVER))
    assertDenied(ofTheVenue.get(Source.SERVER), "a withdrawn member listing")
    signInAs(otherVenue)
    assertDenied(reservation.get(Source.SERVER), "another venue")
    assertEquals(false, assertSucceeds(newReservation().get(Source.SERVER)).exists())
    assertDenied(ofTheVenue.get(Source.SERVER), "another venue listing")
    signOut()
    assertDenied(reservation.get(Source.SERVER), "a signed-out user")
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
  fun venueApprovesOnlyAPartyOfAtLeastTheQuestsSize() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, minPartySize = 2)
    val friend = newUser(Role.EXPLORER)

    signInAs(venue)
    assertDenied(reservation.update("status", "APPROVED"), "a party below the quest's size")
    joinAll(reservation, friend)
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
  }

  @Test
  fun venueApprovesAnExactSizeQuestOnlyAtThatSize() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, minPartySize = 2, exactPartySize = true)
    val friend = newUser(Role.EXPLORER)

    signInAs(venue)
    assertDenied(reservation.update("status", "APPROVED"), "a party below the exact size")
    joinAll(reservation, friend)
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
  }

  /** The venue can always say no, and a member can always cancel, whatever the party's size. */
  @Test
  fun aPartyBelowTheQuestsSizeIsStillRejectedOrCancelled() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val rejected = pendingReservationOf(explorer, venue, minPartySize = 3)
    val cancelled = pendingReservationOf(explorer, venue, minPartySize = 3)

    assertSucceeds(cancelled.update("status", "CANCELLED"))
    signInAs(venue)
    assertSucceeds(rejected.update("status", "REJECTED"))
  }

  /** An exact-size party is full at that size, below the general cap. */
  @Test
  fun anExactSizePartyIsFullAtThatSize() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, minPartySize = 2, exactPartySize = true)
    val friend = newUser(Role.EXPLORER)
    val late = newUser(Role.EXPLORER)
    joinAll(reservation, friend)

    signInAs(late)
    assertDenied(join(reservation.id, late), "a third explorer in a party of exactly 2")
    signInAs(friend)
    assertSucceeds(withdraw(reservation.id, friend))
    signInAs(late)
    assertSucceeds(join(reservation.id, late))
  }

  /** An exact size above the cap fills a party only to the cap, so it is never approved. */
  @Test
  fun anExactSizeAboveTheCapIsNeverApproved() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation =
        pendingReservationOf(
            explorer,
            venue,
            minPartySize = MAX_PARTY_SIZE + 1,
            exactPartySize = true,
        )
    joinAll(reservation, *Array(MAX_PARTY_SIZE - 1) { newUser(Role.EXPLORER) })
    val late = newUser(Role.EXPLORER)

    signInAs(late)
    assertDenied(join(reservation.id, late), "an explorer joining a party at the cap")
    signInAs(venue)
    assertDenied(reservation.update("status", "APPROVED"), "a party below the exact size")
  }

  /** An at-least size above the cap is never reached either, since the party stops at the cap. */
  @Test
  fun anAtLeastSizeAboveTheCapIsNeverApproved() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, minPartySize = MAX_PARTY_SIZE + 1)
    joinAll(reservation, *Array(MAX_PARTY_SIZE - 1) { newUser(Role.EXPLORER) })
    val late = newUser(Role.EXPLORER)

    signInAs(late)
    assertDenied(join(reservation.id, late), "an explorer joining a party at the cap")
    signInAs(venue)
    assertDenied(reservation.update("status", "APPROVED"), "a party below the quest's size")
  }

  /** Quests written before exactPartySize are read as at least, as the rules promise. */
  @Test
  fun aQuestWithoutExactPartySizeIsReadAsAtLeast() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, minPartySize = 2, exactPartySize = null)

    signInAs(venue)
    assertDenied(reservation.update("status", "APPROVED"), "a party below the quest's size")
    joinAll(reservation, newUser(Role.EXPLORER), newUser(Role.EXPLORER))
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
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

  // The bug of #426: cancelling hid the sent proof, since the Quests tab only lists active quests
  @Test
  fun memberCannotCancelOnceTheirProofIsSentWhateverItsStatus() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = approvedReservationOf(explorer, venue)
    val completion = completionOf(reservation)
    assertSucceeds(completion.set(completion(reservation.id, explorer, venue)))

    assertDenied(reservation.update("status", "CANCELLED"), "a pending proof")
    signInAs(venue)
    assertSucceeds(completion.update(rejection()))
    signInAs(explorer)
    assertDenied(reservation.update("status", "CANCELLED"), "a rejected proof")
    signInAs(venue)
    assertSucceeds(
        completion.update(mapOf("status" to "APPROVED", "rejectReason" to FieldValue.delete()))
    )
    signInAs(explorer)
    assertDenied(reservation.update("status", "CANCELLED"), "an approved proof")
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
    val friend = newUser(Role.EXPLORER)
    joinAll(reservation, friend)
    val membership = membership(reservation.id, friend)

    signInAs(venue)
    assertDenied(withdraw(reservation.id, friend), "the venue withdrawing the friend")
    signInAs(explorer)
    assertDenied(withdraw(reservation.id, friend), "another member withdrawing the friend")
    signInAs(friend)
    assertDenied(
        withdraw(reservation.id, friend, withdrawal() + ("joinedAt" to Timestamp.now())),
        "a withdrawal that moves joinedAt",
    )
    assertDenied(
        withdraw(reservation.id, friend, mapOf("status" to "WITHDRAWN")),
        "a withdrawal without updatedAt",
    )
    assertSucceeds(withdraw(reservation.id, friend))
    assertDenied(membership.update("status", "JOINED"), "WITHDRAWN -> JOINED")
    assertDenied(join(reservation.id, friend), "joining again")
    assertDenied(membership.delete(), "a delete")
  }

  @Test
  fun membershipIsReadableByTheMemberTheirPartyAndTheVenueOnly() {
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

    signInAs(friend)
    assertSucceeds(withdraw(reservation.id, friend))
    assertSucceeds(friendsMembership.get(Source.SERVER))
    assertSucceeds(membership(reservation.id, explorer).get(Source.SERVER))
  }

  /** A member who withdrew still sees who else was in the party. */
  @Test
  fun aWithdrawnMemberListsTheirParty() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    assertSucceeds(join(reservation.id, friend))

    assertSucceeds(withdraw(reservation.id, friend))
    assertEquals(2, assertSucceeds(partyOf(reservation.id).get(Source.SERVER)).size())
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

  /** The creator cancels the reservation instead of leaving its party. */
  @Test
  fun theCreatorNeverWithdrawsButCancels() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    joinAll(reservation, friend)

    signInAs(explorer)
    assertDenied(withdraw(reservation.id, explorer), "the creator withdrawing")
    assertSucceeds(reservation.update("status", "CANCELLED"))
  }

  /**
   * The creator is told apart by being joined at the reservation's creation, which nobody else is.
   */
  @Test
  fun onlyTheCreatorIsJoinedAtTheReservationsCreation() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val data = reservation(quest, venue)
    val reservation = newReservation()
    signInAs(explorer)

    assertDenied(
        withPartyCode(reservation, data)
            .set(
                membership(reservation.id, explorer),
                member(reservation.id, explorer, minutesAgo(1)),
            )
            .commit(),
        "a creator joined at another time than the reservation's creation",
    )
    assertSucceeds(createWithMembership(reservation, data, explorer))
    // As stored, which the rules compare with
    val createdAt = createdAtOf(reservation)
    val friend = newUser(Role.EXPLORER)
    assertDenied(
        join(reservation.id, friend, member(reservation.id, friend, createdAt)),
        "a friend joined at the reservation's creation",
    )
    assertSucceeds(join(reservation.id, friend))
  }

  /** Leaving an approved party is allowed even when it drops below the quest's size. */
  @Test
  fun memberWithdrawsOnlyWhileTheReservationIsPendingOrApproved() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val approved = pendingReservationOf(explorer, venue, minPartySize = 2)
    val rejected = pendingReservationOf(explorer, venue)
    val cancelled = pendingReservationOf(explorer, venue)
    val friend = newUser(Role.EXPLORER)
    for (reservation in listOf(approved, rejected, cancelled)) joinAll(reservation, friend)
    signInAs(venue)
    assertSucceeds(approved.update("status", "APPROVED"))
    assertSucceeds(rejected.update("status", "REJECTED"))
    signInAs(explorer)
    assertSucceeds(cancelled.update("status", "CANCELLED"))

    signInAs(friend)
    assertDenied(withdraw(rejected.id, friend), "a rejected reservation")
    assertDenied(withdraw(cancelled.id, friend), "a cancelled reservation")
    assertSucceeds(withdraw(approved.id, friend))
  }

  @Test
  fun memberCannotWithdrawOnceTheirProofIsSent() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, proofType = "TEXT")
    val friend = newUser(Role.EXPLORER)
    joinAll(reservation, friend)
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
    signInAs(friend)
    assertSucceeds(completionOf(reservation).set(completion(reservation.id, friend, venue)))

    assertDenied(withdraw(reservation.id, friend), "a member who sent their proof withdrawing")
  }

  @Test
  fun noMemberCancelsOrWithdrawsOnceAnotherSentThePartysProof() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue, proofType = "TEXT")
    val friend = newUser(Role.EXPLORER)
    val sender = newUser(Role.EXPLORER)
    joinAll(reservation, friend, sender)
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
    signInAs(sender)
    assertSucceeds(completionOf(reservation).set(completion(reservation.id, sender, venue)))

    signInAs(friend)
    assertDenied(withdraw(reservation.id, friend), "a member withdrawing")
    assertDenied(reservation.update("status", "CANCELLED"), "a member cancelling")
    signInAs(explorer)
    assertDenied(reservation.update("status", "CANCELLED"), "the creator cancelling")
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
    assertSucceeds(join(reservation.id, friend))
    signInAs(venue)
    assertDenied(countChange(reservation, -1), "the venue changing the count")
    signInAs(friend)
    assertDenied(countChange(reservation, -1), "a member lowering the count without withdrawing")
    assertDenied(
        membership(reservation.id, friend).update(withdrawal()),
        "a withdrawal that doesn't count",
    )
    assertDenied(
        db.batch()
            .update(reservation, mapOf("status" to "CANCELLED", "joinedCount" to 1))
            .update(membership(reservation.id, friend), withdrawal())
            .commit(),
        "a withdrawal that also cancels",
    )
    assertSucceeds(withdraw(reservation.id, friend))
  }

  // ---- party codes ----

  @Test
  fun reservationIsCreatedWithItsPartyCodeAndAnyExplorerFindsIt() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val code = newPartyCode()
    val reservation = newReservation()
    assertSucceeds(
        createWithMembership(
            reservation,
            reservation(quest, venue) + ("partyCode" to code),
            explorer,
        )
    )
    val stranger = newUser(Role.EXPLORER)

    signInAs(stranger)
    assertEquals(
        reservation.id,
        assertSucceeds(partyCodeDocument(code).get(Source.SERVER)).getString("reservationId"),
    )
    assertDenied(db.collection("party_codes").get(Source.SERVER), "listing the codes")
    signOut()
    assertDenied(partyCodeDocument(code).get(Source.SERVER), "a signed-out user")
  }

  /** Writing a taken code is an update, so a code leads to one reservation only. */
  @Test
  fun aTakenPartyCodeIsRefused() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val code = newPartyCode()
    val first = newReservation()
    assertSucceeds(
        createWithMembership(first, reservation(quest, venue) + ("partyCode" to code), explorer)
    )
    val other = newUser(Role.EXPLORER)

    assertDenied(
        createWithMembership(
            newReservation(),
            reservation(quest, venue) + ("partyCode" to code),
            other,
        ),
        "a second reservation with the same code",
    )
    assertEquals(
        first.id,
        assertSucceeds(partyCodeDocument(code).get(Source.SERVER)).getString("reservationId"),
    )
  }

  @Test
  fun partyCodeMustLeadBackToItsReservation() {
    val venue = newUser(Role.VENUE)
    val quest = questOf(venue)
    val explorer = newUser(Role.EXPLORER)
    val data = reservation(quest, venue)
    val reservation = newReservation()
    signInAs(explorer)
    val creator = member(reservation.id, explorer, data["createdAt"] as Timestamp)

    /** Creates [reservation] with [reservationCode], and [codeDocument] leading to [target]. */
    fun create(reservationCode: String?, codeDocument: String?, target: String = reservation.id) =
        db.batch()
            .set(
                reservation,
                if (reservationCode == null) data else data + ("partyCode" to reservationCode),
            )
            .set(membership(reservation.id, explorer), creator)
            .apply {
              if (codeDocument != null) {
                set(partyCodeDocument(codeDocument), mapOf("reservationId" to target))
              }
            }
            .commit()

    val code = newPartyCode()
    assertDenied(create(null, null), "no party code")
    assertDenied(create(code, null), "a code without its document")
    assertDenied(create(code, code, target = "another"), "a code leading elsewhere")
    assertDenied(create(code, newPartyCode()), "another code's document")
    assertDenied(create("7KQ M42", "7KQ M42"), "a code with a space")
    assertDenied(create("7kqm42", "7kqm42"), "a lower-case code")
    assertDenied(create("OIL234", "OIL234"), "a code with lookalike characters")
    assertDenied(create(null, code), "a code document for a reservation without that code")
    assertSucceeds(create(code, code))
  }

  @Test
  fun partyCodeIsWrittenOnlyWithItsNewReservationAndNeverChanges() {
    val venue = newUser(Role.VENUE)
    val explorer = newUser(Role.EXPLORER)
    val reservation = pendingReservationOf(explorer, venue)
    val code = newPartyCode()

    assertDenied(
        partyCodeDocument(code).set(mapOf("reservationId" to reservation.id)),
        "a code for an existing reservation",
    )
    assertDenied(
        partyCodeDocument(code).set(mapOf("reservationId" to "missing")),
        "a code for no reservation",
    )

    val quest = questOf(venue)
    val withCode = newReservation()
    assertSucceeds(
        createWithMembership(withCode, reservation(quest, venue) + ("partyCode" to code), explorer)
    )
    assertDenied(withCode.update("partyCode", newPartyCode()), "a member changing the code")
    assertDenied(
        partyCodeDocument(code).update("reservationId", reservation.id),
        "a code moved to another reservation",
    )
    assertDenied(partyCodeDocument(code).delete(), "a delete")
    signInAs(venue)
    assertDenied(withCode.update("partyCode", newPartyCode()), "the venue changing the code")
  }

  // ---- completions ----

  @Test
  fun explorerSubmitsOnlyAsThemselvesUnderTheReservationsId() {
    val explorer = newUser(Role.EXPLORER)
    val friend = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val reservation = approvedReservationOf(explorer, venue)
    val valid = completion(reservation.id, explorer, venue)

    assertDenied(completions().document(UUID.randomUUID().toString()).set(valid), "a random id")
    assertDenied(
        completions().document("${reservation.id}_${explorer.uid}").set(valid),
        "an id built from the explorer",
    )
    assertDenied(
        completionOf(reservation).set(valid + ("explorerUid" to friend.uid)),
        "another explorer's uid",
    )
    assertSucceeds(completionOf(reservation).set(valid))
  }

  @Test
  fun anyJoinedMemberSubmitsThePartysButNobodyElse() {
    val creator = newUser(Role.EXPLORER)
    val joined = newUser(Role.EXPLORER)
    val withdrawn = newUser(Role.EXPLORER)
    val stranger = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val reservation = pendingReservationOf(creator, venue, proofType = "TEXT")
    signInAs(joined)
    assertSucceeds(join(reservation.id, joined))
    signInAs(withdrawn)
    assertSucceeds(join(reservation.id, withdrawn))
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
    signInAs(withdrawn)
    assertSucceeds(withdraw(reservation.id, withdrawn))

    assertDenied(
        completionOf(reservation).set(completion(reservation.id, withdrawn, venue)),
        "a withdrawn member",
    )
    signInAs(stranger)
    assertDenied(
        completionOf(reservation).set(completion(reservation.id, stranger, venue)),
        "an explorer outside the party",
    )
    signInAs(joined)
    assertSucceeds(completionOf(reservation).set(completion(reservation.id, joined, venue)))
  }

  @Test
  fun signedOutUserCannotSubmit() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val reservation = approvedReservationOf(explorer, venue)

    signOut()
    assertDenied(completionOf(reservation).set(completion(reservation.id, explorer, venue)))
  }

  @Test
  fun completionNeedsAnApprovedReservation() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)

    val pending = pendingReservationOf(explorer, venue, proofType = "TEXT")
    assertDenied(
        completionOf(pending).set(completion(pending.id, explorer, venue)),
        "a pending reservation",
    )

    val cancelled = approvedReservationOf(explorer, venue)
    assertSucceeds(cancelled.update("status", "CANCELLED"))
    assertDenied(
        completionOf(cancelled).set(completion(cancelled.id, explorer, venue)),
        "a cancelled reservation",
    )

    val missing = newReservation()
    assertDenied(
        completionOf(missing).set(completion(missing.id, explorer, venue)),
        "a missing reservation",
    )
  }

  @Test
  fun completionMustFollowTheSchema() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val reservation = approvedReservationOf(explorer, venue)
    val valid = completion(reservation.id, explorer, venue)

    assertOnlyValidWriteSucceeds(
        completionOf(reservation),
        valid,
        mapOf(
            "an unknown field" to valid + ("questId" to "quest-1"),
            "no submittedAt" to valid - "submittedAt",
            "a submittedAt that is not a timestamp" to valid + ("submittedAt" to 42),
            "already approved" to valid + ("status" to "APPROVED"),
            "a reject reason" to valid + ("rejectReason" to "Blurry"),
            "another venue" to valid + ("venueId" to explorer.uid),
        ),
    )
  }

  @Test
  fun proofMustBeTheOneTheQuestAsksFor() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)

    val text = approvedReservationOf(explorer, venue, proofType = "TEXT")
    val textCompletion = completion(text.id, explorer, venue)
    assertOnlyValidWriteSucceeds(
        completionOf(text),
        textCompletion,
        mapOf(
            "no proof" to textCompletion - "textAnswer",
            "both proofs" to textCompletion + ("photoUrl" to PHOTO_URL),
            "a photo instead" to textCompletion - "textAnswer" + ("photoUrl" to PHOTO_URL),
            "a blank answer" to textCompletion + ("textAnswer" to " "),
            "an answer that is not a string" to textCompletion + ("textAnswer" to 42),
            "a too long answer" to textCompletion + ("textAnswer" to "a".repeat(1001)),
        ),
    )

    val photo = approvedReservationOf(explorer, venue, proofType = "PHOTO")
    val photoCompletion =
        completion(photo.id, explorer, venue) - "textAnswer" + ("photoUrl" to PHOTO_URL)
    assertOnlyValidWriteSucceeds(
        completionOf(photo),
        photoCompletion,
        mapOf(
            "an answer instead" to completion(photo.id, explorer, venue),
            "a blank URL" to photoCompletion + ("photoUrl" to " "),
            "a too long URL" to photoCompletion + ("photoUrl" to "a".repeat(2001)),
        ),
    )
  }

  @Test
  fun explorerSubmitsOnlyOnce() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)
    val again = assertSucceeds(completion.get(Source.SERVER)).data!!

    assertDenied(completion.set(again), "a second submission")

    signInAs(venue)
    assertSucceeds(completion.update(rejection()))
    signInAs(explorer)
    assertDenied(completion.set(again), "a second submission after a rejection")
  }

  @Test
  fun anotherMemberCannotSubmitOnceThePartysProofIsSent() {
    val explorer = newUser(Role.EXPLORER)
    val friend = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val reservation = pendingReservationOf(explorer, venue, proofType = "TEXT")
    joinAll(reservation, friend)
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
    signInAs(explorer)
    assertSucceeds(completionOf(reservation).set(completion(reservation.id, explorer, venue)))

    signInAs(friend)
    assertDenied(
        completionOf(reservation).set(completion(reservation.id, friend, venue)),
        "another member's proof",
    )
    assertDenied(
        completions()
            .document("${reservation.id}_${friend.uid}")
            .set(completion(reservation.id, friend, venue)),
        "another member's proof under an id built from their uid",
    )
  }

  @Test
  fun onlyItsVenueAnswersACompletion() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val otherVenue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)

    assertDenied(completion.update("status", "APPROVED"), "the explorer")
    signInAs(otherVenue)
    assertDenied(completion.update("status", "APPROVED"), "another venue")
    signInAs(venue)
    assertSucceeds(completion.update("status", "APPROVED"))
  }

  @Test
  fun approvalChangesOnlyTheStatusAndIsFinal() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)
    signInAs(venue)

    assertDenied(completion.update(rejection() + ("status" to "APPROVED")), "with a reason")
    assertDenied(
        completion.update(mapOf("status" to "APPROVED", "textAnswer" to "Other")),
        "with a new answer",
    )
    assertDenied(completion.update("status", "PENDING"), "to pending")
    assertDenied(completion.update("status", "LOST"), "to an unknown status")

    assertSucceeds(completion.update("status", "APPROVED"))
    assertDenied(completion.update(rejection()), "rejecting an approved completion")
  }

  @Test
  fun rejectionNeedsAReason() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)
    signInAs(venue)

    assertDenied(completion.update("status", "REJECTED"), "no reason")
    assertDenied(completion.update(rejection() + ("rejectReason" to " ")), "a blank reason")
    assertDenied(
        completion.update(rejection() + ("rejectReason" to "a".repeat(301))),
        "a too long reason",
    )
    assertDenied(completion.update(rejection() + ("explorerUid" to venue.uid)), "another explorer")

    assertSucceeds(completion.update(rejection()))
    assertDenied(completion.update("rejectReason", "Other"), "a new reason")
  }

  @Test
  fun venueApprovesARejectedCompletionWithoutItsReason() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)
    signInAs(venue)
    assertSucceeds(completion.update(rejection()))
    val approval = mapOf("status" to "APPROVED", "rejectReason" to FieldValue.delete())

    assertDenied(completion.update("status", "APPROVED"), "keeping the reason")
    assertDenied(completion.update(approval + ("textAnswer" to "Other")), "with a new answer")
    assertDenied(completion.update(approval + ("status" to "PENDING")), "back to pending")

    signInAs(explorer)
    assertDenied(completion.update(approval), "the explorer")

    signInAs(venue)
    assertSucceeds(completion.update(approval))
    assertDenied(completion.update(rejection()), "rejecting it again")
  }

  @Test
  fun completionIsReadableByItsExplorerAndVenueOnly() {
    val explorer = newUser(Role.EXPLORER)
    val stranger = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val otherVenue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)

    assertSucceeds(completion.get(Source.SERVER))
    assertSucceeds(completions().whereEqualTo("explorerUid", explorer.uid).get(Source.SERVER))

    signInAs(venue)
    assertSucceeds(completion.get(Source.SERVER))
    assertSucceeds(completions().whereEqualTo("venueId", venue.uid).get(Source.SERVER))

    signInAs(otherVenue)
    assertDenied(completion.get(Source.SERVER), "another venue")
    assertDenied(
        completions().whereEqualTo("venueId", venue.uid).get(Source.SERVER),
        "another venue's query",
    )

    signInAs(stranger)
    assertDenied(completion.get(Source.SERVER), "another explorer")
    assertDenied(
        completions().whereEqualTo("explorerUid", explorer.uid).get(Source.SERVER),
        "another explorer's query",
    )
    assertDenied(completions().get(Source.SERVER), "a query of every completion")

    signOut()
    assertDenied(completion.get(Source.SERVER), "signed out")
  }

  @Test
  fun joinedMembersReadTheirPartysCompletions() {
    val creator = newUser(Role.EXPLORER)
    val joined = newUser(Role.EXPLORER)
    val withdrawn = newUser(Role.EXPLORER)
    val stranger = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val reservation = pendingReservationOf(creator, venue, proofType = "TEXT")
    signInAs(joined)
    assertSucceeds(join(reservation.id, joined))
    signInAs(withdrawn)
    assertSucceeds(join(reservation.id, withdrawn))
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
    signInAs(withdrawn)
    assertSucceeds(withdraw(reservation.id, withdrawn))
    signInAs(creator)
    val completion = completionOf(reservation)
    assertSucceeds(completion.set(completion(reservation.id, creator, venue)))
    val ofTheParty = completions().whereEqualTo("reservationId", reservation.id)

    signInAs(joined)
    assertSucceeds(completion.get(Source.SERVER))
    assertEquals(1, assertSucceeds(ofTheParty.get(Source.SERVER)).size())

    signInAs(withdrawn)
    assertDenied(completion.get(Source.SERVER), "a withdrawn member")
    assertDenied(ofTheParty.get(Source.SERVER), "a withdrawn member's query")

    signInAs(stranger)
    assertDenied(ofTheParty.get(Source.SERVER), "a query from outside the party")
  }

  @Test
  fun aMissingCompletionIsReadableOnceSignedIn() {
    val missing = completions().document(UUID.randomUUID().toString())

    assertDenied(missing.get(Source.SERVER), "signed out")
    newUser(Role.VENUE)
    assertEquals(false, assertSucceeds(missing.get(Source.SERVER)).exists())
  }

  @Test
  fun completionIsNeverDeleted() {
    val explorer = newUser(Role.EXPLORER)
    val venue = newUser(Role.VENUE)
    val completion = submittedCompletion(explorer, venue)

    assertDenied(completion.delete(), "the explorer")
    signInAs(venue)
    assertDenied(completion.delete(), "the venue")
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

  /**
   * Has [venue] create a quest with [status] and [proofType], for a party of at least
   * [minPartySize], or exactly that many with [exactPartySize], and returns its id. A null
   * [exactPartySize] leaves the field out, as on quests written before it. Leaves [venue] signed
   * in.
   */
  private fun questOf(
      venue: TestUser,
      status: String = "ACTIVE",
      proofType: String = "PHOTO",
      minPartySize: Int = 1,
      exactPartySize: Boolean? = false,
  ): String {
    signInAs(venue)
    val quest = newQuest()
    val data =
        questData(venue) +
            mapOf(
                "status" to status,
                "proofType" to proofType,
                "minPartySize" to minPartySize,
            ) +
            listOfNotNull(exactPartySize?.let { "exactPartySize" to it })
    assertSucceeds(quest.set(data))
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
   * Has [creator] create [reservation] with [data], in one batch with their joined membership,
   * joined at the reservation's createdAt, and its party code, as the app does: the one in [data],
   * or a new one. Signs [creator] in.
   */
  private fun createWithMembership(
      reservation: DocumentReference,
      data: Map<String, Any?>,
      creator: TestUser,
  ): Task<Void> {
    signInAs(creator)
    val createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()
    return withPartyCode(reservation, data)
        .set(membership(reservation.id, creator), member(reservation.id, creator, createdAt))
        .commit()
  }

  /**
   * A batch that writes [reservation] with [data] and its party code, which the rules require on
   * creation, and the code's document: the code in [data], or a new one.
   */
  private fun withPartyCode(reservation: DocumentReference, data: Map<String, Any?>): WriteBatch {
    val code = data["partyCode"] as? String ?: newPartyCode()
    return db.batch()
        .set(reservation, data + ("partyCode" to code))
        .set(partyCodeDocument(code), mapOf("reservationId" to reservation.id))
  }

  /**
   * Has [explorer] reserve a new active quest of [venue] asking for [proofType] and a party of at
   * least [minPartySize], or exactly that many with [exactPartySize], for a party of just them, and
   * returns the reservation. Leaves [explorer] signed in.
   */
  private fun pendingReservationOf(
      explorer: TestUser,
      venue: TestUser,
      proofType: String = "PHOTO",
      minPartySize: Int = 1,
      exactPartySize: Boolean? = false,
  ): DocumentReference {
    val quest =
        questOf(
            venue,
            proofType = proofType,
            minPartySize = minPartySize,
            exactPartySize = exactPartySize,
        )
    val reservation = newReservation()
    assertSucceeds(createWithMembership(reservation, reservation(quest, venue), explorer))
    return reservation
  }

  /** Same as [pendingReservationOf], with [venue] approving it. Leaves [explorer] signed in. */
  private fun approvedReservationOf(
      explorer: TestUser,
      venue: TestUser,
      proofType: String = "TEXT",
  ): DocumentReference {
    val reservation = pendingReservationOf(explorer, venue, proofType)
    signInAs(venue)
    assertSucceeds(reservation.update("status", "APPROVED"))
    signInAs(explorer)
    return reservation
  }

  private fun completions() = db.collection("completions")

  /** The party's completion of [reservation], under the reservation's id. */
  private fun completionOf(reservation: DocumentReference) = completions().document(reservation.id)

  /** A pending completion of [reservationId] by [explorer], for a `TEXT` quest of [venue]. */
  private fun completion(reservationId: String, explorer: TestUser, venue: TestUser) =
      mapOf(
          "reservationId" to reservationId,
          "venueId" to venue.uid,
          "explorerUid" to explorer.uid,
          "textAnswer" to "The answer",
          "status" to "PENDING",
          "submittedAt" to Timestamp.now(),
      )

  /**
   * Has [explorer] submit a completion of a new approved reservation of [venue] and returns it.
   * Leaves [explorer] signed in.
   */
  private fun submittedCompletion(explorer: TestUser, venue: TestUser): DocumentReference {
    val reservation = approvedReservationOf(explorer, venue)
    val completion = completionOf(reservation)
    assertSucceeds(completion.set(completion(reservation.id, explorer, venue)))
    return completion
  }

  /** The fields a venue changes to reject a completion. */
  private fun rejection() = mapOf("status" to "REJECTED", "rejectReason" to "Blurry photo")

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

  /** A membership of [user] in the party of [reservationId], joined at [joinedAt]. */
  private fun member(
      reservationId: String,
      user: TestUser,
      joinedAt: Timestamp = Timestamp.now(),
  ) =
      mapOf(
          "reservationId" to reservationId,
          "uid" to user.uid,
          "status" to "JOINED",
          "joinedAt" to joinedAt,
          "updatedAt" to joinedAt,
      )

  /**
   * Has each of [friends] join the party of [reservation], signing them in in turn. Leaves the last
   * one signed in.
   */
  private fun joinAll(reservation: DocumentReference, vararg friends: TestUser) {
    for (friend in friends) {
      signInAs(friend)
      assertSucceeds(join(reservation.id, friend))
    }
  }

  /** The createdAt of [reservation], read by the signed-in user. */
  private fun createdAtOf(reservation: DocumentReference) =
      assertSucceeds(reservation.get(Source.SERVER)).getTimestamp("createdAt")!!

  private fun partyCodeDocument(code: String) = db.collection("party_codes").document(code)

  /** A party code no other test uses. */
  private fun newPartyCode() =
      List(PARTY_CODE_LENGTH) { PARTY_CODE_ALPHABET.random() }.joinToString("")

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
    const val PHOTO_URL = "https://example.test/proof.jpg"
    const val TIMEOUT_S = 10L

    /** The characters of a party code: no 0, 1, I, L or O, which are easy to mistake. */
    const val PARTY_CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val PARTY_CODE_LENGTH = 6
  }
}
