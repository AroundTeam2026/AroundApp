<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
# Firestore schema v1

This doc describes what the code on `main` does. A change to a collection updates its model, its repository, `firestore.rules` and this doc together.

## Conventions

- Domain models use our own `Location(lat: Double, lng: Double)`, never `GeoPoint` or `LatLng`. In Firestore a location is stored as a `GeoPoint`; the conversion happens inside the Firestore repository implementations.
- Times are `Long` epoch milliseconds in the Kotlin models and `Timestamp` in Firestore (`createdAt`, `updatedAt`, `slotStart`, `submittedAt`, `reward.expiresAt`). The conversion happens inside the Firestore implementations.
- Enum values are stored as their upper-case names (`EXPLORER`, `PENDING`, ...).
- A document's id is not stored as a field. For `users` it is the uid and for `venues` the owner's uid; for the other collections it is a Firestore auto id.
- Fields marked `?` are optional: they are omitted when null.

## Collections

### `users/{uid}`

| Field | Type | Notes |
|---|---|---|
| `displayName` | string | |
| `role` | `EXPLORER` or `VENUE`, null until chosen | Write-once: once set it cannot change. |
| `createdAt` | timestamp | |

No email: Firebase Auth already has it, and only the owner can read this document.

### `venues/{ownerUid}`

| Field | Type | Notes |
|---|---|---|
| `name` | string | At most 100 characters. |
| `location` | GeoPoint? | Null until the venue confirms its marker on the map. |
| `radiusMeters` | int | Distance from `location` within which a visit counts. 20 to 200, default 50. |
| `address` | string? | |
| `createdAt` | timestamp | |
| `featuredQuestId` | string? | Quest the venue wants shown first. Not checked against the venue's quests. |

One venue per account in v1, so the document id is the owner's uid.

### `quests/{questId}`

| Field | Type | Notes |
|---|---|---|
| `venueId` | string | The venue owner's uid. |
| `venueName` | string | Copied from the venue at creation, so the map needs a single query. |
| `location` | GeoPoint | Copied from the venue at creation. |
| `radiusMeters` | int | Distance from `location` within which a visit counts. 20 to 200; the create-quest form starts from the venue's radius. |
| `title` | string | At most 60 characters. |
| `description` | string | At most 500 characters. |
| `requirements` | string | At most 300 characters. |
| `proofType` | `PHOTO` or `TEXT` | |
| `minPartySize` | int | At least 1; 1 means a solo visit is allowed. |
| `exactPartySize` | bool | True if the party must have exactly `minPartySize` people, false if at least. A quest without it is read as false. |
| `reward` | map? | See the reward map below. Absent when the quest has no reward. |
| `status` | `DRAFT`, `ACTIVE` or `ARCHIVED` | Only `ACTIVE` quests are visible to explorers. |
| `createdAt` | timestamp | Set by the repository. |
| `updatedAt` | timestamp | Set by the repository. |

The `reward` map has a `type` field that says which other fields it holds:

| `type` | Fields |
|---|---|
| `DISCOUNT` | `amount` (number, at least 0.01; at most 100 for `PERCENT` and 1000 for `CHF`), `unit` (`PERCENT` or `CHF`), `specifics` (string?, at most 200 characters) |
| `FREE_ITEM` | `name` (string, at most 60 characters), `specifics` (string?, at most 200 characters) |
| `OTHER` | `description` (string, at most 200 characters) |

Every type may also have `expiresAt` (timestamp?): when the reward stops being valid, absent if never. A quest whose reward has a missing or unknown `type` is skipped when read.

### `reservations/{id}`

