// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.model.quest.RecordedQuestWrite
import com.github.aroundteam2026.aroundapp.model.quest.RefusedQuestWritesStoreSharedPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VenueSignOutTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  // A second venue on the same phone must not be told about the first venue's refusal
  @Test
  fun signingOutForgetsTheVenuesRefusalsAndSignsOut() {
    val store = RefusedQuestWritesStoreSharedPrefs(context, VENUE)
    store.put(RecordedQuestWrite("q1", updatedAt = 2_000L, writtenAt = 9L, refused = true))
    var signedOut = false

    venueSignOut(context, VENUE) { signedOut = true }

    assertEquals(
        emptyList<RecordedQuestWrite>(),
        RefusedQuestWritesStoreSharedPrefs(context, VENUE).all(),
    )
    assertTrue(signedOut)
  }

  private companion object {
    const val VENUE = "cafe"
  }
}
