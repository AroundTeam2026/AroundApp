# Auth and role routing

<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->

`RoleNavigation` is the integration entry point for #7. It requires the shared
`AuthRepository`, a persistent `UserRepository`, and the role-selection and Venue
screens. The existing `SessionNavigation` remains the auth-only app entry until
those dependencies are available; this change does not install a fake profile
repository in the running app.

| Session/profile state | Destination |
| --- | --- |
| No authenticated user | Auth form |
| Waiting for the profile | Loading |
| Missing profile or unset role | Role selection |
| Saved `EXPLORER` role | Existing Explorer tabs |
| Saved `VENUE` role | Supplied Venue flow |
| Profile read fails | Retry or sign out |

The supplied role-selection screen receives the authenticated uid. Its owner
creates a missing profile and saves the chosen role through `UserRepository`.
The router observes that saved role; no navigation callback or temporary role
flag is needed. Role selection must not mark onboarding complete before the
write succeeds.

The supplied Venue screen receives the same uid and owns its nested navigation,
including the initialization/location handoff tracked in #79. This router does
not decide whether an existing Venue needs further setup.

Authentication changes cancel the previous profile subscription. Changing the
account or resolved flow clears the old back stack without saving it for later
restoration. Recreating the activity with the same session preserves the active
flow. The location of the “Are you a venue?” button is still pending design
confirmation and is not implemented here.
