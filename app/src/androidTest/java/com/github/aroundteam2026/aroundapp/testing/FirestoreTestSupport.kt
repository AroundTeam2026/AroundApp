// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** Bounds a test body, so a missing emission fails the test instead of hanging it. */
const val TIMEOUT_MS = 10_000L

/** Bounds setUp and tearDown; the first connection to the emulator can be slow on CI. */
const val SETUP_TIMEOUT_MS = 15_000L

/** The password of every user [signInNewUser] creates; only the Auth emulator ever sees it. */
private const val PASSWORD = "password123"

/** The project id of every emulator app, a `demo-` one so nothing can reach a real project. */
private const val PROJECT_ID = "demo-project"

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
 * app's Firestore client acts as them. With a [role], it also saves their `users/{uid}` profile
 * through the rules, as the app does; the rules only let venue accounts write venues and quests.
 *
 * @return the new user's uid.
 */
suspend fun signInNewUser(appName: String, role: Role? = null): String {
  val email = "$appName-${UUID.randomUUID()}@around.test"
  val auth = FirebaseAuth.getInstance(emulatorApp(appName))
  val uid = auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid
  if (role != null) {
    // Terminated afterwards, so the test's own emulatorClient call gets a fresh client to configure
    val db = emulatorClient(appName)
    try {
      db.collection("users")
          .document(uid)
          .set(mapOf("displayName" to "Test", "role" to role.name, "createdAt" to Timestamp.now()))
          .await()
    } finally {
      db.terminate().await()
    }
  }
  return uid
}

/**
 * Writes [fields] into the document at [path], such as `quests/abc`, on the Firestore emulator
 * without checking firestore.rules, for tests that need a document the rules refuse, such as a
 * malformed one. Other fields of an existing document are kept, and a missing one is created.
 * Values may be strings, timestamps, and lists or maps of those.
 *
 * It uses the emulator's REST API, which skips the rules for the `owner` token. No client sees the
 * write until it reads from the server.
 */
fun writeBypassingRules(path: String, fields: Map<String, Any>) {
  val database = "projects/$PROJECT_ID/databases/(default)"
  val update =
      JSONObject().put("name", "$database/documents/$path").put("fields", restFields(fields))
  val write =
      JSONObject()
          .put("update", update)
          .put("updateMask", JSONObject().put("fieldPaths", JSONArray(fields.keys)))
  val url =
      "http://${FirebaseEmulator.HOST}:${FirebaseEmulator.FIRESTORE_PORT}/v1/$database/documents:commit"
  val connection = URL(url).openConnection() as HttpURLConnection
  try {
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "Bearer owner")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.connectTimeout = TIMEOUT_MS.toInt()
    connection.readTimeout = TIMEOUT_MS.toInt()
    connection.doOutput = true
    connection.outputStream.use {
      it.write(JSONObject().put("writes", JSONArray().put(write)).toString().toByteArray())
    }
    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
      "The emulator refused the write: ${connection.errorStream?.bufferedReader()?.readText()}"
    }
  } finally {
    connection.disconnect()
  }
}

/** [fields] in the REST API's format, which wraps each value in an object naming its type. */
private fun restFields(fields: Map<*, *>): JSONObject =
    JSONObject().apply {
      fields.forEach { (name, value) -> put(name as String, restValue(value!!)) }
    }

private fun restValue(value: Any): JSONObject =
    when (value) {
      is String -> JSONObject().put("stringValue", value)
      is Timestamp ->
          JSONObject()
              .put(
                  "timestampValue",
                  Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()).toString(),
              )
      is List<*> ->
          JSONObject()
              .put(
                  "arrayValue",
                  JSONObject().put("values", JSONArray(value.map { restValue(it!!) })),
              )
      is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", restFields(value)))
      else -> throw IllegalArgumentException("writeBypassingRules can't write $value")
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
                  .setProjectId(PROJECT_ID)
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
