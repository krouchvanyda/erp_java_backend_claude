package com.company.erp.features.chats.presence;

import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.features.chats.service.CallDisconnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

    private final PresenceService presence;
    private final CallDisconnectHandler callDisconnect;

    public WebSocketSessionListener(PresenceService presence, CallDisconnectHandler callDisconnect) {
        this.presence = presence;
        this.callDisconnect = callDisconnect;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userIdOf(sha);
        String sessionId = sha.getSessionId();
        if (userId != null && sessionId != null) {
            log.debug("STOMP CONNECT user={} session={}", userId, sessionId);
            presence.connect(userId, sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        log.debug("STOMP DISCONNECT session={} status={}",
                event.getSessionId(), event.getCloseStatus());
        Long userId = presence.disconnect(event.getSessionId());
        // The socket dropped — if this user was the (online) callee on a ringing
        // call, end the ring quickly instead of waiting for the timeout sweep.
        // Guards for blips / intentional minimize live in the handler.
        if (userId != null && !presence.hasLiveSession(userId)) {
            callDisconnect.onUserConnectionDropped(userId);
        }
    }

    private Long userIdOf(StompHeaderAccessor sha) {
        if (sha.getUser() instanceof Authentication auth
                && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u.userId();
        }
        return null;
    }
}
