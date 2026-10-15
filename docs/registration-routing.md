<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->
<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
# Registration handoff for G2.3

Issue #135 supplies `ui/auth/RegistrationFlow` for `AppRoot`'s `authFlow` and
`roleSelection` slots. G2.3 activates it in `MainActivity`, using the Firestore-backed
`UserRepositoryProvider` for profile resolution and destination selection.

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
handoff. Both home slots remain injectable; MainActivity supplies the existing Explorer
tabs and `VenueEntry`. It reuses
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

MainActivity supplies `AuthRepositoryProvider.repository`, the Firestore-backed
`UserRepositoryProvider.repository`, and `PendingRoleStoreSharedPrefs`. No fake
user repository is used in production. Explorer roles open `AroundApp`; Venue
roles open `VenueEntry` (#136), which reads the venue and opens
`VenueEntryNavigation` at the first step left: business setup, location setup or
the Venue home (see `venue-onboarding-navigation.md`). That NavHost connects
business setup, location setup, Venue home and the create-quest form. Saved
location replaces onboarding in the back stack.

The existing role tests cover missing profiles, pending choices, saved roles,
loading, failures, retry, sign-out and account changes. `RoleRoutingActivityTest`
checks MainActivity's real providers against Firebase emulators, including role
persistence and recreation. `VenueEntryNavigationTest` covers the onboarding/home
handoff, root Back behavior and restoration through the shared venue destinations.
`venueDestinations` from #225 registers the home and quest form, including safe form
closing; this bridge does not introduce a second set of venue routes. Profile
sign-out is dispatched through `ProfileViewModel` for both roles.

The router observes missing profiles and unset roles until a role is saved.
Roles are write-once, so that profile subscription then ends and the resolved
home remains selected for the session. Account change or sign-out still
cancels profile resolution through the session observer.

The role-routing fake repositories are test fixtures only; home slots are empty
by default for callers that do not supply them. The quest provider is also
Firestore-backed, as supplied by #219.
The role selection form is supplied by #181, and Profile sign-out by #187.