| Field | Type | Notes |
|---|---|---|
| `questId` | string | |
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUids` | list of string | The party. Non-empty (enforced by `Reservation`). The creator must be in the list, which holds at most 10 explorers, each listed once (enforced by the rules). |
| `slotStart` | timestamp | Start of the booked slot. On creation it can't be more than 5 minutes in the past, to allow for a phone clock that is behind (enforced by the rules). |
| `status` | `PENDING`, `APPROVED`, `REJECTED` or `CANCELLED` | Set to `PENDING` by the repository on creation. |
| `createdAt` | timestamp | Set by the repository. |

### `completions/{id}`

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | |
| `questId` | string | |
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUid` | string | |
| `proofUrl` | string | Cloud Storage URL. |
| `status` | `PENDING`, `APPROVED` or `REJECTED` | |
| `rejectReason` | string? | Required when `status` is `REJECTED`; must be absent otherwise. Enforced in `Completion`. |
| `submittedAt` | timestamp | |

Model only for now: there is no completions repository yet.

## Allowed status transitions

Encoded in `canTransition(from, to)`, which has one overload per status enum. The rules enforce the reservation transitions too.

| Entity | From | To |
|---|---|---|
| Reservation | `PENDING` | `APPROVED`, `REJECTED`, `CANCELLED` |
| Reservation | `APPROVED` | `CANCELLED` |
| Reservation | `REJECTED`, `CANCELLED` | none |
| Completion | `PENDING` | `APPROVED`, `REJECTED` |
| Completion | `APPROVED`, `REJECTED` | none |

## Security rules summary (v2)

From `firestore.rules`. Anything not listed is denied.

Every create and update must match its collection's table above: only the listed fields, every field not marked `?` present (`role` may be absent), with the listed type, values and limits. A `reward` is checked against the fields of its `type`.

| Collection | Read | Write |
|---|---|---|
| `users` | Only the owner | The owner creates and updates it; `role` must be null, `EXPLORER` or `VENUE`, and can't change once set. No delete. |
| `venues` | Any signed-in user | Only the owner (document id is their uid), whose `users` document has role `VENUE`. |
| `quests` | Any signed-in user | Only the owning venue (`venueId` is their uid), whose `users` document has role `VENUE`; `venueId` can't change. |
| `reservations` | The venue and the listed explorers | An explorer, whose `users` document has role `EXPLORER`, creates a `PENDING` one that lists them, for an `ACTIVE` quest whose `venueId` it repeats. Afterwards only `status` changes, along an allowed transition: the venue makes any of them, and a listed explorer can only cancel. No delete. |
| `completions` | Nobody yet | Nobody yet: rules come with the completions repository. |

## Decided changes

Decided in #259, from the review of #25 and from R2. The tables above still describe `main`; each task updates them when it lands.

- **Copied venue fields in `quests`: adopted.** `venueName` and `location` go stale once a venue can rename itself or move its area, and dropping them later would mean migrating every stored quest. Readers take them from `venues/{venueId}` with `observeVenues`, one query for all the venues on screen, as the overview already does. F4a moves the map and nearby quests to the venue; then F4b stops writing the copies, and the rules reject them.
- **Party membership: adopted, in steps.** `explorerUids` loses who withdrew from a party. A party document plus one membership document per explorer keeps that history. This sprint, P1 decides the model and P2a adds it with a fake repository. Moving the reservation's readers off `explorerUids` (the venue page, the overview, `observeForExplorer` and the read rule) comes with the parties stretch in Sprint 4; until then reservations keep `explorerUids`.
- **Ids copied into `completions`: only `questId` is dropped.** The completion's reservation already names its quest. `venueId` and `explorerUid` stay, and the rules will check them against the reservation on creation. They never change afterwards, so unlike the venue fields in quests they can't go stale. The venue's list of completions to review and the explorer's history filter on them, and the rules can only allow a query that filters on the documents' own fields. Task: F5.
- **Text proof: adopted.** `proofUrl` can't tell a typed answer from an uploaded photo. A completion holds `photoUrl` for a `PHOTO` quest or `textAnswer` for a `TEXT` quest, never both. Task: F5, before Sprint 4 builds proof submission.
- **The explorer's name copied into `reservations`: kept.** A user can read only their own `users/{uid}`, so the venue has no other way to show who reserved. The copy is the name at booking time, which is what the venue needs for a short-lived reservation. Task: R2.
