// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepositoryProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exposes whether either home shell should show its offline banner. */
class OfflineBannerViewModel(repository: ConnectivityRepository) : ViewModel() {
  // Keep the banner hidden until the repository reports the current connection state.
  val showBanner: StateFlow<Boolean> =
      repository.isOnline
          .map { online -> !online }
          .stateIn(
              viewModelScope,
              SharingStarted.Eagerly,
              false,
          )

  companion object {
    /**
     * Builds the ViewModel on [repository], or on the app's connectivity from [context] when it's
     * null. The context is passed in rather than read from the creation extras: the shells are
     * composed above their NavHost, under the signed-in session's ViewModel owner, which supplies
     * no extras (see `SessionViewModel.signedInStoreOwner`).
     */
    fun factory(
        context: Context,
        repository: ConnectivityRepository? = null,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        OfflineBannerViewModel(repository ?: ConnectivityRepositoryProvider.repository(context))
      }
    }
  }
}
