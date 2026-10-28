// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository

/**
 * Builds the real Quests tab on in-memory repositories, for navigation tests: the app's own tab
 * would read Firestore.
 */
fun venueQuestsFactoryOnFakes(): ViewModelProvider.Factory = viewModelFactory {
  initializer { VenueQuestsViewModel(FakeAuthRepository(), FakeQuestRepository()) }
}
