// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.ui.profile

import androidx.lifecycle.ViewModelProvider
import com.github.aroundteam2026.aroundapp.model.auth.AuthRepository
import com.github.aroundteam2026.aroundapp.model.notification.FakeNotificationRegistration
import com.github.aroundteam2026.aroundapp.model.notification.FakeNotificationTokenRepository

/** Builds the [ProfileViewModel] that signs [auth]'s user out, with no queue, on fakes. */
fun profileFactoryOnFakes(auth: AuthRepository): ViewModelProvider.Factory =
    ProfileViewModel.factory(
        auth,
        proofUploadQueue = null,
        FakeNotificationTokenRepository(),
        FakeNotificationRegistration(),
    )
