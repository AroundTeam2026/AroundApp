// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.quest

/**
 * Validation limits for [Quest] fields, shared by the create-quest form and its ViewModel.
 *
 * The length limits are placeholders until the team agrees on them (open decision 6 in the
 * Firestore schema).
 */
object QuestLimits {
  /** Maximum number of characters in [Quest.title]. */
  const val TITLE_MAX_LENGTH = 60

  /** Maximum number of characters in [Quest.description]. */
  const val DESCRIPTION_MAX_LENGTH = 500

  /** Maximum number of characters in [Quest.requirements]. */
  const val REQUIREMENTS_MAX_LENGTH = 300

  /** Smallest allowed [Quest.minPartySize]: a solo visit. */
  const val MIN_PARTY_SIZE = 1

  /** Smallest allowed [Quest.radiusMeters]. */
  const val MIN_RADIUS_METERS = 20

  /** Largest allowed [Quest.radiusMeters]. */
  const val MAX_RADIUS_METERS = 200
}
