// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.party

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests drawing, checking and normalizing party codes, which must match firestore.rules. */
class PartyCodeTest {
  /** Bug: a code has the wrong length or a character the rules refuse, e.g. 0, 1, I, L or O. */
  @Test
  fun newPartyCode_drawsOnlyCodes() {
    val random = Random(SEED)

    repeat(DRAWS) {
      val code = newPartyCode(random)
      assertTrue("Not a party code: $code", isPartyCode(code))
    }
  }

  /** Bug: the draw is off by one, so a character of the alphabet never appears. */
  @Test
  fun newPartyCode_usesEveryCharacterOfTheAlphabet() {
    val random = Random(SEED)

    val used = List(DRAWS) { newPartyCode(random) }.joinToString("").toSet()

    assertEquals(PARTY_CODE_ALPHABET.toSet(), used)
  }

  /** Bug: a code typed in lower case or with spaces between its groups is not found. */
  @Test
  fun normalizePartyCode_uppercasesAndRemovesWhitespace() {
    for (typed in listOf(TYPED, " $TYPED ", TYPED.replace(" ", "\t"), STORED.lowercase())) {
      assertEquals(typed, STORED, normalizePartyCode(typed))
    }
  }

  /** Bug: something that can't be a code is looked up, or a lookalike character is accepted. */
  @Test
  fun normalizePartyCode_refusesWhatIsNotACode() {
    val notCodes =
        listOf("", STORED.dropLast(1), STORED + PARTY_CODE_ALPHABET.first(), "7KQ-42") +
            LOOKALIKES.map { STORED.dropLast(1) + it }

    for (typed in notCodes) assertNull(typed, normalizePartyCode(typed))
  }

  private companion object {
    /** Fixes the random draws, so the tests always see the same codes. */
    const val SEED = 2026

    /** Enough draws for every character to appear many times. */
    const val DRAWS = 1_000

    /** A code as stored. */
    const val STORED = "7KQM42"

    /** [STORED] as an explorer may type it: in lower case and in two groups. */
    const val TYPED = "7kq m42"

    /** Characters left out of codes because they are easy to mistake for one another. */
    const val LOOKALIKES = "01ILO"
  }
}
