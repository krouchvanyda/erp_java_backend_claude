package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import com.company.erp.features.chats.dto.StartCallRequest;
import com.company.erp.features.chats.entity.CallType;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.presence.PresenceService;
import com.company.erp.features.chats.presence.PresenceStatus;
import com.company.erp.features.chats.repository.ChatCallParticipantRepository;
import com.company.erp.features.chats.repository.ChatCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Starting a call: every participant must be a Stream member, otherwise the
 * callee's accept fails with "Only members can reject or accept a call" and
 * the voice never connects.
 */
class ChatCallServiceStreamMembersTest {

    private static final long CONV = 12L, CALL = 22L, VIN = 3L, BIN = 2L;
    private static final String CID = "default:erp-call-22";

    private ChatCallRepository calls;
    private ConversationService conversations;
    private PresenceService presence;
    private StreamVideoService streamVideo;
    private ChatCallService service;

    @BeforeEach
    void setUp() {
        calls = mock(ChatCallRepository.class);
        conversations = mock(ConversationService.class);
        presence = mock(PresenceService.class);
        streamVideo = mock(StreamVideoService.class);
        StreamTokenService tokens = mock(StreamTokenService.class);
        service = new ChatCallService(calls, mock(ChatCallParticipantRepository.class),
                conversations, presence, tokens, streamVideo, mock(AppProperties.class));

        when(conversations.memberUserIds(CONV)).thenReturn(new LinkedHashSet<>(List.of(VIN, BIN)));
        when(calls.existsActiveCallForUser(anyLong(), any(), any())).thenReturn(false);
        when(calls.save(any())).thenAnswer(inv -> {
            ChatCall c = inv.getArgument(0);
            c.setId(CALL);
            return c;
        });
        when(tokens.cidForCall(CALL)).thenReturn(CID);
        when(calls.findWithParticipantsById(CALL)).thenAnswer(inv -> Optional.of(new ChatCall()));
    }

    @Test
    void calleeOnline_streamCallCreatedWithEveryMember_noRing() {
        when(presence.statusOf(BIN)).thenReturn(PresenceStatus.ONLINE);

        service.start(CONV, VIN, new StartCallRequest(CallType.VOICE));

        verify(streamVideo).createWithMembers(eq(CID), eq(VIN), eq(Set.of(VIN, BIN)));
        verify(streamVideo, never()).ring(any(), any(), anyCollection(), anyBoolean());
    }

    @Test
    void calleeOffline_ringPathUnchanged() {
        when(presence.statusOf(BIN)).thenReturn(PresenceStatus.OFFLINE);

        service.start(CONV, VIN, new StartCallRequest(CallType.VOICE));

        verify(streamVideo).ring(eq(CID), eq(VIN), eq(new LinkedHashSet<>(List.of(BIN, VIN))), eq(false));
        verify(streamVideo, never()).createWithMembers(any(), any(), anyCollection());
    }
}
