// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.collectLatest

/** The account chooser's result, before the repository exchanges a token with Firebase. */
sealed interface GoogleCredentialResult {
  data class Token(val idToken: String) : GoogleCredentialResult

  data object Cancelled : GoogleCredentialResult

  data object Unavailable : GoogleCredentialResult

  data object Misconfigured : GoogleCredentialResult

  data object Failed : GoogleCredentialResult
}

/** Android credential acquisition belongs to the UI layer, independently testable from auth. */
fun interface GoogleSignInClient {
  suspend fun requestToken(): GoogleCredentialResult
}

/** Remembers an Activity-context client only for this composition's lifetime. */
@Composable
internal fun rememberGoogleSignInClient(): GoogleSignInClient {
  val context = LocalContext.current
  return remember(context) { CredentialManagerGoogleSignInClient(context) }
}

/** Uses the explicit Google button flow, which also allows adding a new device account. */
internal class CredentialManagerGoogleSignInClient(
    private val context: Context,
    private val clientId: String? = webClientId(context),
    private val manager: CredentialManager = CredentialManager.create(context),
) : GoogleSignInClient {
  override suspend fun requestToken(): GoogleCredentialResult {
    val configuredClientId = clientId ?: return GoogleCredentialResult.Misconfigured
    return try {
      val request =
          GetCredentialRequest.Builder()
              .addCredentialOption(GetSignInWithGoogleOption.Builder(configuredClientId).build())
              .build()
      val credential = manager.getCredential(MutableContextWrapper(context), request).credential
      if (
          credential is CustomCredential &&
              credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        GoogleCredentialResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
      } else GoogleCredentialResult.Failed
    } catch (_: GetCredentialCancellationException) {
      GoogleCredentialResult.Cancelled
    } catch (_: NoCredentialException) {
      GoogleCredentialResult.Unavailable
    } catch (_: GetCredentialProviderConfigurationException) {
      GoogleCredentialResult.Unavailable
    } catch (_: GetCredentialUnsupportedException) {
      GoogleCredentialResult.Unavailable
    } catch (_: GoogleIdTokenParsingException) {
      GoogleCredentialResult.Failed
    } catch (_: GetCredentialException) {
      GoogleCredentialResult.Failed
    }
  }
}

/**
 * Old Firebase config files omit this generated resource; report that instead of failing builds.
 */
@SuppressLint("DiscouragedApi")
internal fun webClientId(context: Context): String? {
  val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
  return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
}

/** Clears Google's active credential session on sign-out; Firebase remains the session source. */
@Composable
internal fun GoogleCredentialSessionEffect(
    repository: AuthRepository,
    clearSession: (suspend () -> Unit)? = null,
) {
  val context = LocalContext.current.applicationContext
  val clear =
      remember(context, clearSession) {
        clearSession
            ?: {
              CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
      }
  LaunchedEffect(repository, clear) {
    var previousUid: String? = null
    repository.currentUserId.collectLatest { uid ->
      val signedOut = previousUid != null && uid == null
      previousUid = uid
      if (signedOut) {
        try {
          clear()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w("GoogleCredentialSession", "Credential session cleanup failed", e)
        }
      }
    }
  }
}
