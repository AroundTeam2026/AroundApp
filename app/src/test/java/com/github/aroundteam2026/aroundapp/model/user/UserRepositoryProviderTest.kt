// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserRepositoryProviderTest {
  @Test
  fun sharesOneFirestoreRepositoryAcrossCallers() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Another test in this process may have initialized the default app already
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)

    val first = UserRepositoryProvider.repository
    assertSame(first, UserRepositoryProvider.repository)
    assertTrue(first is UserRepositoryFirestore)
  }
}
