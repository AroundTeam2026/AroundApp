// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.presence.ExplorerPresence
import com.github.aroundteam2026.aroundapp.model.user.Role
import com.github.aroundteam2026.aroundapp.model.user.User
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests [nearbyExplorers]: who the Explorers mode lists, and in which order. */
class NearbyExplorersTest {

  private val now = 1_000_000L
  private val later = now + 60 * 60 * 1000L
  /** The user, at the Place de la Palud in Lausanne. */
  private val here = Location(46.5210, 6.6330)

  /** Someone [northMeters] north of the user, visible until [until]. */
  private fun presence(uid: String, northMeters: Double, until: Long = later) =
      ExplorerPresence(uid, Location(here.lat + northMeters / 111_195.0, here.lng), until, now)

  private fun explorer(uid: String, name: String = uid, picture: String? = null) =
      User(uid, name, Role.EXPLORER, createdAt = 0L, pictureUrl = picture)

  private fun listed(presences: List<ExplorerPresence>, profiles: Map<String, User?>) =
      nearbyExplorers(presences, profiles, me = "me", from = here, now = now)

  @Test
  fun listsTheExplorersNearestFirstWithTheirProfiles() {
    val rows =
        listed(
            listOf(presence("far", 900.0), presence("near", 100.0)),
            mapOf("far" to explorer("far", "Lea"), "near" to explorer("near", "Noé", "pics/noe")),
        )

    assertEquals(listOf("near", "far"), rows.map { it.uid })
    val near = rows.first()
    assertEquals("Noé", near.name)
    assertEquals("pics/noe", near.pictureUrl)
    assertEquals(later, near.availableUntil)
    assertEquals(100.0, near.distanceMeters!!, 1.0)
  }

  @Test
  fun leavesOutTheUserThemself() {
    val rows =
        listed(
            listOf(presence("me", 0.0), presence("other", 50.0)),
            mapOf("me" to explorer("me"), "other" to explorer("other")),
        )

    assertEquals(listOf("other"), rows.map { it.uid })
  }

  @Test
  fun leavesOutWhoeverIsNoLongerAvailable() {
    val rows =
        listed(
            listOf(
                presence("ended", 50.0, until = now - 1),
                presence("endsNow", 60.0, until = now),
                presence("stays", 70.0, until = now + 1),
            ),
            listOf("ended", "endsNow", "stays").associateWith { explorer(it) },
        )

    assertEquals(listOf("stays"), rows.map { it.uid })
  }

  @Test
  fun leavesOutWhoeverHasNoExplorerProfile() {
    val rows =
        listed(
            listOf(
                presence("missing", 10.0),
                presence("unreadable", 20.0),
                presence("venue", 30.0),
                presence("noRole", 40.0),
                presence("explorer", 50.0),
            ),
            mapOf(
                "unreadable" to null,
                "venue" to explorer("venue").copy(role = Role.VENUE),
                "noRole" to explorer("noRole").copy(role = null),
                "explorer" to explorer("explorer"),
            ),
        )

    assertEquals(listOf("explorer"), rows.map { it.uid })
  }

  @Test
  fun leavesOutWhoeverIsBeyondTheNearbyRadius() {
    val rows =
        listed(
            listOf(presence("inside", 4_900.0), presence("outside", 5_100.0)),
            mapOf("inside" to explorer("inside"), "outside" to explorer("outside")),
        )

    assertEquals(listOf("inside"), rows.map { it.uid })
  }

  @Test
  fun explorersAtTheSameDistanceAreOrderedByUid() {
    val rows =
        listed(
            listOf(presence("b", 100.0), presence("a", 100.0)),
            mapOf("a" to explorer("a"), "b" to explorer("b")),
        )

    assertEquals(listOf("a", "b"), rows.map { it.uid })
  }
}
