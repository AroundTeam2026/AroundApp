// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model

import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryFirebase

/** App-scoped dependencies; the auth listener and session are shared across activity recreation. */
class AppContainer(createAuthRepository: () -> AuthRepository = { AuthRepositoryFirebase() }) {
  val authRepository: AuthRepository by lazy(createAuthRepository)
}
