# How to contribute to the Itur Android client

## Prerequisites

- Android Studio Meerkat or later
- JDK 17
- Android SDK (API 36)

Build the local development flavour to get started without production Firebase configuration:

```bash
./gradlew assembleLocalDebug
```

See the [Getting started](README.md#getting-started) section of the README for the full setup.

## Branching and pull requests

1. Fork the repository and create a feature branch from `main`.
2. Keep each pull request focused on a single change.
3. Sign off every commit as described below.
4. Make sure the CI checks pass before requesting a review.

## Contribution rights

The project is distributed under GPL-3.0-or-later. Contributors retain
copyright in their work, and accepted contributions remain available in this
repository under GPL-3.0-or-later.

Before a contribution can be merged, its author must enter into a written
contributor agreement with the project owner. The agreement grants the project
owner a perpetual, irrevocable, worldwide, transferable right to use, modify,
distribute, sublicense, and relicense the contribution, including under
commercial terms. It does not assign the contributor's copyright.

If an employer or another organisation may own the contribution, that
rights-holder must provide the corresponding authorisation. Discuss a proposed
contribution with the maintainer before investing substantial work; the
agreement is handled directly and a pull request by itself does not grant these
additional rights.

Every commit must also include a Developer Certificate of Origin sign-off:

```text
Signed-off-by: Your Name <your.email@example.com>
```

Create it with `git commit --signoff`. The sign-off certifies the contribution's
provenance under the [Developer Certificate of Origin 1.1](https://developercertificate.org/).
It complements rather than replaces the contributor agreement.

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
