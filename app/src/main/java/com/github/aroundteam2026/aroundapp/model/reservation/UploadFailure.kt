// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/** Why a queued photo proof failed for good, as [ProofUploadQueue.failed] reports it. */
enum class UploadFailure {
  /** The upload or the submission kept failing, e.g. on a weak connection: a retry can pass. */
  RETRYABLE,

  /**
   * The security rules or a repository refused the proof: a retry would likely fail the same way.
   */
  REFUSED,

  /** The kept copy of the photo is gone: the explorer has to take a new one. */
  PHOTO_MISSING,
}
