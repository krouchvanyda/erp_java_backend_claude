package com.company.erp.features.chats.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * The POSTMAN of the plain-WebSocket door (Step R3): puts a
 * {@code {"type":"message","destination","event","payload"}} letter in the
 * phone book's mailboxes. {@code ChatBroadcaster} calls it next to STOMP, so
 * every live letter goes out through BOTH doors.
 *
 * <p>The letter is turned into JSON ONCE (Spring's {@link ObjectMapper}, the
 * same one STOMP uses → the same dates and field names), then the same text
 * goes to every line.</p>
 */
@Component
public class RealtimeBroadcast {

    private final RealtimeSessions sessions;
    private final RealtimeLetters letters;

    public RealtimeBroadcast(RealtimeSessions sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.letters = new RealtimeLetters(json);
    }

    /** Everyone subscribed to a topic ("/topic/conversations/5", "/topic/presence"). */
    public int toTopic(String destination, String event, Object payload) {
        return sessions.sendTo(destination, letters.message(destination, event, payload));
    }

    /**
     * One user's private queue: [queue] "/queue/inbox" → that user's lines on
     * "/user/queue/inbox" (the letter says "/user/queue/inbox", the address the
     * phone subscribed to).
     */
    public int toUser(long userId, String queue, String event, Object payload) {
        String destination = "/user" + queue;
        return sessions.sendToUser(userId, queue, letters.message(destination, event, payload));
    }
}
