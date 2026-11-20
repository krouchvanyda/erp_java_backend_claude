package com.company.erp.features.chats.ws;

import com.company.erp.features.chats.dto.TypingRequest;
import com.company.erp.features.chats.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * "X is typing…" over STOMP.
 *
 * <pre>
 *   client SEND /app/conversations/42/typing   { "typing": true }
 *   server  →   /topic/conversations/42        { "event": "typing",
 *                                                "payload": { conversationId, userId, typing } }
 * </pre>
 *
 * <ul>
 *   <li><b>Who</b> is typing comes from the STOMP session principal (bound from
 *       the access token on CONNECT by {@link StompAuthChannelInterceptor}),
 *       never from the payload — it can't be faked.</li>
 *   <li>Only <b>members</b> of the conversation may send it.</li>
 *   <li>Fire-and-forget: anonymous sessions and non-members are silently
 *       ignored (no ERROR frame, which would tear the client's socket down
 *       for a cosmetic feature).</li>
 *   <li>Nothing is stored. Clients expire the indicator after a few seconds
 *       without a refresh, so a lost {@code typing:false} can't stick.</li>
 * </ul>
 */
@Controller
public class TypingController {

    private static final Logger log = LoggerFactory.getLogger(TypingController.class);

    private final ConversationService conversations;
    private final ChatBroadcaster broadcaster;

    public TypingController(ConversationService conversations, ChatBroadcaster broadcaster) {
        this.conversations = conversations;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable Long conversationId,
                       @Payload(required = false) TypingRequest body,
                       Principal principal) {
        if (principal == null) return; // CONNECT had no valid token
        final Long userId;
        try {
            userId = Long.valueOf(principal.getName());
        } catch (NumberFormatException ex) {
            return;
        }
        try {
            conversations.requireMember(conversationId, userId);
        } catch (RuntimeException notMember) {
            log.debug("typing ignored: user {} is not in conversation {}", userId, conversationId);
            return;
        }
        boolean typing = body == null || body.typing();
        broadcaster.toConversation(conversationId, "typing", Map.of(
                "conversationId", conversationId,
                "userId", userId,
                "typing", typing));
    }
}
