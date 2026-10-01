# Sync & Build Issues — Summary

Quick glossary:
- **AGP** = Android Gradle Plugin (the tool that builds the Android app).
- **Sync** = Android Studio reading the project's build files to set everything up.
- **Package** = the folder-like name (e.g. `com.example.app`) that groups code files and must match where the file lives on disk.
- **Manifest** = `AndroidManifest.xml`, the file that lists app components (screens, permissions...).
- **Lint** = automatic code checker for common mistakes.
- **SDK level** = which version of Android the app is built/tested against.

| # | Problem | Why it happens (implication) | Fix applied |
|---|---------|-------------------------------|-------------|
| 1 | Sync failed: `AgpWithBuiltInKotlinAppliedCheck` error | AGP 9 now compiles Kotlin itself ("built-in Kotlin"). The project was *also* applying the separate Kotlin plugin — having both at once is not allowed. | Removed the separate Kotlin plugin (`jetbrainsKotlinAndroid`) from the app module. |
| 2 | New error after fix #1: `Argument type mismatch: Iterable<*>` at line 77 | Removing the Kotlin plugin also changed how "source sets" (folders Gradle looks for code in) are configured. The old code used an outdated way (`setSrcDirs`) incompatible with AGP 9's built-in Kotlin. | Rewrote the source-set setup using the newer `directories.add(...)` method (same style used in the reference `bootcamp` project). |
| 3 | AGP downgraded to 8.13.2 as a first attempt | Downgrading avoided the AGP 9 issues, but it moved the project *away* from how the reference `bootcamp` project is actually built (which uses AGP 9 + built-in Kotlin). | Reverted: went back to AGP 9.3.0 and applied the real fix (#1 + #2) instead of downgrading. |
| 4 | `compileSdk`/`targetSdk`/`minSdk` were 34/34/28 | The library versions used (Compose, etc.) are recent ones meant for higher Android SDK levels (36/37). Building against an older SDK level risks errors or warnings later. | Bumped to `compileSdk=37`, `targetSdk=36`, `minSdk=29` to match the reference project. |
| 5 | Lint error: `MissingClass` — `SecondActivity` not found | The screen files (`MainActivity.kt`, `SecondActivity.kt`) declared `package com.android.sample`, but the app's real package (set in the build file) is `com.github.aroundteam2026.aroundapp`. Android looks for the class in the real package and doesn't find it — like mailing a letter to the wrong street. | Moved both files into the correct package folder and updated their `package` line. Fixed the two test files that referenced them (added an `import`). |
| 6 | Stale Gradle cache folder `.gradle/8.13` (9 MB) and old build outputs (`app/build`, 273 MB) | Leftovers from the AGP-downgrade experiment (#3) and normal build output; both are safe to delete since Gradle regenerates them. | Ran `./gradlew clean` and deleted `.gradle/8.13`. |
| 7 | Code formatting not matching the project's style checker (`ktfmt`) | A few files (including the moved activities and some sample files) had spacing/indentation the auto-formatter didn't like. | Ran `./gradlew ktfmtFormat` to auto-fix. |
| 8 | SonarCloud config pointed to someone else's project (`gf_android-sample` / `gabrielfleischer`) | This was copied from a different sample project and never updated. Running code-quality analysis (`./gradlew sonar`) would fail or upload results to the wrong place. | Updated the key/org to match this repo's GitHub org (`aroundteam2026_AroundApp` / `aroundteam2026`). **Still needs confirming that this project actually exists on SonarCloud.** |

## Known issues NOT yet fixed (left as-is, not blocking)

| Issue | Why it's not fixed yet |
|-------|--------------------------|
| Instructions file (`AGENTS.md`) describes milestone test flags (`-B1`, `-B2`, `-B3`) and CI filtering that don't actually exist in this project's build/CI files | Bigger feature to port over from the reference project — not a quick fix. |
| No mock-testing libraries (mockk/mockito) and no coroutine-testing library | Needed once real ViewModel code (with repository interfaces) is written — not needed yet. |
| Missing a small Firebase fix (excluding `protobuf-lite`) that prevents a known test crash | Only matters once Firestore-related tests are written. |
| Deprecated test API used in one sample test file; a few lint warnings (hardcoded text, missing icon sizes) | These live in template/sample files that will be replaced once real app screens are built. |
| Project name still says `SampleApp` in `settings.gradle.kts` | Cosmetic, low priority. |
