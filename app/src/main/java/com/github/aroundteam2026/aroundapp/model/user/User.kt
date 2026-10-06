// Co-authored-by: OpenAI Codex

package com.github.aroundteam2026.aroundapp.model.user

/**
 * A user profile with a role that can be assigned once.
 *
 * @property createdAt the profile creation time in epoch milliseconds.
 */
data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: Role?,
    val createdAt: Long,
)
