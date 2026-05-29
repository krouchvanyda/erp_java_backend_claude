package com.company.erp.features.chats.presence;

import com.company.erp.core.security.AuthenticatedUser;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionListener {

    private final PresenceService presence;

    public WebSocketSessionListener(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userIdOf(sha);
        String sessionId = sha.getSessionId();
        if (userId != null && sessionId != null) {
            presence.connect(userId, sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presence.disconnect(event.getSessionId());
    }

    private Long userIdOf(StompHeaderAccessor sha) {
        if (sha.getUser() instanceof Authentication auth
                && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u.userId();
        }
        return null;
    }
}
