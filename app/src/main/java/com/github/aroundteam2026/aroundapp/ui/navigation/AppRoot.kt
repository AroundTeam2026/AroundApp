// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.aroundteam2026.aroundapp.resources.C

/** Root destinations selected by the caller; session and role decisions live outside [AppRoot]. */
enum class AppDestination {
  LOADING,
  AUTH,
  ROLE_SELECTION,
  EXPLORER_HOME,
  VENUE_ENTRY,

  /** A failed profile read; distinct from a missing role that needs role selection. */
  PROFILE_ERROR,
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
 * @param profileError content for a failed profile read; the caller supplies retry and sign-out.
 * @param loading content while the caller resolves the destination; defaults to a centered spinner.
 */
@Composable
fun AppRoot(
    destination: AppDestination,
    authFlow: @Composable () -> Unit,
    roleSelection: @Composable () -> Unit,
    explorerHome: @Composable () -> Unit,
    venueEntry: @Composable () -> Unit,
    profileError: @Composable () -> Unit,
    loading: @Composable () -> Unit = {
      Box(Modifier.fillMaxSize().testTag(C.Tag.APP_LOADING), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    },
) {
  when (destination) {
    AppDestination.LOADING -> loading()
    AppDestination.AUTH -> authFlow()
    AppDestination.ROLE_SELECTION -> roleSelection()
    AppDestination.EXPLORER_HOME -> explorerHome()
    AppDestination.VENUE_ENTRY -> venueEntry()
    AppDestination.PROFILE_ERROR -> profileError()
  }
}
