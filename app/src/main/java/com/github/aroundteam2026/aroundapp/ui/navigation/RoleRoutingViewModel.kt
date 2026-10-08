// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.UserRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/** Access is resolved from the authenticated account and its saved profile role. */
sealed interface RoleSessionState {
  data object Checking : RoleSessionState

  data object SignedOut : RoleSessionState

  /** A missing profile or unset role requires role selection. */
  data class SignedIn(val userId: String, val role: Role? = null) : RoleSessionState

  /** A failed profile read must never be treated as a missing role. */
  data class ProfileUnavailable(val userId: String) : RoleSessionState
}

/**
 * Observes the provider's session and cancels the previous profile subscription on account change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleRoutingViewModel(authRepository: AuthRepository, userRepository: UserRepository) :
    ViewModel() {
  private val retry = MutableStateFlow(0L)
  val uiState: StateFlow<RoleSessionState> =
      combine(authRepository.currentUserId, retry) { uid, _ -> uid }
          .transformLatest { uid ->
            if (uid == null) {
              emit(RoleSessionState.SignedOut)
            } else {
              emit(RoleSessionState.Checking)
              try {
                userRepository.observeUser(uid).collect { user ->
                  emit(RoleSessionState.SignedIn(uid, user?.role))
                }
              } catch (error: CancellationException) {
                throw error
              } catch (_: Exception) {
                emit(RoleSessionState.ProfileUnavailable(uid))
              }
            }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, RoleSessionState.Checking)

  /** Retries profile resolution without discarding the provider's authenticated session. */
  fun retryProfile() {
    retry.update { it + 1 }
  }

  companion object {
    /** Creates the routing ViewModel using repository interfaces. */
    fun factory(
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { RoleRoutingViewModel(authRepository, userRepository) }
    }
  }
}
