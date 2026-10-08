// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.model.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputValidationRulesTest {
  @Test
  fun acceptsEmailsWithSurroundingWhitespace() {
    for (email in listOf("ada@around.test", "  Ada@around.test  ")) {
      assertTrue(email, AuthInputValidation.isValidEmail(email))
    }
  }

  @Test
  fun rejectsMalformedEmailsAndInternalWhitespace() {
    for (email in listOf("", " ", "ada", "ada@around", "@around.test", "ada @around.test")) {
      assertFalse(email, AuthInputValidation.isValidEmail(email))
    }
  }
}
