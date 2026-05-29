package com.company.erp.features.chats.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP endpoint at {@code /ws}. Clients should:
 *
 * <pre>
 *   ws.connect()           // CONNECT frame
 *      header Authorization: Bearer &lt;accessToken&gt;
 *
 *   ws.subscribe("/topic/conversations/42")          // message stream
 *   ws.subscribe("/topic/conversations/42/call")     // call state
 *   ws.subscribe("/user/queue/calls")                // per-user incoming-call invites
 *   ws.subscribe("/user/queue/inbox")                // per-user inbox previews
 * </pre>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native STOMP-over-WebSocket on `/ws` (Flutter clients use this directly).
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        // SockJS fallback on `/ws-sockjs` for browser clients without WebSocket support.
        registry.addEndpoint("/ws-sockjs").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
