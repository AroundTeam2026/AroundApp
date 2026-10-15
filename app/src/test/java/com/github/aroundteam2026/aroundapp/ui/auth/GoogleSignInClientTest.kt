// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleSignInClientTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager = mockk<CredentialManager>()

  private fun client(clientId: String? = "web-client") =
      CredentialManagerGoogleSignInClient(context, clientId, manager)

  @Test
  fun missingConfigurationNeverOpensTheChooser() = runBlocking {
    assertEquals(GoogleCredentialResult.Misconfigured, client(null).requestToken())
    coVerify(exactly = 0) { manager.getCredential(any(), any<GetCredentialRequest>()) }
  }

  @Test
  fun explicitButtonRequestUsesWebClientAndParsesGoogleToken() = runBlocking {
    // The SDK parses JWT claims; this unsigned fixture never reaches Firebase.
    val payload =
        Base64.encodeToString(
            """{"sub":"test-subject","email":"ada@around.test"}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    val idToken = "header.$payload.signature"
    val token =
        GoogleIdTokenCredential.Builder().setId("ada@around.test").setIdToken(idToken).build()
    coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } answers
        {
          val request = secondArg<GetCredentialRequest>()
          val option = request.credentialOptions.single()
          assertTrue(option is GetSignInWithGoogleOption)
          assertEquals("web-client", (option as GetSignInWithGoogleOption).serverClientId)
          assertSame(context, firstArg<Context>())
          GetCredentialResponse(token)
        }
    assertEquals(GoogleCredentialResult.Token(idToken), client().requestToken())
  }

  @Test
  fun unrelatedCredentialIsRejected() = runBlocking {
    coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } returns
        GetCredentialResponse(CustomCredential("other-provider", Bundle()))
    assertEquals(GoogleCredentialResult.Failed, client().requestToken())
  }

  @Test
  fun malformedGoogleTokenIsRejected() = runBlocking {
    coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } returns
        GetCredentialResponse(
            CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
        )
    assertEquals(GoogleCredentialResult.Failed, client().requestToken())
  }

  @Test
  fun dismissingTheChooserIsCancellation() = runBlocking {
    coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } throws
        GetCredentialCancellationException()
    assertEquals(GoogleCredentialResult.Cancelled, client().requestToken())
  }

  @Test
  fun absentOrUnsupportedProvidersAreUnavailable() = runBlocking {
    listOf(
            NoCredentialException(),
            GetCredentialProviderConfigurationException(),
            GetCredentialUnsupportedException(),
        )
        .forEach {
          coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } throws it
          assertEquals(GoogleCredentialResult.Unavailable, client().requestToken())
        }
  }

  @Test
  fun unknownProviderFailureIsAnError() = runBlocking {
    coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } throws
        GetCredentialUnknownException()
    assertEquals(GoogleCredentialResult.Failed, client().requestToken())
  }

  @Test(expected = CancellationException::class)
  fun coroutineCancellationIsPropagated() = runBlocking {
    coEvery { manager.getCredential(any(), any<GetCredentialRequest>()) } throws
        CancellationException()
    client().requestToken()
    Unit
  }
}
