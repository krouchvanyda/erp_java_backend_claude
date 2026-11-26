package com.company.erp.features.chats.realtime;

/**
 * ONE open plain-WebSocket line, as {@link RealtimeSessions} sees it
 * (Step R1, branch {@code feature/raw-websocket}).
 *
 * <p>Small on purpose — only what the phone book needs — so tests can use a
 * fake one (no real socket). The real one wraps Spring's
 * {@code WebSocketSession} (Step R2).</p>
 */
public interface RealtimeClient {

    /** Unique per line (Spring's session id). */
    String id();

    /** Send one text frame (a JSON letter). Must not throw. */
    void send(String text);

    /** Hang up this line. Must not throw. */
    void close();
}
