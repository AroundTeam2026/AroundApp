package com.github.aroundteam2026.aroundapp.model.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs under Robolectric because Firebase's exception constructors touch Android classes. */
@RunWith(AndroidJUnit4::class)
class AuthErrorMappingTest {

  @Test
  fun weakPasswordIsMappedBeforeItsParentClass() {
    val e = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short")
    assertEquals(AuthError.WeakPassword, e.toAuthError())
  }

  @Test
  fun collisionMeansEmailAlreadyInUse() {
    val e = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "taken")
    assertEquals(AuthError.EmailAlreadyInUse, e.toAuthError())
  }

  @Test
  fun malformedEmailIsInvalidEmail() {
    val e = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "bad email")
    assertEquals(AuthError.InvalidEmail, e.toAuthError())
  }

  @Test
  fun wrongPasswordAndUnknownUserBothMeanInvalidCredentials() {
    val wrongPassword = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad")
    val unknownUser = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "none")
    assertEquals(AuthError.InvalidCredentials, wrongPassword.toAuthError())
    assertEquals(AuthError.InvalidCredentials, unknownUser.toAuthError())
  }

  @Test
  fun networkFailureIsNetwork() {
    assertEquals(AuthError.Network, FirebaseNetworkException("offline").toAuthError())
  }

  @Test
  fun anythingElseIsUnknownAndKeepsTheCause() {
    val cause = IllegalStateException("boom")
    val error = cause.toAuthError()
    assertTrue(error is AuthError.Unknown)
    assertSame(cause, error.cause)
  }

  @Test
  fun anAuthErrorIsReturnedAsIs() {
    assertSame(AuthError.Network, AuthError.Network.toAuthError())
  }
}
