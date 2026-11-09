// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class DashboardProofRowTest {
  @get:Rule val compose = createComposeRule()

  private var opens = 0

  private fun show(proof: DashboardProof, clock: Clock = CLOCK) {
    compose.setContent {
      AroundAppTheme { DashboardProofRow(proof, onOpen = { opens++ }, clock = clock) }
    }
  }

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun row() = compose.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_$ID")

  private fun SemanticsNodeInteraction.text() =
      fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  @Test
  fun aPhotoProofShowsItsExplorerQuestPartyAndWhenItWasSent() {
    show(proof())

    row().assertIsDisplayed()
    assertEquals("Yusuf S.", node(C.Tag.DASHBOARD_PROOF_EXPLORER).text())
    assertEquals(
        string(
            R.string.dashboard_proof_quest_and_party,
            "Find the hidden fox",
            context.resources.getQuantityString(R.plurals.dashboard_party_size, 4, 4),
        ),
        node(C.Tag.DASHBOARD_PROOF_DETAILS).text(),
    )
    assertEquals(
        string(R.string.dashboard_proof_sent, string(R.string.dashboard_proof_photo), TWO_MIN_AGO),
        node(C.Tag.DASHBOARD_PROOF_SENT).text(),
    )
    node(C.Tag.DASHBOARD_PROOF_ICON).assertIsDisplayed()
  }

  @Test
  fun aWrittenAnswerOfASoloVisitSaysSo() {
    show(proof().copy(proofType = ProofType.TEXT, partySize = 1))

    assertEquals(
        string(
            R.string.dashboard_proof_quest_and_party,
            "Find the hidden fox",
            string(R.string.dashboard_proof_solo),
        ),
        node(C.Tag.DASHBOARD_PROOF_DETAILS).text(),
    )
    assertEquals(
        string(R.string.dashboard_proof_sent, string(R.string.dashboard_proof_answer), TWO_MIN_AGO),
        node(C.Tag.DASHBOARD_PROOF_SENT).text(),
    )
  }

  @Test
  fun aProofOfAnUnknownReservationShowsPlaceholdersAndNoParty() {
    show(proof().copy(explorerName = null, questTitle = null, partySize = 0))

    assertEquals(
        string(R.string.dashboard_unknown_explorer),
        node(C.Tag.DASHBOARD_PROOF_EXPLORER).text(),
    )
    assertEquals(
        string(R.string.dashboard_unknown_quest),
        node(C.Tag.DASHBOARD_PROOF_DETAILS).text(),
    )
  }

  @Test
  fun aProofUnderAMinuteOldIsJustNow() {
    show(proof().copy(sentAt = NOW - JUST_NOW_AGE_MS))

    assertEquals(
        string(R.string.dashboard_proof_sent_just_now, string(R.string.dashboard_proof_photo)),
        node(C.Tag.DASHBOARD_PROOF_SENT).text(),
    )
  }

  @Test
  fun theSentTimeMovesOnWhileTheRowIsShown() {
    val clock = MovingClock(NOW)
    show(proof().copy(sentAt = NOW - JUST_NOW_AGE_MS), clock)

    clock.now = NOW + DateUtils.MINUTE_IN_MILLIS
    compose.mainClock.advanceTimeBy(DateUtils.MINUTE_IN_MILLIS)

    assertEquals(
        string(R.string.dashboard_proof_sent, string(R.string.dashboard_proof_photo), ONE_MIN_AGO),
        node(C.Tag.DASHBOARD_PROOF_SENT).text(),
    )
  }

  @Test
  fun tappingTheRowOpensTheProof() {
    show(proof())

    row().assertHasClickAction().performClick()

    assertEquals(1, opens)
  }

  @Test
  fun thePreviewShowsAPhotoAndAWrittenAnswer() {
    compose.setContent { DashboardProofRowPreview() }

    compose.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_photo").assertIsDisplayed()
    compose.onNodeWithTag("${C.Tag.DASHBOARD_PROOF_ROW}_text").assertIsDisplayed()
  }

  /** A clock the test moves on by hand, like the phone's while the dashboard stays open. */
  private class MovingClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZONE

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(now)
  }

  private companion object {
    const val ID = "r1_yusuf"
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
    val NOW: Long = LocalDateTime.of(2026, 10, 28, 14, 0).atZone(ZONE).toInstant().toEpochMilli()
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZONE)
    const val JUST_NOW_AGE_MS = 30_000L

    // Android's English relative times, which the row puts after "sent"
    const val TWO_MIN_AGO = "2 min. ago"
    const val ONE_MIN_AGO = "1 min. ago"

    fun proof() =
        DashboardProof(
            completionId = ID,
            explorerName = "Yusuf S.",
            questTitle = "Find the hidden fox",
            partySize = 4,
            proofType = ProofType.PHOTO,
            sentAt = NOW - 2 * DateUtils.MINUTE_IN_MILLIS,
        )
  }
}
