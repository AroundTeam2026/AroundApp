<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
# Around

## Pitch

Going out with friends tends to default to the same few places, while newcomers to a city; such as an international student who wants to explore but lacks local knowledge or an established social circle, have an even harder time discovering somewhere new and finding people to go with. Review apps reinforce existing rankings, making it difficult for newcomers to discover places beyond the most established venues. Discovery is also a coordination problem: trying an unfamiliar place often depends on having company, so people without an available group miss out, while those who find it difficult to approach strangers have no easy way to form one. Small venues face the mirror problem: blanket discounts are costly, and impersonal social-media posts are easy to ignore, assuming they reach nearby locals or newcomers at all. "Around" turns local discovery into a game. Venues (restaurants, cafes， bars) publish short, location-verified quests (i.e.: going with a party of at least six, posting a picture of a dish in the apps menu, finding a small photo of a character hidden in the venue) tied to their premises, each offering a reward (coupon, discount, free desert, etc.) only they can grant. Users discover quests on a live map, team up with like-minded companions when they do not want to explore alone, and climb city and friend leaderboards as they explore. The core insight is that giving discovery a shared activity, social structure, and tangible reward can lower the barrier to trying unfamiliar places while helping small venues reach people who are actively looking for somewhere new.

## Split-app model

The app uses Firebase for its cloud-based functionality. Firebase Authentication manages user accounts and sign-in, while Firebase stores and synchronizes shared application data such as quests, rewards, leaderboards, companion queues, reviews, and chats across users and devices. Frequently needed data, including available quests, the user’s active quest, profile information, and pending quest completions, is cached locally so that parts of the app remain usable without a network connection and can be synchronized once connectivity returns.

## Multi-user support

The app supports multiple users through authenticated accounts, with each user signing up and logging in to their own account either through Google Sign-In or by creating an account directly in the app. Two account types are available: Explorers, who can manage their profile, interests, friends, posts, and quest history, and Venues, which are business accounts used to create and manage quests and rewards. Each account has access only to the functionality and data associated with its role, and users can update their account information and manage their activity within the app.

## Sensor use

The app uses GPS to determine the user’s location, unlock quests when they enter the required geofenced area, and show nearby quests relative to their current position. The camera supports photo-based quest proof, and user posts.

## Offline mode

In offline mode, users can still view quests they have already picked up, access recently cached nearby map data, and view their cached profile. Quest completions made while offline are stored locally and automatically synced once connectivity returns. Features that depend on live data, such as discovering new quests or viewing updated leaderboards, are temporarily unavailable; leaderboards instead display the most recently synced state together with an “as of” timestamp so users know the information may be outdated.

## Getting started

### Prerequisites

- JDK 17
- Android Studio

### Firebase configuration

The app connects to the `around-67942` Firebase project and needs a `google-services.json` file in `app/`. Download it from the [Firebase console](https://console.firebase.google.com/) for the `around-67942` project (Project settings → Your apps → Android app) and place it at `app/google-services.json`.

### Google Maps API key

The map needs a Google Maps API key. Add it to `local.properties` at the repository root, which git ignores:

```properties
MAPS_API_KEY=AIza...
```

Ask the team for the key. It lives in the [Google Cloud console](https://console.cloud.google.com/google/maps-apis/credentials) of the `around-67942` project, restricted to the Maps SDK for Android and to the package `com.github.aroundteam2026.aroundapp`, signed by one of the team's registered debug certificates.

> [!IMPORTANT]
> **Map stays grey? Your certificate isn't registered yet.** Every computer signs debug builds with its own certificate, and the key rejects certificates it doesn't know: logcat then shows `Authorization failure`. Run `./gradlew :app:signingReport`, copy the `SHA1` line of the `debug` variant, and send it to [@ferido1510](https://github.com/ferido1510) to be added to the key. The SHA-1 is a public fingerprint, not a secret, so any team channel is fine. Never share the API key itself in public channels.

On CI, the key comes from the `MAPS_API_KEY` repository secret, and CI builds are signed with the team's CI keystore from the `DEBUG_KEYSTORE` secret (base64-encoded, like `GOOGLE_SERVICES`), whose SHA-1 is registered on the key. The keystore is never committed. It uses Android's standard debug alias and passwords, which Gradle expects; keeping the file secret is what protects it. It was generated with:

```sh
keytool -genkeypair -keystore around-ci-debug.keystore -storetype PKCS12 -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Around CI Debug,O=AroundTeam2026,C=CH"
```

Its SHA-1 is `BA:28:CF:28:FF:37:5A:A9:85:5D:7A:43:84:38:26:0A:1D:14:99:4D`, and [@ferido1510](https://github.com/ferido1510) keeps the original file. To replace it, generate a new keystore with the command above, register its SHA-1 on the key, update the `DEBUG_KEYSTORE` secret, update the SHA-1 here and in `REGISTERED_SHA1` in `.github/workflows/ci.yml` (CI checks the restored keystore against it and fails if they differ), then remove the old SHA-1 from the key.

Without a key the app still builds and runs, but the map stays empty and `MapScreenDeviceTest` fails.

### Firebase emulators

Instrumented tests that exercise the Firestore repository run against the Firebase emulators instead of production. To run them locally, install:

- Node 20+
- JDK 21
- [firebase-tools](https://firebase.google.com/docs/cli)

### Demo quests on the map

Until the map reads quests from Firestore, it shows made-up venues and quests around Lausanne, from `model/demo/MapDemoData.kt`, kept in memory.

### Useful commands

```sh
./gradlew check          # unit tests + lint
./gradlew ktfmtFormat     # auto-format Kotlin sources
./gradlew connectedCheck  # instrumented tests (needs an Android emulator and the Firebase emulators running)
```

## Design

Wireframes: [Figma](https://www.figma.com/design/yC84SMe4qfm0Kgi6J7kRRj/wireframes--first-draft?node-id=0-1&t=u9vmQpgdUfN1YAHC-1)

## Acknowledgements

- The project skeleton comes from [swent-epfl/Android-Sample](https://github.com/swent-epfl/Android-Sample).
- `AGENTS.md` originally comes from the bootcamp template.