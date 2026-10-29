// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class DashboardReservationRowTest {
  @get:Rule val compose = createComposeRule()

  private var approvals = 0
  private var declines = 0

  private fun show(reservation: DashboardReservation) {
    compose.setContent {
      AroundAppTheme {
        DashboardReservationRow(
            reservation,
            onDecline = { declines++ },
            onApprove = { approvals++ },
            clock = CLOCK,
        )
      }
    }
  }

  private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun SemanticsNodeInteraction.text() =
      fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  @Test
  fun aPendingRowShowsItsQuestPartyAndButtons() {
    show(pending(partySize = 3))

    compose.onNodeWithTag("${C.Tag.DASHBOARD_ROW}_a").assertIsDisplayed()
    assertEquals("Find the hidden fox", node(C.Tag.DASHBOARD_ROW_TITLE).text())
    assertTrue(node(C.Tag.DASHBOARD_ROW_REQUESTED).text().startsWith("Requested "))
    assertEquals("Yusuf S.", node(C.Tag.DASHBOARD_ROW_EXPLORER).text())
    node(C.Tag.DASHBOARD_ROW_LEADER).assertExists()
    assertEquals("Party of 3", node(C.Tag.DASHBOARD_ROW_PARTY).text())
    node(C.Tag.DASHBOARD_ROW_DECLINE).assertIsDisplayed()
    node(C.Tag.DASHBOARD_ROW_APPROVE).assertIsDisplayed()
    node(C.Tag.DASHBOARD_ROW_APPROVED).assertDoesNotExist()
  }

  @Test
  fun theButtonsCallBack() {
    show(pending())

    node(C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    node(C.Tag.DASHBOARD_ROW_DECLINE).performClick()
    node(C.Tag.DASHBOARD_ROW_DECLINE).performClick()

    assertEquals(1, approvals)
    assertEquals(2, declines)
  }

  @Test
  fun anApprovedRowShowsItsPillButNoButtons() {
    show(pending().copy(approved = true))

    node(C.Tag.DASHBOARD_ROW_APPROVED).assertIsDisplayed()
    node(C.Tag.DASHBOARD_ROW_APPROVE).assertDoesNotExist()
    node(C.Tag.DASHBOARD_ROW_DECLINE).assertDoesNotExist()
  }

  @Test
  fun aSoloVisitHasNoLeaderTag() {
    show(pending(partySize = 1))

    assertEquals(string(R.string.dashboard_party_solo), node(C.Tag.DASHBOARD_ROW_PARTY).text())
    node(C.Tag.DASHBOARD_ROW_LEADER).assertDoesNotExist()
  }

  @Test
  fun anUnknownQuestAndExplorerShowPlaceholders() {
    show(pending().copy(questTitle = null, explorerName = null))

    assertEquals(string(R.string.dashboard_unknown_quest), node(C.Tag.DASHBOARD_ROW_TITLE).text())
    assertEquals(
        string(R.string.dashboard_unknown_explorer),
        node(C.Tag.DASHBOARD_ROW_EXPLORER).text(),
    )
  }

  @Test
  fun aRequestUnderAMinuteOldIsJustNow() {
    show(pending().copy(requestedAt = NOW - 30_000))

    assertEquals(
        string(R.string.dashboard_requested_just_now),
        node(C.Tag.DASHBOARD_ROW_REQUESTED).text(),
    )
  }

  private companion object {
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
    val NOW: Long = LocalDateTime.of(2026, 10, 28, 14, 0).atZone(ZONE).toInstant().toEpochMilli()
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZONE)

    fun pending(partySize: Int = 2) =
        DashboardReservation(
            reservationId = "a",
            questTitle = "Find the hidden fox",
            explorerName = "Yusuf S.",
            partySize = partySize,
            requestedAt = NOW - 12 * 60_000,
            approved = false,
        )
  }
}
