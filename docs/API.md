# ERP Backend — API Reference

Base URL: `http://<host>:8080` (port from `SERVER_PORT`, default `8080`).
All REST endpoints are versioned under `/api/v1` except `/health`.

**Postman:** import [postman/ERP_API.postman_collection.json](postman/ERP_API.postman_collection.json) and [postman/ERP_Local.postman_environment.json](postman/ERP_Local.postman_environment.json). Then run **Auth → Login**; it saves the access token that every other request uses.

---

## Conventions

### Authentication

Send the access token from `/api/v1/auth/login` on every request:

```
Authorization: Bearer <accessToken>
```

Public endpoints (no token needed): `/health`, `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/refresh`, `/uploads/**`, `/ws`, `/ws-sockjs`. Everything else requires a valid token.
Some endpoints also require a permission (shown in the **Permission** column below). The user's permissions come from their roles.

### Response envelope

Every JSON response is wrapped:

```json
{
  "success": true,
  "message": "Success",
  "data": { },
  "errorCode": null,
  "traceId": "..."
}
```

On error, `success` is `false`, `errorCode` is set, and validation errors put per-field details in `data.fieldErrors`.

### Pagination

Paged endpoints take `page` (1-based) and `pageSize`. Some also take `search` and `sort`. `data` is then:

```json
{
  "items": [ ],
  "page": 1,
  "pageSize": 20,
  "totalItems": 134,
  "totalPages": 7
}
```

### Enums

| Enum | Values |
|---|---|
| `ConversationType` | `DIRECT`, `GROUP` |
| `MessageType` | `TEXT`, `IMAGE`, `VOICE`, `FILE` |
| `CallType` | `VOICE`, `VIDEO` |
| `PresenceStatus` | `ONLINE`, `BUSY`, `OFFLINE` |
| `DevicePlatform` | `android`, `ios`, `web` |
| `EmployeeStatus` | `ACTIVE`, `INACTIVE`, `ON_LEAVE`, `TERMINATED` |
| `AssignRolesRequest.mode` | `ADD` (default), `REPLACE`, `REMOVE` |

---

## Endpoint summary

