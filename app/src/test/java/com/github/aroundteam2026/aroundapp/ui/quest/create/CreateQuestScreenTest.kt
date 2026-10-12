// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.quest.DiscountUnit
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class CreateQuestScreenTest {
  @get:Rule val compose = createComposeRule()
  private val viewModel = CreateQuestViewModel(FakeAuthRepository(), FakeVenueRepository())
  private var closeCalls = 0

  @Before
  fun setUp() {
    compose.setContent {
      AroundAppTheme { CreateQuestScreen(viewModel, onClose = { closeCalls++ }) }
    }
  }

  @Test
  fun showsHeaderAndTheThreeFields() {
    compose.onNodeWithText("New quest").assertIsDisplayed()
    listOf(
            C.Tag.CREATE_QUEST_TITLE,
            C.Tag.CREATE_QUEST_DESCRIPTION,
            C.Tag.CREATE_QUEST_REQUIREMENTS,
        )
        .forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_COUNTER)
        .assertTextEquals("0 / ${QuestLimits.TITLE_MAX_LENGTH}")
    listOf(
            C.Tag.CREATE_QUEST_TITLE_ERROR,
            C.Tag.CREATE_QUEST_DESCRIPTION_ERROR,
            C.Tag.CREATE_QUEST_REQUIREMENTS_ERROR,
        )
        .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
  }

  @Test
  fun typingUpdatesTheStateAndTheCounters() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).performTextInput("  Fox ")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_DESCRIPTION).performTextInput("A fox.")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS).performTextInput("A drink.")

    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_COUNTER)
        .assertTextEquals("3 / ${QuestLimits.TITLE_MAX_LENGTH}")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_DESCRIPTION_COUNTER)
        .performScrollTo()
        .assertTextEquals("6 / ${QuestLimits.DESCRIPTION_MAX_LENGTH}")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS_COUNTER)
        .performScrollTo()
        .assertTextEquals("8 / ${QuestLimits.REQUIREMENTS_MAX_LENGTH}")
    val state = viewModel.uiState.value
    assertEquals("  Fox ", state.title)
    assertEquals("A fox.", state.description)
    assertEquals("A drink.", state.requirements)
  }

  @Test
  fun clearingAFieldShowsTheBlankError() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS).performTextInput("A drink.")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS).performTextClearance()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REQUIREMENTS_ERROR)
        .performScrollTo()
        .assertTextEquals("This field is required.")
  }

  @Test
  fun tooLongTitleShowsTheLengthError() {
    val max = QuestLimits.TITLE_MAX_LENGTH
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE).performTextInput("a".repeat(max + 1))
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_COUNTER).assertTextEquals("${max + 1} / $max")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_TITLE_ERROR)
        .assertTextEquals("Use $max characters or fewer.")
  }

  @Test
  fun tooLongDescriptionShowsTheLengthError() {
    val max = QuestLimits.DESCRIPTION_MAX_LENGTH
    viewModel.updateDescription("a".repeat(max + 1))
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_DESCRIPTION_ERROR)
        .performScrollTo()
        .assertTextEquals("Use $max characters or fewer.")
  }

  @Test
  fun closeButtonCallsOnClose() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_CLOSE).performClick()
    assertEquals(1, closeCalls)
  }

  private fun preset(meters: Int) =
      compose.onNodeWithTag("${C.Tag.CREATE_QUEST_RADIUS_PRESET}_$meters")

  @Test
  fun showsTheDefaultCompletionRules() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_PHOTO).performScrollTo().assertIsSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_TEXT).assertIsNotSelected()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_EXPLORER_PREVIEW)
        .assertTextContains("Will come back to this once explorer view is done", substring = true)
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS)
        .performScrollTo()
        .assertTextEquals("${QuestLimits.DEFAULT_RADIUS_METERS} m")
    preset(QuestLimits.DEFAULT_RADIUS_METERS).assertIsSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_CUSTOM).assertIsNotSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_SLIDER).assertDoesNotExist()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_SIZE)
        .performScrollTo()
        .assertTextEquals("${QuestLimits.MIN_PARTY_SIZE}")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_DECREMENT).assertIsNotEnabled()
    compose.onAllNodesWithTag(C.Tag.CREATE_QUEST_PARTY_SEAT_FILLED).assertCountEquals(1)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_AT_LEAST).assertIsSelected()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXACTLY)
        .assertIsNotSelected()
        .assertTextContains("Only solo visits")
  }

  @Test
  fun tappingAProofTypeSelectsIt() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_TEXT).performScrollTo().performClick()
    assertEquals(ProofType.TEXT, viewModel.uiState.value.proofType)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_TEXT).assertIsSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PROOF_PHOTO).assertIsNotSelected()
  }

  @Test
  fun tappingAPresetSetsAndShowsTheRadius() {
    preset(100).performScrollTo().performClick()
    assertEquals(100, viewModel.uiState.value.radiusMeters)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS).assertTextEquals("100 m")
    preset(100).assertIsSelected()
    preset(QuestLimits.DEFAULT_RADIUS_METERS).assertIsNotSelected()
  }

  @Test
  fun customShowsTheSliderWhichSetsTheRadiusInWholeMeters() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_CUSTOM).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_CUSTOM).assertIsSelected()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_SLIDER)
        .performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(120.6f) }
    assertEquals(121, viewModel.uiState.value.radiusMeters)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS).assertTextEquals("121 m")
  }

  @Test
  fun stepperChangesThePartySizeItsSeatsAndRules() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_INCREMENT).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_INCREMENT).performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_DECREMENT).assertIsEnabled().performClick()
    assertEquals(QuestLimits.MIN_PARTY_SIZE + 1, viewModel.uiState.value.minPartySize)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_SIZE).assertTextEquals("2")
    compose.onAllNodesWithTag(C.Tag.CREATE_QUEST_PARTY_SEAT_FILLED).assertCountEquals(2)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_AT_LEAST).assertTextContains("At least 2")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXACTLY)
        .assertTextContains("Exactly 2")
        .assertTextContains("Only parties of 2")
  }

  @Test
  fun aPartyThatFillsTheSeatsShowsNoExtraCount() {
    repeat(7) { viewModel.incrementPartySize() }
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_SIZE).performScrollTo().assertTextEquals("8")
    compose.onAllNodesWithTag(C.Tag.CREATE_QUEST_PARTY_SEAT_FILLED).assertCountEquals(8)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXTRA).assertDoesNotExist()
  }

  @Test
  fun aLargerPartyCountsTheRestOnTheLastSeat() {
    repeat(10) { viewModel.incrementPartySize() }
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_INCREMENT).performScrollTo().assertIsEnabled()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_SIZE).assertTextEquals("11")
    compose.onAllNodesWithTag(C.Tag.CREATE_QUEST_PARTY_SEAT_FILLED).assertCountEquals(7)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXTRA).assertTextEquals("+4")
  }

  @Test
  fun tappingExactlySelectsIt() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXACTLY).performScrollTo().performClick()
    assertTrue(viewModel.uiState.value.exactPartySize)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_EXACTLY).assertIsSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_PARTY_AT_LEAST).assertIsNotSelected()
  }

  @Test
  fun rewardFieldsShowOnlyForThePickedType() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_NONE).performScrollTo().assertIsSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DISCOUNT).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_NONE).assertIsNotSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM).assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_FREE_ITEM).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DISCOUNT).assertIsNotSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_SPECIFICS).assertExists()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DESCRIPTION).assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_OTHER).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_FREE_ITEM).assertIsNotSelected()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DESCRIPTION).assertExists()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM).assertDoesNotExist()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_NONE).performScrollTo().performClick()
    assertNull(viewModel.uiState.value.reward.type)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DESCRIPTION).assertDoesNotExist()
  }

  @Test
  fun typingInTheRewardFieldsUpdatesTheReward() {
    viewModel.selectRewardType(RewardType.FREE_ITEM)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM).performScrollTo().performTextInput("Tea")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_SPECIFICS).performScrollTo()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_SPECIFICS).performTextInput("Any size")
    viewModel.selectRewardType(RewardType.OTHER)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DESCRIPTION).performScrollTo()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DESCRIPTION).performTextInput("A tour")

    val reward = viewModel.uiState.value.reward
    assertEquals(
        listOf("Tea", "Any size", "A tour"),
        listOf(reward.itemName, reward.specifics, reward.description),
    )
  }

  @Test
  fun clearingTheItemShowsItsError() {
    viewModel.selectRewardType(RewardType.FREE_ITEM)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM).performScrollTo().performTextInput("Tea")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM).performTextClearance()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_ITEM_ERROR)
        .performScrollTo()
        .assertTextEquals("This field is required.")
  }

  @Test
  fun discountShowsTheAmountErrorOfItsUnit() {
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_DISCOUNT).performScrollTo().performClick()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT)
        .performScrollTo()
        .performTextInput("120")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR)
        .performScrollTo()
        .assertTextEquals("Use 100 % or less.")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_UNIT_PERCENT).assertIsSelected()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_UNIT_CHF).performClick().assertIsSelected()
    assertEquals(DiscountUnit.CHF, viewModel.uiState.value.reward.discountUnit)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_SPECIFICS).assertExists()

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).performTextClearance()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).performTextInput("abc")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR)
        .assertTextEquals("Enter a number.")
  }

  @Test
  fun aClearedDiscountAmountIsRequired() {
    viewModel.selectRewardType(RewardType.DISCOUNT)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).performScrollTo().performTextInput("5")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).performTextClearance()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR)
        .performScrollTo()
        .assertTextEquals("This field is required.")
  }

  @Test
  @Config(qualifiers = "+fr")
  fun tooSmallAmountShowsTheMinimumWithThePhonesDecimalSeparator() {
    viewModel.selectRewardType(RewardType.DISCOUNT)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT).performScrollTo().performTextInput("0")
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_AMOUNT_ERROR)
        .performScrollTo()
        .assertTextEquals("Enter at least 0,01.")
  }

  @Test
  fun pickingAnExpiryShowsItAndClearingRemovesIt() {
    viewModel.selectRewardType(RewardType.OTHER)
    val today = LocalDate.now()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY_CONFIRM).performClick()
    // The picker starts at the day it opened, which is today unless midnight just passed
    assertTrue(viewModel.uiState.value.reward.expiryDate in listOf(today, today.plusDays(1)))

    viewModel.updateExpiryDate(LocalDate.of(2099, 12, 31))
    // In the phone's own order: month first in US English
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY).assertTextContains("Dec 31, 2099")
    compose.onNodeWithText("Set date").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY_CLEAR).performClick()
    assertNull(viewModel.uiState.value.reward.expiryDate)
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY)
        .assertTextContains("No expiry")
        .assertTextContains("Set date")
  }

  @Test
  fun aPastExpiryShowsItsError() {
    viewModel.selectRewardType(RewardType.OTHER)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY).performScrollTo()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY_ERROR).assertDoesNotExist()
    viewModel.updateExpiryDate(LocalDate.now().minusDays(1))
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY_ERROR)
        .performScrollTo()
        .assertTextEquals("Pick today or a later date.")
  }

  @Test
  @Config(qualifiers = "+en-rGB")
  fun expiryFollowsTheDayFirstOrderOfABritishPhone() {
    viewModel.selectRewardType(RewardType.OTHER)
    viewModel.updateExpiryDate(LocalDate.of(2099, 12, 31))
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_REWARD_EXPIRY)
        .performScrollTo()
        .assertTextContains("31 Dec 2099")
  }
}
