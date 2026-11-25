// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.reservation

/** Why a queued photo proof failed for good, as [ProofUploadQueue.failed] reports it. */
enum class UploadFailure {
  /** Kept failing, e.g. on a weak connection: a retry can pass. */
  RETRYABLE,

  /** Refused by the rules or a repository: a retry would fail the same way. */
  REFUSED,

  /** The photo's copy is gone: a new photo is needed. */
  PHOTO_MISSING,
}
