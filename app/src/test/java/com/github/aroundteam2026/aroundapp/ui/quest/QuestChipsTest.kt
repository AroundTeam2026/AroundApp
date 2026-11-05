// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.venuepage.venueQuestChipStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestChipsTest {
  @get:Rule val compose = createComposeRule()

  private fun show(exactPartySize: Boolean) {
    compose.setContent {
      AroundAppTheme {
        QuestChips(
            reward = null,
            minPartySize = 3,
            distanceMeters = null,
            style = venueQuestChipStyle(),
            exactPartySize = exactPartySize,
        )
      }
    }
  }

  private fun partyChip() = compose.onNodeWithTag(C.Tag.QUEST_CHIP_PARTY, useUnmergedTree = true)

  @Test
  fun anExactPartyHasNoPlus() {
    show(exactPartySize = true)

    partyChip().assertTextEquals("Groups of 3")
  }

  @Test
  fun aMinimumPartyHasAPlus() {
    show(exactPartySize = false)

    partyChip().assertTextEquals("Groups of 3+")
  }
}
