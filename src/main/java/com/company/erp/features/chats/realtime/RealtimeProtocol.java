package com.company.erp.features.chats.realtime;

import com.company.erp.core.security.JwtService;
import com.company.erp.features.chats.dto.TypingRequest;
import com.company.erp.features.chats.presence.UserConnections;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.ws.TypingController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The BRAIN of the plain-WebSocket door {@code /realtime} (Step R2, branch
 * {@code feature/raw-websocket}). It reads the phone's letters and answers.
 * Plain Java (no Spring WebSocket types), so tests use toy lines.
 *
 * <pre>
 *   open ──► wait for {"type":"auth","token":…}  (10 s, else error + close)
 *            ├─ valid token  → {"type":"ready"}   (this line = user N)
 *            └─ bad token    → error + close
 *   then  subscribe / unsubscribe {destination}  (chat topics: members only)
 *         ping → pong
 *         send {destination, body}                (R3: only /app/conversations/{id}/typing)
 *   silent 25 s (no letter, not even ping) → close  (a dead phone)
 *
 *   R4: "auth" OK → UserConnections.connected   (presence 🟢)
 *       close     → UserConnections.disconnected (presence + the call rule)
 * </pre>
 */
@Component
public class RealtimeProtocol {

    private static final Logger log = LoggerFactory.getLogger(RealtimeProtocol.class);

