package com.company.erp.features.chats.service;

import com.company.erp.features.chats.dto.MessageDto;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.Conversation;
import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.entity.MessageType;
import com.company.erp.features.chats.repository.ConversationRepository;
import com.company.erp.features.chats.repository.MessageRepository;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves the call line in the chat when a call ends ("📞 Voice call · 2:31",
 * "Missed call", "Call declined"), and sends it live like any message.
 *
 * <p>A normal {@link Message} with {@code type = CALL}, so it lives in the
 * history, becomes the inbox preview, counts as unread for the callees, and
 * reaches every member over STOMP ({@code message.send} on the conversation
 * topic + each inbox) — the same fan-out as {@code MessageController.send}.</p>
 *
 * <ul>
 *   <li>sender = the caller (so "mine" = "I called")</li>
 *   <li>{@code durationSeconds} = talk time (0 when never answered)</li>
 *   <li>{@code body} = JSON {@code {callId, callType, status, endReason, answered}};
 *       the apps turn it into words (excluded from message search).</li>
 * </ul>
 *
 * <p>Best effort: a failure here is logged and never breaks ending the call.</p>
 */
@Component
public class CallMessageRecorder {

    private static final Logger log = LoggerFactory.getLogger(CallMessageRecorder.class);

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationService conversationService;
    private final ChatBroadcaster broadcaster;
    private final ObjectMapper json;

    public CallMessageRecorder(MessageRepository messages,
                               ConversationRepository conversations,
                               ConversationService conversationService,
                               ChatBroadcaster broadcaster,
                               ObjectMapper json) {
        this.messages = messages;
        this.conversations = conversations;
        this.conversationService = conversationService;
        this.broadcaster = broadcaster;
        this.json = json;
    }

    public void record(ChatCall c) {
        try {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("callId", c.getId());
            info.put("callType", c.getType().name());
            info.put("status", c.getStatus().name());
            info.put("endReason", c.getEndReason());
            info.put("answered", c.getAnsweredAt() != null);

            Message m = new Message();
            m.setConversationId(c.getConversationId());
            m.setSenderId(c.getCallerId());
            m.setType(MessageType.CALL);
            m.setBody(json.writeValueAsString(info));
            m.setDurationSeconds(c.getDurationSeconds());
            messages.save(m);

            Conversation conv = conversations.findById(c.getConversationId()).orElseThrow();
            conv.setLastMessageId(m.getId());
            conv.setLastMessageAt(m.getCreatedAt());

            MessageDto dto = MessageDto.from(m, List.of());
            broadcaster.toConversation(c.getConversationId(), "message.send", dto);
            broadcaster.toUsers(conversationService.memberUserIds(c.getConversationId()),
                    "inbox", "message.send", dto);
            log.info("[call] call line saved callId={} messageId={} status={}",
                    c.getId(), m.getId(), c.getStatus());
        } catch (Exception ex) {
            log.warn("[call] could not save the call line for callId={}: {}",
                    c.getId(), ex.getMessage());
        }
    }
}
