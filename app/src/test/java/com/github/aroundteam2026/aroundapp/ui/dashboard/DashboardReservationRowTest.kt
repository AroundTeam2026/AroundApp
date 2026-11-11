// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.dashboard

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
// Draws the pictures of the avatars
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardReservationRowTest {
  @get:Rule val compose = createComposeRule()

  private var approvals = 0
  private var declines = 0

  private fun show(
      reservation: DashboardReservation,
      clock: Clock = CLOCK,
      saving: Boolean = false,
  ) {
    compose.setContent {
      AroundAppTheme {
        DashboardReservationRow(
            reservation,
            onDecline = { declines++ },
            onApprove = { approvals++ },
            saving = saving,
            clock = clock,
        )
      }
    }
  }

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun SemanticsNodeInteraction.text() =
      fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

  /** How many nodes are tagged [tag]. */
  private fun count(tag: String) =
      compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

  private fun members(vararg names: String) = names.map { DashboardMember(it) }

  @Test
  fun aPendingRowShowsItsQuestPartyAndButtons() {
    show(pending(partySize = 3))

    compose.onNodeWithTag("${C.Tag.DASHBOARD_ROW}_a").assertIsDisplayed()
    assertEquals("Find the hidden fox", node(C.Tag.DASHBOARD_ROW_TITLE).text())
    assertEquals(
        string(R.string.dashboard_requested, TWELVE_MIN_AGO),
        node(C.Tag.DASHBOARD_ROW_REQUESTED).text(),
    )
    assertEquals("Yusuf S.", node(C.Tag.DASHBOARD_ROW_EXPLORER).text())
    node(C.Tag.DASHBOARD_ROW_LEADER).assertExists()
    assertEquals(
        context.resources.getQuantityString(R.plurals.dashboard_party_size, 3, 3),
        node(C.Tag.DASHBOARD_ROW_PARTY).text(),
    )
    node(C.Tag.DASHBOARD_ROW_DECLINE).assertIsDisplayed().assertIsEnabled()
    node(C.Tag.DASHBOARD_ROW_APPROVE).assertIsDisplayed().assertIsEnabled()
    node(C.Tag.DASHBOARD_ROW_APPROVED).assertDoesNotExist()
  }

  @Test
  fun aRowWhoseAnswerIsSavingDisablesBothButtons() {
    show(pending(), saving = true)

    node(C.Tag.DASHBOARD_ROW_APPROVE).assertIsNotEnabled()
    node(C.Tag.DASHBOARD_ROW_DECLINE).assertIsNotEnabled()
    node(C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    node(C.Tag.DASHBOARD_ROW_DECLINE).performClick()

    assertEquals(0, approvals)
    assertEquals(0, declines)
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
  fun aRowThatCanBeApprovedShowsNoReason() {
    show(pending())

    node(C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED).assertDoesNotExist()
  }

  @Test
  fun aPartyBelowItsSizeDisablesOnlyApproveAndSaysHowManyMore() {
    show(pending().copy(approveBlock = ApproveBlock.TooFew(2)))

    node(C.Tag.DASHBOARD_ROW_APPROVE).assertIsNotEnabled()
    node(C.Tag.DASHBOARD_ROW_DECLINE).assertIsEnabled()
    assertEquals(
        context.resources.getQuantityString(R.plurals.dashboard_approve_needs_more, 2, 2),
        node(C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED).text(),
    )
    node(C.Tag.DASHBOARD_ROW_APPROVE).performClick()
    node(C.Tag.DASHBOARD_ROW_DECLINE).performClick()

    assertEquals(0, approvals)
    assertEquals(1, declines)
  }

  @Test
  fun aPartyAboveAnExactSizeSaysTheSize() {
    show(pending(partySize = 3).copy(approveBlock = ApproveBlock.TooMany(2)))

    node(C.Tag.DASHBOARD_ROW_APPROVE).assertIsNotEnabled()
    assertEquals(
        context.resources.getQuantityString(R.plurals.dashboard_approve_needs_exactly, 2, 2),
        node(C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED).text(),
    )
  }

  @Test
  fun aDeletedQuestSaysItCantBeApproved() {
    show(pending().copy(questTitle = null, approveBlock = ApproveBlock.QuestMissing))

    node(C.Tag.DASHBOARD_ROW_APPROVE).assertIsNotEnabled()
    assertEquals(
        string(R.string.dashboard_approve_quest_missing),
        node(C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED).text(),
    )
  }

  @Test
  fun thePreviewShowsAPartyBelowItsSize() {
    compose.setContent { DashboardReservationRowPreview() }

    assertEquals(
        context.resources.getQuantityString(R.plurals.dashboard_approve_needs_more, 1, 1),
        node(C.Tag.DASHBOARD_ROW_APPROVE_BLOCKED).text(),
    )
  }

  @Test
  fun aPartyNamesItsOtherMembersAndHowManyJoined() {
    show(
        pending(partySize = 3)
            .copy(
                members = members("Yusuf S.", "Lea M.", "Max K."),
                groupSize = 4,
                exactGroupSize = true,
            )
    )

    assertEquals("with Lea M., Max K. · 3 of 4 joined", node(C.Tag.DASHBOARD_ROW_PARTY).text())
    assertEquals(3, count(C.Tag.DASHBOARD_ROW_INITIALS))
    node(C.Tag.DASHBOARD_ROW_MORE).assertDoesNotExist()
  }

  @Test
  fun aQuestForAtLeastAGroupSaysHowManyJoinedAndItsMinimum() {
    show(
        pending(partySize = 6)
            .copy(
                members = members("Nora B.", "Elif T.", "Sam R.", "Ana P.", "Tom L.", "Ida K."),
                groupSize = 4,
            )
    )

    // Not "6 of 4 joined": more can still join
    assertEquals(
        "with Elif T., Sam R. +3 · 6 joined · at least 4",
        node(C.Tag.DASHBOARD_ROW_PARTY).text(),
    )
  }

  @Test
  fun othersWithoutANameAreStillCounted() {
    show(
        pending()
            .copy(
                members = listOf(DashboardMember("Yusuf S."), DashboardMember(null)),
                groupSize = 4,
                exactGroupSize = true,
            )
    )

    assertEquals("with +1 · 2 of 4 joined", node(C.Tag.DASHBOARD_ROW_PARTY).text())
  }

  @Test
  fun aBigPartyShowsThreeAvatarsAndCountsTheRest() {
    show(
        pending(partySize = 6)
            .copy(members = members("Nora B.", "Elif T.", "Sam R.", "Ana P.", "Tom L.", "Ida K."))
    )

    assertEquals("with Elif T., Sam R. +3 · Party of 6", node(C.Tag.DASHBOARD_ROW_PARTY).text())
    assertEquals(3, count(C.Tag.DASHBOARD_ROW_INITIALS))
    assertEquals("+3", node(C.Tag.DASHBOARD_ROW_MORE).text())
  }

  @Test
  fun membersWithoutANameAreCountedNotNamed() {
    show(
        pending(partySize = 3)
            .copy(
                members =
                    listOf(
                        DashboardMember("Yusuf S."),
                        DashboardMember(null),
                        DashboardMember("Lea M."),
                    )
            )
    )

    assertEquals("with Lea M. +1 · Party of 3", node(C.Tag.DASHBOARD_ROW_PARTY).text())
  }

  @Test
  fun aMemberWithAPictureShowsItInsteadOfInitials() {
    show(
        pending()
            .copy(
                members =
                    listOf(
                        DashboardMember("Yusuf S."),
                        DashboardMember("Lea M.", ImageBitmap(2, 2)),
                    )
            )
    )

    assertEquals(1, count(C.Tag.DASHBOARD_ROW_PICTURE))
    assertEquals(1, count(C.Tag.DASHBOARD_ROW_INITIALS))
  }

  @Test
  fun aLoneExplorerOfAGroupQuestShowsHowManyJoined() {
    show(pending(partySize = 1).copy(members = members("Yusuf S."), groupSize = 4))

    assertEquals("1 joined · at least 4", node(C.Tag.DASHBOARD_ROW_PARTY).text())
    node(C.Tag.DASHBOARD_ROW_LEADER).assertDoesNotExist()
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
    show(pending().copy(requestedAt = NOW - JUST_NOW_AGE_MS))

    assertEquals(
        string(R.string.dashboard_requested_just_now),
        node(C.Tag.DASHBOARD_ROW_REQUESTED).text(),
    )
  }

  @Test
  fun theRequestedTimeMovesOnWhileTheRowIsShown() {
    val clock = MovingClock(NOW)
    show(pending().copy(requestedAt = NOW - JUST_NOW_AGE_MS), clock)

    clock.now = NOW + DateUtils.MINUTE_IN_MILLIS
    compose.mainClock.advanceTimeBy(DateUtils.MINUTE_IN_MILLIS)

    assertEquals(
        string(R.string.dashboard_requested, ONE_MIN_AGO),
        node(C.Tag.DASHBOARD_ROW_REQUESTED).text(),
    )
  }

  /** A clock the test moves on by hand, like the phone's while the dashboard stays open. */
  private class MovingClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZONE

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(now)
  }

  private companion object {
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
    val NOW: Long = LocalDateTime.of(2026, 10, 28, 14, 0).atZone(ZONE).toInstant().toEpochMilli()
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZONE)

    /** Under a minute, so the row says "just now". */
    const val JUST_NOW_AGE_MS = 30_000L
    const val REQUEST_AGE_MS = 12 * DateUtils.MINUTE_IN_MILLIS

    // Android's English relative times, which the row puts after "Requested"
    const val TWELVE_MIN_AGO = "12 min. ago"
    const val ONE_MIN_AGO = "1 min. ago"

    fun pending(partySize: Int = 2) =
        DashboardReservation(
            reservationId = "a",
            questTitle = "Find the hidden fox",
            explorerName = "Yusuf S.",
            partySize = partySize,
            requestedAt = NOW - REQUEST_AGE_MS,
            approved = false,
        )
  }
}
