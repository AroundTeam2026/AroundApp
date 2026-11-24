// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Holds one [T] for the user who is signed in, built by [build] from the application context and
 * the user's id, so every screen of that user shares it. Another user signing in gets their own,
 * and the previous one is dropped from memory.
 */
class PerUser<T : Any>(private val build: (context: Context, uid: String) -> T) {
  private var current: Pair<String, T>? = null

  /** The one of the user [uid], built the first time it is asked for. */
  @Synchronized
  fun get(context: Context, uid: String): T {
    current
        ?.takeIf { (held, _) -> held == uid }
        ?.let {
          return it.second
        }
    return build(context.applicationContext, uid).also { current = uid to it }
  }

  /** Forgets the held one, as a new app process starts with none. */
  @Synchronized
  @VisibleForTesting
  fun reset() {
    current = null
  }
}
