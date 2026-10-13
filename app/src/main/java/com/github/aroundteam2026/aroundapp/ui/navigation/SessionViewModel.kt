// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Authentication determines access to the main graph; role routing is added separately. */
sealed interface SessionState {
  data object Checking : SessionState

  data object SignedOut : SessionState

  data class SignedIn(val userId: String) : SessionState
}

/** Restores and observes the provider's session without maintaining a separate login flag. */
class SessionViewModel(repository: AuthRepository) : ViewModel() {
  private val signedInStores = mutableMapOf<String, ViewModelStoreOwner>()

  /** Retained through configuration changes; all tab/back-stack ViewModels use this owner. */
  internal fun signedInStoreOwner(userId: String): ViewModelStoreOwner =
      signedInStores.getOrPut(userId) {
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
      }

  /** Called after a settled session is composed, so disposed flows lose their ViewModels. */
  internal fun retainSignedInStore(userId: String?) {
    val oldUsers = signedInStores.keys.filter { it != userId }
    oldUsers.forEach { signedInStores.remove(it)?.viewModelStore?.clear() }
  }

  override fun onCleared() {
    retainSignedInStore(null)
  }

  val uiState: StateFlow<SessionState> =
      repository.currentUserId
          .map { uid -> if (uid == null) SessionState.SignedOut else SessionState.SignedIn(uid) }
          .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Checking)

  companion object {
    /** Creates the routing ViewModel using only the repository interface. */
    fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
      initializer { SessionViewModel(repository) }
    }
  }
}
