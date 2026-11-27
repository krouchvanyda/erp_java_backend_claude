package com.company.erp.features.chats.presence;

import com.company.erp.core.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP door ({@code /ws}): turns Spring's STOMP session events into
 * {@link UserConnections} calls. The plain door ({@code /realtime}) calls the
 * same {@link UserConnections} from {@code RealtimeProtocol} (Step R4).
 */
@Component
public class WebSocketSessionListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

    private final UserConnections connections;

    public WebSocketSessionListener(UserConnections connections) {
        this.connections = connections;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userIdOf(sha);
        String sessionId = sha.getSessionId();
        if (userId != null && sessionId != null) {
            log.debug("STOMP CONNECT user={} session={}", userId, sessionId);
            connections.connected(userId, sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        log.debug("STOMP DISCONNECT session={} status={}",
                event.getSessionId(), event.getCloseStatus());
        connections.disconnected(event.getSessionId());
    }

    private Long userIdOf(StompHeaderAccessor sha) {
        if (sha.getUser() instanceof Authentication auth
                && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u.userId();
        }
        return null;
    }
}
