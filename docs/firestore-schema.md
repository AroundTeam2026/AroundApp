# Firestore schema v1

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
| `email` | string | |
| `displayName` | string | |
| `role` | `EXPLORER` or `VENUE`, null until chosen | Write-once: once set it cannot change. |
| `createdAt` | timestamp | |

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
| `radiusMeters` | int | 20 to 200. |
| `title` | string | At most 60 characters. |
| `description` | string | At most 500 characters. |
| `requirements` | string | At most 300 characters. |
| `proofType` | `PHOTO` or `TEXT` | |
| `minPartySize` | int | At least 1; 1 means a solo visit is allowed. |
| `reward` | map? | `{description: string, terms: string?, expiresAt: timestamp?}`. |
| `status` | `DRAFT`, `ACTIVE` or `ARCHIVED` | Only `ACTIVE` quests are visible to explorers. |
| `createdAt` | timestamp | Set by the repository. |
| `updatedAt` | timestamp | Set by the repository. |

### `reservations/{id}`

| Field | Type | Notes |
|---|---|---|
| `questId` | string | |
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUids` | list of string | The party. Non-empty, and the creator must be in the list (enforced by the rules and by `Reservation`). |
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

## Allowed status transitions

Encoded in `canTransition(from, to)`, which has one overload per status enum.

| Entity | From | To |
|---|---|---|
| Reservation | `PENDING` | `APPROVED`, `REJECTED`, `CANCELLED` |
| Reservation | `APPROVED` | `CANCELLED` |
| Reservation | `REJECTED`, `CANCELLED` | none |
| Completion | `PENDING` | `APPROVED`, `REJECTED` |
| Completion | `APPROVED`, `REJECTED` | none |
