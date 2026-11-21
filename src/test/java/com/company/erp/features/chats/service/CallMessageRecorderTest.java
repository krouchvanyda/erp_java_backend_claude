package com.company.erp.features.chats.service;

import com.company.erp.features.chats.dto.MessageDto;
import com.company.erp.features.chats.entity.CallStatus;
import com.company.erp.features.chats.entity.CallType;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.Conversation;
import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.entity.MessageType;
import com.company.erp.features.chats.repository.ConversationRepository;
import com.company.erp.features.chats.repository.MessageRepository;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The call line in the chat, saved when a call ends (Step 6.3d). */
class CallMessageRecorderTest {

    private static final long CALL = 46L, CONV = 3L, MIN = 4L, RIN = 5L;

    private MessageRepository messages;
    private ChatBroadcaster broadcaster;
    private Conversation conversation;
    private CallMessageRecorder recorder;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        broadcaster = mock(ChatBroadcaster.class);
        conversation = new Conversation();
        when(conversations.findById(CONV)).thenReturn(Optional.of(conversation));
        when(conversationService.memberUserIds(CONV)).thenReturn(Set.of(MIN, RIN));
        when(messages.save(any())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(99L);
            return m;
        });
        recorder = new CallMessageRecorder(messages, conversations, conversationService,
                broadcaster, json);
    }

    private ChatCall call(CallStatus status, boolean answered, int seconds, String reason) {
        ChatCall c = new ChatCall();
        c.setId(CALL);
        c.setConversationId(CONV);
        c.setCallerId(MIN);
        c.setType(CallType.VOICE);
        c.setStatus(status);
        c.setEndReason(reason);
        c.setDurationSeconds(seconds);
        if (answered) c.setAnsweredAt(Instant.now().minusSeconds(seconds));
        return c;
    }

    @Test
    void answeredCall_savesCallMessage_fromTheCaller_withDuration() throws Exception {
        recorder.record(call(CallStatus.ENDED, true, 151, "caller_left"));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(saved.capture());
        Message m = saved.getValue();
        assertThat(m.getType()).isEqualTo(MessageType.CALL);
        assertThat(m.getSenderId()).isEqualTo(MIN);
        assertThat(m.getConversationId()).isEqualTo(CONV);
        assertThat(m.getDurationSeconds()).isEqualTo(151);

        JsonNode body = json.readTree(m.getBody());
        assertThat(body.get("callId").asLong()).isEqualTo(CALL);
        assertThat(body.get("callType").asText()).isEqualTo("VOICE");
        assertThat(body.get("status").asText()).isEqualTo("ENDED");
        assertThat(body.get("answered").asBoolean()).isTrue();

        assertThat(conversation.getLastMessageId()).isEqualTo(99L); // inbox preview
    }

    @Test
    void missedCall_body_saysNotAnswered() throws Exception {
        recorder.record(call(CallStatus.MISSED, false, 0, "no_answer"));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(saved.capture());
        JsonNode body = json.readTree(saved.getValue().getBody());
        assertThat(body.get("status").asText()).isEqualTo("MISSED");
        assertThat(body.get("endReason").asText()).isEqualTo("no_answer");
        assertThat(body.get("answered").asBoolean()).isFalse();
    }

    @Test
    void sentLive_toTheChat_andEveryInbox() {
        recorder.record(call(CallStatus.REJECTED, false, 0, "busy"));

        verify(broadcaster).toConversation(eq(CONV), eq("message.send"), any(MessageDto.class));
        verify(broadcaster).toUsers(eq(Set.of(MIN, RIN)), eq("inbox"), eq("message.send"),
                any(MessageDto.class));
    }

    @Test
    void aFailure_neverBreaksEndingTheCall() {
        doThrow(new RuntimeException("db down")).when(messages).save(any());

        recorder.record(call(CallStatus.ENDED, true, 5, "caller_left")); // no exception
    }
}
