// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.notification.NotificationPromptStoreSharedPrefs
import com.github.aroundteam2026.aroundapp.model.quest.RefusedQuestWritesProvider
import com.github.aroundteam2026.aroundapp.model.reservation.ProofUploadQueueProvider
import com.github.aroundteam2026.aroundapp.model.reservation.RefusedWritesProvider
import com.github.aroundteam2026.aroundapp.model.user.PendingRoleStoreSharedPrefs
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.RoleNavigation
import com.github.aroundteam2026.aroundapp.ui.navigation.VenueEntry
import com.github.aroundteam2026.aroundapp.ui.notification.NotificationPermissionGate
import com.github.aroundteam2026.aroundapp.ui.profile.ExplorerProfileSetupGate
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileScreen
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileViewModel
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme

/** Entry activity: shows the explorer or venue app depending on the signed-in role. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val auth = AuthRepositoryProvider.repository
      val proofUploadQueue = remember { ProofUploadQueueProvider.queue(applicationContext) }
      val profile: ProfileViewModel =
          viewModel(factory = ProfileViewModel.factory(auth, proofUploadQueue))
      val pendingRole = remember { PendingRoleStoreSharedPrefs(applicationContext) }
      val users = UserRepositoryProvider.repository
      val pictures = remember { UserPictureRepositoryProvider.repository(applicationContext) }
      val venuePictures = remember {
        UserPictureRepositoryProvider.venueRepository(applicationContext)
      }
      val connectivity = remember { ConnectivityRepositoryProvider.repository(applicationContext) }
      val notificationPrompt = remember { NotificationPromptStoreSharedPrefs(applicationContext) }
      AroundAppTheme {
        RoleNavigation(
            authRepository = auth,
            userRepository = users,
            pendingRoleStore = pendingRole,
            explorerScreen = { uid ->
              val signOut = {
                RefusedWritesProvider.forget(applicationContext, uid)
                profile.signOut()
              }
              ExplorerProfileSetupGate(uid, users, pictures, connectivity, signOut) { setup ->
                NotificationPermissionGate(Role.EXPLORER, notificationPrompt) {
                  AroundApp(
                      explorerUid = uid,
                      onSignOut = signOut,
                      profileScreen = { ProfileScreen(signOut, setup, pictures) },
                  )
                }
              }
            },
            venueScreen = { uid ->
              NotificationPermissionGate(Role.VENUE, notificationPrompt) {
                VenueEntry(
                    uid,
                    auth,
                    VenueRepositoryProvider.repository,
                    onSignOut = { venueSignOut(applicationContext, uid, profile::signOut) },
                    venuePictures = venuePictures,
                )
              }
            },
        )
      }
    }
  }
}

/**
 * Signs the venue [venueUid] out with [signOut], after forgetting the quest writes this phone
 * remembers of it, so a second venue signing in on the same phone isn't told about a write that
 * wasn't theirs.
 */
internal fun venueSignOut(context: Context, venueUid: String, signOut: () -> Unit) {
  RefusedQuestWritesProvider.forget(context, venueUid)
  signOut()
}
