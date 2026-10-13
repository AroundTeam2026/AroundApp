// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.aroundteam2026.aroundapp.model.user

/**
 * A user profile with a role that can be assigned once.
 *
 * It holds no email: Firebase Auth already has it, and only the owner can read this profile.
 *
 * @property createdAt the profile creation time in epoch milliseconds.
 */
data class User(
    val uid: String,
    val displayName: String,
    val role: Role?,
    val createdAt: Long,
)