| # | Method | Path | Auth / Permission |
|---|---|---|---|
| 1 | GET | `/health` | Public |
| 2 | POST | `/api/v1/auth/login` | Public |
| 3 | POST | `/api/v1/auth/register` | Public |
| 4 | POST | `/api/v1/auth/refresh` | Public |
| 5 | POST | `/api/v1/auth/logout` | Token |
| 6 | GET | `/api/v1/users/me` | Token |
| 7 | GET | `/api/v1/users` | Token |
| 8 | GET | `/api/v1/users/{id}` | Token |
| 9 | POST | `/api/v1/users` | `user:write` |
| 10 | PATCH | `/api/v1/users/{id}` | `user:write` |
| 11 | DELETE | `/api/v1/users/{id}` | `user:write` |
| 12 | POST | `/api/v1/users/assign-roles` | `user:write` |
| 13 | GET | `/api/v1/roles` | `role:read` |
| 14 | GET | `/api/v1/roles/{id}` | `role:read` |
| 15 | POST | `/api/v1/roles` | `role:write` |
| 16 | PATCH | `/api/v1/roles/{id}` | `role:write` |
| 17 | DELETE | `/api/v1/roles/{id}` | `role:write` |
| 18 | GET | `/api/v1/roles/permissions` | `role:read` |
| 19 | GET | `/api/v1/employees/me` | Token |
| 20 | GET | `/api/v1/employees` | `employee:read` |
| 21 | GET | `/api/v1/employees/{id}` | `employee:read` |
| 22 | POST | `/api/v1/employees` | `employee:write` |
| 23 | PATCH | `/api/v1/employees/{id}` | `employee:write` |
| 24 | DELETE | `/api/v1/employees/{id}` | `employee:write` |
| 25 | POST | `/api/v1/employees/me/avatar` | Token |
| 26 | DELETE | `/api/v1/employees/me/avatar` | Token |
| 27 | POST | `/api/v1/employees/{id}/avatar` | `employee:write` |
| 28 | DELETE | `/api/v1/employees/{id}/avatar` | `employee:write` |
| 29 | GET | `/api/v1/me/devices` | Token |
| 30 | POST | `/api/v1/me/devices` | Token |
| 31 | DELETE | `/api/v1/me/devices/{deviceId}` | Token |
| 32 | GET | `/api/v1/chats/conversations` | Token |
| 33 | GET | `/api/v1/chats/conversations/{id}` | Token |
| 34 | POST | `/api/v1/chats/conversations` | Token |
| 35 | PATCH | `/api/v1/chats/conversations/{id}` | Token (group admin) |
| 36 | POST | `/api/v1/chats/conversations/{id}/members` | Token (group admin) |
| 37 | DELETE | `/api/v1/chats/conversations/{id}/members/{userId}` | Token (admin, or self) |
| 38 | DELETE | `/api/v1/chats/conversations/{id}` | Token (group admin / either DIRECT party) |
| 39 | POST | `/api/v1/chats/conversations/{id}/read` | Token |
| 40 | GET | `/api/v1/chats/conversations/{convId}/messages` | Token |
| 41 | GET | `/api/v1/chats/conversations/{convId}/messages/search` | Token |
| 42 | POST | `/api/v1/chats/conversations/{convId}/messages` | Token |
| 43 | PATCH | `/api/v1/chats/messages/{id}` | Token (sender) |
| 44 | DELETE | `/api/v1/chats/messages/{id}` | Token (sender) |
| 45 | POST | `/api/v1/chats/messages/{id}/reactions` | Token |
| 46 | POST | `/api/v1/chats/attachments` | Token |
| 47 | GET | `/api/v1/chats/calls` | Token |
| 48 | GET | `/api/v1/chats/conversations/{convId}/calls` | Token |
| 49 | GET | `/api/v1/chats/calls/{id}` | Token |
| 50 | POST | `/api/v1/chats/conversations/{convId}/calls` | Token |
| 51 | POST | `/api/v1/chats/calls/{id}/ringing` | Token |
| 52 | POST | `/api/v1/chats/calls/{id}/accept` | Token |
| 53 | POST | `/api/v1/chats/calls/{id}/reject` | Token |
| 54 | POST | `/api/v1/chats/calls/{id}/end` | Token |
| 55 | GET | `/api/v1/chats/calls/stream-token` | Token |
| 56 | GET | `/api/v1/chats/presence/{userId}` | Token |
| 57 | GET | `/api/v1/chats/presence` | Token |
| 58 | POST | `/api/v1/chats/presence/background` | Token |
| 59 | POST | `/api/v1/chats/presence/foreground` | Token |

---

## 1. Health

### `GET /health`
Liveness check. Returns `{ "status": "UP" }`.

---

## 2. Auth — `/api/v1/auth`

### `POST /login`
Exchange email and password for an access and refresh token pair.

```json
{ "email": "user@example.com", "password": "secret" }
```

**Response** `AuthResponse`:

```json
{
  "accessToken": "...",
  "accessTokenExpiresAt": "2026-11-19T10:00:00Z",
  "refreshToken": "...",
  "refreshTokenExpiresAt": "2026-12-19T10:00:00Z",
  "user": { "id": 1, "email": "user@example.com", "fullName": "Jane Doe", "phone": "012345678", "avatarUrl": null }
}
```

### `POST /register`
Create an account and get tokens immediately. Returns `AuthResponse`.

| Field | Type | Rules |
|---|---|---|
| `email` | string | required, email |
| `password` | string | required, 6–100 chars |
| `fullName` | string | required, max 255 |
| `phone` | string | optional, max 50 |

