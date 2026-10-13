// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.navigation.ComingSoonScreen

/**
 * The overview of quests, which the Quests tab shows. It is the tab's own screen rather than a
 * route, so the Quests tab stays selected while it is shown. Until it is built, it is a "Coming
 * soon" stub.
 *
 * @param modifier modifier applied to the overview.
 */
@Composable
fun OverviewScreen(modifier: Modifier = Modifier) {
  ComingSoonScreen(R.string.overview_title, C.Tag.OVERVIEW_STUB, modifier)
}
