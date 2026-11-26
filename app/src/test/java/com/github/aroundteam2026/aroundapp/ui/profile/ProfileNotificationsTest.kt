// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.connectivity.FakeConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserPictureRepository
import com.github.aroundteam2026.aroundapp.model.user.FakeUserRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.venue.VenueProfileContent
import com.github.aroundteam2026.aroundapp.ui.venue.VenueProfileStatus
import com.github.aroundteam2026.aroundapp.ui.venue.VenueProfileUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ProfileNotificationsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Asserts that the Notifications row shows above "Sign out". */
  private fun assertRowAboveSignOut() {
    compose.onNodeWithText("Notifications").assertIsDisplayed()
    compose.onNodeWithText("Choose which ones Around sends").assertIsDisplayed()
    val row = compose.onNodeWithTag(C.Tag.PROFILE_NOTIFICATIONS).getUnclippedBoundsInRoot()
    val signOut = compose.onNodeWithTag(C.Tag.PROFILE_SIGN_OUT).getUnclippedBoundsInRoot()
    assertTrue(row.bottom <= signOut.top)
  }

  @Test
  fun theRowOpensAndroidsNotificationSettingsForAround() {
    compose.setContent { AroundAppTheme { NotificationsCard() } }

    compose.onNodeWithTag(C.Tag.PROFILE_NOTIFICATIONS).performClick()

    val opened = shadowOf(compose.activity).nextStartedActivity
    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
    assertEquals(compose.activity.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))
  }

  @Test
  fun withoutThatScreenItOpensTheAppsDetails() {
    val opened = mutableListOf<Intent>()
    val phone =
        object : ContextWrapper(compose.activity) {
          override fun startActivity(intent: Intent) {
            if (intent.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
              throw ActivityNotFoundException("No notification settings")
            }
            opened += intent
          }
        }

    openNotificationSettings(phone)

    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.single().action)
    assertEquals(Uri.fromParts("package", phone.packageName, null), opened.single().data)
  }

  @Test
  fun theExplorersProfileHasTheRow() {
    val users = FakeUserRepository()
    val pictures = FakeUserPictureRepository()
    runBlocking { users.createUser(User("explorer", "Ada", Role.EXPLORER, 1L)).getOrThrow() }
    val model = ProfileSetupViewModel("explorer", users, pictures, FakeConnectivityRepository())
    compose.setContent { AroundAppTheme { ProfileScreen({}, model, pictures) } }
    compose.waitUntil(5_000) { model.destination.value == ProfileSetupDestination.HOME }

    assertRowAboveSignOut()
  }

  @Test
  fun theVenuesProfileHasTheRow() {
    compose.setContent {
      AroundAppTheme {
        VenueProfileContent(
            VenueProfileUiState(VenueProfileStatus.LOADED, "Café Lumière", "Rue de Bourg 12"),
            onSignOut = {},
            onRetry = {},
        )
      }
    }

    assertRowAboveSignOut()
  }

  @Test
  fun theVenueReachesItEvenWhenItsVenueCantBeRead() {
    compose.setContent {
      AroundAppTheme {
        VenueProfileContent(
            VenueProfileUiState(VenueProfileStatus.FAILED),
            onSignOut = {},
            onRetry = {},
        )
      }
    }

    assertRowAboveSignOut()
  }
}
