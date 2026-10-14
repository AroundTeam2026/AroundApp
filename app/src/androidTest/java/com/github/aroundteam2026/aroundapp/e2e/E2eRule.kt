// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.e2e

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import com.github.aroundteam2026.aroundapp.MainActivity
import com.github.aroundteam2026.aroundapp.testing.FirebaseEmulator
import com.github.aroundteam2026.aroundapp.testing.clearFirestore
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Starts the app signed out against the Firebase emulators, on an empty Firestore, for end-to-end
 * tests. Use it as the test's only rule and drive the app through [composeTestRule].
 *
 * `createAndroidComposeRule<MainActivity>()` launches the activity before `@Before` runs, so
 * connecting to the emulators there would be too late: the app would already use the real Firebase
 * project. This rule connects, clears the app's project on the Firestore emulator and signs out
 * first, grants the location permission (otherwise the map's permission dialog covers the app), and
 * only then launches [MainActivity].
 */
class E2eRule : TestRule {
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  override fun apply(base: Statement, description: Description): Statement =
      RuleChain.outerRule(
              object : ExternalResource() {
                override fun before() {
                  FirebaseEmulator.connect()
                  // What earlier runs saved, such as quests where a test seeds its own, would
                  // change what the app shows
                  clearFirestore(Firebase.app.options.projectId!!)
                  Firebase.auth.signOut()
                }
              }
          )
          .around(GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
          .around(composeTestRule)
          .apply(base, description)
}
