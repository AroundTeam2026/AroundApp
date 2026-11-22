// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefusedWritesProviderTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before fun setUp() = RefusedWritesProvider.reset()

  @Test
  fun everyScreenOfAnExplorerGetsTheSameRefusedWrites() {
    assertSame(
        RefusedWritesProvider.refusedWrites(context, "ada"),
        RefusedWritesProvider.refusedWrites(context, "ada"),
    )
  }

  @Test
  fun anotherExplorerGetsItsOwn() {
    val ada = RefusedWritesProvider.refusedWrites(context, "ada")

    val grace = RefusedWritesProvider.refusedWrites(context, "grace")

    assertNotSame(ada, grace)
  }

  // What it learns is kept on the phone, under the explorer, for the next time the app opens
  @Test
  fun whatItLearnsIsKeptInThePhonesPreferences() {
    RefusedWritesProvider.refusedWrites(context, "ada").record("r1", "quest")

    val remembered = RefusedWritesStoreSharedPrefs(context, "ada").all()

    assertEquals(listOf(RecordedWrite("r1", "quest", WriteState.WRITTEN)), remembered)
  }
}
