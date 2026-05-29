# ERP + E-Commerce Backend API

Enterprise REST + WebSocket API written in **Java 17 + Spring Boot 3.3**, designed to back a **Flutter mobile app** and a **Nuxt/Vue admin dashboard**. Feature-based MVVM-style layout (every feature owns its `entity / dto / repository / service / controller`), Repository Pattern via Spring Data JPA, JWT auth with RBAC, PostgreSQL, Flyway migrations, plus a realtime chat and voice/video call subsystem (STOMP-over-WebSocket + Stream Video + FCM push).

## Stack

| Layer            | Choice                                                                       |
|------------------|------------------------------------------------------------------------------|
| Language         | Java 17                                                                       |
| Framework        | Spring Boot 3.3 (Web, Security, Data JPA, Validation, WebSocket, Actuator)    |
| DI               | Spring                                                                        |
| ORM              | Hibernate via Spring Data JPA (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`)       |
| DB               | PostgreSQL 16                                                                 |
| Migrations       | Flyway 10                                                                     |
| Auth             | JWT (jjwt 0.12.x) + BCrypt (Spring Security)                                  |
| Realtime         | STOMP over WebSocket (`spring-boot-starter-websocket`)                        |
| Voice/Video      | [Stream Video](https://getstream.io/video/) (server issues JWT, client joins) |
| Push             | Firebase Cloud Messaging (incoming-call invites when app is backgrounded)     |
| Rate limiting    | Bucket4j (in-memory token bucket per IP)                                      |
| Validation       | Jakarta Bean Validation + service-layer rules                                 |
| Boilerplate      | Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`)                            |
| Docs             | springdoc-openapi (SwaggerUI at `/docs`)                                      |
| Logging          | Logback + MDC `traceId` (X-Request-Id)                                        |
| Tests            | spring-boot-starter-test + Mockito + Testcontainers                            |
| Container        | Docker, docker-compose, nginx reverse proxy                                    |

## Folder structure

The codebase is **feature-first**. Cross-cutting infra lives under `core/`; each
feature owns its full vertical slice under `features/<name>/`:

```
src/main/java/com/company/erp/
├── ErpApplication.java                  # @SpringBootApplication entry point
├── core/                                # framework-level concerns, no feature logic
│   ├── bootstrap/AdminBootstrap.java    # seeds the super-admin user on first boot
│   ├── config/AppProperties.java        # @ConfigurationProperties
│   ├── database/                        # BaseEntity, PageQuery
│   ├── exceptions/                      # AppException + @RestControllerAdvice
│   ├── response/                        # ApiResponse<T>, PageResponse<T>, envelope advice
│   ├── security/                        # JwtService, JwtAuthFilter, SecurityConfig, Permissions
│   ├── audit/AuditorAwareConfig.java
│   └── web/                             # RequestIdFilter, RateLimitFilter, OpenApiConfig, HealthController
├── features/
│   ├── auth/                            # login, register, refresh (rotating), logout
│   │   ├── controller/AuthController.java
│   │   ├── dto/                         # LoginRequest, RegisterRequest, RefreshRequest, …
│   │   ├── entity/RefreshToken.java
│   │   ├── repository/RefreshTokenRepository.java
│   │   └── service/AuthService.java, RefreshTokenCleanupJob.java
│   ├── users/                           # FULL CRUD + RBAC reference module
│   │   ├── controller/                  # UserController, RoleController
│   │   ├── dto/                         # UserDto, RoleDto, PermissionDto, request types
│   │   ├── entity/                      # User, Role, Permission
│   │   ├── repository/                  # UserRepository, RoleRepository, PermissionRepository
│   │   └── service/                     # UserService, RoleService
│   ├── products/                        # FULL CRUD example with JPA Specifications (planned)
│   ├── categories/                      # FULL CRUD lightweight (planned)
│   ├── chats/                           # realtime chat + voice/video (planned)
│   │   ├── controller/  call/  ws/  …
│   ├── devices/                         # FCM device-token registration per user (planned)
│   ├── employees/, attendance/, customers/, suppliers/, warehouse/,
│   │   inventory/, orders/, payments/, procurement/, accounting/,
│   │   notifications/, reports/, audit/, settings/        (planned stubs)

src/main/resources/
├── application.yml                      # env-driven Spring config
├── logback-spring.xml
└── db/migration/                        # Flyway V1__init … V7__voice_calls
```

