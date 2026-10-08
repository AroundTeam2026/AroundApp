// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.aroundteam2026.aroundapp

import android.app.Application
import com.github.aroundteam2026.aroundapp.model.AppContainer

/** Owns the dependencies shared by all activities in this process. */
class AroundApplication : Application() {
  var container: AppContainer = AppContainer()
    internal set
}
