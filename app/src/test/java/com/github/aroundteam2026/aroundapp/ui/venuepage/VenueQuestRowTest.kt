// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests one quest's row on the venue page: what it says, and how it picks. */
@RunWith(RobolectricTestRunner::class)
class VenueQuestRowTest {
  @get:Rule val compose = createComposeRule()

  private var clicks = 0

  private fun quest(
      title: String = "Bring a friend",
      description: String = "Come with a friend and order two drinks.",
      minPartySize: Int = 1,
      reward: Reward? = Reward.Other("A free dessert"),
  ) =
      VenuePageQuest(
          questId = "quest",
          title = title,
          description = description,
          requirements = "Order two drinks",
          proofType = ProofType.PHOTO,
          minPartySize = minPartySize,
          reward = reward,
      )

  private fun show(quest: VenuePageQuest = quest(), selected: Boolean = false) {
    compose.setContent { AroundAppTheme { VenueQuestRow(quest, selected, onClick = { clicks++ }) } }
  }

  private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun showsTheTitleAndDescription() {
    show()

    compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).assertIsDisplayed()
    node(C.Tag.VENUE_QUEST_ROW_TITLE).assertTextEquals("Bring a friend")
    node(C.Tag.VENUE_QUEST_ROW_DESCRIPTION)
        .assertTextEquals("Come with a friend and order two drinks.")
  }

  @Test
  fun aBlankDescriptionShowsNone() {
    show(quest(description = " "))

    node(C.Tag.VENUE_QUEST_ROW_TITLE).assertIsDisplayed()
    node(C.Tag.VENUE_QUEST_ROW_DESCRIPTION).assertDoesNotExist()
  }

  @Test
  fun aPercentDiscountShowsItsChip() {
    show(quest(reward = Reward.Discount(10.0, DiscountUnit.PERCENT, specifics = null)))

    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("10% off")
  }

  @Test
  fun aFrancDiscountShowsItsChip() {
    show(quest(reward = Reward.Discount(2.5, DiscountUnit.CHF, specifics = null)))

    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("CHF 2.50 off")
  }

  @Test
  fun aFreeItemShowsItsChip() {
    show(quest(reward = Reward.FreeItem("coffee", specifics = null)))

    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("Free coffee")
  }

  @Test
  fun anOtherRewardShowsItsDescription() {
    show(quest(reward = Reward.Other("A free dessert")))

    node(C.Tag.QUEST_CHIP_REWARD).assertTextEquals("A free dessert")
  }

  @Test
  fun noRewardShowsNoRewardChip() {
    show(quest(reward = null, minPartySize = 3))

    node(C.Tag.QUEST_CHIP_REWARD).assertDoesNotExist()
    node(C.Tag.QUEST_CHIP_PARTY).assertTextEquals("Groups of 3+")
  }

  @Test
  fun aGroupQuestShowsItsPartySize() {
    show(quest(minPartySize = 4))

    node(C.Tag.QUEST_CHIP_PARTY).assertTextEquals("Groups of 4+")
  }

  @Test
  fun aSoloQuestShowsNoPartyChip() {
    show(quest(minPartySize = 1))

    node(C.Tag.QUEST_CHIP_PARTY).assertDoesNotExist()
  }

  @Test
  fun noDistanceIsShown() {
    show(quest(minPartySize = 2))

    node(C.Tag.QUEST_CHIP_DISTANCE).assertDoesNotExist()
  }

  @Test
  fun aPickedRowIsSelected() {
    show(selected = true)

    compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).assertIsSelected()
  }

  @Test
  fun anUnpickedRowIsNotSelected() {
    show(selected = false)

    compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).assertIsNotSelected()
  }

  @Test
  fun theRowIsARadioButton() {
    show()

    compose
        .onNodeWithTag(C.Tag.VENUE_QUEST_ROW)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
  }

  @Test
  fun tappingTheRowCallsOnClick() {
    show()

    compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun theRowFollowsItsPick() {
    var selected by mutableStateOf(false)
    compose.setContent {
      AroundAppTheme { VenueQuestRow(quest(), selected, onClick = { selected = !selected }) }
    }

    compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).performClick().assertIsSelected()
    compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).performClick().assertIsNotSelected()
  }

  @Test
  fun aPickedRowInsetsItsContent() {
    show(selected = true)
    assertEquals(8.dp, titleInset())
  }

  @Test
  fun anUnpickedRowIsFlushWithItsEdges() {
    show(selected = false)
    assertEquals(0.dp, titleInset())
  }

  /** How far the title starts from the row's start edge. */
  private fun titleInset() =
      node(C.Tag.VENUE_QUEST_ROW_TITLE).getBoundsInRoot().left -
          compose.onNodeWithTag(C.Tag.VENUE_QUEST_ROW).getBoundsInRoot().left
}
