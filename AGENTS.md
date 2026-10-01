# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

A Kotlin/Android Exploration/Social app (`com.github.aroundteam2026.aroundapp`), built with an **MVVM** architecture.

- `model/` holds the data and repositories (Firestore, Location, ...).
- `ui/` holds the screens and their **ViewModels**.

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.
- Do not edit anything under `sigchecks/` or generated code.

## Definition of done

- The feature works and matches its acceptance criteria (the acceptance criteria of the user story).
- **All new code comes with unit tests.**
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting) before you submit.
- Features covered by instrumented tests (under `androidTest/`, e.g. the Firestore repository) must also pass `./gradlew connectedDebugAndroidTest`, with an Android emulator and the Firebase emulator running.
- The CI runs the whole test suit, no flags exist to govern the CI behavior.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check` passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Commit messages follow Conventional Commits: `type(scope): subject`, with type one of `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `style`, `ci`. The subject is lowercase, imperative, at most 50 characters after the prefix, with no trailing period (e.g. `feat(auth): add user authentication`). Add a blank line to separate it from the body. Add a body wrapped at 72 characters when the subject is not enough.
- **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. Acknowledge contributions on commit messages as well with a `Co-authoried-by` line at the end of the commit message. An AI agent is a contributor, so credit it.

## Code style

- Use **camelCase** for functions, variables and properties (e.g. `userName`, `fetchTasks()`).
- Use PascalCase for classes, objects and composables (e.g. `TaskViewModel`, `TaskScreen`).
- Use UPPER_SNAKE_CASE for constants (e.g. `MAX_RETRIES`).
## Your role

You provide the goal, the context, the acceptance criteria and the permissions. The agent plans, acts and observes. **You review the diff, and you own every line you submit.** "The agent wrote it" is not a defence.
