package com.company.erp.features.chats.presence;

import com.company.erp.features.chats.service.CallDisconnectHandler;
import org.springframework.stereotype.Component;

/**
 * ONE rule for "a user's live connection came / went", shared by BOTH doors
 * (Step R4, branch {@code feature/raw-websocket}):
 *
 * <pre>
 *   STOMP  /ws        ── WebSocketSessionListener ─┐
 *   plain  /realtime  ── RealtimeProtocol ─────────┤
 *                                                  ▼
 *   connected(user, id)    → PresenceService.connect      (🟢 online)
 *   disconnected(id)       → PresenceService.disconnect   (offline if it was the last one)
 *                             no live connection left? → CallDisconnectHandler
 *                             (end a ring / hang up a stuck call after its grace)
 * </pre>
 *
 * <p>Connection ids of the two doors must never clash: the plain door passes
 * {@code "rt:" + lineId}. PresenceService counts ALL of a user's connections
 * (a STOMP phone + a /realtime tablet = still online when one closes).</p>
 */
@Component
public class UserConnections {

    private final PresenceService presence;
    private final CallDisconnectHandler callDisconnect;

    public UserConnections(PresenceService presence, CallDisconnectHandler callDisconnect) {
        this.presence = presence;
        this.callDisconnect = callDisconnect;
    }

    /** A logged-in connection opened (STOMP CONNECT / plain "auth" OK). */
    public void connected(Long userId, String connectionId) {
        presence.connect(userId, connectionId);
    }

    /** How a plain-door line ended (R6): the socket CLOSED, or it went SILENT. */
    public enum Drop {
        /** The socket was closed: the app closed it, or its OS did (app killed). */
        CLOSED,
        /** No letters for 25 s (the sweep closed it): maybe just the network. */
        SILENT
    }

    /** A STOMP connection closed (we can't tell how) → the long grace. */
    public Long disconnected(String connectionId) {
        return disconnected(connectionId, Drop.SILENT);
    }

    /** A connection closed. Returns its user (null if unknown). */
    public Long disconnected(String connectionId, Drop how) {
        Long userId = presence.disconnect(connectionId);
        // The socket dropped — if this user was ringing / talking, the call
        // rule re-checks after its grace (blips and minimize are handled there).
        if (userId != null && !presence.hasLiveSession(userId)) {
            callDisconnect.onUserConnectionDropped(userId, how == Drop.CLOSED);
        }
        return userId;
    }
}
