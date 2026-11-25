// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.notification

import com.github.aroundteam2026.aroundapp.model.auth.AuthRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Shares one [NotificationTokenRegistrar] between MainActivity and the messaging service. */
object NotificationTokenRegistrarProvider {
  /**
   * Saves the phone's notification ID in Firestore. Its scope lives as long as the app's process,
   * since FCM can answer a registration while no activity is open.
   */
  val registrar: NotificationTokenRegistrar by lazy {
    NotificationTokenRegistrar(
        AuthRepositoryProvider.repository,
        NotificationTokenRepositoryProvider.repository,
        NotificationRegistrationProvider.registration,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
  }
}
