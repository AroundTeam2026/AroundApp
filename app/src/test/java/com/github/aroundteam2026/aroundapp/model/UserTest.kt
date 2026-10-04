package com.github.aroundteam2026.aroundapp.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserTest {

  @Test
  fun user_withNoRole_hasExpectedValues() {
    val createdAt = Timestamp(1000, 0)

    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = null,
            createdAt = createdAt,
        )

    assertEquals("user-1", user.uid)
    assertEquals("user@example.com", user.email)
    assertEquals("Test User", user.displayName)
    assertNull(user.role)
    assertEquals(createdAt, user.createdAt)
  }

  @Test
  fun user_withRole_hasExpectedRole() {
    val user =
        User(
            uid = "user-1",
            email = "user@example.com",
            displayName = "Test User",
            role = Role.EXPLORER,
            createdAt = Timestamp(1000, 0),
        )

    assertEquals(Role.EXPLORER, user.role)
  }
}
