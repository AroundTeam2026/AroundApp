// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.navigation

import android.net.Uri

/**
 * Routes of the screens added in Sprint 2, kept in one place so that every screen navigates with
 * the same names.
 *
 * Each route starts as a "Coming soon" stub that the screen's task replaces, keeping the route.
 * Explorer routes are registered in [AroundApp]'s NavHost. Venue routes belong to the venue's own
 * NavHost, so an explorer can't reach them; [venueDestinations] registers them there, while the
 * dashboard is still a tab of [VenueHome].
 */
object Routes {
  /** Argument of [VENUE_PAGE]: the venue's id, equal to its owner's uid. */
  const val VENUE_ID = "venueId"

  private const val VENUE_PAGE_PREFIX = "venue_page"

  /** A venue's page, with its quests. Build the route to one venue with [venuePage]. */
  const val VENUE_PAGE = "$VENUE_PAGE_PREFIX/{$VENUE_ID}"

  /** Argument of [COMPLETE_QUEST]: the id of the reservation whose quest the explorer completes. */
  const val RESERVATION_ID = "reservationId"

  /** Argument of [COMPLETE_QUEST]: the name of the quest's venue, which the screen shows. */
  const val VENUE_NAME = "venueName"

  private const val COMPLETE_QUEST_PREFIX = "complete_quest"

  /**
   * The screen where the explorer sends the proof of an approved reservation's quest. Build the
   * route to one reservation with [completeQuest].
   */
  const val COMPLETE_QUEST = "$COMPLETE_QUEST_PREFIX/{$RESERVATION_ID}?$VENUE_NAME={$VENUE_NAME}"

  /** The venue's home, with its tabs ([VenueHome]). */
  const val VENUE_HOME = "venue_home"

  /** The form where a venue creates a quest. */
  const val CREATE_QUEST = "create_quest"

  /** The venue's dashboard. */
  const val DASHBOARD = "venue_dashboard"

  /** Routes of the explorer's screens, registered in [AroundApp]. */
  val EXPLORER = listOf(VENUE_PAGE, COMPLETE_QUEST)

  /** Routes of the venue's screens, for the venue's own NavHost. */
  val VENUE = listOf(VENUE_HOME, CREATE_QUEST, DASHBOARD)

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
   * The route to completing the quest of one reservation.
   *
   * @param reservationId id of the approved reservation; it is encoded, so it may contain any
   *   character.
   * @param venueName name of the quest's venue, shown on the screen; encoded too.
   * @throws IllegalArgumentException if [reservationId] is blank, which no route could match.
   */
  fun completeQuest(reservationId: String, venueName: String): String {
    require(reservationId.isNotBlank()) { "Completing a quest needs a reservation id" }
    return "$COMPLETE_QUEST_PREFIX/${Uri.encode(reservationId)}?$VENUE_NAME=${Uri.encode(venueName)}"
  }
}
