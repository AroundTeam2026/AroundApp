<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->

# Venue onboarding handoff (#55)

`venueOnboardingDestinations` registers initialization and location in the
auth owner's `NavHost`. Supply the same `AuthRepository` and `VenueRepository`
instances used by signup and location setup.

After a successful **Venue** signup, call
`navController.navigateToVenueInitialization()`. Authentication and role
routing remain with #50 and #7.

Inside that NavHost's builder, register:

```kotlin
venueOnboardingDestinations(navController, authRepository, venueRepository) { venueId, onBack ->
  // Render #54's location screen using venueId and the shared venueRepository.
  // Connect its Back button to onBack.
}
```

The initialization ViewModel belongs to its back-stack entry. Continue saves
the profile before navigating and passes its id to location as a route
argument. Both system Back and the location Back callback return to the form
with its name intact. The success signal is consumed after navigation so
returning does not immediately reopen location. Continuing again preserves
the existing profile. Existing profiles prefill the business name and disable
name editing, including after Back from location. New names are trimmed and
limited to `VenueLimits.MAX_NAME_LENGTH` (100 characters).

Integration is tested with a signup callback fixture, the real initialization
screen, fake repositories, and a location destination fixture. The signup
fixture represents completion of authentication; it does not implement or
test signup itself.

Follow-up integration is tracked in [#79](https://github.com/AroundTeam2026/AroundApp/issues/79).
The running app still needs the auth owner to register this graph and supply
the actual location screen. The auth UI in #67 (`feature/minimal-auth-screen`)
observes authentication in a debug-only demo; it does not select a Venue role
or route a successful Venue signup into this graph.

The location UI in #69 (`feature/venue-marker-screen`, following #62) exposes
`VenueAreaScreen(viewModel, cameraPositionState)`. Its ViewModel holds marker
and radius state, but the screen does not yet accept a venue id or a Back
callback, or save through `VenueRepository.setArea`. Navigation and saving
are explicitly outside #54's scope. The follow-up post-role onboarding work
can wrap the map in this graph's `locationScreen` callback and coordinate
registration with #7's routing owner. Saving and loading the area remain a
separate task.

This change does not satisfy the two end-to-end acceptance criteria until
the real Venue signup and location integrations are connected. The existing
navigation tests cover the handoff contract using destination fixtures.
