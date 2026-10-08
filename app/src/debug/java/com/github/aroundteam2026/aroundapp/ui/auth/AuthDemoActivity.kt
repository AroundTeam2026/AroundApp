// Co-authored-by: OpenAI Codex
package com.github.aroundteam2026.aroundapp.ui.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.aroundteam2026.aroundapp.AroundApplication
import com.github.aroundteam2026.aroundapp.R
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme

/** Debug-only entry for demonstrating the form independently of app and Venue routing. */
class AuthDemoActivity : ComponentActivity() {
  private val repository: AuthRepository by lazy {
    (application as AroundApplication).container.authRepository
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { AroundAppTheme { AuthDemo(repository) } }
  }
}

/** Observes repository authentication success and provides a way to repeat the demo. */
@Composable
internal fun AuthDemo(repository: AuthRepository) {
  val userId by repository.currentUserId.collectAsStateWithLifecycle()
  val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(repository))
  if (userId == null) {
    AuthScreen(authViewModel)
  } else {
    Surface(Modifier.fillMaxSize()) {
      Column(
          modifier = Modifier.safeDrawingPadding().padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(stringResource(R.string.auth_signed_in))
        Button(onClick = repository::signOut) { Text(stringResource(R.string.auth_sign_out)) }
      }
    }
  }
}
