// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.venuepage

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AcceptQuestBar]: what each way accepting can stand shows, and what its button does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class AcceptQuestBarTest {
  @get:Rule val compose = createComposeRule()

  private var accepts = 0

  private fun show(state: VenuePageUiState) {
    compose.setContent { AroundAppTheme { AcceptQuestBar(state, onAccept = { accepts++ }) } }
  }

  private fun text(@StringRes id: Int, vararg args: Any) =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  private val button
    get() = compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BUTTON)

  @Test
  fun showsNothingWhileNoQuestIsPicked() {
    show(LOADED)

    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BAR).assertDoesNotExist()
  }

  @Test
  fun aPickedQuestShowsAnAcceptButtonThatAccepts() {
    show(LOADED.copy(selectedQuestId = "a"))

    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_BAR).assertIsDisplayed()
    button.assertIsEnabled().assertTextContains(text(R.string.accept_quest))
    button.performClick()

    assertEquals(1, accepts)
  }

  @Test
  fun acceptingShowsASkeletonInsteadOfASpinnerAndCannotBeTappedAgain() {
    show(LOADED.copy(selectedQuestId = "a", acceptingQuestIds = setOf("a")))

    button
        .assertIsNotEnabled()
        .assertContentDescriptionEquals(text(R.string.accept_quest_accepting))
    compose.onNodeWithText(text(R.string.accept_quest)).assertDoesNotExist()
    compose
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        .assertCountEquals(0)
    button.performClick()

    assertEquals(0, accepts)
  }

  @Test
  fun acceptedDisablesTheButtonAndSaysTheVenueIsToApprove() {
    show(LOADED.copy(selectedQuestId = "a", acceptedParties = mapOf("a" to 1)))

    button.assertIsNotEnabled().assertTextContains(text(R.string.accept_quest_accepted))
    button.performClick()

    assertEquals(0, accepts)
  }

  @Test
  fun aFailureShowsTheErrorAndAButtonToTryAgain() {
    show(LOADED.copy(selectedQuestId = "a", failedQuestIds = setOf("a")))

    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_FAILED).assertIsDisplayed()
    button.assertIsEnabled().assertTextContains(text(R.string.accept_quest_retry))
    button.performClick()

    assertEquals(1, accepts)
  }

  @Test
  fun aQuestThatNeedsMorePeopleSaysHowManyAndCanStillBeAccepted() {
    show(LOADED.copy(selectedQuestId = "b"))

    compose
        .onNodeWithTag(C.Tag.ACCEPT_QUEST_NEEDS_MORE)
        .assertTextContains(
            ApplicationProvider.getApplicationContext<Context>()
                .resources
                .getQuantityString(R.plurals.accept_quest_needs_more, 2, 2)
        )
    button.assertIsEnabled().performClick()

    assertEquals(1, accepts)
  }

  @Test
  fun aQuestThatNeedsNobodyElseSaysNothingAboutParties() {
    show(LOADED.copy(selectedQuestId = "a"))

    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_NEEDS_MORE).assertDoesNotExist()
  }

  @Test
  fun theNeedForMorePeopleStaysOnceTheQuestIsAccepted() {
    show(LOADED.copy(selectedQuestId = "b", acceptedParties = mapOf("b" to 2)))

    compose
        .onNodeWithTag(C.Tag.ACCEPT_QUEST_NEEDS_MORE)
        .assertTextContains(
            ApplicationProvider.getApplicationContext<Context>()
                .resources
                .getQuantityString(R.plurals.accept_quest_needs_more, 1, 1)
        )
  }

  @Test
  fun whenAcceptingIsUnavailableTheButtonIsDisabledAndWhyIsShown() {
    show(LOADED.copy(selectedQuestId = "a", acceptAvailable = false))

    compose.onNodeWithTag(C.Tag.ACCEPT_QUEST_UNAVAILABLE).assertIsDisplayed()
    button.assertIsNotEnabled().performClick()

    assertEquals(0, accepts)
  }

  private companion object {
    fun quest(id: String, minPartySize: Int) =
        VenuePageQuest(
            questId = id,
            title = "Quest $id",
            description = "",
            requirements = "",
            proofType = ProofType.PHOTO,
            minPartySize = minPartySize,
            reward = null,
        )

    /** Quest "a" needs just the explorer; "b" needs three people. */
    val LOADED =
        VenuePageUiState(
            status = VenuePageStatus.LOADED,
            venueName = "Café Lumen",
            quests = listOf(quest("a", minPartySize = 1), quest("b", minPartySize = 3)),
        )
  }
}
