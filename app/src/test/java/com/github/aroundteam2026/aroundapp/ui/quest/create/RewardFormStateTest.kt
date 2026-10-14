// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.ui.quest.create.DiscountAmountError.NOT_A_NUMBER
import com.github.aroundteam2026.aroundapp.ui.quest.create.DiscountAmountError.TOO_LARGE
import com.github.aroundteam2026.aroundapp.ui.quest.create.DiscountAmountError.TOO_MANY_DECIMALS
import com.github.aroundteam2026.aroundapp.ui.quest.create.DiscountAmountError.TOO_SMALL
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
        freeItem.copy(itemName = " Coffee ", itemSpecifics = "  ").toReward(),
    )
    assertEquals(
        Reward.FreeItem("Coffee", "Any size"),
        freeItem.copy(itemSpecifics = " Any size ").toReward(),
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
                discountSpecifics = "Hot drinks ",
            )
            .toReward(),
    )
  }

  @Test
  fun discountAmountIsOnlyDigitsWithAnOptionalDecimalPointOrComma() {
    // toDoubleOrNull() or a person reads most of these as numbers, but none is plain decimal input
    listOf("abc", "Infinity", "NaN", "1e3", "15f", "0x1p4", "+5", "-5", ".", "1 000", "١٥")
        .forEach { amount -> assertAmountError(DiscountUnit.CHF, amount, NOT_A_NUMBER) }
  }

  @Test
  fun discountAmountMayStartOrEndWithItsDecimalPoint() {
    assertEquals(
        Reward.Discount(0.5, DiscountUnit.PERCENT, null),
        discount.copy(discountAmount = ".5").toReward(),
    )
    assertEquals(
        Reward.Discount(5.0, DiscountUnit.PERCENT, null),
        discount.copy(discountAmount = "5,").toReward(),
    )
  }

  @Test
  fun discountAmountHasAtMostTwoDecimals() {
    assertAmountError(DiscountUnit.CHF, "0.015", TOO_MANY_DECIMALS)
    assertAmountError(DiscountUnit.PERCENT, "99,996", TOO_MANY_DECIMALS)
    assertAmountError(DiscountUnit.CHF, "0,99", null)
    // Some phones read these as a thousand or half a franc, so none is taken at face value
    listOf("1,000", "1.000", "0,500").forEach {
      assertAmountError(DiscountUnit.CHF, it, TOO_MANY_DECIMALS)
    }
  }

  @Test
  fun eachTypeKeepsItsOwnSpecifics() {
    val typed = discount.withSpecifics("Hot drinks")
    assertEquals("Hot drinks", typed.specifics)

    val item = typed.copy(type = RewardType.FREE_ITEM, itemName = "Coffee")
    assertEquals("", item.specifics)
    assertEquals(Reward.FreeItem("Coffee", null), item.toReward())

    assertEquals("Hot drinks", item.copy(type = RewardType.DISCOUNT).specifics)
    assertEquals(other, other.withSpecifics("Ignored"))
  }

  @Test
  fun discountAmountIsWithinTheLimitsOfItsUnit() {
    assertAmountError(DiscountUnit.CHF, "0", TOO_SMALL)
    assertAmountError(DiscountUnit.PERCENT, "0.00", TOO_SMALL)
    assertAmountError(DiscountUnit.CHF, "0.01", null)
    assertAmountError(DiscountUnit.PERCENT, "100", null)
    assertAmountError(DiscountUnit.PERCENT, "100.01", TOO_LARGE)
    assertAmountError(DiscountUnit.CHF, "1000", null)
    assertAmountError(DiscountUnit.CHF, "1000.01", TOO_LARGE)
    assertAmountError(DiscountUnit.CHF, "99999999999999999999", TOO_LARGE)
  }

  private fun assertAmountError(unit: DiscountUnit, amount: String, error: DiscountAmountError?) {
    val reward = discount.copy(discountAmount = amount, discountUnit = unit)
    assertEquals("$amount $unit", error, reward.discountAmountError)
    assertEquals("$amount $unit", error == null, reward.isValid)
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
  fun tooLongSpecificsShowAtOnceAndBlockTheReward() {
    val tooLong = "x".repeat(QuestLimits.REWARD_SPECIFICS_MAX_LENGTH + 1)
    listOf(freeItem.copy(itemSpecifics = tooLong), discount.copy(discountSpecifics = tooLong))
        .forEach { reward ->
          assertEquals(reward.type.toString(), QuestTextError.TOO_LONG, reward.specificsError)
          assertFalse(reward.type.toString(), reward.isValid)
        }
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
}
