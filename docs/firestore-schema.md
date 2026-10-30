<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
# Firestore schema v1

This doc describes what the code on `main` does. A change to a collection updates its model, its repository, `firestore.rules` and this doc together.

## Conventions

- Domain models use our own `Location(lat: Double, lng: Double)`, never `GeoPoint` or `LatLng`. In Firestore a location is stored as a `GeoPoint`; the conversion happens inside the Firestore repository implementations.
- Times are `Long` epoch milliseconds in the Kotlin models and `Timestamp` in Firestore (`createdAt`, `updatedAt`, `slotStart`, `submittedAt`, `reward.expiresAt`). The conversion happens inside the Firestore implementations.
- Enum values are stored as their upper-case names (`EXPLORER`, `PENDING`, ...).
- A document's id is not stored as a field. For `users` it is the uid, for `venues` the owner's uid and for `party_user` `{reservationId}_{uid}`, whose parts are also stored as fields so queries can filter on them; for the other collections it is a Firestore auto id.
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

The venue's name and location are not copied here: they are read from `venues/{venueId}`, so they never go stale.

| Field | Type | Notes |
|---|---|---|
| `venueId` | string | The venue owner's uid. |
| `radiusMeters` | int | Distance from the venue's `location` within which a visit counts, so moving the venue's area re-centres every quest's area too. 20 to 200; the create-quest form starts from the venue's radius. |
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
| `explorerName` | string | Display name of the explorer who made the reservation, copied from their `users` profile on creation so the venue sees who reserved (the venue can't read profiles). Must equal that profile's `displayName` (enforced by the rules). Not updated on a rename. Reservations made before this field existed lack it; the app reads them with an empty name. |

### `party_user/{reservationId}_{uid}`

One membership per explorer and reservation. The id is built from both, so the rules find a membership with one read and an explorer can't join a party twice. Memberships are never deleted, so a withdrawal stays recorded.

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | |
| `uid` | string | The explorer. |
| `status` | `JOINED` or `WITHDRAWN` | Set to `JOINED` by the repository on creation. |
| `joinedAt` | timestamp | Set by the repository. |
| `updatedAt` | timestamp | Set by the repository on creation and on withdrawal. |

The repository creates a reservation in one batch with its creator's `JOINED` membership. The size of a party is the number of its `JOINED` members (`observeJoinedCounts`). Reservations still list their party in `explorerUids`: #295 moves `observeForExplorer` and the rules onto these memberships.

### `completions/{id}`

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | The quest is the reservation's, so it isn't stored here. |
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUid` | string | |
| `photoUrl` | string? | Cloud Storage URL of the photo, for a `PHOTO` quest. |
| `textAnswer` | string? | The written answer, for a `TEXT` quest. |
| `status` | `PENDING`, `APPROVED` or `REJECTED` | |
| `rejectReason` | string? | Required when `status` is `REJECTED`; must be absent otherwise. Enforced in `Completion`. |
| `submittedAt` | timestamp | |

Exactly one of `photoUrl` and `textAnswer` is present, and it isn't blank (enforced by `Proof`). Which one must match the quest's `proofType`; the rules will check that.

Model only for now: there is no completions repository yet.

## Allowed status transitions

Encoded in `canTransition(from, to)`, which has one overload per status enum. The rules enforce the reservation and party member transitions too.

| Entity | From | To |
|---|---|---|
| Reservation | `PENDING` | `APPROVED`, `REJECTED`, `CANCELLED` |
| Reservation | `APPROVED` | `CANCELLED` |
| Reservation | `REJECTED`, `CANCELLED` | none |
| Party member | `JOINED` | `WITHDRAWN` |
| Party member | `WITHDRAWN` | none |
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
| `reservations` | The venue and the listed explorers | An explorer, whose `users` document has role `EXPLORER`, creates a `PENDING` one that lists them, in the same batch as their own `JOINED` membership, and carries their profile's `displayName` as `explorerName`, for an `ACTIVE` quest whose `venueId` it repeats. Afterwards only `status` changes, along an allowed transition: the venue makes any of them, and a listed explorer can only cancel. No delete. |
| `party_user` | The party's members, `WITHDRAWN` ones too, since a withdrawn creator still lists the reservation and so its party, and the reservation's venue. Anyone signed in may list the party of a reservation that doesn't exist, which is empty, since the app listens to one it just created before the server has it. | An explorer, whose `users` document has role `EXPLORER`, creates their own `JOINED` membership, under the id `{reservationId}_{uid}`, for a `PENDING` reservation. Afterwards only the member changes it, from `JOINED` to `WITHDRAWN`, along with `updatedAt`. No delete. |
| `completions` | Nobody yet | Nobody yet: rules come with the completions repository. |

## Decided changes

Decided in #259, from the review of #25 and from R2 (#274). The tables above describe `main`; a change still in progress updates them when it lands.

- **Copied venue fields in `quests`: adopted, done in #279 (F4a and F4b).** `venueName` and `location` go stale once a venue can rename itself or move its area, and dropping them later would mean migrating every stored quest. Readers take them from `venues/{venueId}` with `observeVenues`, one `in` query per 30 venues, as the overview (#269) does, and the rules reject the copies. The quests already stored on `around-67942` keep them until #283 removes them.
- **Party membership: adopted, in #281; the collection is added in #294.** `explorerUids` loses who withdrew from a party. One membership document per explorer and reservation (`party_user`) keeps that history. #281 replaces `explorerUids` this sprint and moves its readers (the venue page, the overview, `observeForExplorer` and the read rule) onto the memberships.
- **Ids copied into `completions`: only `questId` is dropped, done in #272 (F5).** The completion's reservation already names its quest. `venueId` and `explorerUid` stay, and the rules will check them against the reservation and its memberships on creation. They never change afterwards, so unlike the venue fields in quests they can't go stale. The venue's list of completions to review and the explorer's history filter on them, and the rules can only allow a query that filters on the documents' own fields.
- **Text proof: adopted, done in #272 (F5).** `proofUrl` couldn't tell a typed answer from an uploaded photo, so a completion holds `photoUrl` for a `PHOTO` quest or `textAnswer` for a `TEXT` quest, never both.
- **The explorer's name copied into `reservations`: kept, in #274 (R2).** A user can read only their own `users/{uid}`, so the venue has no other way to show who reserved. The copy is the name at booking time, which is what the venue needs for a short-lived reservation.
