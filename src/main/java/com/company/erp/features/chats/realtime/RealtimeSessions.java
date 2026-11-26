package com.company.erp.features.chats.realtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The PHONE BOOK of the plain-WebSocket realtime (Step R1, branch
 * {@code feature/raw-websocket}): what Spring's STOMP broker did for free.
 *
 * <pre>
 *   line (client) ──► who is it?          (userId, after the "auth" letter)
 *                 └─► which addresses?    ("/user/queue/inbox", "/topic/conversations/5" …)
 *
 *   sendTo("/topic/conversations/5", text)    → every line subscribed to it
 *   sendToUser(5, "/queue/inbox", text)       → user 5's lines subscribed to "/user/queue/inbox"
 * </pre>
 *
 * <p>Addresses are the SAME strings as with STOMP, so the app's repositories
 * don't change. A {@code /user/...} address is private: it only ever reaches
 * the lines of the user it was sent to.</p>
 *
 * <p>Thread-safe: lines open, close and subscribe on many threads at once.</p>
 */
@Component
public class RealtimeSessions {

    /** Every open line, by id. */
    private final Map<String, Entry> lines = new ConcurrentHashMap<>();

    /** One line + what we know about it. */
    private static final class Entry {
        final RealtimeClient client;
        volatile Long userId;                                  // null until "auth"
        final Set<String> destinations = ConcurrentHashMap.newKeySet();

        Entry(RealtimeClient client) {
            this.client = client;
        }
    }

    /** A new line opened (not logged in yet). */
    public void open(RealtimeClient client) {
        lines.put(client.id(), new Entry(client));
    }

    /** The line showed a valid token: it belongs to [userId] now. */
    public void authenticate(String lineId, long userId) {
        Entry e = lines.get(lineId);
        if (e != null) e.userId = userId;
    }

    /** Who this line belongs to; empty = unknown line or not logged in yet. */
    public Optional<Long> userOf(String lineId) {
        Entry e = lines.get(lineId);
        return e == null ? Optional.empty() : Optional.ofNullable(e.userId);
    }

    /** Listen to [destination] on this line. Only for a logged-in line. */
    public boolean subscribe(String lineId, String destination) {
        Entry e = lines.get(lineId);
        if (e == null || e.userId == null) return false;
        e.destinations.add(destination);
        return true;
    }

    public void unsubscribe(String lineId, String destination) {
        Entry e = lines.get(lineId);
        if (e != null) e.destinations.remove(destination);
    }

    /**
     * The line closed. Returns who it belonged to and whether it was that
     * user's LAST line (→ presence: offline; calls: "gone", Step R4).
     */
    public Optional<Closed> close(String lineId) {
        Entry e = lines.remove(lineId);
        if (e == null || e.userId == null) return Optional.empty();
        return Optional.of(new Closed(e.userId, !isOnline(e.userId)));
    }

    /** What {@link #close} tells: the user, and whether no line of theirs is left. */
    public record Closed(long userId, boolean lastLine) {}

    /** Does [userId] have at least one logged-in line? */
    public boolean isOnline(long userId) {
        for (Entry e : lines.values()) {
            if (e.userId != null && e.userId == userId) return true;
        }
        return false;
    }

    /** Send to every line subscribed to [destination] ("/topic/…"). Returns how many. */
    public int sendTo(String destination, String text) {
        int sent = 0;
        for (Entry e : lines.values()) {
            if (e.userId != null && e.destinations.contains(destination)) {
                e.client.send(text);
                sent++;
            }
        }
        return sent;
    }

    /**
     * Private send: [userId]'s lines subscribed to {@code "/user" + queue}
     * (e.g. queue "/queue/inbox" → "/user/queue/inbox"). Returns how many.
     */
    public int sendToUser(long userId, String queue, String text) {
        String destination = "/user" + queue;
        int sent = 0;
        for (Entry e : lines.values()) {
            if (e.userId != null && e.userId == userId && e.destinations.contains(destination)) {
                e.client.send(text);
                sent++;
            }
        }
        return sent;
    }

    /** The open lines of [userId] (e.g. to close them all). */
    public List<RealtimeClient> linesOf(long userId) {
        List<RealtimeClient> out = new ArrayList<>();
        for (Entry e : lines.values()) {
            if (e.userId != null && e.userId == userId) out.add(e.client);
        }
        return out;
    }

    /** How many lines are open (logged in or not). For logs / tests. */
    public int size() {
        return lines.size();
    }
}
