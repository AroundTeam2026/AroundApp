// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.aroundteam2026.aroundapp.ui.navigation.SessionNavigation
import com.github.aroundteam2026.aroundapp.ui.theme.AroundAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val repository = (application as AroundApplication).container.authRepository
    setContent { AroundAppTheme { SessionNavigation(repository) } }
  }
}