Every feature folder follows the same shape:

```
features/<name>/
├── entity/      ← JPA @Entity classes extending core.database.BaseEntity (Long id + audit)
├── dto/         ← request + response records, jakarta.validation annotations
├── repository/  ← Spring Data JpaRepository (+ JpaSpecificationExecutor where needed)
├── service/     ← @Service @Transactional — business rules live here
└── controller/  ← @RestController @RequestMapping("/api/v1/<name>") + @PreAuthorize
```

Migrations:

| Version | File                          | Purpose                                                       |
|---------|-------------------------------|---------------------------------------------------------------|
| V1      | `V1__init_schema.sql`         | core tables (users, roles, permissions, refresh tokens)        |
| V2      | `V2__erp_ecommerce_schema.sql`| ERP/e-commerce domain tables (planned)                         |
| V3      | `V3__seed_roles_and_admin.sql`| seed `SUPER_ADMIN`/`ADMIN`/`STAFF`/`CUSTOMER` + permission catalogue |
| V4      | `V4__employee_image.sql`      | employee avatar column (planned)                               |
| V5      | `V5__chat_module.sql`         | conversations, members, messages (planned)                     |
| V6      | `V6__user_avatar.sql`         | user avatar column (planned)                                   |
| V7      | `V7__voice_calls.sql`         | call sessions + participants (planned)                         |
| V9      | `V9__employees.sql`           | employees table (HR profile, optional link to a login user)    |
| V10     | `V10__employee_profile_extra_fields.sql` | adds `last_login_at`, `emergency_contact`, `emergency_phone`        |
| V11     | `V11__employee_avatar_upload_meta.sql`   | adds `avatar_content_type` + `avatar_uploaded_at` columns to employees |
| V13     | `V13__chat_module.sql`                   | conversations, members, messages, reactions                          |
| V14     | `V14__chat_calls.sql`                    | call sessions + participants (signalling only)                       |

Flyway is configured with `out-of-order: true`, so V2 (and any other gap) can
be slotted in later between V1 and V3 without breaking applied history.

## Quick start

### 1. Run with docker-compose

```bash
cp .env.example .env
# edit .env — at minimum, replace JWT_SECRET with a long random value
docker compose up --build
```

The API listens on `http://localhost:8080` (and on port 80 via nginx). Postgres exposes 5432. Flyway runs migrations on boot.

### 2. Run locally (Postgres in Docker, app on host)

```bash
docker compose up -d postgres
gradle bootRun
```

### 3. Verify

```bash
curl http://localhost:8080/health
# { "success": true, "message": "Success", "data": { "status": "UP" }, "errorCode": null, "traceId": "0f1c…" }
```

Open **SwaggerUI** at <http://localhost:8080/docs>.
Actuator health: <http://localhost:8080/actuator/health>

### Bootstrap super-admin

