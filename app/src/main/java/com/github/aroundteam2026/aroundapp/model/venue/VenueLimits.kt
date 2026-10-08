// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.model.venue

/**
 * Limits and default for [Venue.radiusMeters]. The limits are enforced by the future ViewModel, not
 * by the repository.
 *
 * The values are placeholders until the team agrees on the range (open decision 6 of the Firestore
 * schema draft, PR #25).
 */
object VenueLimits {
  /** Maximum length of a trimmed business name entered during initialization. */
  const val MAX_NAME_LENGTH = 100

  /** Smallest allowed [Venue.radiusMeters]. */
  const val MIN_RADIUS_METERS = 20

  /** Largest allowed [Venue.radiusMeters]. */
  const val MAX_RADIUS_METERS = 200

  /**
   * [Venue.radiusMeters] for the caller to pass when creating a venue that has not defined its own
   * area yet. The repository does not apply it.
   */
  const val DEFAULT_RADIUS_METERS = 50
}
