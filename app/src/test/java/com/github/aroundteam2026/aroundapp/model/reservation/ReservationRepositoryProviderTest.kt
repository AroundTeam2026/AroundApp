// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReservationRepositoryProviderTest {
  @Before
  fun initializeFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Another test in this process may have initialized the default app already
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
  }

  @Test
  fun servesTheFirestoreRepository() {
    assertTrue(ReservationRepositoryProvider.repository is ReservationRepositoryFirestore)
  }

  @Test
  fun everyCallerGetsTheSameInstance() {
    assertSame(ReservationRepositoryProvider.repository, ReservationRepositoryProvider.repository)
  }
}
