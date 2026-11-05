// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.net.Uri

/**
 * Routes of the screens added in Sprint 2, kept in one place so that every screen navigates with
 * the same names.
 *
 * Each route starts as a "Coming soon" stub. The task that builds a screen replaces its stub, and
 * the route stays the same, so nothing that navigates to it has to change.
 *
 * Explorer routes are registered in the explorer's NavHost, [AroundApp]. Venue routes belong to the
 * venue's own NavHost, so an explorer can't reach them. [venueDestinations] registers the home, the
 * create-quest form and the Review proof screen there; the dashboard is still a tab of [VenueHome],
 * and its route is registered when its screen is built.
 */
object Routes {
  /** Argument of [VENUE_PAGE]: the venue's id, equal to its owner's uid. */
  const val VENUE_ID = "venueId"

  private const val VENUE_PAGE_PREFIX = "venue_page"

  /** A venue's page, with its quests. Build the route to one venue with [venuePage]. */
  const val VENUE_PAGE = "$VENUE_PAGE_PREFIX/{$VENUE_ID}"

  /** The venue's home, with its tabs ([VenueHome]). */
  const val VENUE_HOME = "venue_home"

  /** The form where a venue creates a quest. */
  const val CREATE_QUEST = "create_quest"

  /** The venue's dashboard. */
  const val DASHBOARD = "venue_dashboard"

  /** Argument of [REVIEW_PROOF]: the id of the completion whose proof the venue reviews. */
  const val COMPLETION_ID = "completionId"

  private const val REVIEW_PROOF_PREFIX = "review_proof"

  /** The screen where a venue reviews one proof. Build the route to one with [reviewProof]. */
  const val REVIEW_PROOF = "$REVIEW_PROOF_PREFIX/{$COMPLETION_ID}"

  /** Routes of the explorer's screens, registered in [AroundApp]. */
  val EXPLORER = listOf(VENUE_PAGE)

  /** Routes of the venue's screens, for the venue's own NavHost. */
  val VENUE = listOf(VENUE_HOME, CREATE_QUEST, DASHBOARD, REVIEW_PROOF)

  /** Every route above. */
  val ALL = EXPLORER + VENUE

  /**
   * The route to one venue's page.
   *
   * @param venueId id of the venue; it is encoded, so it may contain any character.
   * @throws IllegalArgumentException if [venueId] is blank, which no route could match.
   */
  fun venuePage(venueId: String): String {
    require(venueId.isNotBlank()) { "A venue page needs a venue id" }
    return "$VENUE_PAGE_PREFIX/${Uri.encode(venueId)}"
  }

  /**
   * The route to the Review proof screen of one completion.
   *
   * @param completionId id of the completion; it is encoded, so it may contain any character.
   * @throws IllegalArgumentException if [completionId] is blank, which no route could match.
   */
  fun reviewProof(completionId: String): String {
    require(completionId.isNotBlank()) { "A proof to review needs a completion id" }
    return "$REVIEW_PROOF_PREFIX/${Uri.encode(completionId)}"
  }
}
