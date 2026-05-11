# Architecture Notes

## Monorepo layout

- `android-app`: Kotlin + Jetpack Compose + Room + foreground service
- `backend`: Spring Boot + PostgreSQL-ready API
- `admin-web`: React + Ant Design internal console

## Logical architecture

```text
Android App -> Backend API -> ChuangYun Adapter -> ChuangYun
              ^
              |
         Admin Web
```

## Key backend responsibilities

- phone/password authentication
- form-permission lookup per account
- upload orchestration and final status confirmation
- idempotency using `record_id`
- file-level resume bookkeeping
- internal audit of device and login activity

## Android responsibilities

- offline-first capture
- encrypted local metadata storage
- attachment storage in app-private directories
- record queue management
- foreground upload service with pause/resume/cancel hooks

## Admin responsibilities

- create and disable users
- reset passwords
- assign allowed forms
- set upload/delete permissions
- inspect recent device/login activity

## API draft

### Auth

- `POST /api/auth/login`
- `POST /api/auth/change-password`

### User context

- `GET /api/me`
- `GET /api/me/forms`
- `GET /api/me/policy`

### Upload

- `POST /api/uploads`
- `POST /api/uploads/{recordId}/resume`
- `POST /api/uploads/{recordId}/cancel`
- `GET /api/uploads/{recordId}`

### Admin

- `GET /api/admin/users`
- `POST /api/admin/users`
- `POST /api/admin/users/{userId}/reset-password`
- `POST /api/admin/users/{userId}/status`
- `POST /api/admin/users/{userId}/permissions`
- `GET /api/admin/forms`

## Implemented backend notes

- Persistence uses Spring Data JPA entities and repositories.
- The default runtime uses an H2 database in PostgreSQL compatibility mode for local development.
- `application-postgres.yml` switches dialect/driver settings for real PostgreSQL.
- Session auth uses `Authorization: Bearer <token>`.
- Upload records are persisted by `record_id`, so duplicate submissions reuse the same row.
- The current ChuangYun integration point is an internal gateway interface with a simulated implementation.

## Idempotency model

- Mobile creates a stable `record_id`
- Mobile creates stable `file_id` values per attachment
- Backend deduplicates by `record_id`
- Adapter may be retried without duplicate ChuangYun writes
