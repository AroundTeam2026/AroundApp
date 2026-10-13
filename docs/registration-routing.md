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

`router::retryProfile` is G2.3's callback, not part of this change. It shows
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

## PR 1: role-routing scaffold (Refs #183)

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
| Failed profile setup/read | Error with retry |

These rules follow #181's two registration choices. A pending choice is
completed before routing, including after a restart; it is never replaced
with a guessed Explorer role. A choice bound to another account stays
with that account. An existing stored role takes precedence.

## PR 2: activate role routing after the provider exists (completes #183)

This follow-up depends on another task supplying a Firestore-backed
`UserRepositoryProvider`. Implementing that repository and provider is
outside both routing PRs. Until that dependency is available, MainActivity
keeps the session-only entry; do not activate it with FakeUserRepository.

Once the provider is available on main, the follow-up PR must:

- Inject the provider's real `UserRepository` into `RoleNavigation`, along
  with the real auth repository and `PendingRoleStoreSharedPrefs`.
- Replace the session-only `MainActivity` entry with the scaffold and supply
  the real Explorer tabs (including Profile sign-out) and Venue entry.
- Verify routing from real saved roles, restored sessions, missing profiles,
  registration role persistence, profile failures and retry against the
  Firebase emulators. Keep Firebase inside the model repository; the
  routing ViewModel continues to depend on repository interfaces.

The scaffold PR uses `Refs #183`; it does not close the issue. The activation
PR uses `Closes #183` once its dependencies and acceptance criteria are met.
There is no activation PR implementation yet: it waits for the provider.

Existing fake repositories remain test fixtures only. Their unit tests
specify the scaffold's routing contract; they do not demonstrate Firestore
integration. Home slots are empty by default and no fake profiles are seeded
in production. The role selection form is already supplied by #181, not
#108 (which owns session back-stack cleanup); #187 supplies Profile sign-out.
