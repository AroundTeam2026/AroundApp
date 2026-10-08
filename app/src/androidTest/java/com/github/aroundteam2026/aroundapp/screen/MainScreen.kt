// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.github.aroundteam2026.aroundapp.resources.C
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

class MainScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<MainScreen>(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag(C.Tag.APP) },
    ) {

  val navBar: KNode = child { hasTestTag(C.Tag.NAV_BAR) }

  val exploreTab: KNode = child { hasTestTag(C.Tag.EXPLORE_TAB) }
  val questsTab: KNode = child { hasTestTag(C.Tag.QUESTS_TAB) }
  val friendsTab: KNode = child { hasTestTag(C.Tag.FRIENDS_TAB) }
  val profileTab: KNode = child { hasTestTag(C.Tag.PROFILE_TAB) }

  val mapScreen: KNode = child { hasTestTag(C.Tag.MAP_SCREEN) }
  val questsScreen: KNode = child { hasTestTag(C.Tag.QUESTS_SCREEN) }
  val friendsScreen: KNode = child { hasTestTag(C.Tag.FRIENDS_SCREEN) }
  val profileScreen: KNode = child { hasTestTag(C.Tag.PROFILE_SCREEN) }

  val map: KNode = child { hasTestTag(C.Tag.MAP) }
}
