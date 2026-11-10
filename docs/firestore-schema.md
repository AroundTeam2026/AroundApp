<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->
<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
# Firestore schema v1

This doc describes what the code on `main` does. A change to a collection updates its model, its repository, `firestore.rules` and this doc together.

## Conventions

- Domain models use our own `Location(lat: Double, lng: Double)`, never `GeoPoint` or `LatLng`. In Firestore a location is stored as a `GeoPoint`; the conversion happens inside the Firestore repository implementations.
- Times are `Long` epoch milliseconds in the Kotlin models and `Timestamp` in Firestore (`createdAt`, `updatedAt`, `slotStart`, `submittedAt`, `reward.expiresAt`). The conversion happens inside the Firestore implementations.
- Enum values are stored as their upper-case names (`EXPLORER`, `PENDING`, ...).
- A document's id is not stored as a field. For `users` it is the uid, for `venues` the owner's uid and for `party_user` `{reservationId}_{uid}`, whose parts are also stored as fields so queries can filter on them, and for `party_codes` the code itself; for the other collections it is a Firestore auto id.
- Fields marked `?` are optional: they are omitted when null.

## Collections

### `users/{uid}`

| Field | Type | Notes |
|---|---|---|
| `displayName` | string | At most 100 characters; initially empty until profile setup. |
| `pictureUrl` | string? | Cloud Storage location (`gs://...`) under this user's `user-pictures/{uid}/`; omitted until set. At most 2,000 characters. |
| `role` | `EXPLORER` or `VENUE`, null until chosen | Write-once: once set it cannot change. |
| `createdAt` | timestamp | |

No email: Firebase Auth already has it. Signed-in users can get a profile by uid to display its name and picture, but cannot list the users collection; only the owner can write a profile.

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

The venue's name and location are not copied here: they are read from `venues/{venueId}`, so they never go stale. A quest can have a `location` of its own instead, for one that takes place away from the venue; that is the quest's own data, not a copy of the venue's.

