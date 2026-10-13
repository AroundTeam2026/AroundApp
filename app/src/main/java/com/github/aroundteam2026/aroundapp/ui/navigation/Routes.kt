// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.net.Uri

/**
 * Routes of the screens added in Sprint 2, kept in one place so that every screen navigates with
 * the same names.
 *
 * Each route is registered in the app's NavHost and starts as a "Coming soon" stub. The task that
 * builds a screen replaces its stub, and the route stays the same, so nothing that navigates to it
 * has to change.
 */
object Routes {
  /** Argument of [VENUE_PAGE]: the venue's id, equal to its owner's uid. */
  const val VENUE_ID = "venueId"

  /** A venue's page, with its quests. Build the route to one venue with [venuePage]. */
  const val VENUE_PAGE = "venue_page/{venueId}"

  /** The form where a venue creates a quest. */
  const val CREATE_QUEST = "create_quest"

  /** The venue's dashboard. */
  const val DASHBOARD = "venue_dashboard"

  /** The overview of quests shown in the Quests tab. */
  const val OVERVIEW = "quests_overview"

  /** Every route above, as registered in the NavHost. */
  val ALL = listOf(VENUE_PAGE, CREATE_QUEST, DASHBOARD, OVERVIEW)

  /**
   * The route to one venue's page.
   *
   * @param venueId id of the venue; it is encoded, so it may contain any character.
   */
  fun venuePage(venueId: String) = "venue_page/${Uri.encode(venueId)}"
}
