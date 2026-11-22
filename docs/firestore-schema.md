<!-- Co-authored-by: OpenAI Codex <noreply@openai.com> -->
<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
<!-- Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com> -->
# Firestore schema v1

This doc describes what the code on `main` does. A change to a collection updates its model, its repository, `firestore.rules` and this doc together.

## Conventions

- Domain models use our own `Location(lat: Double, lng: Double)`, never `GeoPoint` or `LatLng`. In Firestore a location is stored as a `GeoPoint`; the conversion happens inside the Firestore repository implementations.
- Times are `Long` epoch milliseconds in the Kotlin models and `Timestamp` in Firestore (`createdAt`, `updatedAt`, `slotStart`, `submittedAt`, `reward.expiresAt`). The conversion happens inside the Firestore implementations.
- Enum values are stored as their upper-case names (`EXPLORER`, `PENDING`, ...).
- A document's id is not stored as a field. For `users` it is the uid, for `venues` the owner's uid and for `party_user` `{reservationId}_{uid}`, whose parts are also stored as fields so queries can filter on them, for `party_codes` the code itself, and for `party_code_lookups` the uid; for the other collections it is a Firestore auto id.
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

The party isn't stored here: it is the reservation's memberships in `party_user`. The rules let an explorer create a reservation only in one batch with their own `JOINED` membership, joined at the reservation's `createdAt`, and with the `party_codes` document of its `partyCode`.

