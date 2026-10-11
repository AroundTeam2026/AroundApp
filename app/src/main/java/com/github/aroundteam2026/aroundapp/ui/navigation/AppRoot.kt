// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Root destinations selected by the caller; session and role decisions live outside [AppRoot]. */
enum class AppDestination {
  Loading,
  Auth,
  RoleSelection,
  ExplorerHome,
  VenueEntry,
}

/**
 * Renders exactly the slot selected by [destination], without owning navigation or session state.
 * Slots may contain placeholders or independent navigation graphs, including the existing tabs.
 *
 * @param destination selects the only slot to compose.
 * @param authFlow content for signing in or creating an account.
 * @param roleSelection content for choosing a user role.
 * @param explorerHome content for the explorer's home, including tab navigation.
 * @param venueEntry content for entering the venue flow.
 * @param loading content while the caller resolves the destination; defaults to a centered spinner.
 */
@Composable
fun AppRoot(
    destination: AppDestination,
    authFlow: @Composable () -> Unit,
    roleSelection: @Composable () -> Unit,
    explorerHome: @Composable () -> Unit,
    venueEntry: @Composable () -> Unit,
    loading: @Composable () -> Unit = {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    },
) {
  when (destination) {
    AppDestination.Loading -> loading()
    AppDestination.Auth -> authFlow()
    AppDestination.RoleSelection -> roleSelection()
    AppDestination.ExplorerHome -> explorerHome()
    AppDestination.VenueEntry -> venueEntry()
  }
}
