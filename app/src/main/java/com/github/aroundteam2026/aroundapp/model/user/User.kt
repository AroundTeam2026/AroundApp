// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.aroundteam2026.aroundapp.model.user

/**
 * A user profile with a role that can be assigned once.
 *
 * It holds no email: Firebase Auth already has it. Signed-in users can get a profile by uid, but
 * cannot list all profiles.
 *
 * @property createdAt the profile creation time in epoch milliseconds.
 * @property pictureUrl the Cloud Storage location of the profile picture, or null when unset.
 */
data class User(
    val uid: String,
    val displayName: String,
    val role: Role?,
    val createdAt: Long,
    val pictureUrl: String? = null,
)
