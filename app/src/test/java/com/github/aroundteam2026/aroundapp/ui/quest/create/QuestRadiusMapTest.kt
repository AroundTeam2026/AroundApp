// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w413dp-h917dp-mdpi")
class QuestRadiusMapTest {
  @get:Rule val compose = createComposeRule()

  private fun showForm(state: CreateQuestUiState) {
    compose.setContent {
      AroundAppTheme {
        CreateQuestContent(
            state = state,
            onTitleChange = {},
            onDescriptionChange = {},
            onRequirementsChange = {},
            completionActions = QuestCompletionActions(),
            rewardActions = QuestRewardActions(),
            onClose = {},
        )
      }
    }
  }

  @Test
  fun showsTheMapAndTheWalkTimeOnceTheVenueLocationIsKnown() {
    showForm(CreateQuestUiState(radiusMeters = 200, venueLocation = Location(46.5220, 6.6330)))
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_MAP).performScrollTo()
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_WALK_TIME).assertTextEquals("≈ 3 min walk")
  }

  @Test
  fun hidesTheMapWithoutAVenueLocation() {
    showForm(CreateQuestUiState())
    compose.onNodeWithTag(C.Tag.CREATE_QUEST_RADIUS_MAP).assertDoesNotExist()
  }
}
