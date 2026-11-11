// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.user.PendingRoleStoreSharedPrefs
import com.github.aroundteam2026.aroundapp.model.user.UserPictureRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.RoleNavigation
import com.github.aroundteam2026.aroundapp.ui.navigation.VenueEntry
import com.github.aroundteam2026.aroundapp.ui.profile.ExplorerProfileSetupGate
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileViewModel
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme

/** Entry activity: shows the explorer or venue app depending on the signed-in role. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val auth = AuthRepositoryProvider.repository
      val profile: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(auth))
      val pendingRole = remember { PendingRoleStoreSharedPrefs(applicationContext) }
      val users = UserRepositoryProvider.repository
      val pictures = remember { UserPictureRepositoryProvider.repository(applicationContext) }
      AroundAppTheme {
        RoleNavigation(
            authRepository = auth,
            userRepository = users,
            pendingRoleStore = pendingRole,
            explorerScreen = { uid ->
              ExplorerProfileSetupGate(uid, users, pictures, profile::signOut) {
                AroundApp(explorerUid = uid, onSignOut = profile::signOut)
              }
            },
            venueScreen = { uid ->
              VenueEntry(uid, auth, VenueRepositoryProvider.repository, profile::signOut)
            },
        )
      }
    }
  }
}
