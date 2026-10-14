// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the sheet with the whole of a quest: what it shows, and each way it closes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class QuestDetailSheetTest {
  @get:Rule val compose = createComposeRule()

  private var dismissals = 0

  private fun quest(
      description: String = "Spot the ceramic fox hidden in the café.",
      requirements: String = "Show the photo at the counter.",
      proofType: ProofType = ProofType.PHOTO,
      minPartySize: Int = 1,
      reward: Reward? = Reward.FreeItem("croissant", specifics = "Plain or almond"),
  ) =
      VenuePageQuest(
          questId = "fox",
          title = "Find the hidden fox",
          description = description,
          requirements = requirements,
          proofType = proofType,
          minPartySize = minPartySize,
          reward = reward,
      )

  /** Shows [quest] in the sheet until it is dismissed, as the venue page does. */
  private fun show(quest: VenuePageQuest = quest()) {
    var open by mutableStateOf(true)
    compose.setContent {
      AroundAppTheme {
        if (open) {
          QuestDetailSheet(
              quest,
              onDismiss = {
                dismissals++
                open = false
              },
          )
        }
      }
    }
    compose.waitForIdle()
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun showsTheWholeQuest() {
    show()

    node(C.Tag.QUEST_DETAIL_TITLE).assertTextEquals("Find the hidden fox")
    node(C.Tag.QUEST_DETAIL_DESCRIPTION)
        .assertTextEquals("Spot the ceramic fox hidden in the café.")
    node(C.Tag.QUEST_DETAIL_REQUIREMENTS).assertTextEquals("Show the photo at the counter.")
    node(C.Tag.QUEST_DETAIL_PROOF).assertTextEquals("Send a photo")
    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("Free croissant")
    node(C.Tag.QUEST_DETAIL_REWARD_DETAILS).assertTextEquals("Plain or almond")
  }

  @Test
  fun aLongDescriptionIsShownWholeAndTheRestCanBeScrolledTo() {
    val description = "A long hunt through the café. ".repeat(40).trim()
    show(quest(description = description))

    node(C.Tag.QUEST_DETAIL_DESCRIPTION).assertTextEquals(description)
    listOf(
            C.Tag.QUEST_DETAIL_REQUIREMENTS,
            C.Tag.QUEST_DETAIL_PROOF,
            C.Tag.QUEST_DETAIL_REWARD_DETAILS,
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

    node(C.Tag.QUEST_DETAIL_PROOF).assertTextEquals("Send a text answer")
  }

  @Test
  fun aGroupQuestShowsItsPartySize() {
    show(quest(minPartySize = 3))

    node(C.Tag.QUEST_CHIP_PARTY).assertTextEquals("Groups of 3+")
  }

  @Test
  fun aDiscountShowsItsChipAndSpecifics() {
    show(quest(reward = Reward.Discount(10.0, DiscountUnit.PERCENT, specifics = "Hot drinks")))

    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("10% off")
    node(C.Tag.QUEST_DETAIL_REWARD_DETAILS).assertTextEquals("Hot drinks")
  }

  @Test
  fun blankPartsAreLeftOut() {
    show(quest(description = " ", requirements = "", reward = null))

    node(C.Tag.QUEST_DETAIL_TITLE).assertIsDisplayed()
    node(C.Tag.QUEST_DETAIL_PROOF).assertIsDisplayed()
    listOf(
            C.Tag.QUEST_DETAIL_DESCRIPTION,
            C.Tag.QUEST_DETAIL_REQUIREMENTS,
            C.Tag.QUEST_DETAIL_REWARD_DETAILS,
            C.Tag.QUEST_CHIP_REWARD,
        )
        .forEach { node(it).assertDoesNotExist() }
  }

  @Test
  fun theCloseButtonClosesTheSheet() {
    show()

    compose.onNodeWithContentDescription("Close quest details").performClick()
    compose.waitForIdle()

    assertEquals(1, dismissals)
    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).assertDoesNotExist()
  }

  @Test
  fun draggingTheSheetDownClosesIt() {
    show()

    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).performTouchInput { swipeDown() }
    compose.waitForIdle()

    assertEquals(1, dismissals)
    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).assertDoesNotExist()
  }

  @Test
  fun systemBackClosesTheSheet() {
    show()

    Espresso.pressBack()
    compose.waitForIdle()

    assertEquals(1, dismissals)
    compose.onNodeWithTag(C.Tag.QUEST_DETAIL_SHEET).assertDoesNotExist()
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
}
