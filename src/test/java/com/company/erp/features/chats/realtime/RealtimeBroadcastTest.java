package com.company.erp.features.chats.realtime;

import com.company.erp.features.chats.dto.ChatEvent;
import com.company.erp.features.chats.realtime.RealtimeSessionsTest.FakeClient;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** R3: {@link RealtimeBroadcast} (the plain door's postman) + {@link ChatBroadcaster} on BOTH doors. */
class RealtimeBroadcastTest {

    private RealtimeSessions sessions;
    private SimpMessagingTemplate stomp;
    private ChatBroadcaster broadcaster;
    private FakeClient bin, lin;

    @BeforeEach
    void setUp() {
        sessions = new RealtimeSessions();
        stomp = mock(SimpMessagingTemplate.class);
        broadcaster = new ChatBroadcaster(stomp, new RealtimeBroadcast(sessions, new ObjectMapper()));
        bin = new FakeClient("a");
        lin = new FakeClient("c");
        sessions.open(bin);
        sessions.open(lin);
        sessions.authenticate("a", 5);
        sessions.authenticate("c", 7);
    }

    @Test
    void toConversation_reachesStomp_andThePlainDoor_withTheSameAddress() {
        sessions.subscribe("a", "/topic/conversations/1");
        sessions.subscribe("c", "/topic/conversations/1");

        broadcaster.toConversation(1L, "message.send", Map.of("id", 264, "body", "Hello"));

        verify(stomp).convertAndSend(eq("/topic/conversations/1"),
                eq(ChatEvent.of("message.send", Map.of("id", 264, "body", "Hello"))));
        String expected = "{\"type\":\"message\",\"destination\":\"/topic/conversations/1\","
                + "\"event\":\"message.send\",\"payload\":";
        assertThat(bin.got).singleElement().asString().startsWith(expected).contains("\"body\":\"Hello\"");
        assertThat(lin.got).isEqualTo(bin.got);              // the same text for everyone
    }

    @Test
    void toUser_isPrivate_onBothDoors() {
        sessions.subscribe("a", "/user/queue/inbox");
        sessions.subscribe("c", "/user/queue/inbox");

        broadcaster.toUsers(List.of(5L), "inbox", "message.send", Map.of("id", 1));

        verify(stomp).convertAndSendToUser(eq("5"), eq("/queue/inbox"),
                eq(ChatEvent.of("message.send", Map.of("id", 1))));
        assertThat(bin.got).singleElement().asString()
                .contains("\"destination\":\"/user/queue/inbox\"")   // the address the phone subscribed to
                .contains("\"event\":\"message.send\"");
        assertThat(lin.got).isEmpty();                              // user 7 never gets user 5's letter
    }

    @Test
    void toCall_and_toPresence_reachTheirTopics() {
        sessions.subscribe("a", "/topic/conversations/1/call");
        sessions.subscribe("c", "/topic/presence");

        broadcaster.toCall(1L, "call.invite", Map.of("id", 9));
        broadcaster.toPresence("presence.update", Map.of("userId", 5, "status", "ONLINE"));

        assertThat(bin.got).singleElement().asString().contains("\"event\":\"call.invite\"");
        assertThat(lin.got).singleElement().asString().contains("\"event\":\"presence.update\"");
        verify(stomp).convertAndSend(eq("/topic/presence"),
                eq(ChatEvent.of("presence.update", Map.of("userId", 5, "status", "ONLINE"))));
    }

    @Test
    void nobodyOnThePlainDoor_stillGoesToStomp() {
        broadcaster.toConversation(2L, "typing", Map.of("typing", true));

        verify(stomp).convertAndSend(eq("/topic/conversations/2"),
                eq(ChatEvent.of("typing", Map.of("typing", true))));
        assertThat(bin.got).isEmpty();
    }
}
