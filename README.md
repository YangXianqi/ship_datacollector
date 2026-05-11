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

- Backend build is verified with JDK 17 and Gradle 8.7.
- Admin web build is verified with Node 20 and npm 10.
- Android build is verified against a local Android SDK install.

### Android SDK

This machine is currently configured with:

```properties
sdk.dir=/home/sm6/project/.android-sdk
```

If you need to recreate it manually, create `android-app/local.properties` with your SDK path:

```properties
sdk.dir=/path/to/Android/Sdk
```

Then build with:

```bash
cd android-app
./gradlew :app:assembleDebug
```

Android API base URL is currently set in [app/build.gradle.kts](/home/sm6/project/android-app/app/build.gradle.kts) as:

- Emulator default: `http://10.0.2.2:8080/api`
- If you run on a physical Android device, change it to your backend machine's LAN IP
- Captured images and audio are stored in the app-private `files/media/` directory

### Backend

```bash
cd backend
./gradlew build
```

Run with embedded H2-compatible storage:

```bash
cd backend
./gradlew bootRun
```

Run with PostgreSQL profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shipyard \
SPRING_DATASOURCE_USERNAME=shipyard \
SPRING_DATASOURCE_PASSWORD=shipyard \
./gradlew bootRun
```

Run with the repo-local PostgreSQL instance that was set up for this project:

```bash
./scripts/start-local-postgres.sh
source ./scripts/backend-postgres-env.sh
/home/sm6/project/.tools/gradle-8.7/bin/gradle -Djava.io.tmpdir=/home/sm6/project/.tmp -p /home/sm6/project/backend bootRun --no-daemon
```

Stop the repo-local PostgreSQL instance:

```bash
./scripts/stop-local-postgres.sh
```

Seeded accounts:

- Admin: `13900000000 / admin123`
- Worker: `13800000000 / worker123`
- Uploaded attachment payloads are decoded and stored under `backend/build/attachments/<recordId>/`
- Verified local PostgreSQL database: `shipyard`
- Verified local PostgreSQL role: `shipyard / shipyard`

### Admin web

```bash
cd admin-web
npm install
npm run build
```

Optional dev override:

```bash
cd admin-web
VITE_API_BASE_URL=http://localhost:8080/api npm run dev
```

## Next recommended steps

1. Replace the simulated ChuangYun gateway with the real platform adapter.
2. Validate the Android app end-to-end against the live backend once the Android SDK is configured.
3. Add edit/re-record flows for saved local records, plus richer upload progress feedback.
4. Add production-grade attachment storage, device audit logs, and automated tests.
