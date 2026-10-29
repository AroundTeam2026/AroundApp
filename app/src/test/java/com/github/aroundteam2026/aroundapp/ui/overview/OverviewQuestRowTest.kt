// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import android.text.format.DateFormat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w413dp-h917dp-mdpi")
class OverviewQuestRowTest {
  @get:Rule val compose = createComposeRule()

  private var clicks = 0

  private fun show(row: OverviewRow) {
    compose.setContent {
      AroundAppTheme { OverviewQuestRow(row, onClick = { clicks++ }, clock = CLOCK) }
    }
  }

  /** The card is one clickable, so its parts are only found in the unmerged tree. */
  private fun part(tag: String) =
      compose.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag("${C.Tag.OVERVIEW_ROW}_$ID")),
          useUnmergedTree = true,
      )

  /** The text of the card's status, if it says [text]. */
  private fun status(text: String) =
      compose.onNode(
          hasText(text) and hasAnyAncestor(hasTestTag(C.Tag.OVERVIEW_ROW_STATUS)),
          useUnmergedTree = true,
      )

  @Test
  fun aWaitingCardShowsItsQuestVenueSlotAndParty() {
    show(row())

    part(C.Tag.OVERVIEW_ROW_TITLE).assertTextEquals("Find the hidden fox")
    part(C.Tag.OVERVIEW_ROW_VENUE).assertTextEquals("Café Lumen · Rue du Pont 1")
    part(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("Free Coffee")
    part(C.Tag.OVERVIEW_ROW_SLOT).assertTextEquals("Today · ${time(SLOT)}")
    part(C.Tag.OVERVIEW_ROW_PARTY).assertTextEquals("1 person")
    status("WAITING FOR THE VENUE").assertIsDisplayed()
    status("APPROVED").assertDoesNotExist()
  }

  @Test
  fun anApprovedCardSaysApproved() {
    show(row(status = OverviewRowStatus.APPROVED, partySize = 3))

    status("APPROVED").assertIsDisplayed()
    status("WAITING FOR THE VENUE").assertDoesNotExist()
    part(C.Tag.OVERVIEW_ROW_PARTY).assertTextEquals("3 people")
  }

  @Test
  fun aSlotOnAnotherDayShowsItsWeekdayAndDate() {
    val later = SLOT + 2 * DAY
    show(row(slotStart = later))

    // In English, e.g. "Sun, Jan 17 · 8:00 AM"
    val day =
        DateTimeFormatter.ofPattern(
                DateFormat.getBestDateTimePattern(Locale.ENGLISH, "EEEdMMM"),
                Locale.ENGLISH,
            )
            .format(Instant.ofEpochMilli(later).atZone(UTC))
    part(C.Tag.OVERVIEW_ROW_SLOT).assertTextEquals("$day · ${time(later)}")
  }

  // The app passes no clock, so the card reads the device's: a quest booked now is today
  @Test
  fun withoutAClockTheCardUsesTheDevicesDay() {
    compose.setContent {
      AroundAppTheme {
        OverviewQuestRow(row(slotStart = System.currentTimeMillis()), onClick = { clicks++ })
      }
    }

    part(C.Tag.OVERVIEW_ROW_SLOT).assert(hasText("Today · ", substring = true))
  }

  @Test
  fun aCardWithoutAddressOrRewardLeavesThemOut() {
    show(row(address = null, reward = null))

    part(C.Tag.OVERVIEW_ROW_VENUE).assertTextEquals("Café Lumen")
    part(C.Tag.QUEST_CHIP_REWARD).assertDoesNotExist()
  }

  @Test
  fun tappingTheCardCallsOnClick() {
    show(row())

    compose.onNodeWithTag("${C.Tag.OVERVIEW_ROW}_$ID").performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun theCardTellsScreenReadersWhereItGoes() {
    show(row())

    compose
        .onNodeWithTag("${C.Tag.OVERVIEW_ROW}_$ID")
        .assert(
            SemanticsMatcher("opens Café Lumen") {
              it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Café Lumen"
            }
        )
  }

  private fun row(
      status: OverviewRowStatus = OverviewRowStatus.WAITING,
      partySize: Int = 1,
      address: String? = "Rue du Pont 1",
      reward: Reward? = Reward.FreeItem("Coffee", specifics = null),
      slotStart: Long = SLOT,
  ) =
      OverviewRow(
          reservationId = ID,
          questId = "fox",
          venueId = "cafe",
          questTitle = "Find the hidden fox",
          reward = reward,
          venueName = "Café Lumen",
          address = address,
          avatar = VenueAvatar.Initials("CL"),
          slotStart = slotStart,
          partySize = partySize,
          status = status,
      )

  private companion object {
    const val ID = "reservation-1"
    /** 15 January 2027, 08:00 UTC. */
    const val SLOT = 1_800_000_000_000L
    const val DAY = 86_400_000L
    val UTC: ZoneOffset = ZoneOffset.UTC
    /** Two hours after [SLOT], the same day. */
    val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(SLOT + 2 * 3_600_000L), UTC)

    /** The time of [millis] in English, e.g. "8:00 AM", whichever space the JDK puts before AM. */
    fun time(millis: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.ENGLISH)
            .format(Instant.ofEpochMilli(millis).atZone(UTC))
  }
}
