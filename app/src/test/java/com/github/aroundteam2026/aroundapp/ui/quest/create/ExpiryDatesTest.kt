// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.material3.ExperimentalMaterial3Api
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ExpiryDatesTest {
  private val today = LocalDate.of(2026, 10, 14)
  private val dates = selectableFrom(today)

  @Test
  fun daysFromTodayOnCanBePickedButNotEarlierOnes() {
    assertFalse(dates.isSelectableDate(today.minusDays(1).toUtcMillis()))
    assertTrue(dates.isSelectableDate(today.toUtcMillis()))
    assertTrue(dates.isSelectableDate(today.plusDays(1).toUtcMillis()))
  }

  @Test
  fun yearsFromThisOneOnCanBePickedButNotEarlierOnes() {
    assertFalse(dates.isSelectableYear(2025))
    assertTrue(dates.isSelectableYear(2026))
    assertTrue(dates.isSelectableYear(2027))
  }
}
