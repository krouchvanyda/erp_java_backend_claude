package com.company.erp.features.chats.service;

import com.company.erp.features.chats.entity.Conversation;
import com.company.erp.features.chats.entity.ConversationMember;
import com.company.erp.features.chats.entity.ConversationMemberId;
import com.company.erp.features.chats.entity.ConversationType;
import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.entity.MessageType;
import com.company.erp.features.chats.repository.ConversationMemberRepository;
import com.company.erp.features.chats.repository.ConversationRepository;
import com.company.erp.features.devices.entity.Device;
import com.company.erp.features.devices.service.DeviceService;
import com.company.erp.features.devices.service.FcmService;
import com.company.erp.features.users.entity.User;
import com.company.erp.features.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatMessageNotifierTest {

    private static final long CONV = 12L, BIN = 1L, VIN = 2L, SOK = 3L;

    private ConversationRepository conversations;
    private ConversationMemberRepository members;
    private UserRepository users;
    private DeviceService devices;
    private FcmService fcm;
    private ChatMessageNotifier notifier;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        members = mock(ConversationMemberRepository.class);
        users = mock(UserRepository.class);
        devices = mock(DeviceService.class);
        fcm = mock(FcmService.class);
        notifier = new ChatMessageNotifier(conversations, members, users, devices, fcm);

        when(fcm.isReady()).thenReturn(true);
        when(users.findById(BIN)).thenReturn(Optional.of(user("Mr Bin")));
        when(devices.listForUsers(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(id -> device("token-" + id)).toList();
        });
    }

    @Test
    void directMessage_pushesToTheOtherMember_notTheSender() {
        when(conversations.findById(CONV)).thenReturn(Optional.of(conversation(ConversationType.DIRECT, null)));
        when(members.findByConversation_Id(CONV)).thenReturn(List.of(member(BIN, false), member(VIN, false)));

        notifier.notifyNewMessage(text("Hello Vin"));

        Map<String, String> data = capturePush(List.of("token-" + VIN));
        assertThat(data).containsEntry("type", "chat")
                .containsEntry("conversationId", "12")
                .containsEntry("messageId", "345")
                .containsEntry("senderId", "1")
                .containsEntry("isGroup", "false")
                .containsEntry("title", "Mr Bin")
                .containsEntry("body", "Hello Vin");
        verify(devices).listForUsers(List.of(VIN));
    }

    @Test
    void groupMessage_titleIsGroupName_bodyPrefixedWithSender_mutedSkipped() {
        when(conversations.findById(CONV)).thenReturn(Optional.of(conversation(ConversationType.GROUP, "Team A")));
        when(members.findByConversation_Id(CONV))
                .thenReturn(List.of(member(BIN, false), member(VIN, false), member(SOK, true)));

        notifier.notifyNewMessage(text("Meeting at 3"));

        Map<String, String> data = capturePush(List.of("token-" + VIN));
        assertThat(data).containsEntry("isGroup", "true")
                .containsEntry("title", "Team A")
                .containsEntry("body", "Mr Bin: Meeting at 3");
        verify(devices).listForUsers(List.of(VIN)); // SOK muted → not targeted
    }

    @Test
    void mediaMessages_useLabels_andLongTextIsCapped() {
        Message img = text(null);
        img.setType(MessageType.IMAGE);
        assertThat(ChatMessageNotifier.preview(img)).isEqualTo("📷 Photo");
        img.setType(MessageType.VOICE);
        assertThat(ChatMessageNotifier.preview(img)).isEqualTo("🎤 Voice message");
        img.setType(MessageType.FILE);
        assertThat(ChatMessageNotifier.preview(img)).isEqualTo("📎 File");

        String longText = "x".repeat(ChatMessageNotifier.MAX_BODY_CHARS + 50);
        assertThat(ChatMessageNotifier.preview(text(longText)))
                .hasSize(ChatMessageNotifier.MAX_BODY_CHARS + 1)
                .endsWith("…");
    }

    @Test
    void noPush_whenFcmDisabled() {
        when(fcm.isReady()).thenReturn(false);

        notifier.notifyNewMessage(text("Hello"));

        verifyNoInteractions(members, devices, conversations, users);
        verify(fcm, never()).sendDataToTokens(anyList(), any());
    }

    @Test
    void noPush_whenEveryoneElseMuted() {
        when(members.findByConversation_Id(CONV)).thenReturn(List.of(member(BIN, false), member(VIN, true)));

        notifier.notifyNewMessage(text("Hello"));

        verify(fcm, never()).sendDataToTokens(anyList(), any());
    }

    // ---- helpers ---------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, String> capturePush(List<String> expectedTokens) {
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(fcm).sendDataToTokens(tokens.capture(), data.capture());
        assertThat(tokens.getValue()).containsExactlyElementsOf(expectedTokens);
        return data.getValue();
    }

    private static Message text(String body) {
        Message m = new Message();
        m.setId(345L);
        m.setConversationId(CONV);
        m.setSenderId(BIN);
        m.setType(MessageType.TEXT);
        m.setBody(body);
        return m;
    }

    private static ConversationMember member(long userId, boolean muted) {
        ConversationMember m = new ConversationMember();
        m.setId(new ConversationMemberId(CONV, userId));
        m.setMuted(muted);
        return m;
    }

    private static Conversation conversation(ConversationType type, String name) {
        Conversation c = new Conversation();
        c.setId(CONV);
        c.setType(type);
        c.setName(name);
        return c;
    }

    private static User user(String fullName) {
        User u = new User();
        u.setFullName(fullName);
        return u;
    }

    private static Device device(String token) {
        Device d = new Device();
        d.setFcmToken(token);
        return d;
    }
}
