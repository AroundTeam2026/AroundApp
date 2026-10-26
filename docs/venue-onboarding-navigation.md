<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->
<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->

# Venue entry and onboarding navigation

Where a signed-in **Venue** user enters the app, and how onboarding is wired
(#136, which rewrote #79).

## Where a venue lands

Role routing (`RoleNavigation`, G2.3) sends a signed-in user whose role is
`VENUE` to C2's `venueEntry` slot, giving it the user's uid. That slot holds
`VenueEntry`:

```kotlin
// MainActivity
RoleNavigation(
    ...,
    venueScreen = { uid ->
      VenueEntry(uid, auth, VenueRepositoryProvider.repository, profile::signOut)
    },
)
```

`VenueEntryViewModel` reads the user's venue once (the venue's id is its
owner's uid) and chooses where it starts:

| Venue state             | Starts at                      |
| ----------------------- | ------------------------------ |
| No venue doc            | Initialization (business name) |
| Venue without location  | Area screen                    |
| Venue with a location   | Venue home                     |

Progress is read back from `VenueRepository` each time, so a venue that
leaves mid-onboarding (or whose app is killed) resumes at the right step when
it signs in again. While reading, the entry shows a spinner; if the read
fails, it offers to retry or to sign out.

## The venue's NavHost

Once it knows where the venue starts, `VenueEntry` shows
`VenueEntryNavigation` (#221), the venue's NavHost, separate from the
explorer's `AroundApp`. It opens on the venue home for a venue already set up,
and on initialization otherwise; for a venue resuming on its area, it opens
the area above initialization itself. It registers:

- `venueOnboardingDestinations(...)`: initialization and location, with the
  real `VenueAreaScreen(venueId, onSaved, onBack)` (contract C4) as the
  location step.
- `venueDestinations(...)`: the venue home (`VenueHome`, with Dashboard,
  Quests and Profile tabs) and the create-quest form. The Quests tab's "New
  quest" opens the form, and closing it or saving a quest pops back to My
  quests. An explorer can't reach these, since `AroundApp` doesn't register
  them.

The dashboard has no route here: it is the venue home's own Dashboard tab, so
`dashboardDestination()` isn't registered.

## How onboarding moves

- Continue on initialization saves the venue, then opens the location step
  with its id as the route argument (`VenueOnboardingRoutes.location`).
- Saving the area opens the venue home and clears onboarding from the back
  stack, so Back can't return to it.
- Back from the area (its arrow or system Back) returns to initialization.
  The initialization ViewModel belongs to its back-stack entry, so the name
  survives; an existing venue's name is prefilled and can't be edited. The
  navigation signal is consumed once handled, so returning doesn't move
  forward again.
- A venue resuming on the area has initialization under it, so Back behaves
  as above. `VenueEntryNavigation` opens the area once, not again after
  recreation.

## Tests

- `VenueEntryViewModelTest`: the start for each venue state, failures and
  retry.
- `VenueEntryTest` (Robolectric, fake repositories, a stand-in for the area
  screen whose map Robolectric can't draw): each start point, setup in one go,
  saving the area, Back from the area, resuming after signing in again, the
  venue routes and sign-out.
- `VenueEntryNavigationTest`: the NavHost on its own, from business setup or
  resuming on the area, including that the area doesn't reopen after
  recreation.
- `RoleRoutingActivityTest` (Firebase emulators): a set-up venue signing in
  opens its home.
- `VenueOnboardingNavigationTest`: the onboarding graph on its own.
- `RoutesTest`: the explorer's `AroundApp` registers no venue route.
