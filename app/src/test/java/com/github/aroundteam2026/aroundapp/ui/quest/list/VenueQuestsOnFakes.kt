// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.list

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Builds the real Quests tab on in-memory repositories, for navigation tests: the app's own tab
 * would read Firestore.
 */
fun venueQuestsFactoryOnFakes(): ViewModelProvider.Factory = viewModelFactory {
  initializer { VenueQuestsViewModel(FakeAuthRepository(), FakeQuestRepository()) }
}

/**
 * [quests], except that the first listener to a venue's quests fails, as when the backend stops it;
 * later listeners work.
 */
fun failingFirstListen(quests: FakeQuestRepository): QuestRepository =
    object : QuestRepository by quests {
      private var listens = 0

      override fun observeQuestsByVenue(venueId: String): Flow<List<Quest>> =
          if (listens++ == 0) flow { throw IOException() } else quests.observeQuestsByVenue(venueId)
    }
