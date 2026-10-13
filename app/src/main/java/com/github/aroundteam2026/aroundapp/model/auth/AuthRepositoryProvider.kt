// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model.auth

/** Shares the provider's auth session and listener across screens and activity recreation. */
object AuthRepositoryProvider {
  val repository: AuthRepository by lazy { AuthRepositoryFirebase() }
}
