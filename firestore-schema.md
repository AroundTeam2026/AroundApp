# Firestore schema v1 (draft)

Status: **draft for the Day 1 contracts meeting**. Once the team agrees, every change goes through a PR to this file, reviewed by jiayizhngepfl and announced in the team chat.

## Conventions

- Domain models use our own `Location(lat: Double, lng: Double)`, never `GeoPoint` or `LatLng`. Conversion happens inside the Firestore implementations.
- All timestamps are Firestore `Timestamp`, stored as `createdAt` / `submittedAt` / `slotStart`.
- Enum values are stored as upper-case strings (`EXPLORER`, `PENDING`, ...).
- Document ids are Firestore auto ids unless stated otherwise.
- Fields ending in `?` are optional (may be missing or null).

## Collections

### `users/{uid}`

| Field | Type | Notes |
|---|---|---|
| `email` | string | |
| `displayName` | string | |
| `role` | `EXPLORER` or `VENUE`, null until chosen | **Write-once.** Rules reject any change after it is set. |
| `createdAt` | timestamp | |

### `venues/{ownerUid}`

| Field | Type | Notes |
|---|---|---|
| `name` | string | Set at the business-name step. |
| `location` | Location? | Null until the venue places its marker. |
| `radiusMeters` | int | Area in which a visit counts. Range to be agreed (suggestion: 20 to 200). |
| `address` | string? | Optional. |
| `createdAt` | timestamp | |

One venue per account in v1, so the document id is the owner's uid. This depends on the G3 decision.

### `quests/{questId}`

| Field | Type | Notes |
|---|---|---|
| `venueId` | string | = owner uid |
| `venueName` | string | Copied from the venue at creation, so the map needs a single query. |
| `location` | Location | Copied from the venue at creation. |
| `title` | string | Required, length limit to be agreed. |
| `description` | string | Required. |
| `requirements` | string | Required. |
| `proofType` | `PHOTO` or `TEXT` | |
| `reward` | map? | Exists now, UI in Sprint 2. |
| `slots` | list | Exists now, UI in Sprint 2. |
| `status` | `ACTIVE` or `ARCHIVED` | See open decision 1. |
| `createdAt` | timestamp | |

### `reservations/{id}`

| Field | Type | Notes |
|---|---|---|
| `questId` | string | |
| `venueId` | string | |
| `explorerUids` | list of string | The party. |
| `slotStart` | timestamp | |
| `status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` | See open decision 3. |
| `createdAt` | timestamp | |

Repository only, no UI this sprint.

### `completions/{id}`

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | |
| `questId` | string | |
| `explorerUid` | string | |
| `proofUrl` | string | Cloud Storage URL. |
| `status` | `PENDING`, `APPROVED`, `REJECTED` | |
| `rejectReason` | string? | Required when status is `REJECTED`. |
| `submittedAt` | timestamp | |

Data class only this sprint.

## Allowed status transitions

Encoded in `canTransition(from, to)` and enforced again in the security rules.

| Entity | From | To |
|---|---|---|
| Reservation | `PENDING` | `APPROVED`, `REJECTED`, `CANCELLED` |
| Reservation | `APPROVED` | `CANCELLED` |
| Reservation | `REJECTED`, `CANCELLED` | none |
| Completion | `PENDING` | `APPROVED`, `REJECTED` |
| Completion | `APPROVED`, `REJECTED` | none |

## Security rules summary (v1)

| Collection | Read | Write |
|---|---|---|
| `users` | See open decision 2 | Only the owner; `role` cannot change once set |
| `venues` | Any signed-in user | Only the owner (`venueId == auth.uid`) |
| `quests` | Any signed-in user | Only the owning venue |
| `reservations` | The venue and the listed explorers | Explorer creates one that lists them; only the venue changes the status |
| `completions` | The venue and the explorer | Explorer creates; only the venue changes the status |

## Open decisions for the Day 1 meeting

1. **Quest `DRAFT` state?** V4 says a draft is not visible to Explorers. The plan has only `ACTIVE` and `ARCHIVED`, so quests are published on creation. Either add `DRAFT` or write in V7 that creation publishes.
2. **Who can read `users`?** Allowing any signed-in user exposes every email. Proposal: only the owner reads their document; public fields (display name) go to a separate public document when needed.
3. **Reservation `EXPIRED`?** V11 is P1, but adding the value now avoids a schema change later.
4. **G3: one role per account, or both?** The draft assumes one role per account, chosen once and permanent.
5. **Must every quest be reserved?** The draft assumes yes: unlock requires an approved reservation, and a solo visit is a reservation with a party of one.
6. **Radius range and title length limit.**
