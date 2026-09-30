# PR Checklist

Go through each point before approving (or opening) a pull request.

## Functionality
- [ ] The change does what the linked task / user story asks
- [ ] Acceptance criteria are met
- [ ] Edge cases and error states are handled
- [ ] New code has unit tests, and `./gradlew check` is green
- [ ] CI passes

## Design
- [ ] MVVM separation is respected (ViewModels never import Firebase)
- [ ] Code lives in the right package (`model/`, `ui/`)
- [ ] No unrelated changes; the PR is small and reviewable
- [ ] UI matches the Figma mockups

## Complexity
- [ ] Logic is as simple as it can be
- [ ] No duplicated code, dead code, or leftover debug code
- [ ] Functions are short and do one thing

## Naming conventions
- [ ] camelCase for functions and variables, PascalCase for classes and composables, UPPER_SNAKE_CASE for constants
- [ ] Names are clear and descriptive
- [ ] Commit messages and PR title follow `type(scope): subject`

## Documentation
- [ ] Public functions and classes have KDoc
- [ ] README / wiki updated if behavior or setup changed
- [ ] Non-obvious decisions are explained in a comment or the PR description