// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardFormStateTest {
  private val freeItem = RewardFormState(type = RewardType.FREE_ITEM, itemName = "Coffee")
  private val discount = RewardFormState(type = RewardType.DISCOUNT, discountAmount = "15")
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
  fun discountKeepsItsUnitAndReadsACommaAsADecimalPoint() {
    assertEquals(Reward.Discount(15.0, DiscountUnit.PERCENT, null), discount.toReward())
    assertEquals(
        Reward.Discount(12.5, DiscountUnit.CHF, "Hot drinks"),
        discount
            .copy(
                discountAmount = " 12,5 ",
                discountUnit = DiscountUnit.CHF,
                specifics = "Hot drinks ",
            )
            .toReward(),
    )
  }

  @Test
  fun discountAmountIsANumberWithinTheLimitsOfItsUnit() {
    val cases =
        listOf(
            Triple(DiscountUnit.PERCENT, "abc", DiscountAmountError.NOT_A_NUMBER),
            Triple(DiscountUnit.CHF, "Infinity", DiscountAmountError.NOT_A_NUMBER),
            Triple(DiscountUnit.CHF, "0", DiscountAmountError.TOO_SMALL),
            Triple(DiscountUnit.PERCENT, "-5", DiscountAmountError.TOO_SMALL),
            Triple(DiscountUnit.PERCENT, "100.01", DiscountAmountError.TOO_LARGE),
            Triple(DiscountUnit.PERCENT, "100", null),
            Triple(DiscountUnit.CHF, "100.01", null),
            Triple(DiscountUnit.CHF, "0.01", null),
        )
    cases.forEach { (unit, amount, error) ->
      val reward = discount.copy(discountAmount = amount, discountUnit = unit)
      assertEquals("$amount $unit", error, reward.discountAmountError)
      assertEquals("$amount $unit", error == null, reward.isValid)
    }
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
    assertNull(untouched.discountAmountError)
    assertFalse(untouched.copy(type = RewardType.DISCOUNT).isValid)
    assertEquals(
        DiscountAmountError.BLANK,
        untouched.copy(discountAmountTouched = true).discountAmountError,
    )
    assertEquals(QuestTextError.BLANK, untouched.copy(itemNameTouched = true).itemNameError)
    assertEquals(QuestTextError.BLANK, untouched.copy(descriptionTouched = true).descriptionError)
  }

  @Test
  fun rewardExpiresAtTheMidnightThatEndsItsLastDay() {
    val lastDay = LocalDate.of(2026, 12, 31)
    val endOfDay = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    assertEquals(
        Reward.Other("A tour", endOfDay),
        other.copy(expiryDate = lastDay).toReward(ZoneOffset.UTC),
    )
    assertNull(other.toReward(ZoneOffset.UTC)?.expiresAt)
  }

  @Test
  fun anExpiryThatHasPassedBlocksAnyRewardButNotNoReward() {
    assertFalse(other.copy(expiryInPast = true).isValid)
    assertTrue(RewardFormState(expiryInPast = true).isValid)
  }
}