### `POST /refresh`
Swap a refresh token for a new token pair (the refresh token is rotated). Returns `AuthResponse`.

```json
{ "refreshToken": "..." }
```

### `POST /logout`
Revoke a refresh token. Succeeds even if the token is unknown. Needs an access token.

```json
{ "refreshToken": "..." }
```

---

## 3. Users — `/api/v1/users`

### `GET /me`
The current user's own profile: `id`, `email`, `fullName`, `phone`, `avatarUrl`. This and the auth `user` object don't include `enabled`, `roles` or `permissions`; the admin user endpoints below still return them.

### `GET /`
Paged user list. Any logged-in user can call it, so the chat module can pick people to message.
Query: `page` (default 1), `pageSize` (default 20), `search` (matches email or full name), `sort`.

### `GET /{id}`
One user. Any logged-in user can call it.

### `POST /` — `user:write`
Create a user. The password is stored as a bcrypt hash.

| Field | Type | Rules |
|---|---|---|
| `email` | string | required, email |
| `password` | string | required, 8–100 chars |
| `fullName` | string | required, max 255 |
| `phone` | string | optional, max 50 |
| `roles` | string[] | optional role codes, e.g. `["STAFF"]` |

### `PATCH /{id}` — `user:write`
Partial update: only the fields you send change. An empty body changes nothing.

| Field | Type | Notes |
|---|---|---|
| `fullName` | string | max 255 |
| `phone` | string | max 50 |
| `avatarUrl` | string | max 1024 |
| `enabled` | boolean | |
| `roles` | string[] | replaces the whole role set; leave it out to keep current roles |

### `DELETE /{id}` — `user:write`
Permanently delete a user. The database also deletes their refresh tokens.

### `POST /assign-roles` — `user:write`
Change roles for many users at once. Returns the updated `UserDto[]`.

```json
{ "userIds": [1, 2, 3], "roles": ["STAFF"], "mode": "ADD" }
```

---

## 4. Roles — `/api/v1/roles`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `role:read` | All roles with their permission codes |
| GET | `/{id}` | `role:read` | One role |
| POST | `/` | `role:write` | Create a role |
| PATCH | `/{id}` | `role:write` | Update a role |
| DELETE | `/{id}` | `role:write` | Delete a role. Users keep their accounts but lose its permissions |
| GET | `/permissions` | `role:read` | Every permission code, for the role editor |

**Create** body:

```json
{ "code": "SALES", "name": "Sales", "description": "optional", "permissions": ["order:read"] }
```

`code` is required (max 64) and `name` is required (max 128).

**Update** body: `name`, `description` and `permissions` are all optional. `permissions` replaces the whole set; leave it out to keep the current set.

---

## 5. Employees — `/api/v1/employees`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/me` | Token | My employee profile (404 if my user has no linked employee) |
| GET | `/` | `employee:read` | Paged list. `search` matches fullName, employeeNo or workEmail |
| GET | `/{id}` | `employee:read` | One employee, including tenure and last login |
| POST | `/` | `employee:write` | Create an employee |
| PATCH | `/{id}` | `employee:write` | Partial update |
| DELETE | `/{id}` | `employee:write` | Permanently delete (the linked user is kept) |
| POST | `/me/avatar` | Token | Upload my avatar (multipart `file`) |
| DELETE | `/me/avatar` | Token | Remove my avatar |
| POST | `/{id}/avatar` | `employee:write` | Upload any employee's avatar (multipart `file`) |
| DELETE | `/{id}/avatar` | `employee:write` | Remove any employee's avatar |

List query: `page` (default 1), `pageSize` (default 20), `search`, `sort`.

**Create / update body.** On create, `employeeNo` and `fullName` are required. On update, every field is optional.

