# How to contribute to the Itur Android client

## Prerequisites

- Android Studio Meerkat or later
- JDK 17
- Android SDK (API 36)

Build the demo flavour to get started without any credentials:

```bash
./gradlew assembleDemoDebug
```

See the [Getting started](README.md#getting-started) section of the README for the full setup.

## Branching and pull requests

1. Fork the repository and create a feature branch from `main`.
2. Keep each pull request focused on a single change.
3. Make sure the CI checks pass before requesting a review.

## Code style

Spotless enforces KTLint formatting and the licence header on every Kotlin and XML file. Run before committing:

```bash
./gradlew -I spotless/spotless.gradle.kts spotlessApply
```

The CI pipeline runs `spotlessCheck` on every push, so unformatted code will fail the build.

## Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (connected device or emulator required)
./gradlew connectedAndroidTest
```

All existing tests must pass. New behaviour must be accompanied by tests.

## Commit messages

Use the [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <short summary>
```

Common types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.
Scope is optional but helpful (e.g. `map`, `auth`, `data`).

Examples:

```
feat(map): add user name label to participant markers
fix(auth): handle null Google credential gracefully
docs: update Firebase setup instructions
```