    /** The first letter must be "auth" within this time. */
    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);

    /** The phone pings every 10 s; nothing for this long = a dead line. */
    static final Duration IDLE_TIMEOUT = Duration.ofSeconds(25);

    /** "/topic/conversations/42" or "/topic/conversations/42/call". */
    private static final Pattern CONVERSATION_TOPIC =
            Pattern.compile("^/topic/conversations/(\\d+)(/call)?$");

    /** "/user/queue/inbox", "/user/queue/calls" … (always MY queue). */
    private static final Pattern USER_QUEUE = Pattern.compile("^/user/queue/[a-z-]+$");

    private static final String PRESENCE_TOPIC = "/topic/presence";

    /** The only phone → server address today: "/app/conversations/42/typing". */
    private static final Pattern TYPING = Pattern.compile("^/app/conversations/(\\d+)/typing$");

    private final RealtimeSessions sessions;
    private final JwtService jwt;
    private final ConversationService conversations;
    private final TypingController typing;
    private final UserConnections connections;
    private final ObjectMapper json;
    private final RealtimeLetters letters;
    private final Clock clock;

    /** Every open line + its times (for the 10 s / 25 s rules). */
    private final Map<String, Line> lines = new ConcurrentHashMap<>();

    private static final class Line {
        final RealtimeClient client;
        final Instant openedAt;
        volatile Instant lastSeen;
        volatile boolean authed;
        volatile boolean silent; // closed by the sweep (no letters 25 s), not by the phone

        Line(RealtimeClient client, Instant now) {
            this.client = client;
            this.openedAt = now;
            this.lastSeen = now;
        }
    }

    @Autowired
    public RealtimeProtocol(RealtimeSessions sessions, JwtService jwt,
                            ConversationService conversations, TypingController typing,
                            UserConnections connections, ObjectMapper json) {
        this(sessions, jwt, conversations, typing, connections, json, Clock.systemUTC());
    }

    RealtimeProtocol(RealtimeSessions sessions, JwtService jwt,
                     ConversationService conversations, TypingController typing,
                     UserConnections connections, ObjectMapper json, Clock clock) {
        this.sessions = sessions;
        this.jwt = jwt;
        this.conversations = conversations;
        this.typing = typing;
        this.connections = connections;
        this.json = json;
        this.letters = new RealtimeLetters(json);
        this.clock = clock;
    }

    public RealtimeLetters letters() {
        return letters;
    }

    /** A new line opened (not logged in yet). */
    public void onOpen(RealtimeClient client) {
        lines.put(client.id(), new Line(client, clock.instant()));
        sessions.open(client);
    }

    /** One letter from the phone. */
    public void onText(RealtimeClient client, String text) {
        Line line = lines.get(client.id());
        if (line == null) return; // already closed
        line.lastSeen = clock.instant();

        JsonNode letter;
        try {
            letter = json.readTree(text);
        } catch (Exception e) {
            if (line.authed) client.send(letters.error("Not JSON"));
            else fail(line, "Not JSON"); // the FIRST letter must be auth
            return;
        }
        String type = letter.path("type").asText("");

        if (!line.authed) {
            if (!"auth".equals(type)) {
                fail(line, "Send auth first");
            } else {
                auth(line, letter.path("token").asText(""));
            }
            return;
        }

        switch (type) {
            case "ping" -> client.send(letters.pong());
            case "subscribe" -> subscribe(line, letter.path("destination").asText(""));
            case "unsubscribe" -> sessions.unsubscribe(client.id(), letter.path("destination").asText(""));
            case "send" -> send(line, letter.path("destination").asText(""), letter.path("body"));
            case "auth" -> { } // already logged in on this line
            default -> client.send(letters.error("Unknown letter type: " + type));
        }
    }

    /**
     * The line closed (either side). Who left, and was it their last line on
     * THIS door. Presence + the call rule are told (R4); they count both doors.
     */
    public Optional<RealtimeSessions.Closed> onClose(String lineId) {
        Line line = lines.remove(lineId);
        Optional<RealtimeSessions.Closed> closed = sessions.close(lineId);
        if (closed.isPresent()) {
            // R6: the OS closes a killed app's socket AT ONCE; a network drop is
            // only noticed by the sweep (silent). Closed → the call ends fast.
            boolean silent = line != null && line.silent;
            connections.disconnected(connectionId(lineId),
                    silent ? UserConnections.Drop.SILENT : UserConnections.Drop.CLOSED);
        }
        return closed;
    }

    /** Presence's id for a plain-door line: never the same as a STOMP session id. */
    static String connectionId(String lineId) {
        return "rt:" + lineId;
    }

    /** Every 5 s: close lines that never logged in (10 s) or went silent (25 s). */
    @Scheduled(fixedRate = 5_000)
    public void sweep() {
        Instant now = clock.instant();
        for (Line line : lines.values()) {
            if (!line.authed && line.openedAt.plus(AUTH_TIMEOUT).isBefore(now)) {
                fail(line, "auth timeout");
            } else if (line.lastSeen.plus(IDLE_TIMEOUT).isBefore(now)) {
                log.info("[realtime] line {} silent > {} s → close", line.client.id(),
                        IDLE_TIMEOUT.toSeconds());
                line.silent = true; // → onClose: maybe only the network (long grace)
                line.client.close(); // the handler's close callback → onClose
            }
        }
    }

    private void auth(Line line, String token) {
        try {
            long userId = jwt.parseAccess(token).userId();
            line.authed = true;
            sessions.authenticate(line.client.id(), userId);
            connections.connected(userId, connectionId(line.client.id())); // presence 🟢 (R4)
            line.client.send(letters.ready());
        } catch (Exception e) {
            fail(line, "Invalid or expired token");
        }
    }

    private void subscribe(Line line, String destination) {
        long me = sessions.userOf(line.client.id()).orElseThrow();
        if (!allowed(destination, me)) {
            line.client.send(letters.error("Not allowed", destination));
            return;
        }
        sessions.subscribe(line.client.id(), destination);
    }

    /**
     * A letter FROM the phone to a server address (R3). Today only typing.
     * WHO = this line's user (from its token), never from the body: the same
     * rule as STOMP's principal. The member check lives in TypingController,
     * shared by both doors.
     */
    private void send(Line line, String destination, JsonNode body) {
        Matcher m = TYPING.matcher(destination);
        if (!m.matches()) {
            line.client.send(letters.error("Unknown destination", destination));
            return;
        }
        long me = sessions.userOf(line.client.id()).orElseThrow();
        boolean isTyping = !body.has("typing") || body.path("typing").asBoolean(true);
        typing.typing(Long.parseLong(m.group(1)), new TypingRequest(isTyping),
                () -> String.valueOf(me));
    }

    /** Which addresses may this user listen to? */
    boolean allowed(String destination, long userId) {
        if (PRESENCE_TOPIC.equals(destination)) return true;
        if (USER_QUEUE.matcher(destination).matches()) return true; // only ever MY lines get it
        Matcher m = CONVERSATION_TOPIC.matcher(destination);
        if (m.matches()) {
            try {
                conversations.requireMember(Long.parseLong(m.group(1)), userId);
                return true;
            } catch (Exception e) {
                return false; // not a member (or no such chat)
            }
        }
        return false; // unknown address
    }

    /** Tell why, then hang up. */
    private void fail(Line line, String message) {
        line.client.send(letters.error(message));
        line.client.close();
    }
}
