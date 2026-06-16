# [BACKEND] Gate Stream VoIP ring on STOMP presence — do not ring online callees

**Component:** `erp_java_backend_claude` · `ChatCallService` / `StreamVideoService` / `PresenceService` / `ChatCallController`
**Type:** Behavior contract (already implemented — this documents & protects it)
**Priority:** High — regression here breaks iOS in-call audio
**Status:** Confirmed working in prod-equivalent test 2026-06-11

## Summary
When user **C** calls user **D**, the backend must fire Stream's VoIP ring **only to callees that are OFFLINE** (no live STOMP session). Online (and busy) callees must be notified over STOMP only. Ringing a foreground iOS device via VoIP push causes a duplicate native CallKit header **and** an audio-session conflict (CallKit + WebRTC both grab `AVAudioSession` → mute/speaker stop working).

## Required behavior
On call start (`ChatCallService.start`, `ChatCallService.java:96`), for each callee resolve presence via `PresenceService.statusOf(uid)` (`PresenceService.java:101`). The enum `PresenceStatus` (`PresenceStatus.java:3–10`) has three values:

- `ONLINE` — ≥1 active STOMP session, not in a call
- `BUSY` — ≥1 active STOMP session **and** in an active call
- `OFFLINE` — no active STOMP sessions

| Callee presence | Notify via | Result on device |
|---|---|---|
| **ONLINE / BUSY** (live STOMP session) | STOMP `call.invite` only | In-app `IncomingCallOverlay`; WebRTC owns audio session alone |
| **OFFLINE** (no STOMP session — app backgrounded/killed) | Stream VoIP ring (+ FCM data push backup) | Native CallKit ring |

The gate is implemented in `ChatCallService.start` (`ChatCallService.java:145–160`):

```java
Set<Long> ringTargets = memberIds.stream()
        .filter(uid -> !uid.equals(callerId))
        .filter(uid -> presence.statusOf(uid) == PresenceStatus.OFFLINE)  // ← GATE
        .collect(Collectors.toCollection(LinkedHashSet::new));
if (ringTargets.isEmpty()) {
    // all callees ONLINE → in-app overlay handles foreground, no CallKit
} else {
    Set<Long> ringMembers = new LinkedHashSet<>(ringTargets);
    ringMembers.add(callerId);
    streamVideo.ring(c.getStreamCallCid(), callerId, ringMembers);       // ← FIRES RING
}
```

- **Never** replace this with an unconditional `streamVideo.ring(..., allMembers)` (`StreamVideoService.java:59`).
- The three notify channels are: STOMP invite (online only), Stream VoIP ring (gated to offline), FCM data push (backup for offline).

## Dependent backend requirements (the iOS call flow relies on these)
1. **`streamCallCid` must be present in every call-related FCM payload** — `call.invite` (`ChatCallController.java:193`) **and** `call.cancel` (`ChatCallController.java:221`). The killed/minimized callee recovers the call only from this id.
2. **Terminal teardown must always hit Stream `mark_ended`** — `StreamVideoService.endCall()` (`StreamVideoService.java:131`, POSTs `mark_ended` at `:151`) is invoked from `ChatCallService.endCallInternal()` (`ChatCallService.java:288`, call at `:316`) on *every* terminal path (cancel, reject, hangup, timeout). The client's cancel-dismiss poll reads the resulting terminal status to clear a lingering ring.
3. **`GET /api/v1/chats/calls/{id}` must return a terminal status promptly** once the call ends — `ChatCallController.get` (`ChatCallController.java:86`) returns `ChatCallDto` with `status` (`RINGING`/`ANSWERED`/`ENDED`/`MISSED`/`REJECTED`/`BUSY`), `endedAt`, `endReason`. The minimized iOS callee polls this every 3s (≤36s) to dismiss its CallKit ring, because PushKit rings can't be dismissed by a late push.
4. **`app.fcm.enabled` (env `FCM_ENABLED`)** defaults to `false` (`AppProperties.java:64`; logs `[fcm] disabled (FCM_ENABLED=false)` at `FcmService.java:49`). Confirm the intended prod value — with it off, killed-app callees have no backup delivery path.

## STOMP invite reference
`call.invite` is published in `ChatCallController.start` to:
- the conversation topic — `broadcaster.toCall(convId, "call.invite", dto)` → `/topic/conv.{convId}` (`ChatCallController.java:101`)
- the per-user queue — `broadcaster.toUser(userId, "calls", "call.invite", dto)` → `/user/queue/calls` (`ChatCallController.java:106`)

## Acceptance criteria
- [ ] Foreground/ONLINE callee receives STOMP `call.invite` and **no** Stream VoIP push; no native CallKit header appears; in-call mute/speaker work.
- [ ] Background/OFFLINE callee receives the Stream VoIP ring → native CallKit header.
- [ ] `call.invite` and `call.cancel` FCM payloads both carry `streamCallCid`.
- [ ] Every terminal call path calls Stream `mark_ended`; `GET /api/v1/chats/calls/{id}` reflects terminal status within a few seconds.

## Notes / out of scope
- The native `AppDelegate.swift` `CXCallObserver` foreground-suppression is a **safety net** for the presence-race window only — not a substitute for this gate.
- "Second call to a minimized callee shows no ring" is a **separate** issue (Stream APN VoIP device registration + sandbox cert), not this presence gate.
