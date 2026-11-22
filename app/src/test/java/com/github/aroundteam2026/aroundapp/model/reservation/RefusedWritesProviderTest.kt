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

  // Signing out: the next explorer on the phone starts clean, and so does this one signing in again
  @Test
  fun forgettingAnExplorerEmptiesWhatThePhoneRemembersOfThem() {
    RefusedWritesProvider.refusedWrites(context, "grace").record("r2", "other")
    val before = RefusedWritesProvider.refusedWrites(context, "ada")
    before.record("r1", "quest")

    RefusedWritesProvider.forget(context, "ada")

    val after = RefusedWritesProvider.refusedWrites(context, "ada")
    assertNotSame(before, after)
    assertEquals(
        emptyList<RecordedWrite>(),
        RefusedWritesStoreSharedPrefs(context, "ada") { System.currentTimeMillis() }.all(),
    )
    assertEquals(
        1,
        RefusedWritesStoreSharedPrefs(context, "grace") { System.currentTimeMillis() }.all().size,
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

    val remembered =
        RefusedWritesStoreSharedPrefs(context, "ada") { System.currentTimeMillis() }.all()

    assertEquals(listOf(RecordedWrite("r1", "quest", WriteState.WRITTEN)), remembered)
  }
}