A super-admin is created on first boot by `core.bootstrap.AdminBootstrap`
(after Flyway's V3 seeds the SUPER_ADMIN role). The hash has to be produced
at runtime because SQL can't generate bcrypt:

```
email:    admin@company.local
password: Admin@12345
```

**Change this password immediately in any non-local environment.**

## Standard API envelope

Every endpoint returns:

```json
{
  "success": true,
  "message": "Success",
  "data": { /* ... */ },
  "errorCode": null,
  "traceId": "0f1c…"
}
```

Errors map to stable codes: `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `CONFLICT`, `NOT_FOUND`, `RATE_LIMITED`, `INTERNAL_ERROR`. Validation errors include per-field details in `data.fieldErrors`.

## Pagination & filtering

All list endpoints share the same query convention:

```
GET /api/v1/products?page=1&pageSize=20&search=shirt&sort=price:asc&minPrice=10&maxPrice=200
```

- `page` ≥ 1 (1-indexed; Spring's internal 0-based pagination is hidden by `PageResponse`)
- `pageSize` clamped to 1..100
- `search` case-insensitive substring on resource-specific columns
- `sort` is `field:asc|desc`; the controller passes an explicit whitelist of allowed fields

## Authentication & RBAC

- `Authorization: Bearer <accessToken>` for every protected endpoint
- Access tokens are short-lived (default 15 min); refresh tokens are long-lived (default 14 d) and **rotated** on every `/auth/refresh`
- Refresh tokens are tracked server-side by `jti` (table `refresh_tokens`) so they can be revoked
- Controllers guard themselves via `@PreAuthorize("hasAuthority(Permissions.<X>)")`
- Roles aggregate permissions; the seed migration ships `SUPER_ADMIN`, `ADMIN`, `STAFF`, `CUSTOMER`
- Permission codes live in `com.company.erp.core.security.Permissions` — keep that constants file in sync with the V3 seed migration

### Bulk role assignment

Assign or revoke roles for many users in one call. Requires `user:write`.

```
POST /api/v1/users/assign-roles
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "userIds": [12, 17, 23],
  "roles":   ["STAFF"],
  "mode":    "ADD"          // ADD (default) | REPLACE | REMOVE
}
```

Modes:

| Mode      | Effect on each target user                                       |
|-----------|------------------------------------------------------------------|
| `ADD`     | Union the given roles into the user's existing role set          |
| `REPLACE` | Set the user's roles to exactly the given set (empty = clear all)|
| `REMOVE`  | Subtract the given roles from the user's existing role set       |

Returns the updated users as `UserDto[]`. The operation is transactional and
atomic — if any `userId` doesn't exist or any role code is unknown, the whole
call fails (`NOT_FOUND`) and nothing is persisted.

## Employees

HR-side employee profiles, separate from the login `User`. An `Employee` row
*may* link to a `User` (`userId`, one-to-one) — this is how the mobile
**My Profile** screen finds the current user's profile via
`GET /api/v1/employees/me`.

Photos on the My Profile screen change **only on the device** (per
[TC-SET.3](#mobile-app-test-cases)); the server-side `avatarUrl` field is just
a plain URL the client can set when persistence is desired — there is no
upload endpoint on this module.

### Fields

| Field              | Type                                              | Notes                                                                                  |
|--------------------|---------------------------------------------------|----------------------------------------------------------------------------------------|
| `userId`           | `Long?`                                           | Unique. Set to link the profile to a login account.                                    |
| `employeeNo`       | `String` (unique, required)                       | HR identifier (e.g. `EMP-00012`). Doubles as the "employee id / employee number".      |
| `fullName`         | `String` (required)                               |                                                                                        |
| `workEmail`        | `String?`                                         | Separate from login email.                                                             |
| `phone`            | `String?`                                         |                                                                                        |
| `position`         | `String?`                                         | Job title.                                                                             |
| `department`       | `String?`                                         |                                                                                        |
| `hireDate`         | `LocalDate?`                                      |                                                                                        |
| `dateOfBirth`      | `LocalDate?`                                      |                                                                                        |
| `gender`           | `String?`                                         | Free-form (e.g. `MALE`, `FEMALE`, `OTHER`).                                            |
| `address`          | `String?`                                         |                                                                                        |
| `avatarUrl`        | `String?`                                         | Public URL of the stored avatar. Set by the avatar upload endpoint (see below) or by client. |
| `avatarContentType`| `String?` (read-only)                             | MIME type of the stored avatar (e.g. `image/jpeg`). Set by the avatar upload endpoint.       |
| `avatarUploadedAt` | `Instant?` (read-only)                            | When the current avatar was uploaded. Set by the avatar upload endpoint.                     |
| `emergencyContact` | `String?`                                         | Name of the emergency contact person.                                                  |
| `emergencyPhone`   | `String?`                                         | Phone number for the emergency contact.                                                |
| `lastLoginAt`      | `Instant?` (read-only)                            | Bumped automatically on successful login when an employee is linked to a user.         |
| `tenure`           | `String?` (read-only, derived)                    | Computed from `hireDate` at read time, formatted `"<years>y <months>m"` (e.g. `2y 5m`).|
| `status`           | `ACTIVE` / `INACTIVE` / `ON_LEAVE` / `TERMINATED` | Defaults to `ACTIVE`.                                                                  |

### Endpoints

```
GET    /api/v1/employees/me              — current user's profile (404 if unlinked)
GET    /api/v1/employees?page=…&pageSize=…&search=…&sort=fullName:asc
GET    /api/v1/employees/{id}
POST   /api/v1/employees                 — create
PATCH  /api/v1/employees/{id}            — partial update (empty/no body = no-op)
DELETE /api/v1/employees/{id}

