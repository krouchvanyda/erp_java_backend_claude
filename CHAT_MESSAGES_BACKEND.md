# Chat Messages — Backend (Spring Boot) Server Guide

Server-side contract for Module 10 chat messaging: REST endpoints, request/
response DTOs, persistence, the STOMP broadcast envelope the Flutter app
depends on, and how attachments (image · voice · file) are expected to work.
Reflects the as-built code; every claim is cited to `file:line`.

> **TL;DR — what happens when a message is sent**
>
> ```
> POST /api/v1/chats/conversations/{convId}/messages   (SendMessageRequest)
>        │  MessageController.send()  — resolve current user, return MessageDto
>        ▼
> MessageService.send()  — requireMember → validateBody(type) → save Message
>        │                  → denormalise Conversation.lastMessage*
>        ▼
> ChatBroadcaster fan-out (TWO destinations):
>    • /topic/conversations/{convId}     event "message.send"   payload MessageDto
>    • /user/queue/inbox  (each member)  event "message.send"   payload MessageDto
> ```

Module root:
`src/main/java/com/company/erp/features/chats/`

Key files:
- Controllers — [MessageController.java](src/main/java/com/company/erp/features/chats/controller/MessageController.java), [ConversationController.java](src/main/java/com/company/erp/features/chats/controller/ConversationController.java)
- Service — [MessageService.java](src/main/java/com/company/erp/features/chats/service/MessageService.java), [ConversationService.java](src/main/java/com/company/erp/features/chats/service/ConversationService.java)
- DTOs — [dto/](src/main/java/com/company/erp/features/chats/dto/)
- Entities — [entity/](src/main/java/com/company/erp/features/chats/entity/)
- WebSocket — [ws/ChatBroadcaster.java](src/main/java/com/company/erp/features/chats/ws/ChatBroadcaster.java), [ws/WebSocketConfig.java](src/main/java/com/company/erp/features/chats/ws/WebSocketConfig.java), [ws/StompAuthChannelInterceptor.java](src/main/java/com/company/erp/features/chats/ws/StompAuthChannelInterceptor.java)
- Schema — [db/migration/V13__chat_module.sql](src/main/resources/db/migration/V13__chat_module.sql)

---

## 1. REST endpoints

