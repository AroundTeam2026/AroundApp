// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.completion

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [CompletionViewModel.factory] builds the ViewModel from the app's repositories. The main
 * dispatcher stays paused, so the ViewModel never starts: started, it would query the real
 * Firestore, which fails on the unit tests' classpath and posts its "Internal error" to the main
 * thread, where it fails whichever test runs next (#393).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CompletionViewModelFactoryTest {
  private val app = ApplicationProvider.getApplicationContext<Application>()

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
    // Another test in this process may have initialized the default app already
    if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app)
  }

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun itBuildsAFormThatStartsLoading() {
    val viewModel =
        CompletionViewModel.factory("reservation-1", "explorer")
            .create(
                CompletionViewModel::class.java,
                MutableCreationExtras().apply { set(APPLICATION_KEY, app) },
            )

    assertEquals(CompletionFormStatus.LOADING, viewModel.uiState.value.status)
  }
}
