// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.model

import com.github.aroundteam2026.aroundapp.model.auth.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {
  @Test
  fun createsOneRepositoryOnFirstAccessAndReusesIt() {
    var creations = 0
    val repository = FakeAuthRepository()
    val container = AppContainer {
      creations++
      repository
    }
    assertEquals(0, creations)
    assertSame(repository, container.authRepository)
    assertSame(repository, container.authRepository)
    assertEquals(1, creations)
  }
}
