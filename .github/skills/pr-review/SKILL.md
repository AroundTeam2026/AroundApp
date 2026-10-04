---
name: pr-review
description: AroundApp pull request checklist. Use when reviewing or opening a PR.
---

# PR Checklist

Go through each point before approving (or opening) a pull request.

## Functionality
- [ ] The change does what the linked task / user story asks
- [ ] Acceptance criteria are met
- [ ] Edge cases and error states are handled
- [ ] New code has unit tests, and `./gradlew check` is green
- [ ] CI passes

## Test quality
- [ ] For each test, it's clear what must break in production code for it to fail; no test only checks the compiler or a constant
- [ ] No two tests fail for exactly the same breakages
- [ ] Every behaviour in the acceptance criteria and KDoc is covered, including error paths and "nothing changes on failure"
- [ ] Async and flow tests can't pass vacuously or hang: they subscribe before acting and use a timeout
- [ ] Tests use fakes or emulators, not the real Firebase project

## Design
- [ ] MVVM separation is respected (ViewModels never import Firebase)
- [ ] Code lives in the right package (`model/`, `ui/`)
- [ ] No unrelated changes; the PR is small and reviewable
- [ ] UI matches the Figma mockups

## Complexity
- [ ] Logic is as simple as it can be
- [ ] No duplicated code, dead code, or leftover debug code
- [ ] Functions are short and do one thing

## Coverage
- [ ] Line coverage of new code is above the determined threshold

## Naming conventions
- [ ] camelCase for functions and variables, PascalCase for classes and composables, UPPER_SNAKE_CASE for constants
- [ ] Names are clear and descriptive
- [ ] Commit messages and PR title follow `type(scope): subject`

## Documentation
- [ ] Public functions and classes have KDoc
- [ ] README / wiki updated if behavior or setup changed
- [ ] Non-obvious decisions are explained in a comment or the PR description