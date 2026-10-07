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
the existing profile.

Integration is tested with a signup callback fixture, the real initialization
screen, fake repositories, and a location destination fixture. The signup
fixture represents completion of authentication; it does not implement or
test signup itself.

The running app still needs the auth owner to register this graph and supply
the actual location screen. At implementation time, `feature/auth-screens`
contains no auth UI, and #54's screen exists only on
`feature/venue-area-map-screen`. This change does not satisfy the two
end-to-end acceptance criteria until those integrations are connected.
