// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.model.auth

/** Shared local validation rules for email/password authentication. */
object AuthInputValidation {
  /** Minimum password length for account creation. */
  const val MIN_PASSWORD_LENGTH = 6

  private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

  /** Checks email syntax, ignoring surrounding whitespace. */
  fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())
}
