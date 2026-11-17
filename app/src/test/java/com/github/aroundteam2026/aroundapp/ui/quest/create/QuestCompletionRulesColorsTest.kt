// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.map.marker.textLayout
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme
import com.github.aroundteam2026.aroundapp.ui.theme.AroundColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the radius and party size values stay readable: purple on the light theme, cream on the
 * dark one, where purple would vanish into the background.
 */
@RunWith(AndroidJUnit4::class)
class QuestCompletionRulesColorsTest {
  @get:Rule val compose = createComposeRule()

  private fun show(dark: Boolean) {
    compose.setContent {
      AroundAppTheme(darkTheme = dark) {
        QuestCompletionRules(CreateQuestUiState(), QuestCompletionActions())
      }
    }
  }

  private fun colorOf(tag: String): Color =
      compose.onNodeWithTag(tag, useUnmergedTree = true).textLayout().layoutInput.style.color

  @Test
  fun theValuesArePurpleInLightMode() {
    show(dark = false)

    assertEquals(AroundColors.Purple, colorOf(C.Tag.CREATE_QUEST_RADIUS))
    assertEquals(AroundColors.Purple, colorOf(C.Tag.CREATE_QUEST_PARTY_SIZE))
  }

  @Test
  fun theValuesAreCreamInDarkMode() {
    show(dark = true)

    assertEquals(AroundColors.Cream, colorOf(C.Tag.CREATE_QUEST_RADIUS))
    assertEquals(AroundColors.Cream, colorOf(C.Tag.CREATE_QUEST_PARTY_SIZE))
  }
}
