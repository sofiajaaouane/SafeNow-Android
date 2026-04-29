# SafeNow-Android

SafeNow-Android is an Android mobile app inspired by SafeNow, focused on **personal safety** and **fast assistance**.

## Overview

- **Goal**: a “safety” app (profile, contacts, groups, SOS, history, notifications)
- **Architecture**: **MVVM** with **LiveData** (ViewModels expose LiveData to Activities/Fragments)
- **Data**: **Firebase Realtime Database (RTDB)** as the source of truth + **Room (SQLite)** as a local cache
- **Realtime**: a service (e.g. `AlwaysListenService`) listens to RTDB and refreshes the cache automatically

## Tech stack

- **Language**: Kotlin
- **Platform**: Android
- **Build**: Gradle
- **UI**: XML layouts (e.g. `app/src/main/res/layout/`)
- **Data**: Firebase RTDB + Room (SQLite)

## Requirements

- **Android Studio** (recent version recommended)
- **Android SDK** installed via Android Studio
- **JDK** compatible with Android Studio/Gradle (Android Studio’s embedded JDK is usually enough)

## Setup

1. **Clone the project**

```bash
git clone <votre-repo>
cd SafeNow-Android
```

2. **Open in Android Studio**

- Android Studio → **Open** → select the `SafeNow-Android` folder

3. **Sync Gradle**

- On first launch, Android Studio will suggest a **Gradle Sync**
- Wait for indexing and sync to complete

4. **Run the app**

- Connect a phone (USB debugging enabled) **or** create an emulator (AVD)
- Click **Run** (▶) and choose the device

## Firebase setup

This project uses **Realtime Database** (and may use FCM). The main missing file is `google-services.json` (it is ignored by git).

1. **Create/open a Firebase project**

- Firebase Console → create (or select) a project

2. **Add an Android app in Firebase**

- “Add app” → Android
- **Android package name**: `com.example.safefnow2` (must match `applicationId` in `app/build.gradle.kts`)
- (Recommended) Add **SHA-1 / SHA-256** if you use services that require them (e.g. FCM, Auth, etc.)

3. **Download `google-services.json`**

- Download the file from Firebase Console
- Place it here: `app/google-services.json`

4. **Sync Gradle**

- Android Studio → “Sync Now”
- Note: the `com.google.gms.google-services` plugin is applied automatically **only** if `app/google-services.json` exists.

5. **Enable Firebase products (if needed)**

- **Realtime Database**: enable the database and configure rules
- **FCM**: enable Cloud Messaging (notification delivery also depends on Android/server configuration)

## RTDB data structure notes

Main paths (indicative):

- `users/<userId>`
- `emergencyGroups/<groupId>`
- `groupMembers/<groupId>/<userId> = true`
- `groupMembersByUser/<userId>/<groupId> = true`
- `alerts/<alertId>`
- `declarationAlerts/<userId>/<alertId>`

## Useful commands (optional)

From the project root:

```bash
./gradlew.bat :app:assembleDebug
```

```bash
./gradlew.bat :app:lintDebug
```

## Quick structure

- `app/` : module principal Android
- `app/src/main/java/` : code Kotlin
- `app/src/main/res/` : ressources (layouts XML, strings, drawables, etc.)

## Notes

- **Do not commit**: `.gradle/`, `app/build/`, generated files, `google-services.json` (contains project information)
- **UI text**: user-visible text should be stored in `app/src/main/res/values/strings.xml` and referenced via `@string/...` in layouts (and via `R.string...` in UI messages).
