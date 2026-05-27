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

## Realtime chat & voice/video calls (planned)

The `features/chats/` module exposes both a REST surface (for history, pagination, media uploads) and a STOMP-over-WebSocket surface (for live message and call-state fan-out).

### REST endpoints (selection)

```
POST /api/v1/chats/conversations                       — create a 1:1 or group conversation
GET  /api/v1/chats/conversations?page=…&pageSize=…     — paginated conversation list
GET  /api/v1/chats/conversations/{id}/messages         — paginated message history
POST /api/v1/chats/conversations/{id}/messages         — send a message
POST /api/v1/chats/uploads                             — upload attachment, returns CDN URL

POST /api/v1/chats/{convId}/calls                      — initiate a call (returns CallSessionDto)
POST /api/v1/chats/calls/{callId}/accept|reject|end    — call lifecycle
GET  /api/v1/chats/calls/{callId}                      — fetch current call state (reconciliation)
GET  /api/v1/chats/calls/stream-token                  — short-lived Stream Video JWT for the client
```

All endpoints sit behind `@PreAuthorize("hasAuthority(Permissions.CHAT_READ|CHAT_WRITE)")`.

### WebSocket

- Endpoint: `/ws` (SockJS-compatible). Authenticate with `Authorization: Bearer <accessToken>` on the CONNECT frame.
- Destinations:
  - `/topic/conversations/{convId}` — message stream for a conversation
  - `/topic/conversations/{convId}/call` — per-call state transitions (`RINGING → ACTIVE → ENDED`)
  - `/user/queue/calls` — per-user incoming-call invite (private destination)
  - `/app/...` — client → server send destinations handled by the chat signaling controller

### Voice/video

The actual A/V stream runs on **Stream Video**, not on this server. Flow:

1. Caller `POST /api/v1/chats/{convId}/calls` → backend creates a `CallSession` (status `RINGING`, `streamCallCid` allocated) and stores participants.
2. Backend broadcasts the invite via STOMP to each callee's `/user/queue/calls`, **and** sends an FCM data-message for backgrounded devices.
3. Each side calls `GET /api/v1/chats/calls/stream-token` to obtain a short-lived JWT, then joins `streamCallCid` directly on Stream's SDK.
4. Lifecycle endpoints (`accept`/`reject`/`end`) update server state and fan-out STOMP frames on `/topic/conversations/{convId}/call`.
5. A `CallTimeoutScheduler` auto-terminates calls that stay in `RINGING` past the configured TTL.

Required configuration (in `.env` or environment):

```
STREAM_API_KEY=…
STREAM_API_SECRET=…
STREAM_TOKEN_TTL_MINUTES=60     # default

FCM_ENABLED=true                # default false; when false, push is skipped
FCM_SERVICE_ACCOUNT_JSON_PATH=/secrets/firebase-sa.json
```

When `FCM_ENABLED=false` the push service no-ops (useful in dev / tests). When `STREAM_API_KEY` is empty, `/chats/calls/stream-token` will return an error — set both keys before exercising calls.

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
- `GET /api/v1/roles` — list roles + their permissions
- `GET /api/v1/roles/permissions` — list every permission code
- `GET /api/v1/products` — paginated list with search/filter (planned)
- `GET /api/v1/chats/conversations` — paginated conversations for the current user (planned)
- `POST /api/v1/chats/{convId}/calls` — initiate a voice/video call (planned)
- `GET /api/v1/chats/calls/stream-token` — Stream Video JWT for the client (planned)
- `ws://…/ws` — STOMP WebSocket endpoint for live chat and call signaling (planned)


***list pagination users
 http://172.20.17.31:8080/api/v1/users?page=1&pageSize=50