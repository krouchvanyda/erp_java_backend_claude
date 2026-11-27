package com.company.erp.features.chats.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring's side of the door {@code /realtime} (Step R2): turns WebSocket
 * events into {@link RealtimeProtocol} calls. No rules here.
 */
@Component
public class RealtimeSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSocketHandler.class);

    /** A slow phone must not block other sends forever. */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final RealtimeProtocol protocol;
    private final Map<String, SpringClient> clients = new ConcurrentHashMap<>();

    public RealtimeSocketHandler(RealtimeProtocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SpringClient client = new SpringClient(
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
        clients.put(session.getId(), client);
        protocol.onOpen(client);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SpringClient client = clients.get(session.getId());
        if (client != null) protocol.onText(client, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
        protocol.onClose(session.getId()); // R4: presence + calls use the result
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        log.debug("[realtime] transport error on {}: {}", session.getId(), error.getMessage());
    }

    /** The real line: Spring's session, safe to send from many threads. */
    private record SpringClient(WebSocketSession session) implements RealtimeClient {

        @Override
        public String id() {
            return session.getId();
        }

        @Override
        public void send(String text) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(text));
            } catch (Exception e) {
                log.debug("[realtime] send failed on {}: {}", session.getId(), e.getMessage());
            }
        }

        @Override
        public void close() {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception e) {
                log.debug("[realtime] close failed on {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
