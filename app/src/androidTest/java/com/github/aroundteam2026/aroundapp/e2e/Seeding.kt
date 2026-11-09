// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryFirestore
import com.github.aroundteam2026.aroundapp.model.venue.Venue
import com.github.aroundteam2026.aroundapp.model.venue.VenueLimits.DEFAULT_RADIUS_METERS
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryFirestore
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/** The name of the Firebase app tests seed data through; see [seedApp]. */
private const val SEED_APP = "e2e-seed"

/**
 * A second Firebase app on the app's own project, so its data reaches the app, and on the
 * emulators. It has its own sign-in, so the accounts a test seeds through it never sign the app in:
 * the app starts signed out, as [E2eRule] leaves it.
 */
fun seedApp(): FirebaseApp {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  return FirebaseApp.getApps(context).find { it.name == SEED_APP }
      ?: FirebaseApp.initializeApp(context, Firebase.app.options, SEED_APP).also {
        Firebase.auth(it).useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
        Firebase.firestore(it).useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.FIRESTORE_PORT)
      }
}

/** The venue and quest [seedVenueAndQuest] saved: the venue's id is its owner's uid. */
data class SeededQuest(val venueId: String, val questId: String)

/**
 * Creates the venue account [email] through [app], as a fresh venue account would: its profile, its
 * venue [venueName] at [location], and an active quest [title] there that asks for [proofType].
 * Waits until the server has them, since the app reads the quest from there, and leaves [app]
 * signed out.
 */
suspend fun seedVenueAndQuest(
    app: FirebaseApp,
    email: String,
    venueName: String,
    title: String,
    location: Location,
    proofType: ProofType,
): SeededQuest {
  val auth = Firebase.auth(app)
  val db = Firebase.firestore(app)
  val uid = auth.createUserWithEmailAndPassword(email, E2E_PASSWORD).await().user!!.uid
  // The rules let only an account with the VENUE role write a venue and its quests
  UserRepositoryFirestore(db).createUser(User(uid, "Test", Role.VENUE, 0L)).getOrThrow()
  VenueRepositoryFirestore(db)
      .createVenue(Venue(uid, venueName, location, DEFAULT_RADIUS_METERS, null, createdAt = 0L))
      .getOrThrow()
  val questId =
      QuestRepositoryFirestore(db)
          .createQuest(
              Quest(
                  id = "",
                  venueId = uid,
                  radiusMeters = QuestLimits.DEFAULT_RADIUS_METERS,
                  title = title,
                  description = "Find the hidden fox",
                  requirements = "Show where the fox is",
                  proofType = proofType,
                  reward = null,
                  status = QuestStatus.ACTIVE,
                  createdAt = 0L,
                  updatedAt = 0L,
              )
          )
          .getOrThrow()
  // createQuest doesn't wait for the server, which is where the app reads the quest from
  db.waitForPendingWrites().await()
  auth.signOut()
  return SeededQuest(uid, questId)
}
