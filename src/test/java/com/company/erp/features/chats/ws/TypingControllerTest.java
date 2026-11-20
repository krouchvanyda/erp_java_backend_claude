package com.company.erp.features.chats.ws;

import com.company.erp.core.exceptions.ForbiddenException;
import com.company.erp.features.chats.dto.TypingRequest;
import com.company.erp.features.chats.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link TypingController} — who may say "typing…", and what goes out. */
class TypingControllerTest {

    private static final long CONV = 42L, MEMBER = 7L, STRANGER = 9L;

    private ConversationService conversations;
    private ChatBroadcaster broadcaster;
    private TypingController controller;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationService.class);
        broadcaster = mock(ChatBroadcaster.class);
        controller = new TypingController(conversations, broadcaster);
        when(conversations.requireMember(CONV, STRANGER))
                .thenThrow(new ForbiddenException("Not a member of this conversation"));
    }

    private static Principal user(long id) {
        return () -> String.valueOf(id);
    }

    @Test
    void member_typing_isBroadcastWithTheSessionUserId() {
        controller.typing(CONV, new TypingRequest(true), user(MEMBER));

        verify(broadcaster).toConversation(CONV, "typing",
                Map.of("conversationId", CONV, "userId", MEMBER, "typing", true));
    }

    @Test
    void stopTyping_isBroadcastToo() {
        controller.typing(CONV, new TypingRequest(false), user(MEMBER));

        verify(broadcaster).toConversation(CONV, "typing",
                Map.of("conversationId", CONV, "userId", MEMBER, "typing", false));
    }

    @Test
    void nonMember_isIgnored() {
        controller.typing(CONV, new TypingRequest(true), user(STRANGER));

        verify(broadcaster, never()).toConversation(anyLong(), anyString(), any());
    }

    @Test
    void anonymousSession_isIgnored() {
        controller.typing(CONV, new TypingRequest(true), null);

        verify(broadcaster, never()).toConversation(anyLong(), anyString(), any());
    }
}
