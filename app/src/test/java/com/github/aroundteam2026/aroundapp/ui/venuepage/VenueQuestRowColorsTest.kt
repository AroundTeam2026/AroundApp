// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests the colour of a quest row's reward chip, which depends on the kind of reward. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
class VenueQuestRowColorsTest {
  @get:Rule val compose = createComposeRule()

  /**
   * The colour just inside the start of [reward]'s chip, before its icon, on a solo quest so the
   * reward chip is the only one.
   */
  private fun rewardChipColor(reward: Reward, dark: Boolean = false): Color {
    val quest =
        VenuePageQuest(
            questId = "quest",
            title = "Find the hidden fox",
            description = "",
            requirements = "",
            proofType = ProofType.PHOTO,
            minPartySize = 1,
            reward = reward,
        )
    compose.setContent {
      AroundAppTheme(darkTheme = dark) { VenueQuestRow(quest, selected = false, onClick = {}) }
    }
    val chips = compose.onNodeWithTag(C.Tag.QUEST_CHIPS, useUnmergedTree = true).captureToImage()
    return chips.toPixelMap()[EDGE_INSET_PX, chips.height / 2]
  }

  @Test
  fun aFreeItemIsOnTheTertiaryColour() {
    assertEquals(AroundColors.Tertiary, rewardChipColor(Reward.FreeItem("croissant", null)))
  }

  @Test
  fun aDiscountIsOnThePrimaryColour() {
    val reward = Reward.Discount(15.0, DiscountUnit.PERCENT, specifics = null)

    assertEquals(AroundColors.Purple, rewardChipColor(reward))
  }

  @Test
  fun anOtherRewardIsOnThePrimaryColour() {
    assertEquals(AroundColors.Purple, rewardChipColor(Reward.Other("A free dessert")))
  }

  @Test
  fun aFreeItemIsOnTheTertiaryColourInDarkMode() {
    val color = rewardChipColor(Reward.FreeItem("croissant", null), dark = true)

    assertEquals(AroundColors.Tertiary, color)
  }

  @Test
  fun aDiscountIsOnThePrimaryColourInDarkMode() {
    val color = rewardChipColor(Reward.Discount(5.0, DiscountUnit.CHF, null), dark = true)

    assertEquals(AroundColors.Purple, color)
  }

  private companion object {
    /** Far enough in to clear the chip's rounded end, short of the gift icon. */
    const val EDGE_INSET_PX = 4
  }
}
