// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Bounds a test body, so a missing emission fails the test instead of hanging it. */
const val TIMEOUT_MS = 10_000L

/** Bounds setUp and tearDown; the first connection to the emulator can be slow on CI. */
const val SETUP_TIMEOUT_MS = 15_000L

/** The password of every user [signInNewUser] creates; only the Auth emulator ever sees it. */
private const val PASSWORD = "password123"

/**
 * Runs [block] on the calling thread until it completes, failing the test with a
 * `TimeoutCancellationException` after [TIMEOUT_MS] instead of hanging on a missing emission.
 */
fun withTimeoutBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking {
  withTimeout(TIMEOUT_MS, block)
}

/**
 * Returns a Firestore client of the [FirebaseApp] named [appName], for repository tests on the Auth
 * and Firestore emulators, started with `firebase emulators:start --only auth,firestore --project
 * demo-project`. The app is created on first use.
 *
 * The app is on the `demo-project` id and never the default app, so a test cannot reach the real
 * project. [FirebaseEmulator.connect] only configures the default app, so this reuses its host and
 * ports instead. The client caches in memory only, so a fresh client reads from the server.
 *
 * A test uses one name per role it needs, such as a second user or a client it terminates on
 * purpose, and terminates every client it gets, so no listener or cached data leaks into the next
 * test.
 */
fun emulatorClient(appName: String): FirebaseFirestore =
    FirebaseFirestore.getInstance(emulatorApp(appName)).apply {
      useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.FIRESTORE_PORT)
      firestoreSettings =
          FirebaseFirestoreSettings.Builder()
              .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
              .build() // keep local cache in ram only
    }

/**
 * Creates a fresh user on the Auth emulator and signs them in on the app named [appName], so that
 * app's Firestore client acts as them.
 *
 * @return the new user's uid.
 */
suspend fun signInNewUser(appName: String): String {
  val email = "$appName-${UUID.randomUUID()}@around.test"
  val auth = FirebaseAuth.getInstance(emulatorApp(appName))
  return auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid
}

/**
 * Returns the [FirebaseApp] named [appName] on the `demo-project` id. On first use it creates the
 * app and points its Auth at the emulator; Firestore is pointed per client, since a terminated
 * client is replaced by a new one.
 */
private fun emulatorApp(appName: String): FirebaseApp {
  val context = InstrumentationRegistry.getInstrumentation().targetContext // get app context
  return FirebaseApp.getApps(context).firstOrNull { it.name == appName }
      ?: FirebaseApp.initializeApp(
              context,
              FirebaseOptions.Builder()
                  .setProjectId("demo-project")
                  .setApplicationId("1:1:android:1")
                  .setApiKey("fake-api-key")
                  .build(),
              appName,
          )
          .also {
            FirebaseAuth.getInstance(it)
                .useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
          }
}
