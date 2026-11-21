package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import com.company.erp.features.chats.dto.StartCallRequest;
import com.company.erp.features.chats.entity.CallType;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.ChatCallParticipant;
import com.company.erp.features.chats.entity.ParticipantStatus;
import com.company.erp.features.chats.presence.PresenceService;
import com.company.erp.features.chats.presence.PresenceStatus;
import com.company.erp.features.chats.repository.ChatCallParticipantRepository;
import com.company.erp.features.chats.repository.ChatCallRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The call returned by {@link ChatCallService#start} must carry its
 * participants: the controller sends {@code call.invite} to each of them.
 * Bug (Nov 20 2026): the reload returned the SAME managed object (Hibernate's
 * first-level cache) with an empty set → the invite reached nobody and the
 * callee never rang ("participants": [] in the response).
 */
class ChatCallServiceStartParticipantsTest {

    private static final long CONV = 3L, CALL = 46L, MIN = 4L, RIN = 5L;

    @Test
    void start_returnsCallerAnsweredAndCalleeRinging() {
        ChatCallRepository calls = mock(ChatCallRepository.class);
        ConversationService conversations = mock(ConversationService.class);
        PresenceService presence = mock(PresenceService.class);
        StreamTokenService tokens = mock(StreamTokenService.class);
        ChatCallService service = new ChatCallService(calls,
                mock(ChatCallParticipantRepository.class), conversations, presence,
                tokens, mock(StreamVideoService.class), mock(AppProperties.class), mock(CallMessageRecorder.class));

        when(conversations.memberUserIds(CONV)).thenReturn(new LinkedHashSet<>(List.of(MIN, RIN)));
        when(calls.existsActiveCallForUser(anyLong(), any(), any())).thenReturn(false);
        when(presence.statusOf(RIN)).thenReturn(PresenceStatus.ONLINE);
        when(tokens.cidForCall(CALL)).thenReturn("default:erp-call-46");
        // Like Hibernate inside one transaction: save + reload = the SAME object.
        AtomicReference<ChatCall> saved = new AtomicReference<>();
        when(calls.save(any())).thenAnswer(inv -> {
            ChatCall c = inv.getArgument(0);
            c.setId(CALL);
            saved.set(c);
            return c;
        });
        when(calls.findWithParticipantsById(CALL)).thenAnswer(inv -> Optional.of(saved.get()));

        ChatCall call = service.start(CONV, MIN, new StartCallRequest(CallType.VOICE));

        Map<Long, ParticipantStatus> byUser = call.getParticipants().stream()
                .collect(Collectors.toMap(ChatCallParticipant::getUserId,
                        ChatCallParticipant::getStatus));
        assertThat(byUser).containsExactlyInAnyOrderEntriesOf(Map.of(
                MIN, ParticipantStatus.ANSWERED,
                RIN, ParticipantStatus.RINGING));
    }
}
