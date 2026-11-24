// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerUserTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val built = mutableListOf<String>()
  private val perUser = PerUser { _, uid -> Any().also { built += uid } }

  @Test
  fun aUserGetsTheSameOneEveryTimeAndItIsBuiltOnce() {
    assertSame(perUser.get(context, "ada"), perUser.get(context, "ada"))
    assertEquals(listOf("ada"), built)
  }

  @Test
  fun anotherUserGetsItsOwn() {
    assertNotSame(perUser.get(context, "ada"), perUser.get(context, "grace"))
  }

  // Only the latest user is kept: the previous one is dropped from memory
  @Test
  fun aUserWhoComesBackAfterAnotherGetsANewOne() {
    val first = perUser.get(context, "ada")
    perUser.get(context, "grace")

    assertNotSame(first, perUser.get(context, "ada"))
  }

  @Test
  fun theBuilderGetsTheApplicationContextAndTheUser() {
    var seen: Pair<Context, String>? = null
    val holder = PerUser { c, uid -> Any().also { seen = c to uid } }

    holder.get(context, "ada")

    assertEquals(context.applicationContext to "ada", seen)
  }

  @Test
  fun resetStartsOver() {
    val before = perUser.get(context, "ada")

    perUser.reset()

    assertNotSame(before, perUser.get(context, "ada"))
  }
}
