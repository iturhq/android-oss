# Itur Android Client

[![CI](https://github.com/mnohe/itur-android/actions/workflows/ci.yml/badge.svg)](https://github.com/mnohe/itur-android/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/mnohe/itur-android/graph/badge.svg)](https://codecov.io/gh/mnohe/itur-android)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Min SDK 24](https://img.shields.io/badge/minSdk-24-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org/)

Android app for the **Itur** group activity tracking system.
Participants join a shared map session, see each other's real-time positions, and coordinate through a minimal, map-first interface.

## Features

- **Live map**: follows the user's position using [MapLibre](https://maplibre.org/).
- **Join via QR**: scan a QR code to join an ongoing activity.
- **Start & manage activities**: organisers start activities, share a QR for others to join, broadcast an SOS, and end the session.
- **Google Sign-In**: Firebase-backed authentication. Anyone with a Google account can sign in and become an organiser.
- **Real-time positions**: participant and organiser locations synced through Firestore.
- **Demo flavour**: fully functional offline build with fake repositories, used for testing; no Firebase account or API keys required.

## Tech stack

Written in [Kotlin](https://kotlinlang.org/), with a [multi-module Clean Architecture](https://developer.android.com/topic/modularization) layout and [Hilt](https://dagger.dev/hilt/) for dependency injection.
The UI is built entirely with [Jetpack Compose](https://developer.android.com/compose) and [Material 3](https://m3.material.io/), using [Jetpack Navigation](https://developer.android.com/guide/navigation) between screens.
Maps are rendered by the [MapLibre Android SDK](https://maplibre.org/maplibre-native/android/api/).
Authentication and live data sync run on [Firebase Auth](https://firebase.google.com/docs/auth) and [Cloud Firestore](https://firebase.google.com/docs/firestore).
User preferences are persisted with [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore) using [Protocol Buffers](https://protobuf.dev/) (via [Wire](https://github.com/square/wire)).
QR codes are scanned with [ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning) and generated with [ZXing](https://github.com/zxing/zxing).
Code style is enforced by [KTLint](https://pinterest.github.io/ktlint/) via [Spotless](https://github.com/diffplug/spotless).

## Module structure

```
app/
core/
  auth/          Firebase authentication
  data/          Firestore repositories
  datastore/     User preferences (DataStore + Proto)
  domain/        Business logic, use cases
  location/      Location services
  model/         Shared data models
  ui/            Shared Compose components
feature/
  map/           Main map screen and ViewModels
```

## Build flavours

* `demo`: In-memory fake repositories, no credentials needed. Never links or calls Firebase Crashlytics/Performance Monitoring — see link:docs/adr/ADR-003-observability.adoc[ADR-003].
* `local`: Connects to Firebase Emulator Suite running in [itur-dashboard](https://github.com/mnohe/itur-dashboard), requires `local.properties`. Reports crashes/performance to Firebase.
* `prod`: Live Firebase backend, requires `local.properties` and `google-services.json`. Reports crashes/performance to Firebase.

## Prerequisites

- Android Studio Meerkat or later
- JDK 17
- Android SDK with API 36 platform

## Getting started

### Demo build (no credentials required)

```bash
./gradlew assembleDemoDebug
```

Install the resulting APK, or run directly on a device/emulator:

```bash
./gradlew installDemoDebug
```

### Production build

1. Copy `local.properties.example` to `local.properties` and fill in your MapLibre and Google OAuth credentials.
2. Create a Firebase project, enable **Authentication** (Google provider) and **Firestore**, download `google-services.json`, and place it in `app/`.
3. Build:

```bash
./gradlew assembleProdDebug
```

### Emulated Firestore (development)

To test against an emulated Firestore without requiring live Firebase credentials:

1. Set up and run the Firebase Emulator Suite using the [itur-dashboard](https://github.com/mnohe/itur-dashboard) project:
   ```bash
   cd itur-dashboard
   firebase emulators:start --only firestore,auth,functions
   ```

2. Copy `local.properties.example` to `local.properties` and fill in your MapLibre credentials (Google OAuth is not required for the emulator).

3. Build and run the `local` flavour:
   ```bash
   ./gradlew installLocalDebug
   ```

The `local` flavour connects to the Firebase Emulator Suite and works alongside the [itur-dashboard](https://github.com/mnohe/itur-dashboard) web dashboard for full system testing.

## Running the tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires a connected device or emulator)
./gradlew connectedAndroidTest
```

## Code style

Spotless enforces KTLint formatting and the project copyright header on every
Kotlin file.  Run before committing:

```bash
./gradlew -I spotless/spotless.gradle.kts spotlessApply
```

## Licence

[GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html) — Itur © 2025 Max Noé \<code@itur.cat>

Contributions are accepted under the same public licence and require the
rights and provenance records described in [CONTRIBUTING.md](CONTRIBUTING.md).

---

This project was developed as the practical component of a final degree project at Universitat Oberta de Catalunya (UOC). The [associated academic paper](https://openaccess.uoc.edu/server/api/core/bitstreams/0ac923ef-e562-4ea5-a111-d57ac7154640/content) is available at the UOC's open access repository.
