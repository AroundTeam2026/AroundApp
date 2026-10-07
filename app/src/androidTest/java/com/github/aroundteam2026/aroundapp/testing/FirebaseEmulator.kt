// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.testing

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/**
 * Points Firebase Auth and Firestore at the local emulators, for instrumented tests.
 *
 * Firebase accepts this only once per process and before its first use, so every test that talks to
 * Firebase calls [connect] in its setup. Start the emulators first with `firebase emulators:start`;
 * the ports match firebase.json.
 */
object FirebaseEmulator {
  /** The host machine as seen from the Android emulator. */
  const val HOST = "10.0.2.2"
  const val AUTH_PORT = 9099
  const val FIRESTORE_PORT = 8080

  private var connected = false

  @Synchronized
  fun connect() {
    if (connected) return
    Firebase.auth.useEmulator(HOST, AUTH_PORT)
    Firebase.firestore.useEmulator(HOST, FIRESTORE_PORT)
    connected = true
  }
}
