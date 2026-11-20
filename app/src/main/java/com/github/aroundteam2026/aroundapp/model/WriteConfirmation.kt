// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

/**
 * Whether the server answered a write a repository made, which waits only a few seconds for it: the
 * write succeeded either way, but a queued one may still be refused when the server gets it.
 */
enum class WriteConfirmation {
  /** The server accepted the write. */
  CONFIRMED,
  /** The server hasn't answered, e.g. the device is offline: Firestore sends the write later. */
  QUEUED,
}
