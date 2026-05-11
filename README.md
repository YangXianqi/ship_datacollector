# Shipyard Offline Collector

Monorepo MVP for a shipyard field data collection platform with:

- `android-app/`: Android native collector app
- `backend/`: Spring Boot API for auth, form routing, upload orchestration, and ChuangYun adapter
- `admin-web/`: React + Ant Design admin console
- `docs/`: product and architecture notes

## Agreed MVP scope

- Android only
- First login must be online, later offline use is allowed for 30 days
- Records are stored locally first and uploaded later
- Upload success means "written to ChuangYun"
- Upload uses backend relay, not direct mobile-to-ChuangYun calls
- Record-level status is user-facing; file-level resume is internal
- Fixed capture structure for v1:
  - required `location_name`
  - required at least 1 photo
  - optional 1 audio note
  - optional text note

## Repository layout

```text
android-app/
backend/
admin-web/
docs/
```

## Current state

This repository now includes:

- Android local persistence, session storage, real attachment capture scaffolding, and upload queue logic
- A Spring Boot backend with JPA persistence, token-based session auth, and PostgreSQL-ready configuration
- A simulated internal ChuangYun gateway so the backend contract is real even before the vendor API lands
- A React admin console wired to the live backend admin endpoints

## Local setup notes

### What a fresh clone includes

The GitHub repository includes:

- all source code
- Gradle wrapper for `backend/` and `android-app/`
- npm lockfile for `admin-web/`
- docs and helper scripts

The GitHub repository does **not** include:

- JDK
- Android SDK
- Android emulator / AVD files
- local PostgreSQL binaries or data directories
- Gradle caches
- build outputs

### Required software on a new machine

To run this project after cloning, install:

- Git
- JDK 17
- Node.js 20 and npm 10
- Android Studio or Android SDK command line tools

Optional:

- PostgreSQL 16 if you want to run the backend with PostgreSQL instead of the default H2 local mode
- Android Emulator if you want to run the APK in an emulator

### Recommended first-run order

1. Clone the repo
2. Start the backend
3. Start the admin web
4. Build or run the Android app

### Backend

The backend requires Java 17. Gradle does **not** need to be installed globally because the repo already includes `backend/gradlew`.

Quick local start with embedded H2 storage:

```bash
cd backend
./gradlew bootRun
```

Build check:

```bash
cd backend
./gradlew build
```

Run with PostgreSQL:

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres \
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/shipyard \
SPRING_DATASOURCE_USERNAME=shipyard \
SPRING_DATASOURCE_PASSWORD=shipyard \
./gradlew bootRun
```

Notes:

- default backend port is `8080`
- if `8080` is already in use, run `./gradlew bootRun --args='--server.port=8081'`
- the helper scripts under `scripts/` were created for one specific local machine and depend on ignored local directories such as `.postgres-dist/`; teammates pulling from GitHub should not rely on those scripts unless they prepare the same local assets themselves

Seeded accounts:

- Admin: `13900000000 / admin123`
- Worker: `13800000000 / worker123`

### Admin web

The admin web requires Node.js and npm.

Install dependencies and start dev mode:

```bash
cd admin-web
npm install
npm run dev
```

Optional explicit backend URL:

```bash
cd admin-web
VITE_API_BASE_URL=http://localhost:8080/api npm run dev
```

Production build check:

```bash
cd admin-web
npm run build
```

Default dev address:

- `http://localhost:5173`

### Android app

The Android app requires Android SDK and Java 17. Gradle does **not** need to be installed globally because the repo already includes `android-app/gradlew`.

Recommended Android SDK components:

- Android SDK Platform 34
- Android SDK Build-Tools 34.0.0
- Android SDK Platform-Tools
- Android SDK Command-line Tools
- Android Emulator and one API 34 system image if you want to run an emulator

Create `android-app/local.properties` on your own machine:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build debug APK:

```bash
cd android-app
./gradlew :app:assembleDebug
```

Android API base URL is currently set in [app/build.gradle.kts](/home/sm6/project/android-app/app/build.gradle.kts) as:

- emulator default: `http://10.0.2.2:8080/api`
- if you run on a physical Android device, change it to your backend machine's LAN IP

Captured images and audio are stored in the app-private `files/media/` directory.

## Next recommended steps

1. Replace the simulated ChuangYun gateway with the real platform adapter.
2. Validate the Android app end-to-end against the live backend once the Android SDK is configured.
3. Add edit/re-record flows for saved local records, plus richer upload progress feedback.
4. Add production-grade attachment storage, device audit logs, and automated tests.
