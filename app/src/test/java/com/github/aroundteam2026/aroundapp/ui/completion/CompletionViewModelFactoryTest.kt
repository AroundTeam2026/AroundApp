// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.location.FakeLocationRepository
import com.github.aroundteam2026.aroundapp.model.reservation.FakeProofPhotoRepository
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/**
 * DO NOT MERGE, reproduces #393 on CI: the main dispatcher is not paused, so the ViewModel starts
 * on the real Firestore. The location and photo repositories stay fakes, so Play services never
 * starts.
 *
 * [CompletionViewModel.factory] builds the ViewModel from the app's repositories. The main
 * dispatcher is paused, so the ViewModel never starts listening to the real Firestore, and the
 * location and photo repositories are fakes: a real location client would start Play services,
 * whose checks stay cached for the tests that run after this one, such as the map's.
 */
@RunWith(AndroidJUnit4::class)
class CompletionViewModelFactoryTest {
  private val app = ApplicationProvider.getApplicationContext<Application>()

  @Before
  fun setUp() {
    // Another test in this process may have initialized the default app already
    if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app)
  }

  // Built from a screen's context, they would keep that screen in memory as long as the ViewModel
  @Test
  fun itBuildsAFormThatStartsLoadingWithRepositoriesOfTheApplication() {
    val builtFrom = mutableListOf<Context>()
    val factory =
        CompletionViewModel.factory(
            "reservation-1",
            "explorer",
            locations = {
              builtFrom += it
              FakeLocationRepository(null)
            },
            photos = {
              builtFrom += it
              FakeProofPhotoRepository()
            },
        )

    val viewModel =
        factory.create(
            CompletionViewModel::class.java,
            MutableCreationExtras().apply { set(APPLICATION_KEY, app) },
        )

    // Lets the ViewModel's listeners run, as a screen would
    ShadowLooper.idleMainLooper()
    viewModel.uiState.value
    assertEquals(2, builtFrom.size)
    builtFrom.forEach { assertSame(app, it) }
  }
}
