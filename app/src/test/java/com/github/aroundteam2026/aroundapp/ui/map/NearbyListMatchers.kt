// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C
import com.github.aroundteam2026.aroundapp.ui.quest.TestAvatar

/** The node tagged [tag] in the nearby list's card of the quest [questId], e.g. its pin. */
fun SemanticsNodeInteractionsProvider.inQuest(
    questId: String,
    tag: String,
): SemanticsNodeInteraction =
    onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("${C.Tag.NEARBY_QUEST}_$questId")))

/** What the nearby list's header says under its title: how many quests, [count], then [rest]. */
fun nearbyHeader(context: Context, count: Int, rest: String): String =
    context.getString(
        R.string.map_nearby_pair,
        context.resources.getQuantityString(R.plurals.map_nearby_count, count, count),
        rest,
    )

/** How far this list is scrolled, in pixels, from 0 at its top. */
fun SemanticsNodeInteraction.scrolled(): Float =
    fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

/** [count] nearby quests, q1 to q[count], each at a venue of its own. */
fun nearbyQuests(count: Int): List<NearbyQuest> =
    (1..count).map { n ->
      NearbyQuest(
          venueId = "venue-q$n",
          venueName = "Venue q$n",
          avatar = TestAvatar("q$n"),
          questId = "q$n",
          title = "Quest q$n",
          reward = null,
          minPartySize = 1,
          exactPartySize = false,
          distanceMeters = null,
      )
    }
