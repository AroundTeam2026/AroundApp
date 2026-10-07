// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.demo

import com.github.aroundteam2026.aroundapp.model.common.Location
import com.github.aroundteam2026.aroundapp.model.quest.ProofType
import com.github.aroundteam2026.aroundapp.model.quest.Quest
import com.github.aroundteam2026.aroundapp.model.quest.QuestStatus
import com.github.aroundteam2026.aroundapp.model.quest.Reward
import com.github.aroundteam2026.aroundapp.model.venue.Venue

/**
 * Made-up venues and quests, which the map shows until it reads the real ones from Firestore. Most
 * are around Lausanne, where the map opens, and show every case the map handles: a venue featuring
 * an older quest, one with several quests, group quests, quests with and without rewards, an
 * expired reward and a draft, which the map hides, and a venue in Geneva, out of view at first.
 */
object MapDemoData {
  private const val HOUR = 60 * 60 * 1000L
  private const val DAY = 24 * HOUR

  private val lumen = venue("demo-lumen", "Café Lumen", Location(46.5220, 6.6330), "demo-lumen-fox")
  private val quais = venue("demo-quais", "Brasserie des Quais", Location(46.5078, 6.6275))
  private val nocturne = venue("demo-nocturne", "Bar Nocturne", Location(46.5235, 6.6365))
  private val riviera = venue("demo-riviera", "Gelateria Riviera", Location(46.5150, 6.6450))
  private val atelier = venue("demo-atelier", "Atelier Genève", Location(46.2044, 6.1432))

  /** The demo venues. */
  fun venues(): List<Venue> = listOf(lumen, quais, nocturne, riviera, atelier)

  /** The demo quests, with times relative to [now], in epoch milliseconds. */
  fun quests(now: Long): List<Quest> =
      listOf(
          quest(
              lumen,
              "demo-lumen-fox",
              "Find the hidden fox",
              now - 10 * DAY,
              reward("Free coffee"),
          ),
          quest(
              lumen,
              "demo-lumen-menu",
              "Order from the secret menu",
              now - DAY,
              reward("Free cookie"),
          ),
          quest(
              lumen,
              "demo-lumen-latte",
              "Photograph your latte art",
              now - 3 * DAY,
              reward = null,
          ),
          quest(
              lumen,
              "demo-lumen-brunch",
              "Join the Sunday brunch club",
              now - 2 * HOUR,
              reward("Free juice"),
              status = QuestStatus.DRAFT,
          ),
          quest(
              quais,
              "demo-quais-table",
              "Come as a party of six",
              now - 2 * DAY,
              reward("Free dessert for the table"),
              minPartySize = 6,
          ),
          quest(
              nocturne,
              "demo-nocturne-quiz",
              "Win a round of the pub quiz",
              now - 5 * DAY,
              reward("Free drink", expiresAt = now + 7 * DAY),
              minPartySize = 2,
          ),
          quest(
              nocturne,
              "demo-nocturne-neon",
              "Snap the neon sign",
              now - DAY,
              reward("Half-price cocktail", expiresAt = now - HOUR),
          ),
          quest(
              riviera,
              "demo-riviera-flavour",
              "Taste the flavour of the week",
              now - 4 * DAY,
              reward = null,
          ),
          quest(
              atelier,
              "demo-atelier-sketch",
              "Sketch the view from the terrace",
              now - 6 * DAY,
              reward("Free tea"),
          ),
      )

  private fun venue(id: String, name: String, location: Location, featuredQuestId: String? = null) =
      Venue(
          id = id,
          name = name,
          location = location,
          radiusMeters = 50,
          address = null,
          createdAt = 0L,
          featuredQuestId = featuredQuestId,
      )

  private fun reward(description: String, expiresAt: Long? = null) =
      Reward(description, terms = null, expiresAt = expiresAt)

  private fun quest(
      venue: Venue,
      id: String,
      title: String,
      createdAt: Long,
      reward: Reward?,
      minPartySize: Int = 1,
      status: QuestStatus = QuestStatus.ACTIVE,
  ) =
      Quest(
          id = id,
          venueId = venue.id,
          venueName = venue.name,
          location = venue.location!!,
          radiusMeters = venue.radiusMeters,
          title = title,
          description = "A demo quest at ${venue.name}.",
          requirements = title,
          proofType = ProofType.PHOTO,
          minPartySize = minPartySize,
          reward = reward,
          status = status,
          createdAt = createdAt,
          updatedAt = createdAt,
      )
}
