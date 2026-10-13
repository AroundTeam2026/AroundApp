// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardFormStateTest {
  private val freeItem = RewardFormState(type = RewardType.FREE_ITEM, itemName = "Coffee")
  private val other = RewardFormState(type = RewardType.OTHER, description = "A tour")

  @Test
  fun noTypeIsAValidFormWithNoReward() {
    val reward = RewardFormState(itemName = "x".repeat(QuestLimits.REWARD_ITEM_MAX_LENGTH + 1))
    assertTrue(reward.isValid)
    assertNull(reward.toReward())
  }

  @Test
  fun freeItemIsTrimmedAndBlankSpecificsAreDropped() {
    assertEquals(
        Reward.FreeItem("Coffee", null),
        freeItem.copy(itemName = " Coffee ", specifics = "  ").toReward(),
    )
    assertEquals(
        Reward.FreeItem("Coffee", "Any size"),
        freeItem.copy(specifics = " Any size ").toReward(),
    )
  }

  @Test
  fun otherIsTrimmed() {
    assertEquals(Reward.Other("A tour"), other.copy(description = " A tour ").toReward())
  }

  @Test
  fun freeItemNeedsANameWithinItsLimit() {
    assertTrue(freeItem.isValid)
    assertFalse(freeItem.copy(itemName = " ").isValid)
    assertFalse(
        freeItem.copy(itemName = "x".repeat(QuestLimits.REWARD_ITEM_MAX_LENGTH + 1)).isValid
    )
  }

  @Test
  fun tooLongSpecificsShowAtOnceAndBlockTheFreeItem() {
    val reward = freeItem.copy(specifics = "x".repeat(QuestLimits.REWARD_SPECIFICS_MAX_LENGTH + 1))
    assertEquals(QuestTextError.TOO_LONG, reward.specificsError)
    assertFalse(reward.isValid)
  }

  @Test
  fun otherNeedsADescriptionAndIgnoresTheItemName() {
    assertTrue(other.copy(itemName = "").isValid)
    assertFalse(other.copy(description = "").isValid)
  }

  @Test
  fun blankErrorsWaitUntilTheFieldIsTouched() {
    val untouched = RewardFormState(type = RewardType.FREE_ITEM)
    assertNull(untouched.itemNameError)
    assertNull(untouched.descriptionError)
    assertEquals(QuestTextError.BLANK, untouched.copy(itemNameTouched = true).itemNameError)
    assertEquals(QuestTextError.BLANK, untouched.copy(descriptionTouched = true).descriptionError)
  }
}
