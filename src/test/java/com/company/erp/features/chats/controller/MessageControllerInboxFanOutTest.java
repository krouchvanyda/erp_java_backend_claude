package com.company.erp.features.chats.controller;

import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.features.chats.dto.EditMessageRequest;
import com.company.erp.features.chats.dto.MessageDto;
import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.repository.ConversationMemberRepository;
import com.company.erp.features.chats.service.ChatMessageNotifier;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.service.MessageService;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Edit / delete must reach every member's INBOX too (not only the
 * conversation topic), so an inbox preview of the last message updates live.
 */
class MessageControllerInboxFanOutTest {

    private static final long ME = 1L, OTHER = 2L, CONV = 5L, MSG = 9L;

    private MessageService messages;
    private ConversationService conversations;
    private ChatBroadcaster broadcaster;
    private MessageController controller;

    @BeforeEach
    void setUp() {
        messages = mock(MessageService.class);
        conversations = mock(ConversationService.class);
        broadcaster = mock(ChatBroadcaster.class);
        controller = new MessageController(messages, conversations,
                mock(ConversationMemberRepository.class), broadcaster,
                mock(ChatMessageNotifier.class));

        var me = new AuthenticatedUser(ME, "me@x.com", Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me, null, List.of()));

        when(conversations.memberUserIds(CONV)).thenReturn(Set.of(ME, OTHER));
        when(messages.reactionsFor(MSG)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Message message() {
        Message m = new Message();
        m.setId(MSG);
        m.setConversationId(CONV);
        m.setSenderId(ME);
        m.setBody("Hi Bin");
        return m;
    }

    @Test
    void edit_goesToTheTopic_andEveryInbox() {
        when(messages.edit(eq(MSG), eq(ME), any())).thenReturn(message());

        controller.edit(MSG, new EditMessageRequest("Hi Bin"));

        verify(broadcaster).toConversation(eq(CONV), eq("message.edit"), any(MessageDto.class));
        verify(broadcaster).toUsers(eq(Set.of(ME, OTHER)), eq("inbox"),
                eq("message.edit"), any(MessageDto.class));
    }

    @Test
    void delete_goesToTheTopic_andEveryInbox() {
        when(messages.delete(MSG, ME)).thenReturn(message());

        controller.delete(MSG);

        verify(broadcaster).toConversation(eq(CONV), eq("message.delete"), any(MessageDto.class));
        verify(broadcaster).toUsers(eq(Set.of(ME, OTHER)), eq("inbox"),
                eq("message.delete"), any(MessageDto.class));
    }
}
