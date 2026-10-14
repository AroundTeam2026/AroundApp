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
| `explorerUids` | list of string | The party. Non-empty (enforced by `Reservation`), and the creator must be in the list (enforced by the rules). |
| `slotStart` | timestamp | Start of the booked slot. |
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

## Deferred to Sprint 3

Proposed in the review of this PR (#25). The schema above keeps what `main` does until the team decides.

- **Copied venue fields in `quests`.** `venueName` and `location` go stale when a venue renames itself or moves its area. Proposal: read them from `venues/{venueId}` instead.
- **Party membership.** `explorerUids` loses who withdrew from a party. Proposal: a party document plus one membership document per explorer (party_user), so a withdrawal keeps its history.
- **Ids copied into `completions`.** `questId`, `venueId` and `explorerUid` can be reached through the reservation and the party. Proposal: drop them. The venue's rules and queries currently need `venueId` on the document, so the rules change with it.
- **Text proof.** Quests allow `TEXT` proof, but `proofUrl` can't tell a typed answer from an uploaded photo. Proposal: separate fields for a photo and for a text answer.