All chat endpoints are under `@RequestMapping("/api/v1/chats")`
([MessageController.java:24](src/main/java/com/company/erp/features/chats/controller/MessageController.java#L24)).
Every endpoint resolves the caller with
`AuthenticatedUser.require().userId()` and gates on conversation membership.

### Messages — [MessageController.java](src/main/java/com/company/erp/features/chats/controller/MessageController.java)

| Method | Path | Request | Response | Notes |
|---|---|---|---|---|
| GET | `/conversations/{convId}/messages` | `page,pageSize,sort` | `PageResponse<MessageDto>` | History, newest-first, excludes soft-deleted. |
| GET | `/conversations/{convId}/messages/search?q=` | `q,page,pageSize` | `PageResponse<MessageDto>` | Case-insensitive `LIKE` on `body`. |
| POST | `/conversations/{convId}/messages` | `SendMessageRequest` | `MessageDto` | Send. Broadcasts `message.send` (×2). |
| PATCH | `/messages/{id}` | `EditMessageRequest` | `MessageDto` | Own TEXT, ≤15 min. Broadcasts `message.edit`. |
| DELETE | `/messages/{id}` | — | `MessageDto` | Soft delete (sender only). Broadcasts `message.delete`. |
| POST | `/messages/{id}/reactions` | `ToggleReactionRequest` | `List<ReactionDto>` | Toggle emoji. Broadcasts `reaction.toggle`. |
| POST | `/attachments` | `multipart/form-data` (`file`) | `ChatAttachmentDto` | Upload an image/voice/file; returns the hosted URL to send as `attachmentUrl`. See [§8](#8-attachments). |

### Conversations — [ConversationController.java](src/main/java/com/company/erp/features/chats/controller/ConversationController.java)

| Method | Path | Request | Response | Notes |
|---|---|---|---|---|
| GET | `/conversations` | `page,pageSize,search,sort` | `PageResponse<ConversationDto>` | Inbox list. |
| GET | `/conversations/{id}` | — | `ConversationDto` | One conversation + members + unread. |
| POST | `/conversations` | `CreateConversationRequest` | `ConversationDto` | DIRECT auto-deduped. Broadcasts `conversation.create`. |
| PATCH | `/conversations/{id}` | `UpdateConversationRequest` | `ConversationDto` | Rename / avatar (admin). `conversation.update`. |
| POST | `/conversations/{id}/members` | `AddMembersRequest` | `ConversationDto` | Admin. `conversation.update`. |
| DELETE | `/conversations/{id}/members/{userId}` | — | `ConversationDto` | Kick (admin) or leave (self). |
| DELETE | `/conversations/{id}` | — | `204` | GROUP admin / either DIRECT party. `conversation.remove`. |
| POST | `/conversations/{id}/read` | `MarkReadRequest` | `ConversationDto` | Broadcasts `message.read` + self inbox update. |

---

## 2. Request DTOs

### `SendMessageRequest` — [dto/SendMessageRequest.java](src/main/java/com/company/erp/features/chats/dto/SendMessageRequest.java)

```java
public record SendMessageRequest(
    @NotNull MessageType type,                  // TEXT | IMAGE | VOICE | FILE
    String body,                                // text, or caption for media
    @Size(max = 1024) String attachmentUrl,     // hosted URL of image/voice/file
    @Size(max = 64)   String attachmentContentType,
    Long    attachmentSizeBytes,
    Integer durationSeconds,                    // VOICE only
    Long    replyToMessageId                    // optional reply target
) {}
```

**Type-based validation** — `MessageService.validateBody`
([MessageService.java](src/main/java/com/company/erp/features/chats/service/MessageService.java)):

| `type` | Required | Rejected if missing |
|---|---|---|
| `TEXT` | non-blank `body` | — |
| `IMAGE` | `attachmentUrl` | — |
| `FILE` | `attachmentUrl` | — |
| `VOICE` | `attachmentUrl` **and** `durationSeconds` | — |

A reply (`replyToMessageId`) must point to a message **in the same
conversation** or the send is rejected `400`.

### Other request DTOs

- `EditMessageRequest( @NotBlank String body )` — TEXT only, ≤15 min.
- `MarkReadRequest( @NotNull Long lastReadMessageId )`.
- `ToggleReactionRequest( @NotBlank @Size(max=16) String emoji )`.
- `CreateConversationRequest( @NotNull ConversationType type, @NotEmpty Set<Long> memberIds, @Size(max=255) String name, @Size(max=1024) String avatarUrl )` — caller is auto-added; creator becomes `ADMIN`; DIRECT needs exactly 2 members and is deduped.
- `UpdateConversationRequest( String name, String avatarUrl )`.
- `AddMembersRequest( @NotEmpty Set<Long> memberIds )`.

---

## 3. Response DTOs

### `MessageDto` — [dto/MessageDto.java](src/main/java/com/company/erp/features/chats/dto/MessageDto.java)

```java
public record MessageDto(
    Long id, Long conversationId, Long senderId,
    MessageType type,
    String body,                    // nulled when deleted
    String attachmentUrl,           // nulled when deleted
    String attachmentContentType,
    Long attachmentSizeBytes,
    Integer durationSeconds,
    Long replyToMessageId,
    Instant editedAt,               // non-null ⇒ "(edited)"
    boolean deleted,
    List<ReactionDto> reactions,    // [{ userId, emoji }]
    Set<Long> readByUserIds,        // members (excl. sender) who read up to this id
    Instant createdAt
) {}
```

- When `deleted == true`, all content fields (`body`, `attachmentUrl`, …) are
  nulled in the response — the row stays but carries no content.
- `readByUserIds` is **computed at read time** from each member's
  `lastReadMessageId >= message.id`. There is no separate receipts table.

Other responses: `ReactionDto( Long userId, String emoji )`,
`ConversationDto`, `MemberDto( userId, fullName, avatarUrl, role, muted,
lastReadMessageId )`.

---

## 4. Persistence & schema

Migration: [V13__chat_module.sql](src/main/resources/db/migration/V13__chat_module.sql)
(calls add `V14__chat_calls.sql`, `V15__chat_call_stream_cid.sql`).

### `chat_messages` — [entity/Message.java](src/main/java/com/company/erp/features/chats/entity/Message.java)

```sql
id BIGSERIAL PK,
conversation_id BIGINT NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
sender_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE SET NULL,
type            VARCHAR(16) NOT NULL,          -- TEXT | IMAGE | VOICE | FILE
body            TEXT,                          -- text or caption
attachment_url  VARCHAR(1024),
attachment_content_type VARCHAR(64),
attachment_size_bytes   BIGINT,
duration_seconds        INTEGER,               -- voice only
reply_to_message_id     BIGINT REFERENCES chat_messages(id) ON DELETE SET NULL,
edited_at  TIMESTAMPTZ,
deleted_at TIMESTAMPTZ,                         -- soft delete (isDeleted = deleted_at != null)
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### `chat_message_reactions` — composite PK `(message_id, user_id, emoji)`

One reaction per (user, emoji, message); toggling removes the row if it exists.

### `chat_conversations` / `chat_conversation_members`

- `chat_conversations` carries denormalised `last_message_id` /
  `last_message_at` for the inbox preview (set in `MessageService.send`).
- `chat_conversation_members` PK `(conversation_id, user_id)`, with `role`
  (`ADMIN|MEMBER`), `muted`, and `last_read_message_id` (drives read receipts).

`MessageType` enum: `TEXT, IMAGE, VOICE, FILE`
([entity/MessageType.java](src/main/java/com/company/erp/features/chats/entity/MessageType.java)).
(A `SYSTEM` kind exists on the client; the server persists the four above.)

---

## 5. Service logic — [MessageService.java](src/main/java/com/company/erp/features/chats/service/MessageService.java)

- **`send(convId, senderId, req)`** — `requireMember` → `validateBody` →
  build & save `Message` → validate reply target same-conversation → update
  `Conversation.lastMessageId/At`.
- **`edit(id, actorId, req)`** — sender-only, not deleted, `type == TEXT`,
  within the 15-minute `EDIT_WINDOW`; sets `body` + `editedAt`.
- **`delete(id, actorId)`** — sender-only, idempotent; sets `deletedAt`.
- **`toggleReaction(id, userId, emoji)`** — membership required, not deleted;
  insert/delete the composite-key row; returns the full reaction list.
- **`history` / `search`** — membership required; read-only; newest-first.

All mutating methods rely on JPA dirty-checking inside `@Transactional` — no
explicit second save for `edit`/`delete`/`markRead`.

---

## 6. STOMP broadcast — the realtime contract

### Envelope — [dto/ChatEvent.java](src/main/java/com/company/erp/features/chats/dto/ChatEvent.java)

Every realtime frame is `ChatEvent`:

```json
{ "event": "message.send", "payload": { /* DTO for this event */ } }
```

### Broker config — [ws/WebSocketConfig.java](src/main/java/com/company/erp/features/chats/ws/WebSocketConfig.java)

- Endpoints: `/ws` (native STOMP) and `/ws-sockjs` (SockJS fallback).
- Simple broker on `/topic` and `/queue`; app prefix `/app`; user prefix
  `/user`. Heartbeat 10s/10s.

### Destinations & events — [ChatBroadcaster.java](src/main/java/com/company/erp/features/chats/ws/ChatBroadcaster.java) + the controllers

| Action | Destination(s) | `event` | `payload` |
|---|---|---|---|
| Send | `/topic/conversations/{id}` **and** `/user/queue/inbox` (every member) | `message.send` | `MessageDto` |
| Edit | `/topic/conversations/{id}` | `message.edit` | `MessageDto` |
| Delete | `/topic/conversations/{id}` | `message.delete` | `MessageDto` |
| React | `/topic/conversations/{id}` | `reaction.toggle` | `{ messageId, reactions: [ReactionDto] }` |
| Mark read | `/topic/conversations/{id}` + self `/user/queue/inbox` | `message.read` | `{ conversationId, userId, lastReadMessageId }` |
| Create conv | `/user/queue/inbox` (members) | `conversation.create` | `ConversationDto` |
| Rename / avatar / add member | `/topic/conversations/{id}` + members' inbox | `conversation.update` | `ConversationDto` |
| Remove / delete conv | `/topic/conversations/{id}` + removed user inbox | `conversation.remove` | `ConversationDto` / `{ conversationId }` |

> The dual fan-out on **send** is deliberate: `/topic/conversations/{id}`
> updates anyone with the chat open; `/user/queue/inbox` updates each member's
> inbox tile/badge even when they're not in that conversation. The Flutter
> client filters its own echo by the returned message id.

### Auth — [ws/StompAuthChannelInterceptor.java](src/main/java/com/company/erp/features/chats/ws/StompAuthChannelInterceptor.java)

On the STOMP `CONNECT` frame the client sends
`Authorization: Bearer <accessToken>`. The interceptor validates the JWT and
sets the principal so that `convertAndSendToUser(userId, "/queue/…")` routes
private frames correctly (`Principal.getName()` returns the numeric user id).

---

## 7. Auth & membership

- Current user: `AuthenticatedUser.require().userId()` reads the principal off
  the Spring `SecurityContext` (set by the JWT filter on REST, by the STOMP
  interceptor on the socket).
- Every message read/write calls
  `conversationService.requireMember(convId, userId)` →
  `403 Not a member of this conversation` otherwise.
- Admin-only conversation ops (`rename`, `addMembers`, `removeMember`,
  group `delete`) go through `requireAdmin(...)` in
  [ConversationService.java](src/main/java/com/company/erp/features/chats/service/ConversationService.java).

---

## 8. Attachments

Media (image / voice / file) is a **two-step** flow: upload the bytes, then
send a message carrying the resulting hosted URL. The server never stores the
sender's local device path — that path is invisible to other devices, which is
why a peer couldn't load the image before this endpoint existed.

### Upload endpoint

```
POST /api/v1/chats/attachments        (multipart/form-data, field "file")
→ ChatAttachmentDto { url, contentType, sizeBytes }
```

- Controller — [ChatAttachmentController.java](src/main/java/com/company/erp/features/chats/controller/ChatAttachmentController.java):
  any authenticated user may upload (`AuthenticatedUser.require()`).
- Service — [ChatAttachmentService.java](src/main/java/com/company/erp/features/chats/service/ChatAttachmentService.java):
  validates size + content type, writes `UUID.ext` to disk, returns a
  **server-relative** URL like `/uploads/chat/<uuid>.jpg`.
- DTO — [ChatAttachmentDto.java](src/main/java/com/company/erp/features/chats/dto/ChatAttachmentDto.java):
  `record ChatAttachmentDto(String url, String contentType, long sizeBytes)`.

The returned `url` is **relative**; the client resolves it to an absolute
`http://host:port/uploads/chat/<uuid>.jpg` and sends THAT back as
`SendMessageRequest.attachmentUrl`, so every participant loads the same file.

### Config & serving

```yaml
# application.yml  → app.uploads.chat-attachment
dir:                   ${UPLOAD_CHAT_DIR:./uploads/chat}
public-base-url:       ${UPLOAD_CHAT_PUBLIC_BASE_URL:/uploads/chat}
max-file-size:         ${UPLOAD_CHAT_MAX_SIZE:26214400}   # 25 MiB
allowed-content-types: image/jpeg,image/png,image/webp,image/gif,image/heic,
                       audio/mpeg,audio/mp4,audio/aac,audio/ogg,audio/wav,application/pdf
```

- Bound by `AppProperties.Uploads.ChatAttachment`
  ([AppProperties.java](src/main/java/com/company/erp/core/config/AppProperties.java)).
- Served statically by
  [UploadsWebConfig.java](src/main/java/com/company/erp/core/web/UploadsWebConfig.java)
  (maps `public-base-url/**` → the disk dir).
- `/uploads/**` is `permitAll` in
  [SecurityConfig.java](src/main/java/com/company/erp/core/security/SecurityConfig.java),
  so stored files are publicly fetchable (filenames are random UUIDs).
- Spring's multipart cap (`spring.servlet.multipart.max-file-size`, 25 MB)
  matches the chat limit.

> Pattern source: this mirrors the employee-avatar upload
> ([EmployeeAvatarService.java](src/main/java/com/company/erp/features/employees/service/EmployeeAvatarService.java)),
> minus the entity write — chat attachments aren't owned by a row, the URL just
> rides the message.

### Client status

The Flutter **image** and **voice** sends both upload here first (image as
`image/*`, voice as `audio/mp4` from the `record` package); **file** can reuse
the exact same endpoint once its client handler is switched from the stub to
upload-then-send.

> The client sets an explicit `Content-Type` per file extension before posting —
> required, because the allowlist check rejects the `application/octet-stream`
> that a multipart upload otherwise defaults to.

---

## 9. End-to-end summary

**Send:** `POST …/messages` → resolve user → `requireMember` →
`validateBody(type)` → save `Message` → bump `Conversation.lastMessage*` →
return `MessageDto` → broadcast `message.send` to the conversation topic **and**
every member's inbox queue.

**Edit / Delete / React / Mark-read:** mutate via JPA dirty-checking, then
broadcast the matching `message.edit` / `message.delete` / `reaction.toggle` /
`message.read` event to `/topic/conversations/{id}` (mark-read also nudges the
caller's own inbox).

**Attachments:** upload first via `POST /api/v1/chats/attachments` (multipart) →
get a hosted URL → send the message with that URL as `attachmentUrl`. Stored
under `./uploads/chat`, served publicly from `/uploads/chat/**` (§8).

> Companion (client side): see `chat_conversation.md` in the Flutter repo for
> how each message kind is built and sent from the app.
