package com.company.erp.features.chats.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link RealtimeSessions} — the phone book of the plain-WebSocket realtime (R1). */
class RealtimeSessionsTest {

    /** A toy line: remembers what it was sent. */
    static final class FakeClient implements RealtimeClient {
        final String id;
        final List<String> got = new ArrayList<>();
        boolean closed;

        FakeClient(String id) { this.id = id; }

        @Override public String id() { return id; }
        @Override public void send(String text) { got.add(text); }
        @Override public void close() { closed = true; }
    }

    private RealtimeSessions sessions;
    private FakeClient bin, binTablet, lin, stranger;

    @BeforeEach
    void setUp() {
        sessions = new RealtimeSessions();
        bin = new FakeClient("a");          // user 5, phone
        binTablet = new FakeClient("b");    // user 5, tablet
        lin = new FakeClient("c");          // user 7
        stranger = new FakeClient("d");     // never logs in
        for (FakeClient c : List.of(bin, binTablet, lin, stranger)) sessions.open(c);
        sessions.authenticate("a", 5);
        sessions.authenticate("b", 5);
        sessions.authenticate("c", 7);
    }

    @Test
    void topic_reachesEveryLineSubscribedToIt() {
        sessions.subscribe("a", "/topic/conversations/1");
        sessions.subscribe("c", "/topic/conversations/1");
        sessions.subscribe("b", "/topic/conversations/2");     // another chat

        int sent = sessions.sendTo("/topic/conversations/1", "hello");

        assertThat(sent).isEqualTo(2);
        assertThat(bin.got).containsExactly("hello");
        assertThat(lin.got).containsExactly("hello");
        assertThat(binTablet.got).isEmpty();
    }

    @Test
    void userQueue_isPrivate_allLinesOfThatUser_only() {
        sessions.subscribe("a", "/user/queue/inbox");
        sessions.subscribe("b", "/user/queue/inbox");
        sessions.subscribe("c", "/user/queue/inbox");          // user 7 has an inbox too

        int sent = sessions.sendToUser(5, "/queue/inbox", "for 5");

        assertThat(sent).isEqualTo(2);
        assertThat(bin.got).containsExactly("for 5");
        assertThat(binTablet.got).containsExactly("for 5");
        assertThat(lin.got).isEmpty();                          // never another user's letter
    }

    @Test
    void notLoggedIn_cannotSubscribe_andGetsNothing() {
        assertThat(sessions.subscribe("d", "/topic/conversations/1")).isFalse();
        assertThat(sessions.subscribe("unknown", "/topic/conversations/1")).isFalse();

        sessions.sendTo("/topic/conversations/1", "x");

        assertThat(stranger.got).isEmpty();
        assertThat(sessions.userOf("d")).isEmpty();
        assertThat(sessions.userOf("a")).contains(5L);
    }

    @Test
    void unsubscribe_stopsLetters() {
        sessions.subscribe("a", "/topic/conversations/1");
        sessions.unsubscribe("a", "/topic/conversations/1");

        assertThat(sessions.sendTo("/topic/conversations/1", "x")).isZero();
        assertThat(bin.got).isEmpty();
    }

    @Test
    void close_tellsWhoAndWhetherItWasTheLastLine() {
        // user 5 has 2 lines: closing one → still online
        assertThat(sessions.close("a")).contains(new RealtimeSessions.Closed(5, false));
        assertThat(sessions.isOnline(5)).isTrue();

        // the second one → last line → offline (presence, calls: R4)
        assertThat(sessions.close("b")).contains(new RealtimeSessions.Closed(5, true));
        assertThat(sessions.isOnline(5)).isFalse();

        // a line that never logged in / already closed → nothing to tell
        assertThat(sessions.close("d")).isEmpty();
        assertThat(sessions.close("a")).isEmpty();
    }

    @Test
    void closedLine_getsNoMoreLetters() {
        sessions.subscribe("c", "/topic/conversations/1");
        sessions.close("c");

        assertThat(sessions.sendTo("/topic/conversations/1", "x")).isZero();
        assertThat(lin.got).isEmpty();
        assertThat(sessions.linesOf(7)).isEmpty();
        assertThat(sessions.linesOf(5)).containsExactlyInAnyOrder(bin, binTablet);
    }
}
