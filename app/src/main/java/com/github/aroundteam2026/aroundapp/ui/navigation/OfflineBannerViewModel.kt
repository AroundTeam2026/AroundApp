// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.connectivity.ConnectivityRepository
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
    fun factory(repository: ConnectivityRepository): ViewModelProvider.Factory = viewModelFactory {
      initializer { OfflineBannerViewModel(repository) }
    }
  }
}
