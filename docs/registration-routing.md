<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->
<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
# Registration handoff for G2.3

Issue #135 supplies `ui/auth/RegistrationFlow` for `AppRoot`'s `authFlow` and
`roleSelection` slots. It does not activate a router in `MainActivity`: G2.3
owns profile resolution and destination selection, and #136 owns venue entry.

## Wiring

Create one `ProfileSetup(authRepository, userRepository, pendingRoleStore)`,
shared by the router and both slots (`PendingRoleStoreSharedPrefs` in the app).

```kotlin
// AppRoot(authFlow = { ... }): replaces the AuthScreen in SessionNavigation
RegistrationFlow(authRepository, profileSetup, onAccountSetupComplete = router::retryProfile)

// AppRoot(roleSelection = { ... }): a signed-in user with no profile or role
RegistrationFlow(
    authRepository,
    profileSetup,
    onAccountSetupComplete = router::retryProfile,
    completeRegistration = true,
)
```

`router::retryProfile` is G2.3's completion callback. It shows
loading and calls `profileSetup.finish()` again. Normal sign-up never calls it:
the router reacts to the repository's user ID instead.

## Rules for the router

- Call `finish()` after any signed-in session is established or restored,
  before choosing a home. This also recovers a save interrupted by process
  death. Never infer Explorer from a missing profile.
- Do not discard pending choices on user-ID emissions: sign-in already clears
  an abandoned, unbound choice before authenticating.
- Replace the auth slot with the resolved home; never push home onto auth, so
  Back cannot return to authentication.
- The form's ViewModel is activity-scoped, as in `SessionNavigation`: rotation
  keeps a pending request, and a successful submit clears the credentials.

## Role-routing scaffold

`RoleNavigation(authRepository, userRepository, pendingRoleStore,
explorerScreen = {}, venueScreen = {})` implements the repository-interface
handoff. Both home slots are empty until their owners supply real content. It reuses
`SessionNavigation` for auth gating and per-account state cleanup. Both
registration slots share its `ProfileSetup`; the router calls `finish()`
on restored/new sessions and on completion or retry, then observes the
profile's stored role.

| Profile state | Destination |
| --- | --- |
| Missing document or unset role, with no applicable pending choice | Complete registration |
| Explorer | Supplied Explorer home |
| Venue | Supplied Venue entry |
| Profile setup/read in progress | Loading |
| Failed profile setup/read | Error with retry and sign-out |

These rules follow #181's two registration choices. A pending choice is
completed before routing, including after a restart; it is never replaced
with a guessed Explorer role. A choice bound to another account stays
with that account. An existing stored role takes precedence.

## Production integration

Production activation depends on a Firestore-backed `UserRepositoryProvider`
from the persistent repository task. Until it is available, MainActivity
keeps the session-only entry. Do not supply FakeUserRepository there.

Inject the provider's repository into `RoleNavigation`, alongside the real
auth repository and `PendingRoleStoreSharedPrefs`, and supply the Explorer
tabs and Venue entry. Verify saved-role routing, restored sessions, missing
profiles, registration persistence, failures, retry and sign-out against the
Firebase emulators. Repository implementation stays in the model layer.

The router observes missing profiles and unset roles until a role is saved.
Roles are write-once, so that profile subscription then ends and the resolved
home remains selected for the session. Account change or sign-out still
cancels profile resolution through the session observer.

Fake repositories are test fixtures only; home slots are empty by default.
The role selection form is supplied by #181, and Profile sign-out by #187.
