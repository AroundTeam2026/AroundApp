// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venue

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository

/** Builds the venue Profile tab's ViewModel for [venueId], signed in, on fakes. */
fun venueProfileFactoryOnFakes(venueId: String = "venue"): ViewModelProvider.Factory =
    viewModelFactory {
      initializer {
        VenueProfileViewModel(
            FakeAuthRepository(venueId),
            FakeVenueRepository(listOf(testVenue(id = venueId))),
        )
      }
    }
