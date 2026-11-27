package com.company.erp.features.chats.realtime;

import com.company.erp.core.exceptions.ForbiddenException;
import com.company.erp.core.exceptions.UnauthorizedException;
import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.core.security.JwtService;
import com.company.erp.features.chats.realtime.RealtimeSessionsTest.FakeClient;
import com.company.erp.features.chats.dto.TypingRequest;
import com.company.erp.features.chats.presence.UserConnections;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.ws.TypingController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link RealtimeProtocol} — the brain of the door /realtime (R2). */
class RealtimeProtocolTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-27T10:00:00Z");
        void plus(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private RealtimeSessions sessions;
    private RealtimeProtocol protocol;
    private TestClock clock;
    private FakeClient phone;
    private TypingController typing;
    private UserConnections connections;

    @BeforeEach
    void setUp() {
        sessions = new RealtimeSessions();
        clock = new TestClock();
        JwtService jwt = mock(JwtService.class);
        // doThrow/doReturn: stubbing must not CALL the (already throwing) method
        doThrow(new UnauthorizedException("Invalid or expired token")).when(jwt).parseAccess(anyString());
        doReturn(new AuthenticatedUser(5L, "bin@x.com", Set.of())).when(jwt).parseAccess("good");
        ConversationService conversations = mock(ConversationService.class);
        typing = mock(TypingController.class);
        connections = mock(UserConnections.class);
        when(conversations.requireMember(99L, 5L))
                .thenThrow(new ForbiddenException("Not a member of this conversation"));
        protocol = new RealtimeProtocol(sessions, jwt, conversations, typing, connections, new ObjectMapper(), clock);
        phone = new FakeClient("line-1");
        protocol.onOpen(phone);
    }

    private void login() {
        protocol.onText(phone, "{\"type\":\"auth\",\"token\":\"good\"}");
    }

    @Test
    void auth_goodToken_ready_andTheLineIsUser5() {
        login();

        assertThat(phone.got).containsExactly("{\"type\":\"ready\"}");
        assertThat(sessions.userOf("line-1")).contains(5L);
        assertThat(phone.closed).isFalse();
    }

    @Test
    void auth_badToken_errorAndClose() {
        protocol.onText(phone, "{\"type\":\"auth\",\"token\":\"expired\"}");

        assertThat(phone.got).hasSize(1);
        assertThat(phone.got.get(0)).contains("\"type\":\"error\"").contains("Invalid or expired token");
        assertThat(phone.closed).isTrue();
        assertThat(sessions.userOf("line-1")).isEmpty();
    }

    @Test
    void anythingBeforeAuth_errorAndClose() {
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/user/queue/inbox\"}");

        assertThat(phone.got.get(0)).contains("Send auth first");
        assertThat(phone.closed).isTrue();
    }

    @Test
    void ping_pong() {
        login();
        protocol.onText(phone, "{\"type\":\"ping\"}");

        assertThat(phone.got).last().isEqualTo("{\"type\":\"pong\"}");
    }

    @Test
    void subscribe_myQueue_presence_andAChatIAmIn() {
        login();
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/user/queue/inbox\"}");
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/topic/presence\"}");
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/topic/conversations/1\"}");
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/topic/conversations/1/call\"}");

        assertThat(sessions.sendToUser(5, "/queue/inbox", "a")).isEqualTo(1);
        assertThat(sessions.sendTo("/topic/presence", "b")).isEqualTo(1);
        assertThat(sessions.sendTo("/topic/conversations/1", "c")).isEqualTo(1);
        assertThat(sessions.sendTo("/topic/conversations/1/call", "d")).isEqualTo(1);
    }

    @Test
    void subscribe_aChatIAmNotIn_orAnUnknownAddress_isRefused_lineStaysOpen() {
        login();
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/topic/conversations/99\"}");
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/topic/secret\"}");

        assertThat(phone.got).filteredOn(l -> l.contains("Not allowed")).hasSize(2);
        assertThat(sessions.sendTo("/topic/conversations/99", "x")).isZero();
        assertThat(phone.closed).isFalse();
    }

    @Test
    void unsubscribe_stopsLetters() {
        login();
        protocol.onText(phone, "{\"type\":\"subscribe\",\"destination\":\"/topic/conversations/1\"}");
        protocol.onText(phone, "{\"type\":\"unsubscribe\",\"destination\":\"/topic/conversations/1\"}");

        assertThat(sessions.sendTo("/topic/conversations/1", "x")).isZero();
    }

    @Test
    void notJson_asTheFirstLetter_errorAndClose() {
        protocol.onText(phone, "hello?");

        assertThat(phone.got).containsExactly("{\"type\":\"error\",\"message\":\"Not JSON\"}");
        assertThat(phone.closed).isTrue();
    }

    @Test
    void notJson_afterAuth_isAnError_butTheLineStays() {
        login();
        protocol.onText(phone, "hello?");

        assertThat(phone.got).last().asString().contains("Not JSON");
        assertThat(phone.closed).isFalse();
    }

    @Test
    void noAuthWithin10s_closed() {
        clock.plus(Duration.ofSeconds(9));
        protocol.sweep();
        assertThat(phone.closed).isFalse();

        clock.plus(Duration.ofSeconds(2));
        protocol.sweep();
        assertThat(phone.got).last().asString().contains("auth timeout");
        assertThat(phone.closed).isTrue();
    }

    @Test
    void silentFor25s_closed_butPingsKeepItAlive() {
        login();
        for (int i = 0; i < 5; i++) {               // 50 s of pings every 10 s
            clock.plus(Duration.ofSeconds(10));
            protocol.onText(phone, "{\"type\":\"ping\"}");
            protocol.sweep();
        }
        assertThat(phone.closed).isFalse();

        clock.plus(Duration.ofSeconds(26));          // the phone died
        protocol.sweep();
        assertThat(phone.closed).isTrue();
    }

    @Test
    void close_tellsWhoLeft() {
        login();

        assertThat(protocol.onClose("line-1")).contains(new RealtimeSessions.Closed(5, true));
        assertThat(sessions.isOnline(5)).isFalse();
    }

    @Test
    void send_typing_goesToTypingController_asTheLinesUser() {
        login();
        protocol.onText(phone,
                "{\"type\":\"send\",\"destination\":\"/app/conversations/1/typing\",\"body\":{\"typing\":false}}");

        // WHO = user 5 (this line's token), whatever the body says
        verify(typing).typing(eq(1L), eq(new TypingRequest(false)),
                argThat(p -> "5".equals(p.getName())));
    }

    @Test
    void send_typing_withoutBody_meansTyping() {
        login();
        protocol.onText(phone, "{\"type\":\"send\",\"destination\":\"/app/conversations/1/typing\"}");

        verify(typing).typing(eq(1L), eq(new TypingRequest(true)), any());
    }

    @Test
    void send_unknownDestination_error_andNothingCalled() {
        login();
        protocol.onText(phone, "{\"type\":\"send\",\"destination\":\"/topic/conversations/1\",\"body\":{}}");

        assertThat(phone.got).last().asString().contains("Unknown destination");
        verify(typing, never()).typing(anyLong(), any(), any());
    }

    @Test
    void authOk_tellsPresence_withAPlainDoorId() {
        login();

        verify(connections).connected(5L, "rt:line-1");
    }

    @Test
    void badToken_neverTellsPresence() {
        protocol.onText(phone, "{\"type\":\"auth\",\"token\":\"expired\"}");
        protocol.onClose("line-1");

        verify(connections, never()).connected(anyLong(), anyString());
        verify(connections, never()).disconnected(anyString(), any());
    }

    @Test
    void close_ofALoggedInLine_tellsPresence_once() {
        login();
        protocol.onClose("line-1");
        protocol.onClose("line-1");               // a second close callback: ignored

        verify(connections).disconnected("rt:line-1", UserConnections.Drop.CLOSED);
    }

    @Test
    void phoneClosesTheSocket_isCLOSED_appGone() {
        login();
        protocol.onClose("line-1");               // the OS closed a killed app's socket

        verify(connections).disconnected("rt:line-1", UserConnections.Drop.CLOSED);
    }

    @Test
    void sweepClosesASilentLine_isSILENT_maybeOnlyTheNetwork() {
        login();
        clock.plus(Duration.ofSeconds(26));       // no letters: lift / Wi-Fi gone
        protocol.sweep();                         // closes it…
        protocol.onClose("line-1");               // …and Spring calls back

        verify(connections).disconnected("rt:line-1", UserConnections.Drop.SILENT);
    }
}
