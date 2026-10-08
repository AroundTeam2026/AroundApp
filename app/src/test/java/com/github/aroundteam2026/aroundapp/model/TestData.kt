// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestLimits
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.venue.Venue

/** Builds a valid, active quest; tests override only the fields they care about. */
fun testQuest(
    id: String = "quest",
    venueId: String = "venue",
    venueName: String = "Café Lumen",
    location: Location = Location(46.5220, 6.6330),
    title: String = "Order the secret menu",
    reward: Reward? = Reward.FreeItem("Coffee", specifics = null),
    minPartySize: Int = QuestLimits.MIN_PARTY_SIZE,
    status: QuestStatus = QuestStatus.ACTIVE,
    createdAt: Long = 1_000L,
    updatedAt: Long = createdAt,
) =
    Quest(
        id = id,
        venueId = venueId,
        venueName = venueName,
        location = location,
        radiusMeters = 50,
        title = title,
        description = "Description",
        requirements = "Requirements",
        proofType = ProofType.PHOTO,
        minPartySize = minPartySize,
        reward = reward,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** A reward that stops being valid at [expiresAt]. */
fun rewardExpiringAt(expiresAt: Long) = Reward.Other("Free dessert", expiresAt = expiresAt)

/** Builds a placed venue; tests override only the fields they care about. */
fun testVenue(
    id: String = "venue",
    name: String = "Café Lumen",
    location: Location? = Location(46.5220, 6.6330),
    address: String? = null,
    featuredQuestId: String? = null,
) =
    Venue(
        id = id,
        name = name,
        location = location,
        radiusMeters = 50,
        address = address,
        createdAt = 1_000L,
        featuredQuestId = featuredQuestId,
    )
