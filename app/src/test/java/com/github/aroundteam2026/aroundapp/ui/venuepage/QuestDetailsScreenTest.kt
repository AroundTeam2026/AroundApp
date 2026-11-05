// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.VenueAvatar
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the screen with the whole of a quest: what it shows, and how it accepts and cancels. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class QuestDetailsScreenTest {
  @get:Rule val compose = createComposeRule()

  private var backs = 0
  private var accepts = 0
  private var cancels = 0

  private fun quest(
      description: String = "Spot the ceramic fox hidden in the café.",
      requirements: String = "Show the photo at the counter.",
      proofType: ProofType = ProofType.PHOTO,
      minPartySize: Int = 1,
      exactPartySize: Boolean = false,
      radiusMeters: Int = 80,
      reward: Reward? = Reward.FreeItem("croissant", specifics = "Plain or almond"),
  ) =
      VenuePageQuest(
          questId = QUEST,
          title = "Find the hidden fox",
          description = description,
          requirements = requirements,
          proofType = proofType,
          minPartySize = minPartySize,
          reward = reward,
          exactPartySize = exactPartySize,
          radiusMeters = radiusMeters,
      )

  private fun state(quest: VenuePageQuest) =
      VenuePageUiState(
          status = VenuePageStatus.LOADED,
          venueName = "Café Lumen",
          avatar = VenueAvatar.Initials("CL"),
          address = "Rue du Pont 1",
          quests = listOf(quest),
          selectedQuestId = quest.questId,
          detailQuestId = quest.questId,
      )

  private fun accepted(quest: VenuePageQuest, party: Int = 1) =
      state(quest)
          .copy(acceptedParties = mapOf(QUEST to party), reservationIds = mapOf(QUEST to "r"))

  private fun show(quest: VenuePageQuest = quest(), state: VenuePageUiState = state(quest)) {
    compose.setContent {
      AroundAppTheme {
        QuestDetailsScreen(
            state,
            quest,
            onBack = { backs++ },
            onAccept = { accepts++ },
            onCancel = { cancels++ },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )
      }
    }
    compose.waitForIdle()
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  private fun nodes(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true)

  @Test
  fun showsTheVenueAndTheWholeQuest() {
    show()

    node(C.Tag.QUEST_DETAIL_VENUE_NAME).assertTextEquals("Café Lumen")
    node(C.Tag.QUEST_DETAIL_VENUE_ADDRESS).assertTextEquals("Rue du Pont 1")
    node(C.Tag.QUEST_DETAIL_TITLE).assertTextEquals("Find the hidden fox")
    node(C.Tag.QUEST_DETAIL_DESCRIPTION)
        .assertTextEquals("Spot the ceramic fox hidden in the café.")
    node(C.Tag.QUEST_DETAIL_REQUIREMENTS).assertTextEquals("Show the photo at the counter.")
    node(C.Tag.QUEST_DETAIL_PROOF).assertTextEquals("A photo")
    node(C.Tag.QUEST_DETAIL_REWARD_DETAILS).assertTextEquals("Plain or almond")
    assertEquals(2, nodes(C.Tag.QUEST_CHIP_REWARD).fetchSemanticsNodes().size)
  }

  @Test
  fun aVenueWithoutAnAddressLeavesItOut() {
    val quest = quest()
    show(quest, state(quest).copy(address = null))

    node(C.Tag.QUEST_DETAIL_VENUE_ADDRESS).assertDoesNotExist()
  }

  @Test
  fun aLongDescriptionIsShownWholeAndTheRestCanBeScrolledTo() {
    val description = "A long hunt through the café. ".repeat(40).trim()
    show(quest(description = description))

    node(C.Tag.QUEST_DETAIL_DESCRIPTION).assertTextEquals(description)
    listOf(
            C.Tag.QUEST_DETAIL_REQUIREMENTS,
            C.Tag.QUEST_DETAIL_REWARD_DETAILS,
            C.Tag.QUEST_DETAIL_PROOF,
            C.Tag.QUEST_DETAIL_RADIUS_NOTE,
        )
        .forEach { node(it).performScrollTo().assertIsDisplayed() }
  }

  @Test
  fun aLongOtherRewardIsShownWhole() {
    val reward = "A guided tour of the roastery, with a tasting of three single-origin coffees"
    show(quest(reward = Reward.Other(reward)))

    node(C.Tag.QUEST_DETAIL_REWARD_DETAILS).assertTextEquals(reward)
  }

  @Test
  fun aTextProofAsksForATextAnswer() {
    show(quest(proofType = ProofType.TEXT))

    node(C.Tag.QUEST_DETAIL_PROOF).assertTextEquals("A text answer")
  }

  @Test
  fun aDiscountShowsItsSpecifics() {
    show(quest(reward = Reward.Discount(10.0, DiscountUnit.PERCENT, specifics = "Hot drinks")))

    node(C.Tag.QUEST_DETAIL_REWARD_DETAILS).assertTextEquals("Hot drinks")
  }

  @Test
  fun anExpiringRewardSaysUntilWhen() {
    val expiresAt = Instant.parse("2026-12-31T12:00:00Z").toEpochMilli()
    show(quest(reward = Reward.FreeItem("croissant", specifics = null, expiresAt = expiresAt)))

    node(C.Tag.QUEST_DETAIL_REWARD_EXPIRY).assertTextEquals("Valid until Dec 31, 2026")
  }

  @Test
  fun aRewardThatNeverExpiresSaysNothingAboutIt() {
    show()

    node(C.Tag.QUEST_DETAIL_REWARD_EXPIRY).assertDoesNotExist()
  }

  @Test
  fun anAtLeastPartyWelcomesBiggerGroups() {
    show(quest(minPartySize = 3))

    node(C.Tag.QUEST_CHIP_PARTY).assertTextEquals("Groups of 3+")
    node(C.Tag.QUEST_DETAIL_PARTY_RULE).assertTextEquals("At least 3 per visit")
    node(C.Tag.QUEST_DETAIL_PARTY_BIGGER).assertTextEquals("Bigger groups welcome")
  }

  @Test
  fun anExactPartySaysSoAndDoesNotWelcomeBiggerGroups() {
    show(quest(minPartySize = 3, exactPartySize = true))

    node(C.Tag.QUEST_CHIP_PARTY).assertTextEquals("Groups of 3")
    node(C.Tag.QUEST_DETAIL_PARTY_RULE).assertTextEquals("Exactly 3 per visit")
    node(C.Tag.QUEST_DETAIL_PARTY_BIGGER).assertDoesNotExist()
  }

  @Test
  fun beforeAcceptingOnlyTheExplorersSeatIsTaken() {
    show(quest(minPartySize = 4))

    node(C.Tag.QUEST_DETAIL_PARTY_JOINED).assertTextEquals("1 of 4 joined")
    compose.onNodeWithText("ME").assertExists()
    assertEquals(3, nodes(C.Tag.QUEST_DETAIL_SEAT_EMPTY).fetchSemanticsNodes().size)
  }

  @Test
  fun joinedMembersTakeTheirSeats() {
    val quest = quest(minPartySize = 4)
    show(quest, accepted(quest, party = 3))

    node(C.Tag.QUEST_DETAIL_PARTY_JOINED).assertTextEquals("3 of 4 joined")
    assertEquals(1, nodes(C.Tag.QUEST_DETAIL_SEAT_EMPTY).fetchSemanticsNodes().size)
  }

  @Test
  fun aBiggerPartyThanTheMinimumHasASeatForEveryone() {
    val quest = quest(minPartySize = 2)
    show(quest, accepted(quest, party = 3))

    node(C.Tag.QUEST_DETAIL_PARTY_JOINED).assertTextEquals("3 of 3 joined")
    node(C.Tag.QUEST_DETAIL_SEAT_EMPTY).assertDoesNotExist()
  }

  @Test
  fun showsTheUnlockRadiusOnAMapAroundTheVenue() {
    val quest = quest()
    show(quest, state(quest).copy(venueLocation = Location(46.52, 6.63)))

    node(C.Tag.QUEST_DETAIL_RADIUS).assertTextEquals("80 m")
    node(C.Tag.QUEST_DETAIL_RADIUS_MAP).assertExists()
    node(C.Tag.QUEST_DETAIL_RADIUS_NOTE)
        .assertTextEquals("Café Lumen asks for your proof from inside this radius.")
  }

  @Test
  fun aVenueWithoutALocationKeepsTheRadiusButHasNoMap() {
    show()

    node(C.Tag.QUEST_DETAIL_RADIUS).assertTextEquals("80 m")
    node(C.Tag.QUEST_DETAIL_RADIUS_MAP).assertDoesNotExist()
  }

  @Test
  fun blankPartsAreLeftOut() {
    show(quest(description = " ", requirements = "", reward = null))

    node(C.Tag.QUEST_DETAIL_TITLE).assertIsDisplayed()
    node(C.Tag.QUEST_DETAIL_PROOF).performScrollTo().assertIsDisplayed()
    listOf(
            C.Tag.QUEST_DETAIL_DESCRIPTION,
            C.Tag.QUEST_DETAIL_REQUIREMENTS,
            C.Tag.QUEST_DETAIL_REWARD_DETAILS,
            C.Tag.QUEST_DETAIL_REWARD_EXPIRY,
            C.Tag.QUEST_CHIP_REWARD,
        )
        .forEach { node(it).assertDoesNotExist() }
  }

  @Test
  fun theBackArrowGoesBack() {
    show()

    node(C.Tag.VENUE_PAGE_BACK).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun systemBackGoesBack() {
    show()

    Espresso.pressBack()
    compose.waitForIdle()

    assertEquals(1, backs)
  }

  @Test
  fun theBarAcceptsTheQuest() {
    show()

    node(C.Tag.ACCEPT_QUEST_BUTTON).performClick()

    assertEquals(1, accepts)
  }

  @Test
  fun aQuestNotAcceptedHasNothingToCancel() {
    show()

    node(C.Tag.OVERVIEW_ROW_CANCEL).assertDoesNotExist()
  }

  @Test
  fun cancellingAsksFirstAndKeepingItChangesNothing() {
    val quest = quest()
    show(quest, accepted(quest))

    node(C.Tag.OVERVIEW_ROW_CANCEL).performClick()
    compose.onNodeWithTag(C.Tag.OVERVIEW_CANCEL_DIALOG).assertIsDisplayed()
    compose.onNodeWithText("Keep it").performClick()

    compose.onNodeWithTag(C.Tag.OVERVIEW_CANCEL_DIALOG).assertDoesNotExist()
    assertEquals(0, cancels)
  }

  @Test
  fun confirmingCancels() {
    val quest = quest()
    show(quest, accepted(quest))

    node(C.Tag.OVERVIEW_ROW_CANCEL).performClick()
    compose
        .onNodeWithText(
            "Café Lumen will see it cancelled. To go after all, accept the quest again."
        )
        .assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.OVERVIEW_CANCEL_CONFIRM).performClick()

    assertEquals(1, cancels)
  }

  @Test
  fun whileCancellingTheButtonIsDisabled() {
    val quest = quest()
    show(quest, accepted(quest).copy(cancellingIds = setOf("r")))

    node(C.Tag.OVERVIEW_ROW_CANCEL).assertIsNotEnabled()
    compose.onNodeWithText("Cancelling…").assertExists()
  }

  @Test
  fun afterAFailureTheButtonRetriesAtOnce() {
    val quest = quest()
    show(quest, accepted(quest).copy(failedCancelIds = setOf("r")))

    node(C.Tag.OVERVIEW_ROW_CANCEL_FAILED).assertIsDisplayed()
    node(C.Tag.OVERVIEW_ROW_CANCEL).performClick()

    assertEquals(1, cancels)
    compose.onNodeWithTag(C.Tag.OVERVIEW_CANCEL_DIALOG).assertDoesNotExist()
  }

  @Test
  fun rewardDetailsAreWhatTheChipCannotFit() {
    assertEquals("Oat milk", rewardDetails(Reward.FreeItem("latte", "Oat milk")))
    assertEquals("Pastries", rewardDetails(Reward.Discount(5.0, DiscountUnit.CHF, "Pastries")))
    assertNull(rewardDetails(Reward.FreeItem("latte", " ")))
    assertNull(rewardDetails(Reward.Discount(5.0, DiscountUnit.CHF, null)))
    assertEquals("A tour", rewardDetails(Reward.Other("A tour")))
    assertNull(rewardDetails(Reward.Other(" ")))
    assertNull(rewardDetails(null))
  }

  @Test
  fun thePreviewShowsAQuest() {
    compose.setContent { QuestDetailsPreview() }

    node(C.Tag.QUEST_DETAIL_TITLE).assertTextEquals("Find the hidden fox")
    node(C.Tag.QUEST_DETAIL_PARTY_RULE).assertTextEquals("At least 4 per visit")
  }

  private companion object {
    const val QUEST = "fox"
  }
}
