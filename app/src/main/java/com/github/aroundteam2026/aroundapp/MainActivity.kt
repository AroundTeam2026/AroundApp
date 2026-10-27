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
import com.github.aroundteam2026.aroundapp.model.user.UserRepositoryProvider
import com.github.aroundteam2026.aroundapp.model.venue.VenueRepositoryProvider
import com.github.aroundteam2026.aroundapp.ui.navigation.AroundApp
import com.github.aroundteam2026.aroundapp.ui.navigation.RoleNavigation
import com.github.aroundteam2026.aroundapp.ui.navigation.VenueEntry
import com.github.aroundteam2026.aroundapp.ui.profile.ProfileViewModel
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val auth = AuthRepositoryProvider.repository
      val profile: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(auth))
      val pendingRole = remember { PendingRoleStoreSharedPrefs(applicationContext) }
      AroundAppTheme {
        RoleNavigation(
            authRepository = auth,
            userRepository = UserRepositoryProvider.repository,
            pendingRoleStore = pendingRole,
            explorerScreen = { uid -> AroundApp(explorerUid = uid, onSignOut = profile::signOut) },
            venueScreen = { uid ->
              VenueEntry(uid, auth, VenueRepositoryProvider.repository, profile::signOut)
            },
        )
      }
    }
  }
}
