// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository

/** Profile session actions; navigation observes the repository's session state. */
class ProfileViewModel(private val repository: AuthRepository) : ViewModel() {
  /** Ends the shared auth session; the session observer returns navigation to auth. */
  fun signOut() = repository.signOut()

  companion object {
    /** Supplies the repository interface when Compose creates this ViewModel. */
    fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
      initializer { ProfileViewModel(repository) }
    }
  }
}
