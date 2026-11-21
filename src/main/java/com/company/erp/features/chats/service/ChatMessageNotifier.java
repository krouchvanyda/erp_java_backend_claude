package com.company.erp.features.chats.service;

import com.company.erp.features.chats.entity.Conversation;
import com.company.erp.features.chats.entity.ConversationMember;
import com.company.erp.features.chats.entity.ConversationType;
import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.repository.ConversationMemberRepository;
import com.company.erp.features.chats.repository.ConversationRepository;
import com.company.erp.features.devices.entity.Device;
import com.company.erp.features.devices.service.DeviceService;
import com.company.erp.features.devices.service.FcmService;
import com.company.erp.features.users.entity.User;
import com.company.erp.features.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FCM push for a newly sent chat message — the chat counterpart of
 * {@link CallEndNotifier}.
 *
 * <p>STOMP already delivers the message to members whose app is open; this
 * push covers members whose app is minimised or killed (socket gone) and
 * lets the mobile app show a banner. It is a <b>data-only</b> message, like
 * the call pushes, so the app renders it itself (tap → opens the
 * conversation; no banner while the user is already viewing that chat).
 *
 * <p>Recipients: every member except the sender and members who muted the
 * conversation. Payload:
 * <pre>{ type: "chat", conversationId, messageId, senderId, senderName,
 *   isGroup, title, body }</pre>
 * Title is the sender (direct) or the group name (group, body prefixed with
 * the sender). Media messages use a short label instead of the body.
 */
@Component
public class ChatMessageNotifier {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageNotifier.class);

    /** Keep the push payload small (FCM data limit is 4 KB in total). */
    static final int MAX_BODY_CHARS = 200;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final UserRepository users;
    private final DeviceService devices;
    private final FcmService fcm;

    public ChatMessageNotifier(ConversationRepository conversations,
                               ConversationMemberRepository members,
                               UserRepository users,
                               DeviceService devices,
                               FcmService fcm) {
        this.conversations = conversations;
        this.members = members;
        this.users = users;
        this.devices = devices;
        this.fcm = fcm;
    }

    public void notifyNewMessage(Message m) {
        // Cheap early exit: no FCM credentials (FCM_ENABLED=false) → no DB work.
        if (!fcm.isReady()) return;

        Long convId = m.getConversationId();
        List<Long> targetIds = members.findByConversation_Id(convId).stream()
                .filter(member -> !member.isMuted())
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(m.getSenderId()))
                .toList();
        if (targetIds.isEmpty()) return;

        List<String> tokens = devices.listForUsers(targetIds).stream()
                .map(Device::getFcmToken)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        if (tokens.isEmpty()) {
            log.debug("[fcm] chat msg={} → users={} have no registered device", m.getId(), targetIds);
            return;
        }

        Conversation c = conversations.findById(convId).orElse(null);
        boolean isGroup = c != null && c.getType() == ConversationType.GROUP;
        String senderName = users.findById(m.getSenderId())
                .map(User::getFullName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("New message");
        String preview = preview(m);

        String title;
        String body;
        if (isGroup) {
            String groupName = c.getName();
            title = groupName == null || groupName.isBlank() ? senderName : groupName;
            body = senderName + ": " + preview;
        } else {
            title = senderName;
            body = preview;
        }

        Map<String, String> data = new HashMap<>();
        data.put("type",           "chat");
        data.put("conversationId", String.valueOf(convId));
        data.put("messageId",      String.valueOf(m.getId()));
        data.put("senderId",       String.valueOf(m.getSenderId()));
        data.put("senderName",     senderName);
        data.put("isGroup",        String.valueOf(isGroup));
        data.put("title",          title);
        data.put("body",           body);

        log.info("[fcm] chat msg={} conv={} → users={} tokens={}",
                m.getId(), convId, targetIds, tokens.size());
        fcm.sendDataToTokens(tokens, data);
    }

    /** Text body (trimmed + capped), or a label for media messages. */
    static String preview(Message m) {
        if (m.getType() == null) return "New message";
        return switch (m.getType()) {
            case IMAGE -> "📷 Photo";
            case VOICE -> "🎤 Voice message";
            case FILE -> "📎 File";
            case CALL -> "📞 Call";
            case TEXT -> {
                String b = m.getBody() == null ? "" : m.getBody().trim();
                if (b.isEmpty()) yield "New message";
                yield b.length() > MAX_BODY_CHARS ? b.substring(0, MAX_BODY_CHARS) + "…" : b;
            }
        };
    }
}
