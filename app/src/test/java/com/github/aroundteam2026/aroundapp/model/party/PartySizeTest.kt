// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import com.github.aroundteam2026.aroundapp.model.testQuest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [partyCapacity] and [meetsPartySize], which follow the rules' functions of those names. */
class PartySizeTest {
  private val atLeast = testQuest(minPartySize = MIN_SIZE)
  private val exact = atLeast.copy(exactPartySize = true)

  @Test
  fun partyCapacity_ofAnAtLeastQuest_isTheMaximum() {
    assertEquals(MAX_PARTY_SIZE, atLeast.partyCapacity)
  }

  @Test
  fun partyCapacity_ofAnExactQuest_isItsSize() {
    assertEquals(MIN_SIZE, exact.partyCapacity)
  }

  /** The quest form doesn't cap the size yet (#157), so the capacity does. */
  @Test
  fun partyCapacity_ofAnExactQuestAboveTheMaximum_isTheMaximum() {
    assertEquals(MAX_PARTY_SIZE, exact.copy(minPartySize = MAX_PARTY_SIZE + 1).partyCapacity)
  }

  @Test
  fun meetsPartySize_ofAnAtLeastQuest_needsAtLeastItsSize() {
    assertFalse(atLeast.meetsPartySize(MIN_SIZE - 1))
    assertTrue(atLeast.meetsPartySize(MIN_SIZE))
    assertTrue(atLeast.meetsPartySize(MIN_SIZE + 1))
  }

  @Test
  fun meetsPartySize_ofAnExactQuest_needsExactlyItsSize() {
    assertFalse(exact.meetsPartySize(MIN_SIZE - 1))
    assertTrue(exact.meetsPartySize(MIN_SIZE))
    assertFalse(exact.meetsPartySize(MIN_SIZE + 1))
  }

  private companion object {
    const val MIN_SIZE = 3
  }
}
