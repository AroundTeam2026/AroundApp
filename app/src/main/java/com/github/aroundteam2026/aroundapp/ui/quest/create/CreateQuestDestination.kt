// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.quest.create

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.ComingSoonScreen
import com.github.aroundteam2026.aroundapp.ui.navigation.Routes

/** Registers the form where a venue creates a quest. Until it is built, it shows a stub. */
fun NavGraphBuilder.createQuestDestination() {
  composable(Routes.CREATE_QUEST) {
    ComingSoonScreen(R.string.create_quest_heading, C.Tag.CREATE_QUEST_STUB)
  }
}

/** Opens the create-quest form, without stacking it again if it is already on top. */
fun NavController.navigateToCreateQuest() = navigate(Routes.CREATE_QUEST) { launchSingleTop = true }