| Field | Type | Notes |
|---|---|---|
| `venueId` | string | The venue owner's uid. |
| `location` | geopoint? | Where explorers go for this quest (#429). Absent when the quest takes place at its venue, which is then read from `venues/{venueId}` and moves with the venue's area. A quest whose `location` is not a geopoint is skipped when read. |
| `radiusMeters` | int | Distance from the quest's `location`, or from the venue's when the quest has none, within which a visit counts, so moving the venue's area re-centres the area of every quest without a location of its own. 20 to 200; the create-quest form starts from the venue's radius. |
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

The party isn't stored here: it is the reservation's memberships in `party_user`. The rules let an explorer create a reservation only in one batch with their own `JOINED` membership, joined at the reservation's `createdAt`, and with its `party_codes` document if it has a `partyCode`.

| Field | Type | Notes |
|---|---|---|
| `questId` | string | |
| `venueId` | string | Id of the venue that owns the quest. |
| `slotStart` | timestamp | Start of the booked slot. On creation it can't be more than 5 minutes in the past, to allow for a phone clock that is behind (enforced by the rules). |
| `status` | `PENDING`, `APPROVED`, `REJECTED` or `CANCELLED` | Set to `PENDING` by the repository on creation. |
| `createdAt` | timestamp | Set by the repository. |
| `explorerName` | string | Display name of the explorer who made the reservation, copied from their `users` profile on creation. Must equal that profile's `displayName` (enforced by the rules). Not updated on a rename. Reservations made before this field existed lack it; the app reads them with an empty name. |
| `joinedCount` | int | Number of `JOINED` members in `party_user`, so the rules can cap the party at its [capacity](#party-size) and check its size on approval: rules can't count documents. 1 on creation, for the creator. Every join adds 1 and every withdrawal takes 1 off, in the same batch as the membership (enforced by the rules). The app shows the party size from the memberships, not from this field. |
| `partyCode` | string? | The [party code](#party_codescode) explorers share to join the party, written once on creation with its `party_codes` document (enforced by the rules). Reservations made before party codes have none. |

### `party_user/{reservationId}_{uid}`

One membership per explorer and reservation. The id is built from both, so the rules find a membership with one read and an explorer can't join a party twice. Memberships are never deleted, so a withdrawal stays recorded.

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | |
| `uid` | string | The explorer. |
| `status` | `JOINED` or `WITHDRAWN` | Set to `JOINED` by the repository on creation. |
| `joinedAt` | timestamp | Set by the repository. The creator's is the reservation's `createdAt`, which no other member's may be: that is how the rules tell the creator (enforced by the rules). The memberships backfilled in #318 were all given their reservation's `createdAt`, so every member of those reservations counts as its creator. |
| `updatedAt` | timestamp | Set by the repository on creation and on withdrawal. |

The size of a party is the number of its `JOINED` members (`observeJoinedCounts`). An explorer's reservations are those they have a `JOINED` membership of (`observeForExplorer`).

### `party_codes/{code}`

A short code that leads to one reservation, so its creator can give it to friends to join the party. The code is the document id: 6 characters out of the digits 2 to 9 and the capital letters but I, L and O, which are easy to mistake for one another. The app will show it as two groups of 3 (`7KQ M42`), and only to the creator, though every member knows it.

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | The reservation whose `partyCode` it is. |

A code is created in one batch with its reservation and never changes. Writing a code that is already taken is refused, so codes stay unique. The app doesn't write party codes yet.

### `completions/{reservationId}`

One completion per reservation (`completionId`): the proof of its whole party. Any `JOINED` member sends it, and every member reads it and its result. The id is the reservation's, so a retried submission writes the same document instead of a second one, and the party can't complete a reservation twice, whichever member sends it.

Completions stored before parties shared one proof are under `{reservationId}_{explorerUid}`. They stay as they are: their fields are unchanged, so the venue still lists and answers them, and their party still reads them. The rules only look for the party's completion under the reservation's id, so an old one doesn't stop the party from sending a new proof, leaving or cancelling. They aren't migrated.

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | The quest is the reservation's, so it isn't stored here. |
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUid` | string | The member who sent the proof. |
| `photoUrl` | string? | The photo's Cloud Storage location (`gs://...`, not a download URL), under `proofs/{reservationId}/` (see [Cloud Storage](#cloud-storage)), for a `PHOTO` quest. At most 2000 characters. |
| `textAnswer` | string? | The written answer, for a `TEXT` quest. At most 1000 characters. |
| `status` | `PENDING`, `APPROVED` or `REJECTED` | |
| `rejectReason` | string? | Required when `status` is `REJECTED`, not blank and at most 300 characters; must be absent otherwise. Enforced in `Completion` and by the rules. |
| `submittedAt` | timestamp | |

Exactly one of `photoUrl` and `textAnswer` is present, and it isn't blank (enforced by `Proof`). Which one must match the quest's `proofType`, which the rules check.

Read and written through `CompletionRepository`: `CompletionRepositoryFirestore` in the app, `FakeCompletionRepository` in unit tests.

## Party size

A quest asks for a party of at least `minPartySize` explorers, or exactly that many if `exactPartySize` is true. The venue can approve a reservation only when its `joinedCount` meets that size; rejecting and cancelling are allowed at any size.

A party is full at its capacity: `minPartySize` for an exact-size quest, 10 otherwise, and never more than 10. Joining a full party is refused. A quest asking for more than 10 can never be approved, whether its size is exact or at least.

A member who leaves an approved party can take it below the quest's size. That is allowed; the reservation stays approved.

A member leaves while the reservation is `PENDING` or `APPROVED`, as long as the party hasn't sent its proof. The creator never leaves: they cancel the reservation instead.

## Allowed status transitions

Encoded in `canTransition(from, to)`, which has one overload per status enum. The rules enforce them too.

| Entity | From | To |
|---|---|---|
| Reservation | `PENDING` | `APPROVED`, `REJECTED`, `CANCELLED` |
| Reservation | `APPROVED` | `CANCELLED` |
| Reservation | `REJECTED`, `CANCELLED` | none |
| Party member | `JOINED` | `WITHDRAWN` |
| Party member | `WITHDRAWN` | none |
| Completion | `PENDING` | `APPROVED`, `REJECTED` |
| Completion | `REJECTED` | `APPROVED`, if the venue changes its mind |
| Completion | `APPROVED` | none |

## Security rules summary (v2)

From `firestore.rules`. Anything not listed is denied.

Every create and update must match its collection's table above: only the listed fields, every field not marked `?` present (`role` may be absent), with the listed type, values and limits. A `reward` is checked against the fields of its `type`.

| Collection | Read | Write |
|---|---|---|
| `users` | Only the owner | The owner creates and updates it; `role` must be null, `EXPLORER` or `VENUE`, and can't change once set. No delete. |
| `venues` | Any signed-in user | Only the owner (document id is their uid), whose `users` document has role `VENUE`. |
| `quests` | Any signed-in user | Only the owning venue (`venueId` is their uid), whose `users` document has role `VENUE`; `venueId` can't change. |
| `reservations` | The venue and the party's `JOINED` members. Anyone signed in may read an id that has no reservation, since the app listens to one it just created before the server has it. | An explorer, whose `users` document has role `EXPLORER`, creates a `PENDING` one in the same batch as their own `JOINED` membership, joined at its `createdAt`, and its `party_codes` document if it has a `partyCode`, carrying their profile's `displayName` as `explorerName`, with a `joinedCount` of 1, for an `ACTIVE` quest whose `venueId` it repeats. Afterwards `status` changes along an allowed transition: the venue makes any of them, approving only a party of the [size](#party-size) its quest asks for, and a `JOINED` member can only cancel, until the party sends its proof, whatever its status. `joinedCount` changes alone, by 1, in the same batch as the user's own membership joining or withdrawing. No delete. |
| `party_user` | The party's members, `WITHDRAWN` ones too, so a member who withdrew still sees who else was in the party, and the reservation's venue. Anyone signed in may list the party of a reservation that doesn't exist, which is empty, for the same reason. | An explorer, whose `users` document has role `EXPLORER`, creates their own `JOINED` membership, under the id `{reservationId}_{uid}`, for a `PENDING` reservation whose party is below its [capacity](#party-size), joined at another time than the reservation's `createdAt`, adding 1 to its `joinedCount` in the same batch. Afterwards only the member changes it, from `JOINED` to `WITHDRAWN`, along with `updatedAt`, taking 1 off `joinedCount` in the same batch, while the reservation is `PENDING` or `APPROVED`, before the party sends its proof, and never the creator. No delete. |
| `party_codes` | Anyone signed in may get a code they know; nobody lists them. | Created only in the same batch as the reservation whose `partyCode` it is, with `reservationId` alone. Never updated or deleted. |
| `completions` | The explorer who submitted it, the party's `JOINED` members and its venue. Anyone signed in may read an id that has no completion, so an answer to an unknown id can report it. | Any `JOINED` member of an `APPROVED` reservation creates the party's, as themselves in `explorerUid`, `PENDING` and without a `rejectReason`, under the reservation's id, repeating the reservation's `venueId`, with the proof its quest's `proofType` asks for. There is no second submission, by the same member or another, whatever the first one's status. Afterwards only the venue changes it: a `PENDING` one to `REJECTED`, changing only `status` and `rejectReason`, and a `PENDING` or `REJECTED` one to `APPROVED`, changing only `status` and removing any `rejectReason`. `APPROVED` is final. No delete. |

Joining a party needs only its reservation's id, and a `JOINED` membership lets the explorer read and cancel the reservation. So an explorer who learns the id of someone else's `PENDING` reservation can join that party and cancel it. Ids are random and only shown to the party and the venue, so this rests on them staying private. A party code leads to its reservation's id, and is much shorter and easier to guess than an id, so it weakens this: anyone signed in who guesses a code can join that party. An invite check, e.g. when parties get companions, would close it.

## Cloud Storage

From `storage.rules`. Anything not listed is denied, including listing, overwriting and deleting.

| Path | Read | Write |
|---|---|---|
| `proofs/{reservationId}/{photoId}` | The reservation's party's `JOINED` members and its venue | A `JOINED` member of the reservation creates it while the reservation is `APPROVED`: an image (`image/*`) under 5 MB. Each photo gets a new `photoId`, so a retake never replaces an earlier one. |
| `user-pictures/{uid}/{photoId}` | Signed-in users | Only `uid` creates an image under 5 MB. Each change creates a new photo; nobody overwrites or deletes one. |

The rules read the reservation and the membership from Firestore, so a proof follows the same party as `reservations`. A completion's `photoUrl` points to its photo here.

## Decided changes

Decided in #259, from the review of #25 and from R2 (#274). The tables above describe `main`; a change still in progress updates them when it lands.

- **Copied venue fields in `quests`: adopted, done in #279 (F4a and F4b).** `venueName` and `location` go stale once a venue can rename itself or move its area, and dropping them later would mean migrating every stored quest. Readers take them from `venues/{venueId}` with `observeVenues`, one `in` query per 30 venues, as the overview (#269) does, and the rules reject a copied `venueName`. #283 removed the copies from the quests already stored on `around-67942`. Since #430 the rules accept a quest `location` again, but as the quest's own place, set by the venue for that quest (#429); a quest without one still takes the venue's.
- **Party membership: adopted, done in #281 (#294, #298, #295).** `explorerUids` loses who withdrew from a party. One membership document per explorer and reservation (`party_user`) keeps that history. Reservations no longer have `explorerUids`, and its readers (the venue page, the overview, `observeForExplorer` and the read and cancel rules) use the memberships. Reservations already stored on `around-67942` have no memberships, so only their venue can read them.
- **Ids copied into `completions`: only `questId` is dropped, done in #272 (F5).** The completion's reservation already names its quest. `venueId` and `explorerUid` stay, and the rules check them against the reservation and its memberships on creation. They never change afterwards, so unlike the venue fields in quests they can't go stale. The venue's list of completions to review and the explorer's history filter on them, and the rules can only allow a query that filters on the documents' own fields.
- **Text proof: adopted, done in #272 (F5).** `proofUrl` couldn't tell a typed answer from an uploaded photo, so a completion holds `photoUrl` for a `PHOTO` quest or `textAnswer` for a `TEXT` quest, never both.
- **The explorer's name copied into `reservations`: kept, in #274 (R2).** A user can read only their own `users/{uid}`, so the venue has no other way to show who reserved. The copy is the name at booking time, which is what the venue needs for a short-lived reservation.
