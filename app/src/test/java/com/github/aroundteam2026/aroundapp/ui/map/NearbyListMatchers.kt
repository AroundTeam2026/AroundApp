// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.map

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.resources.C

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
