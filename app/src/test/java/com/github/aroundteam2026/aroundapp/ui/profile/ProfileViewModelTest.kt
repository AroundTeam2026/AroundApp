// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileViewModelTest {
  @Test
  fun signOutClearsTheSharedSession() {
    val repository = FakeAuthRepository("explorer")
    val store = ViewModelStore()
    try {
      val viewModel =
          ViewModelProvider(store, ProfileViewModel.factory(repository))[
              ProfileViewModel::class.java]
      viewModel.signOut()
      assertNull(repository.currentUserId.value)
    } finally {
      store.clear()
    }
  }
}
