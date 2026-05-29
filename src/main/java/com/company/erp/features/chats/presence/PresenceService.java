package com.company.erp.features.chats.presence;

import com.company.erp.features.chats.ws.ChatBroadcaster;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence tracker. Sessions are added on STOMP CONNECT and removed
 * on DISCONNECT. Busy-state is driven separately by {@code ChatCallService}
 * (a user is BUSY whenever they're in an active call).
 *
 * <p>State is process-local — multi-instance deployments need to move this
 * to Redis (pub/sub for cross-instance fan-out plus a hash for the map).
 * Single-instance is the current scope.</p>
 */
@Service
public class PresenceService {

    /** userId → set of currently active STOMP session ids. */
    private final Map<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    /** sessionId → userId, used for O(1) cleanup on DISCONNECT. */
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();
    /** userId → last time they went OFFLINE (for "last seen 5 min ago" UI). */
    private final Map<Long, Instant> lastSeenAt = new ConcurrentHashMap<>();
    /** userId → true if they're currently in an active call. */
    private final Set<Long> busy = ConcurrentHashMap.newKeySet();

    private final ChatBroadcaster broadcaster;
    private final SimpMessagingTemplate template;

    public PresenceService(ChatBroadcaster broadcaster, SimpMessagingTemplate template) {
        this.broadcaster = broadcaster;
        this.template = template;
    }

    public void connect(Long userId, String sessionId) {
        if (userId == null || sessionId == null) return;
        PresenceStatus before = statusOf(userId);
        sessionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        userBySession.put(sessionId, userId);
        PresenceStatus after = statusOf(userId);
        if (before != after) emit(userId, after);
    }

    public void disconnect(String sessionId) {
        Long userId = userBySession.remove(sessionId);
        if (userId == null) return;
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) sessionsByUser.remove(userId);
        }
        if (!sessionsByUser.containsKey(userId)) {
            lastSeenAt.put(userId, Instant.now());
        }
        // Always emit on disconnect: status may have flipped to OFFLINE,
        // or stayed ONLINE/BUSY because other sessions are still up.
        emit(userId, statusOf(userId));
    }

    public void markBusy(Long userId) {
        if (userId == null) return;
        boolean changed = busy.add(userId);
        if (changed) emit(userId, statusOf(userId));
    }

    public void clearBusy(Long userId) {
        if (userId == null) return;
        boolean changed = busy.remove(userId);
        if (changed) emit(userId, statusOf(userId));
    }

    public PresenceStatus statusOf(Long userId) {
        boolean online = sessionsByUser.containsKey(userId);
        if (!online) return PresenceStatus.OFFLINE;
        return busy.contains(userId) ? PresenceStatus.BUSY : PresenceStatus.ONLINE;
    }

    public PresenceDto dtoOf(Long userId) {
        PresenceStatus s = statusOf(userId);
        Instant seen = (s == PresenceStatus.OFFLINE) ? lastSeenAt.get(userId) : null;
        return new PresenceDto(userId, s, seen);
    }

    /** Snapshot of every user the service has ever seen (online or offline). */
    public List<PresenceDto> snapshot() {
        Set<Long> ids = new HashSet<>();
        ids.addAll(sessionsByUser.keySet());
        ids.addAll(lastSeenAt.keySet());
        ids.addAll(busy);
        return ids.stream().map(this::dtoOf).toList();
    }

    public List<PresenceDto> dtosFor(List<Long> userIds) {
        return userIds.stream().map(this::dtoOf).toList();
    }

    private void emit(Long userId, PresenceStatus status) {
        Instant seen = (status == PresenceStatus.OFFLINE) ? lastSeenAt.get(userId) : null;
        PresenceDto dto = new PresenceDto(userId, status, seen);
        // Public topic — anyone interested in any user's presence can subscribe.
        template.convertAndSend("/topic/presence",
                Map.of("event", "presence.update", "payload", dto));
    }
}
