// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.user.FakePendingRoleStore
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.ProfileSetup
import com.github.aroundteam2026.aroundapp.model.user.ProfileStatus
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.screen.RegistrationScreen
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Rotation must not unlock another registration while Firebase account creation is pending. */
@RunWith(RobolectricTestRunner::class)
class RegistrationRecreationTest {
  @get:Rule val compose = createEmptyComposeRule()

  @Test
  fun rotatingDuringSignUpKeepsTheVenueChoiceAndOnePendingRequest() {
    val delegate = FakeAuthRepository()
    val gate = CompletableDeferred<Unit>()
    var signUps = 0
    val auth =
        object : AuthRepository by delegate {
          override suspend fun signUpWithEmail(email: String, password: String): Result<String> {
            signUps++
            gate.await()
            return delegate.signUpWithEmail(email, password)
          }
        }
    val pending = FakePendingRoleStore()
    val setup = ProfileSetup(auth, FakeUserRepository(), pending)
    RegistrationRecreationActivity.content = {
      val uid by auth.currentUserId.collectAsState()
      var ready by remember { mutableStateOf<Role?>(null) }
      LaunchedEffect(uid) {
        if (uid != null) ready = (setup.finish().getOrThrow() as ProfileStatus.Ready).role
      }
      AroundAppTheme {
        if (ready != null) Text("Ready: $ready")
        else RegistrationFlow(auth, setup, onAccountSetupComplete = {})
      }
    }
    val controller = Robolectric.buildActivity(RegistrationRecreationActivity::class.java)
    try {
      controller.setup()
      RegistrationScreen(compose).registerVenue("venue@around.test", "123456")
      compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).assertIsNotEnabled()
      val before = controller.get()
      compose.runOnUiThread {
        controller.configurationChange(
            Configuration(before.resources.configuration).apply {
              orientation = Configuration.ORIENTATION_LANDSCAPE
            }
        )
        // Robolectric recreates the activity without attaching its window.
        controller.visible()
      }
      assertNotSame(before, controller.get())
      compose.onNodeWithTag(C.Tag.AUTH_TITLE).assertTextContains("Create account")
      compose.onNodeWithTag(C.Tag.AUTH_EMAIL).assertTextContains("venue@around.test")
      compose.onNodeWithTag(C.Tag.AUTH_SUBMIT).assertIsNotEnabled().performClick()
      compose.onNodeWithTag(C.Tag.AUTH_SWITCH_MODE).assertIsNotEnabled()
      assertEquals(1, signUps)
      assertEquals(Role.VENUE, pending.pending?.role)
      compose.runOnUiThread { gate.complete(Unit) }
      compose.onNodeWithText("Ready: VENUE").assertIsDisplayed()
      assertEquals(1, signUps)
    } finally {
      controller.pause().stop().destroy()
      RegistrationRecreationActivity.content = null
    }
  }
}

class RegistrationRecreationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { content?.invoke() }
  }

  companion object {
    var content: (@Composable () -> Unit)? = null
  }
}