| Field | Type | Notes |
|---|---|---|
| `questId` | string | |
| `venueId` | string | Id of the venue that owns the quest. |
| `slotStart` | timestamp | Start of the booked slot. On creation it can't be more than 5 minutes in the past, to allow for a phone clock that is behind (enforced by the rules). |
| `status` | `PENDING`, `APPROVED`, `REJECTED` or `CANCELLED` | Set to `PENDING` by the repository on creation. |
| `createdAt` | timestamp | Set by the repository. |
| `explorerName` | string | Display name of the explorer who made the reservation, copied from their `users` profile on creation. Must equal that profile's `displayName` (enforced by the rules). Not updated on a rename. Reservations made before this field existed lack it; the app reads them with an empty name. |
| `joinedCount` | int | Number of `JOINED` members in `party_user`, so the rules can cap the party at its [capacity](#party-size) and check its size on approval: rules can't count documents. 1 on creation, for the creator. Every join adds 1 and every withdrawal takes 1 off, in the same batch as the membership (enforced by the rules). The app shows the party size from the memberships, not from this field. |
| `partyCode` | string? | The [party code](#party_codescode) explorers share to join the party, drawn by the repository and written once on creation with its `party_codes` document (enforced by the rules). Required on creation; reservations made before party codes have none, and the app reads them with none. |

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

A code is created in one batch with its reservation and never changes. Writing a code that is already taken is refused, so codes stay unique. When the server refuses a new reservation and its code's document exists, the repository draws another code and writes the batch again under a new id, at most 5 codes in all (`MAX_PARTY_CODE_ATTEMPTS`). A reservation created offline can't be retried: if its code turns out to be taken once it is sent, the server refuses it and it is lost, which with about 887 million codes is rare.

The app finds a reservation by its code (`findReservationIdByCode`) in capitals and without spaces, so `7kq m42` finds `7KQM42`, and only from the server: offline, the lookup fails rather than reporting an unknown code. Each lookup is counted first in the explorer's [`party_code_lookups`](#party_code_lookupsuid) document, which the rules require to read the code, so nobody looks up more than 10 codes an hour; past that, the lookup fails with `TooManyPartyCodeLookupsException` and the Join a party screen says to try again in an hour. Before joining, the app reads the reservation by that id (`getPartyPreview`), also only from the server, with its `joinedCount`: an explorer outside the party can't read the memberships.

### `party_code_lookups/{uid}`

How many party codes the user `uid` looked up, so nobody can find parties by trying codes (#542). The rules let a user get a party code only while it is the `code` of their own lookups document.

| Field | Type | Notes |
|---|---|---|
| `code` | string | The party code looked up last. |
| `count` | number | How many codes were looked up since `windowStart`, from 1 to 10 (`MAX_PARTY_CODE_LOOKUPS`). |
| `windowStart` | timestamp | The server's time of the first lookup of the hour (`FieldValue.serverTimestamp()`). |

Each lookup writes its code with `count` one higher, in a transaction so lookups at the same time all count. After 10, the next lookup starts a new hour at `count` 1, which the rules refuse until an hour has passed since `windowStart`. A user reads and writes only their own document, and never deletes it, so the count can't be reset. Reading a code to check it is taken when creating a reservation counts too, which is rare.

### `completions/{reservationId}`

One completion per reservation (`completionId`): the proof of its whole party. Any `JOINED` member sends it, and every member reads it and its result. The id is the reservation's, so a retried submission writes the same document instead of a second one, and the party can't complete a reservation twice, whichever member sends it. While it is `PENDING` or `REJECTED`, any `JOINED` member can replace it with a new proof, which makes it `PENDING` again without a `rejectReason`; once `APPROVED` it is final.

Completions written before #466 under `{reservationId}_{explorerUid}` were only test data and aren't supported.

| Field | Type | Notes |
|---|---|---|
| `reservationId` | string | The quest is the reservation's, so it isn't stored here. |
| `venueId` | string | Id of the venue that owns the quest. |
| `explorerUid` | string | The member who sent the latest proof. |
| `photoUrl` | string? | The photo's Cloud Storage location (`gs://...`, not a download URL), under `proofs/{reservationId}/` (see [Cloud Storage](#cloud-storage)), for a `PHOTO` quest. At most 2000 characters. |
| `textAnswer` | string? | The written answer, for a `TEXT` quest. At most 1000 characters. |
| `status` | `PENDING`, `APPROVED` or `REJECTED` | |
| `rejectReason` | string? | Required when `status` is `REJECTED`, not blank and at most 300 characters; must be absent otherwise. Enforced in `Completion` and by the rules. |
| `submittedAt` | timestamp | When the latest proof was sent. |

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
| `reservations` | An explorer may get a reservation whose id they know, so one given a party code can see its quest, status and `joinedCount` before joining. The app also listens to a reservation it just created before the server has it. A venue gets its own reservations, and only it lists them. Anyone signed in may read an id that has no reservation. | An explorer, whose `users` document has role `EXPLORER`, creates a `PENDING` one in the same batch as their own `JOINED` membership, joined at its `createdAt`, and the `party_codes` document of its `partyCode`, carrying their profile's `displayName` as `explorerName`, with a `joinedCount` of 1, for an `ACTIVE` quest whose `venueId` it repeats. Afterwards `status` changes along an allowed transition: the venue makes any of them from `PENDING`, approving only a party of the [size](#party-size) its quest asks for, and its creator, the member joined at its `createdAt`, can only cancel, until the party sends its proof, whatever its status; the other members withdraw from the party instead (#536). `joinedCount` changes alone, by 1, in the same batch as the user's own membership joining or withdrawing. No delete. |
| `party_user` | The party's members, `WITHDRAWN` ones too, so a member who withdrew still sees who else was in the party, and the reservation's venue. Anyone signed in may list the party of a reservation that doesn't exist, which is empty, for the same reason. | An explorer, whose `users` document has role `EXPLORER`, creates their own `JOINED` membership, under the id `{reservationId}_{uid}`, for a `PENDING` reservation whose party is below its [capacity](#party-size), joined at another time than the reservation's `createdAt`, adding 1 to its `joinedCount` in the same batch. Afterwards only the member changes it, from `JOINED` to `WITHDRAWN`, along with `updatedAt`, taking 1 off `joinedCount` in the same batch, while the reservation is `PENDING` or `APPROVED`, before the party sends its proof, and never the creator. No delete. |
| `party_codes` | Anyone signed in may get the code their `party_code_lookups` document names; nobody lists them. | Created only in the same batch as the reservation whose `partyCode` it is, with `reservationId` alone. Never updated or deleted. |
| `party_code_lookups` | Only its user. | Only its user: created with `count` 1 and `windowStart` the server's time, then each update counts one more with any party code, up to 10, or after an hour starts again at 1. No delete. |
| `completions` | The explorer who submitted it, the party's `JOINED` members and its venue. Anyone signed in may read an id that has no completion, so an answer to an unknown id can report it. | Any `JOINED` member of an `APPROVED` reservation creates the party's, as themselves in `explorerUid`, `PENDING` and without a `rejectReason`, under the reservation's id, repeating the reservation's `venueId`, with the proof its quest's `proofType` asks for. A `JOINED` member of the reservation can replace a `PENDING` or `REJECTED` one with a new proof of the same shape: `PENDING`, without a `rejectReason`, as themselves in `explorerUid`, for the same reservation and venue and the proof its quest asks for; an `APPROVED` one can't be replaced. Apart from that only the venue changes it: a `PENDING` one to `REJECTED`, changing only `status` and `rejectReason`, and a `PENDING` or `REJECTED` one to `APPROVED`, changing only `status` and removing any `rejectReason`. `APPROVED` is final. No delete. |

Joining a party needs only its reservation's id, and a `JOINED` membership lets the explorer read the reservation. So an explorer who learns the id of someone else's `PENDING` reservation can join that party, though not cancel it: only its creator cancels (#536). Ids are random and only shown to the party and the venue, so this rests on them staying private. A party code leads to its reservation's id, and is much shorter and easier to guess than an id, so each user may look up at most 10 codes an hour (see [`party_code_lookups`](#party_code_lookupsuid)): with about 887 million codes and a thousand pending parties, one account trying codes finds a party about once in 10 years, though many accounts find one sooner. Anyone given a code can still join its party; an invite check, e.g. when parties get companions, would close that. Any explorer can also read a reservation they know the id of, so its `explorerName`, `status` and `joinedCount` are no more private than the id or the party code that leads to it. Other venues can't.

## Cloud Storage

From `storage.rules`. Anything not listed is denied, including listing, overwriting and deleting.

| Path | Read | Write |
|---|---|---|
| `proofs/{reservationId}/{photoId}` | The reservation's party's `JOINED` members and its venue | A `JOINED` member of the reservation creates it while the reservation is `APPROVED`: an image (`image/*`) under 5 MB. Each photo gets a new `photoId`, so a retake never replaces an earlier one. |
| `user-pictures/{uid}/{photoId}` | Signed-in users | Only `uid` creates an image under 5 MB. Each change creates a new photo; nobody overwrites or deletes one. |

The rules read the reservation and the membership from Firestore, so a proof follows the same party as `reservations`. A completion's `photoUrl` points to its photo here.

## Planned: party and venue chat (E42)

Not built yet: this section is the plan from #645, following the designs of #644. The chat's model, repository and rules update the tables above when they land, and this section goes.

A reservation's party and its venue get one chat, to say they're running late or to ask a question before the visit. A chat only inside the party comes later.

### `chats/{reservationId}/messages/{messageId}`

One document per message, under an auto id. Messages are never edited or deleted.

| Field | Type | Notes |
|---|---|---|
| `senderUid` | string | The sender: a member of the party or the venue owner. |
| `senderRole` | `EXPLORER` or `VENUE` | `VENUE` exactly when `senderUid` is the reservation's `venueId`. The app shows a "Venue" tag on those messages. |
| `text` | string | Not blank, at most 1000 characters. |
| `sentAt` | timestamp | The server's time when it was written (`request.time`), so every phone sees the same order. |

No sender name is copied here: the app reads a member's name and picture from `users/{uid}` and the venue's name from `venues/{venueId}`, so they never go stale (see the venue fields in [Decided changes](#decided-changes)).

### `chats/{reservationId}`

A summary of the chat, for the venue's list of chats and the last message shown in it. The id is the reservation's. Only a Cloud Function writes it, each time a message is created and when the reservation is cancelled; the app only reads it. Until that function is deployed (see the README), no summary is written and the venue's list stays empty.

| Field | Type | Notes |
|---|---|---|
| `venueId` | string | The reservation's `venueId`, so a venue can list its chats. |
| `lastMessageText` | string | The latest message's `text`. |
| `lastSenderUid` | string | The latest message's `senderUid`. |
| `lastMessageAt` | timestamp | The latest message's `sentAt`. The venue's list shows the newest first. |
| `closesAt` | timestamp | When the chat [closes](#when-the-chat-closes). When the reservation is cancelled, the function sets it to the time of the cancellation, so the chat leaves the venue's list at once. |

A chat without messages has no summary yet.

### `chats/{reservationId}/reads/{uid}`

When each person last read the chat, for the unread counts on the Quests tab, the Messages tab and the venue's Dashboard.

| Field | Type | Notes |
|---|---|---|
| `lastReadAt` | timestamp | The server's time (`request.time`) when the user last had the chat open. |

The unread count is the number of messages whose `sentAt` is after `lastReadAt`, or all of them without a `reads` document. The app counts them with a listener on that query, limited to 100 and shown as "99+" above 99, not with a count query: a count query needs the server and doesn't update live, while a listener updates as messages arrive and offline counts the cached messages. Sending a message also moves the sender's `lastReadAt` to now, in the same batch, so their own messages never count as unread.

### Who can read and write

| Path | Read | Write |
|---|---|---|
| `chats/{reservationId}/messages` | The reservation's `JOINED` members and its venue. A member who withdrew left the party and its chat. | The same people create a message as themselves (`senderUid`, and `senderRole` matching it) while the chat is open. No update or delete. |
| `chats/{reservationId}` | The same people; a venue lists its open chats with `venueId == uid` and `closesAt` after now, ordered by `closesAt` (Firestore needs the first ordering on the range field), and the app sorts them by `lastMessageAt`. | Nobody: only the Cloud Function writes it. |
| `chats/{reservationId}/reads/{uid}` | Only `uid` | Only `uid`, while they can read the chat, with `lastReadAt` set to `request.time`. |

Checking a new message reads the reservation and, for an explorer, their `party_user` membership: at most 2 document reads.

### When the chat closes

The chat is open while the reservation is `APPROVED` and until its closing time. It never opens for a `PENDING` reservation, so the app shows no Messages tab before the venue approves.

Once the reservation is cancelled or the closing time has passed, the chat is read-only: its messages stay readable in Quest details, and the venue's list only shows open chats (`closesAt` after now). A time limit closes the chat of a party that never comes, which waiting for its proof wouldn't, and the rules check it from the reservation they already read.

**To decide before the chat is built: the closing time.** The plan was 1 day after `slotStart`, but explorers don't pick a slot yet: `slotStart` is the time they tapped accept, so the chat could close before the venue even approves. The options:

- 7 days after the venue approves, from a new `approvedAt` on the reservation set on approval. Doesn't depend on slots; needs a rules change.
- 7 days after `createdAt`. No new field, but a venue that approves late leaves the party less time.
- 1 day after `slotStart`, once explorers pick a slot.

## Decided changes

Decided in #259, from the review of #25 and from R2 (#274). The tables above describe `main`; a change still in progress updates them when it lands.

- **Copied venue fields in `quests`: adopted, done in #279 (F4a and F4b).** `venueName` and `location` go stale once a venue can rename itself or move its area, and dropping them later would mean migrating every stored quest. Readers take them from `venues/{venueId}` with `observeVenues`, one `in` query per 30 venues, as the overview (#269) does, and the rules reject a copied `venueName`. #283 removed the copies from the quests already stored on `around-67942`. Since #430 the rules accept a quest `location` again, but as the quest's own place, set by the venue for that quest (#429); a quest without one still takes the venue's.
- **Party membership: adopted, done in #281 (#294, #298, #295).** `explorerUids` loses who withdrew from a party. One membership document per explorer and reservation (`party_user`) keeps that history. Reservations no longer have `explorerUids`, and its readers (the venue page, the overview, `observeForExplorer` and the read and cancel rules) use the memberships. Reservations already stored on `around-67942` have no memberships, so only their venue can read them.
- **Ids copied into `completions`: only `questId` is dropped, done in #272 (F5).** The completion's reservation already names its quest. `venueId` and `explorerUid` stay, and the rules check them against the reservation and its memberships on creation. `venueId` never changes afterwards, so unlike the venue fields in quests it can't go stale; `explorerUid` is the member who sent the latest proof. The venue's list of completions to review and the explorer's history filter on them, and the rules can only allow a query that filters on the documents' own fields.
- **Text proof: adopted, done in #272 (F5).** `proofUrl` couldn't tell a typed answer from an uploaded photo, so a completion holds `photoUrl` for a `PHOTO` quest or `textAnswer` for a `TEXT` quest, never both.
- **The explorer's name copied into `reservations`: kept, in #274 (R2).** A user can read only their own `users/{uid}`, so the venue has no other way to show who reserved. The copy is the name at booking time, which is what the venue needs for a short-lived reservation.
- **A venue cancelling an approved reservation: refused, in #475.** After an approval the party may send its proof, and cancelling then would hide that proof from the explorer's Quests tab, the bug #426 fixed for the explorer. The rule could allow it until the proof is sent, as it does for members (`!exists(partyCompletion(reservationId))`, one read since #494), but no venue screen cancels today, so the venue never cancels an approved reservation, proof or not, which is the simpler rule. It still cancels a pending one. Clearing a party that never shows up (V22) would loosen it to "until the proof is sent".
