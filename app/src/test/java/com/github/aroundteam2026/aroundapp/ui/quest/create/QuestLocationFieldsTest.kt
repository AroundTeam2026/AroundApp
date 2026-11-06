// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.aroundteam2026.aroundapp.model.address.AddressSearchResult
import com.github.aroundteam2026.aroundapp.model.address.AddressSuggestion
import com.github.aroundteam2026.aroundapp.model.address.FakeAddressSearchRepository
import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.FakeQuestRepository
import com.github.aroundteam2026.aroundapp.model.testVenue
import com.github.aroundteam2026.aroundapp.model.venue.FakeVenueRepository
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class QuestLocationFieldsTest {
  @get:Rule val compose = createComposeRule()

  private fun showForm(
      state: CreateQuestUiState,
      actions: QuestLocationActions = QuestLocationActions(),
  ) {
    compose.setContent {
      AroundAppTheme {
        CreateQuestContent(
            state = state,
            onTitleChange = {},
            onDescriptionChange = {},
            onRequirementsChange = {},
            completionActions = QuestCompletionActions(location = actions),
            rewardActions = QuestRewardActions(),
            onSubmit = {},
            onClose = {},
            onRetryVenue = {},
        )
      }
    }
  }

  @Test
  fun theQuestHappensAtTheVenueUntilTheVenueChoosesElsewhere() {
    showForm(AT_VENUE)
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_AT_VENUE).performScrollTo().assertIsSelected()
    compose.onNodeWithText("At Café Lumière").assertExists()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_ELSEWHERE).assertIsNotSelected()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_HINT)
        .assertTextEquals("Explorers find the quest at your venue's pin.")
    compose.onNodeWithTag(C.Tag.VENUE_ADDRESS_FIELD).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_WALK_TIME).assertExists()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_DISTANCE_FROM_VENUE).assertDoesNotExist()
  }

  @Test
  fun theChoicesCallTheirActions() {
    var atVenue = 0
    var elsewhere = 0
    showForm(
        AT_VENUE,
        QuestLocationActions(onAtVenue = { atVenue++ }, onElsewhere = { elsewhere++ }),
    )
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_ELSEWHERE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_AT_VENUE).performClick()
    assertEquals(1, elsewhere)
    assertEquals(1, atVenue)
  }

  @Test
  fun aQuestElsewhereShowsTheSearchAndHowFarItIsFromTheVenue() {
    var atVenue = 0
    showForm(ELSEWHERE, QuestLocationActions(onAtVenue = { atVenue++ }))
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_ELSEWHERE)
        .performScrollTo()
        .assertIsSelected()
    compose.onNodeWithText("Search for the quest's address").assertExists()
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_HINT)
        .assertTextEquals(
            "Tap the map or drag the pin to the exact spot. Explorers find the quest there and " +
                "send their proof from inside its radius."
        )
    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_DISTANCE_FROM_VENUE)
        .assertTextEquals("650 m from Café Lumière")
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_WALK_TIME).assertDoesNotExist()

    compose
        .onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_USE_VENUE)
        .performScrollTo()
        .assertTextEquals("Use Café Lumière's location")
        .performClick()
    assertEquals(1, atVenue)
  }

  @Test
  fun noChoiceShowsBeforeTheVenueIsLoaded() {
    showForm(CreateQuestUiState())
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_AT_VENUE).assertDoesNotExist()
  }

  @Test
  fun theFormMovesTheQuestToAPickedAddressAndBack() {
    val addresses = FakeAddressSearchRepository { AddressSearchResult.Found(listOf(MONTBENON)) }
    val venue = testVenue(id = VENUE_ID, name = "Café Lumière", location = VENUE)
    val viewModel =
        CreateQuestViewModel(
            FakeAuthRepository(VENUE_ID),
            FakeVenueRepository(listOf(venue)),
            FakeQuestRepository(),
            addressSearchRepository = addresses,
        )
    compose.setContent {
      AroundAppTheme { CreateQuestScreen(viewModel, onClose = {}, onCreated = {}) }
    }

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_ELSEWHERE).performScrollTo().performClick()
    compose.onNodeWithTag(C.Tag.VENUE_ADDRESS_FIELD).performScrollTo().performTextInput("Esp")
    compose.onNodeWithTag(C.Tag.VENUE_ADDRESS_FIELD).performImeAction()
    // The suggestions scroll into view on their own
    compose.onNodeWithTag(C.Tag.VENUE_ADDRESS_RESULT).assertIsDisplayed().performClick()
    assertFalse(viewModel.uiState.value.atVenue)
    assertEquals(MONTBENON.location, viewModel.uiState.value.location)

    compose.onNodeWithTag(C.Tag.CREATE_QUEST_LOCATION_USE_VENUE).performScrollTo().performClick()
    assertTrue(viewModel.uiState.value.atVenue)
    assertEquals(VENUE, viewModel.uiState.value.location)
  }

  private companion object {
    const val VENUE_ID = "venue-1"
    val VENUE = Location(46.5220, 6.6330)
    /** 651 m south-west of [VENUE]. */
    val SPOT = Location(46.5200, 6.6250)
    val MONTBENON =
        AddressSuggestion("Esplanade de Montbenon", "1003 Lausanne", Location(46.5208, 6.6267))
    val AT_VENUE = CreateQuestUiState(venueName = "Café Lumière", venueLocation = VENUE)
    val ELSEWHERE = AT_VENUE.copy(questLocation = SPOT)
  }
}
