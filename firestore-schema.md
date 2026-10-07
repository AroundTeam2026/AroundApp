# Firestore schema v1 (draft)

## Conventions

- Domain models use our own `Location(lat: Double, lng: Double)`, never `GeoPoint` or `LatLng`. Conversion happens inside the Firestore implementations.
- All timestamps (createdAt, updatedAt, slotStart, submittedAt, expiresAt) are stored as epoch milliseconds (Long), matching the Kotlin models.
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
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUids` | list of string | The party. Non-empty, and the creator must be in the list (enforced by the rules and by `Reservation`). |
| `slotStart` | int (epoch ms) | |
| `status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` | |
| `createdAt` | int (epoch ms) | |

Repository only, no UI this sprint.

### `completions/{id}`

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | |
| `questId` | string | |
| `venueId` | string | Id of the venue that owns the quest. Used by the venue's security rules. |
| `explorerUid` | string | |
| `proofUrl` | string | Cloud Storage URL. |
| `status` | `PENDING`, `APPROVED`, `REJECTED` | |
| `rejectReason` | string? | Required when `status` is `REJECTED`; must be absent otherwise. Enforced in `Completion`. |
| `submittedAt` | int (epoch ms) | |

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