| Field | Type | Rules |
|---|---|---|
| `userId` | long | optional link to a login user |
| `employeeNo` | string | max 64 |
| `fullName` | string | max 255 |
| `workEmail` | string | email, max 255 |
| `phone` | string | max 50 |
| `position` | string | max 128 |
| `department` | string | max 128 |
| `hireDate` | date `YYYY-MM-DD` | |
| `dateOfBirth` | date `YYYY-MM-DD` | |
| `gender` | string | max 16 |
| `address` | string | max 1024 |
| `avatarUrl` | string | max 1024 |
| `emergencyContact` | string | max 255 |
| `emergencyPhone` | string | max 50 |
| `status` | `EmployeeStatus` | |

---

## 6. Devices (push tokens) — `/api/v1/me/devices`

| Method | Path | Description |
|---|---|---|
| GET | `/` | My registered devices |
| POST | `/` | Register or update this device's FCM token. If the same user and `deviceId` already exist, it is updated |
| DELETE | `/{deviceId}` | Remove a device, e.g. on logout |

**Register** body:

```json
{ "deviceId": "abc-123", "fcmToken": "...", "platform": "android", "appVersion": "1.4.0" }
```

`deviceId` (max 128), `fcmToken` and `platform` are required. `appVersion` is optional (max 32).

---

## 7. Chat — Conversations — `/api/v1/chats/conversations`

| Method | Path | Description |
|---|---|---|
| GET | `/` | My inbox, paged, newest first. Query: `page` (default 1), `pageSize` (default 20), `search`, `sort` |
| GET | `/{id}` | One conversation with members, unread count and last message |
| POST | `/` | Create a DIRECT or GROUP conversation. Creating a DIRECT chat that already exists returns the existing one |
| PATCH | `/{id}` | Rename a group or change its avatar (admin only) |
| POST | `/{id}/members` | Add members to a group (admin only) |
| DELETE | `/{id}/members/{userId}` | Remove a member (admin), or leave the group (use your own `userId`) |
| DELETE | `/{id}` | Delete a conversation (GROUP: admin only, DIRECT: either person) |
| POST | `/{id}/read` | Mark messages read up to an id. Clears unread and sends `message.read` |

Request bodies:

```jsonc
// POST /
{ "type": "GROUP", "memberIds": [2, 3], "name": "Team", "avatarUrl": null }

// PATCH /{id}
{ "name": "New name", "avatarUrl": "/uploads/..." }

// POST /{id}/members
{ "memberIds": [4, 5] }

// POST /{id}/read
{ "lastReadMessageId": 987 }
```

---

## 8. Chat — Messages — `/api/v1/chats`

| Method | Path | Description |
|---|---|---|
| GET | `/conversations/{convId}/messages` | Message history, newest first, with read receipts. Query: `page` (default 1), `pageSize` (default 30), `sort` |
| GET | `/conversations/{convId}/messages/search` | Search message text (case-insensitive). Query: `q` (required), `page`, `pageSize` |
| POST | `/conversations/{convId}/messages` | Send a message. Also sends an FCM push to members whose app is in the background or closed |
| PATCH | `/messages/{id}` | Edit your own TEXT message, within 15 minutes of sending |
| DELETE | `/messages/{id}` | Delete your own message. The text and attachment are removed from responses |
| POST | `/messages/{id}/reactions` | Add or remove an emoji reaction. Returns the full reaction list |

**Send** body:

| Field | Type | Notes |
|---|---|---|
| `type` | `MessageType` | required |
| `body` | string | text content |
| `attachmentUrl` | string | max 1024. Get it from `POST /chats/attachments` |
| `attachmentContentType` | string | max 64 |
| `attachmentSizeBytes` | long | |
| `durationSeconds` | int | for VOICE |
| `replyToMessageId` | long | |

**Edit** body: `{ "body": "updated text" }`
**Reaction** body: `{ "emoji": "👍" }` (max 16 chars)

---

## 9. Chat — Attachments — `/api/v1/chats`

### `POST /attachments`
Upload a file as multipart form data, field `file`. Upload first, then send a message that uses the returned `url` as its `attachmentUrl`.