POST   /api/v1/employees/me/avatar       — upload current user's avatar (multipart `file`)
DELETE /api/v1/employees/me/avatar       — remove current user's avatar
POST   /api/v1/employees/{id}/avatar     — upload avatar (admin, `employee:write`)
DELETE /api/v1/employees/{id}/avatar     — remove avatar (admin, `employee:write`)
```

`GET /me` is authenticated-only (any logged-in user). The CRUD endpoints
require `employee:read` / `employee:write`. Search is case-insensitive
substring across `fullName`, `employeeNo`, and `workEmail`. Allowed sort
fields: `employeeNo`, `fullName`, `department`, `hireDate`, `status`,
`createdAt`.

### Avatar upload

Server-side multipart upload — files are stored on the API host's filesystem
and served back as static resources.

```
POST   /api/v1/employees/me/avatar     Content-Type: multipart/form-data
                                        form field: file=<binary>
DELETE /api/v1/employees/me/avatar
```

`/me` endpoints are authenticated-only (any logged-in user with an employee
profile). The `/{id}/avatar` variants require `employee:write`.

Constraints (env-overridable):

| Setting                          | Default                                          |
|----------------------------------|--------------------------------------------------|
| `UPLOAD_AVATAR_DIR`              | `./uploads/avatars`                              |
| `UPLOAD_AVATAR_PUBLIC_BASE_URL`  | `/uploads/avatars`                               |
| `UPLOAD_AVATAR_MAX_SIZE`         | `5242880` (5 MiB)                                |
| `UPLOAD_AVATAR_ALLOWED_TYPES`    | `image/jpeg,image/png,image/webp`                |

On a successful upload, the response is the updated `EmployeeDto` with:

```json
{
  "avatarUrl":         "/uploads/avatars/12-3f5e…b21.jpg",
  "avatarContentType": "image/jpeg",
  "avatarUploadedAt":  "2026-05-27T03:42:11Z"
}
```

Files are served publicly (no auth) at the `publicBaseUrl` prefix — point an
`<img src=…>` at `avatarUrl` to render. Replacing or deleting an avatar
best-effort removes the previous file from disk.

> The `My Profile` screen (TC-SET.3) still has the "Photo only changes on
> this device" subtitle for a *local* preview before the user confirms.
> Calling `POST /me/avatar` is what makes the change permanent on the server.

### Startup backfill

On every boot, `core.bootstrap.EmployeeBackfillBootstrap` ensures every
existing `User` has a matching `Employee` row. New rows are created with:

- `userId`     ← user's id (links the two records)
- `employeeNo` ← `EMP-<userId padded to 5 digits>` (e.g. `EMP-00001`)
- `fullName`   ← copied from the user
- `workEmail`  ← copied from the user's login email
- `phone`      ← copied from the user
- `status`     ← `ACTIVE`

It is idempotent — users that already have an employee row are skipped, so
it's safe to leave running. HR can rename the generated `employeeNo` via
`PATCH /api/v1/employees/{id}` whenever they want a custom number.

### Sample requests

Create:

```bash
curl -X POST http://localhost:8080/api/v1/employees \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" \
  -d '{
        "userId":     5,
        "employeeNo": "EMP-00012",
        "fullName":   "Sok Dara",
        "workEmail":  "dara@company.local",
        "phone":      "+85512345678",
        "position":   "Backend Engineer",
        "department": "Engineering",
        "hireDate":   "2025-01-15",
        "status":     "ACTIVE"
      }'
```

Fetch the signed-in user's profile (used by the mobile **My Profile** screen):

```bash
curl http://localhost:8080/api/v1/employees/me \
  -H "Authorization: Bearer $TOK"
```

Returns `404 NOT_FOUND` if no employee row references the current user.

## Realtime chat & voice/video calls

Backs **Module 10** from `CHAT_MODULE_GUIDE.md`. Conversations (1:1 + group),
messages (text/image/voice/file metadata), reactions, replies, edits,
deletes, group management, and voice/video call **signalling** (no WebRTC /
Stream — only the ceremony). All writes go over REST and fan out via STOMP.

### REST endpoints

```
POST   /api/v1/chats/conversations                       create direct or group
GET    /api/v1/chats/conversations?page=&pageSize=       inbox (paginated, newest-first)
GET    /api/v1/chats/conversations/{id}                  get one + members + unread
PATCH  /api/v1/chats/conversations/{id}                  rename / set avatar (admin)
POST   /api/v1/chats/conversations/{id}/members          add members (admin)
DELETE /api/v1/chats/conversations/{id}/members/{userId} remove (admin, or self leave)
POST   /api/v1/chats/conversations/{id}/read             mark-as-read (set lastReadMessageId)

