// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository

/**
 * Builds the real create-quest form on in-memory repositories, for navigation tests: the app's own
 * form would read Firestore.
 */
fun createQuestFactoryOnFakes(): ViewModelProvider.Factory = viewModelFactory {
  initializer {
    CreateQuestViewModel(FakeAuthRepository(), FakeVenueRepository(), FakeQuestRepository())
  }
}
