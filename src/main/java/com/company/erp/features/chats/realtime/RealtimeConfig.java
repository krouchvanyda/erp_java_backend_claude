package com.company.erp.features.chats.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Opens the plain-WebSocket door {@code /realtime} (Step R2), NEXT TO the
 * STOMP door {@code /ws} ({@code chats/ws/WebSocketConfig}). Both work until
 * Track B is finished.
 *
 * <p>The HTTP upgrade has no token ({@code SecurityConfig}: permitAll); the
 * token comes in the first letter ({"type":"auth"}).</p>
 */
@Configuration
@EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeSocketHandler handler;

    public RealtimeConfig(RealtimeSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/realtime").setAllowedOriginPatterns("*");
    }
}