GET    /api/v1/chats/conversations/{id}/messages         paginated history
GET    /api/v1/chats/conversations/{id}/messages/search  case-insensitive substring
POST   /api/v1/chats/conversations/{id}/messages         send (text/image/voice/file)
PATCH  /api/v1/chats/messages/{id}                       edit (sender, 15-min window, TEXT only)
DELETE /api/v1/chats/messages/{id}                       soft-delete
POST   /api/v1/chats/messages/{id}/reactions             toggle emoji

POST   /api/v1/chats/conversations/{id}/calls            start (VOICE | VIDEO)
POST   /api/v1/chats/calls/{id}/accept                   callee accepts
POST   /api/v1/chats/calls/{id}/reject?reason=…          callee declines
POST   /api/v1/chats/calls/{id}/end                      hangup (caller end = all end)
GET    /api/v1/chats/calls/{id}                          reconcile state
GET    /api/v1/chats/calls                               my call history
GET    /api/v1/chats/conversations/{id}/calls            per-conv call history
```

All read endpoints require `chat:read`; all writes require `chat:write`.
Membership is enforced at the service layer — non-members get `FORBIDDEN`
even if they have the permission code.

### STOMP wire protocol

Endpoint: `ws://<host>/ws` (or `/ws-sockjs` for browser SockJS fallback).
On the CONNECT frame, the client must send:

```
Authorization: Bearer <accessToken>
```

The JWT is validated by `StompAuthChannelInterceptor`; failure rejects the
connection. The user's id becomes the STOMP session principal (so
`/user/queue/...` routes work).

| Destination                              | Type   | Payload                                              |
|------------------------------------------|--------|------------------------------------------------------|
| `/topic/conversations/{convId}`          | public | message.send / message.edit / message.delete / reaction.toggle / conversation.update |
| `/topic/conversations/{convId}/call`     | public | call.invite / call.accept / call.reject / call.hangup |
| `/user/queue/inbox`                      | private | conversation.create / conversation.update / conversation.remove / message.send (inbox preview) |
| `/user/queue/calls`                      | private | call.invite (per-callee fan-out, mirrors the guide)  |

Every frame is wrapped in this envelope, matching the wire envelopes
documented in `CHAT_MODULE_GUIDE.md`:

```json
{ "event": "message.send", "payload": { /* MessageDto, ConversationDto, … */ } }
```

The Flutter client subscribes to `/topic/conversations/{convId}` for the
open chat, plus `/user/queue/inbox` for live inbox previews and
`/user/queue/calls` for incoming-call sheets.

### What's NOT in this module (intentional)

| Capability | Why | Where it'd live |
|---|---|---|
| WebRTC media | Out of scope — signalling only | A media SFU (LiveKit, Stream Video, Janus) |
| Attachment binary upload | Messages carry `attachmentUrl` only | Mirror the employee-avatar pattern under `/api/v1/chats/uploads`, then put the URL in the `SendMessage` body |
| FCM push for backgrounded calls | Disabled by default | `FCM_ENABLED=true` + service-account JSON, then a hook off `call.invite` |
| 30-second ring timeout | Hook exists on `ChatCallService` but no scheduler is wired | Add a `@Scheduled` sweep that calls `markMissedIfStaleRinging(...)` for every RINGING call |
| Typing indicators | Not in the guide | Easy follow-up — `/app/conversations/{id}/typing` STOMP send |

### Tables

| Migration | Purpose |
|---|---|
| `V13__chat_module.sql` | `chat_conversations`, `chat_conversation_members`, `chat_messages`, `chat_message_reactions` |
| `V14__chat_calls.sql`  | `chat_calls`, `chat_call_participants` |

Unread counts are derived per member from `lastReadMessageId` (no separate
table). The `chat_conversations.last_message_id` / `last_message_at` are
denormalised to keep the inbox query a single sort without joining
messages.

