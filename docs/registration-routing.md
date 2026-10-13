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