```json
{ "url": "/uploads/chat/12-uuid.jpg", "contentType": "image/jpeg", "sizeBytes": 20480 }
```

`url` is a path on this server. Files are served publicly from `/uploads/**`.

---

## 10. Chat — Calls — `/api/v1/chats`

| Method | Path | Description |
|---|---|---|
| GET | `/calls` | My call history across all conversations. Query: `page` (default 1), `pageSize` (default 30) |
| GET | `/conversations/{convId}/calls` | Call history for one conversation |
| GET | `/calls/{id}` | A call's current state, e.g. to resync after a reconnect. Includes `callerAvatarUrl` |
| POST | `/conversations/{convId}/calls` | Start a call. Rings every other member over STOMP and FCM |
| POST | `/calls/{id}/ringing` | The callee's device is ringing. Tells the caller. Returns `{ callId, notified }` |
| POST | `/calls/{id}/accept` | Accept the call. Status becomes ANSWERED and the user becomes BUSY. Stops ringing on the user's other devices |
| POST | `/calls/{id}/reject` | Decline the call. Optional query parameter `reason` (e.g. `busy`) |
| POST | `/calls/{id}/end` | Hang up. If the caller hangs up, the call ends for everyone. If the last callee hangs up, the call ends |
| GET | `/calls/stream-token` | Get a short-lived Stream Video token for the mobile SDK |

**Start** body: `{ "type": "VOICE" }` or `{ "type": "VIDEO" }`

**Stream token** response:

```json
{ "token": "...", "apiKey": "...", "userId": "42", "expiresAt": "2026-11-19T11:00:00Z" }
```

**FCM data pushes** sent by the call endpoints:

| `type` | When | Fields |
|---|---|---|
| `call.invite` | A call starts (sent to every callee) | `callId`, `conversationId`, `callerId`, `callerName`, `callerAvatarUrl`, `callType`, `startedAt`, `streamCallCid` |
| `call.cancel` | Someone accepts on another device, or the call is rejected or ended | `callId`, `streamCallCid`, `reason` (`accepted_elsewhere` / `rejected` / `hangup`) |

See [CALL_FLOW.md](CALL_FLOW.md) for the full signalling sequence.

---

## 11. Chat — Presence — `/api/v1/chats/presence`

| Method | Path | Description |
|---|---|---|
| GET | `/{userId}` | One user's presence |
| GET | `/?ids=1,2,3` | Presence for several users. Leave out `ids` to get everyone |
| POST | `/background` | The app went to the background: mark the user OFFLINE right away so incoming calls ring through VoIP/CallKit |
| POST | `/foreground` | The app came back to the foreground |

**Response** `PresenceDto`:

```json
{ "userId": 42, "status": "ONLINE", "lastSeenAt": null }
```

`lastSeenAt` is only set while the user is OFFLINE.

---

## 12. Real-time — WebSocket (STOMP)

| Item | Value |
|---|---|
| Endpoint | `/ws` (plain WebSocket) or `/ws-sockjs` (SockJS) |
| Auth | Send the `Authorization: Bearer <accessToken>` header on the STOMP `CONNECT` frame |
| App prefix | `/app` |
| Broker prefixes | `/topic`, `/queue` |

Every event has the same shape: `{ "event": "<name>", "payload": { ... } }`.

### Subscriptions

| Destination | Events |
|---|---|
| `/topic/conversations/{conversationId}` | `message.send`, `message.edit`, `message.delete`, `message.read`, `reaction.toggle`, `conversation.update` |
| `/topic/conversations/{conversationId}/call` | `call.invite`, `call.ringing`, `call.accept`, `call.reject`, `call.hangup` |
| `/topic/presence` | Presence changes |
| `/user/queue/inbox` | `conversation.create`, `conversation.update`, `conversation.remove`, `message.send` |
| `/user/queue/calls` | `call.invite`, `call.ringing` |
