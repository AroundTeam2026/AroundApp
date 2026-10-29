// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeReservationRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository

/**
 * Builds the real Dashboard tab of the signed-in venue [venueId] on in-memory repositories, for
 * navigation tests: the app's own tab would read Firestore. It has no reservations yet.
 */
fun venueDashboardFactoryOnFakes(venueId: String = "venue"): ViewModelProvider.Factory =
    viewModelFactory {
      initializer {
        VenueDashboardViewModel(
            FakeAuthRepository(venueId),
            FakeReservationRepository(),
            FakeQuestRepository(),
            FakeVenueRepository(listOf(testVenue(id = venueId))),
        )
      }
    }