## Tests

```bash
gradle test
```

Sample suite (planned):

- `JwtServiceTest` — token round-trip
- `ErpApplicationTest` — context load against a real Postgres (Testcontainers); proves JPA entities match the Flyway schema

## Extending: adding a new module

The Users module is the working reference. To add a new feature `foo`:

1. **Migration** — add tables in a new `V<N>__foo.sql`.
2. **`features/foo/entity/Foo.java`** — `@Entity` extending `BaseEntity` (uses Lombok `@Getter @Setter @NoArgsConstructor`).
3. **`features/foo/repository/FooRepository.java`** — `JpaRepository<Foo, Long>` (add `JpaSpecificationExecutor` if you need filtering).
4. **`features/foo/repository/FooSpecifications.java`** — composable filters (per the products template).
5. **`features/foo/service/FooService.java`** — `@Service @Transactional`, business rules live here.
6. **`features/foo/dto/`** — request + response records with `jakarta.validation` annotations.
7. **`features/foo/controller/FooController.java`** — `@RestController @RequestMapping("/api/v1/foo")` with `@PreAuthorize` per endpoint.
8. Add the new permission codes to `core.security.Permissions` and the V3 seed migration.

## Security checklist (production)

- [ ] Replace `JWT_SECRET` with a 64+ character random string (in a secret manager).
- [ ] Replace the seeded super-admin password (or remove the bootstrap user entirely).
- [ ] Tighten `CORS_ALLOWED_HOSTS` to the actual frontend origins.
- [ ] Run behind HTTPS (nginx already sets HSTS; terminate TLS at the proxy or LB).
- [ ] Tune `RATE_LIMIT_PER_MINUTE` and the stricter `AUTH_RATE_LIMIT_PER_MINUTE` bucket.
- [ ] Move rate limiting to Redis (bucket4j-redis) if you run more than one instance.
- [ ] Wire a real notification adapter (email/push/SMS).
- [ ] Provide `STREAM_API_KEY` / `STREAM_API_SECRET` and a Firebase service-account JSON; rotate both periodically.
- [ ] Terminate WebSocket traffic over `wss://` at the proxy; ensure sticky sessions if running multiple instances behind a load balancer.

## Useful endpoints

- `GET /health` — liveness
- `GET /actuator/health` — Spring Boot actuator
- `GET /docs` — SwaggerUI
- `GET /v3/api-docs` — raw OpenAPI JSON
- `POST /api/v1/auth/login` — get tokens
- `GET /api/v1/users/me` — the current user
- `GET /api/v1/users` — paginated list with search/filter
- `POST /api/v1/users/assign-roles` — bulk-assign roles to many users (ADD / REPLACE / REMOVE)
- `GET /api/v1/roles` — list roles + their permissions
- `GET /api/v1/roles/permissions` — list every permission code
- `GET /api/v1/employees/me` — current user's employee profile (for the mobile My Profile screen)
- `GET /api/v1/employees` — paginated list with search/filter (`employee:read`)
- `POST /api/v1/employees` — create employee (`employee:write`)
- `PATCH /api/v1/employees/{id}` — partial update (`employee:write`)
- `DELETE /api/v1/employees/{id}` — delete (`employee:write`)
- `POST /api/v1/employees/me/avatar` — upload current user's avatar (multipart `file`)
- `DELETE /api/v1/employees/me/avatar` — remove current user's avatar
- `POST /api/v1/employees/{id}/avatar` — admin: upload an employee's avatar (`employee:write`)
- `DELETE /api/v1/employees/{id}/avatar` — admin: remove an employee's avatar (`employee:write`)
- `GET /api/v1/products` — paginated list with search/filter (planned)
- `GET /api/v1/chats/conversations` — paginated conversations for the current user (`chat:read`)
- `POST /api/v1/chats/conversations` — create direct or group conversation (`chat:write`)
- `POST /api/v1/chats/conversations/{id}/messages` — send a message (`chat:write`)
- `POST /api/v1/chats/conversations/{id}/calls` — initiate a voice/video call (`chat:write`)
- `GET /api/v1/chats/calls` — current user's call history
- `ws://…/ws` — STOMP WebSocket endpoint for live chat and call signaling


***list pagination users
 http://172.20.17.31:8080/api/v1/users?page=1&pageSize=50