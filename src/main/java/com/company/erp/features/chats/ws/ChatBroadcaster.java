package com.company.erp.features.chats.ws;

import com.company.erp.features.chats.dto.ChatEvent;
import com.company.erp.features.chats.realtime.RealtimeBroadcast;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * The ONE place that sends live letters. Services call it and never touch the
 * messaging plumbing. Destinations follow the contract documented in
 * {@link WebSocketConfig}.
 *
 * <p><b>Two doors</b> (Track B, branch {@code feature/raw-websocket}): every
 * letter goes to the STOMP broker ({@code /ws}) AND to the plain-WebSocket door
 * ({@code /realtime}, {@link RealtimeBroadcast}), with the SAME address. A
 * phone uses one door or the other, so nobody gets a letter twice.</p>
 */
@Component
public class ChatBroadcaster {

    private final SimpMessagingTemplate template;
    private final RealtimeBroadcast realtime;

    public ChatBroadcaster(SimpMessagingTemplate template, RealtimeBroadcast realtime) {
        this.template = template;
        this.realtime = realtime;
    }

    /** Public conversation topic — anyone subscribed sees this. */
    public void toConversation(Long conversationId, String event, Object payload) {
        toTopic("/topic/conversations/" + conversationId, event, payload);
    }

    /** Per-call topic for ringing → answered → ended state transitions. */
    public void toCall(Long conversationId, String event, Object payload) {
        toTopic("/topic/conversations/" + conversationId + "/call", event, payload);
    }

    /** Online / busy / offline of one user, for everyone (Step 5.6). */
    public void toPresence(String event, Object payload) {
        toTopic("/topic/presence", event, payload);
    }

    /** Private fan-out to a single user — {@code /user/queue/<destination>}. */
    public void toUser(Long userId, String destination, String event, Object payload) {
        template.convertAndSendToUser(String.valueOf(userId), "/queue/" + destination,
                ChatEvent.of(event, payload));
        realtime.toUser(userId, "/queue/" + destination, event, payload);
    }

    /** Convenience: notify every user in a collection on a private destination. */
    public void toUsers(Collection<Long> userIds, String destination, String event, Object payload) {
        for (Long id : userIds) toUser(id, destination, event, payload);
    }

    private void toTopic(String destination, String event, Object payload) {
        template.convertAndSend(destination, ChatEvent.of(event, payload));
        realtime.toTopic(destination, event, payload);
    }
}
