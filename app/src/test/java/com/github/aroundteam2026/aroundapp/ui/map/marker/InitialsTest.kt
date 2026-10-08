// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map.marker

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [initialsOf], what the card's avatar shows for a venue without an image. */
class InitialsTest {
  private val defaultLocale = Locale.getDefault()

  @After fun restoreLocale() = Locale.setDefault(defaultLocale)

  @Test
  fun takesTheFirstAndLastWords() {
    // As in the design: Bar des Arches is BA, not BD
    assertEquals("BA", initialsOf("Bar des Arches"))
    assertEquals("CL", initialsOf("Café Lumen"))
  }

  @Test
  fun aOneWordNameGivesOneLetter() {
    assertEquals("S", initialsOf("Satellite"))
  }

  @Test
  fun areUppercase() {
    assertEquals("BN", initialsOf("bar nocturne"))
  }

  @Test
  fun ignoreExtraSpaces() {
    assertEquals("CL", initialsOf("  Café \t  Lumen  "))
  }

  @Test
  fun skipWordsWithoutALetterOrDigit() {
    // An ampersand or an emoji isn't an initial
    assertEquals("FH", initialsOf("Fox & Hound"))
    assertEquals("L", initialsOf("☕ Lumen"))
  }

  @Test
  fun startFromAWordsFirstLetterOrDigit() {
    assertEquals("FA", initialsOf("'Fox' Atelier"))
    assertEquals("7S", initialsOf("7 Seas"))
  }

  @Test
  fun keepAccentedLetters() {
    assertEquals("ÉG", initialsOf("écluse genève"))
  }

  @Test
  fun doNotDependOnTheDevicesLanguage() {
    // In Turkish, "i".uppercase() is a dotted İ, which would change a venue's avatar by phone
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))

    assertEquals("IS", initialsOf("ice station"))
  }

  @Test
  fun aNameWithoutAnyLetterGivesAPlaceholder() {
    assertEquals("?", initialsOf(""))
    assertEquals("?", initialsOf("  & ☕ "))
  }
}
