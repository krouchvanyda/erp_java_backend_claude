package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import com.company.erp.features.chats.entity.CallStatus;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.ChatCallParticipant;
import com.company.erp.features.chats.entity.ChatCallParticipantId;
import com.company.erp.features.chats.entity.ParticipantStatus;
import com.company.erp.features.chats.presence.PresenceService;
import com.company.erp.features.chats.repository.ChatCallParticipantRepository;
import com.company.erp.features.chats.repository.ChatCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A user's app dies DURING a call (ANSWERED): after the grace, the server
 * hangs up for them — otherwise the call stays open forever and they get
 * "Caller is already in an active call" on their next call.
 */
class CallDisconnectHandlerAnsweredTest {

    private static final long CALL = 7L, CONV = 12L, CALLER = 1L, CALLEE = 2L;

    private ChatCallRepository calls;
    private ChatCallParticipantRepository participants;
    private PresenceService presence;
    private CallEndNotifier notifier;
    private ChatCallService service;
    private CallDisconnectHandler handler;
    private ChatCall call;

    @BeforeEach
    void setUp() {
        calls = mock(ChatCallRepository.class);
        participants = mock(ChatCallParticipantRepository.class);
        presence = mock(PresenceService.class);
        notifier = mock(CallEndNotifier.class);
        AppProperties props = mock(AppProperties.class);
        service = new ChatCallService(calls, participants,
                mock(ConversationService.class), presence,
                mock(StreamTokenService.class), mock(StreamVideoService.class), props);
        handler = new CallDisconnectHandler(presence, service, notifier, props);

        call = new ChatCall();
        call.setId(CALL);
        call.setCallerId(CALLER);
        call.setConversationId(CONV);
        call.setStatus(CallStatus.ANSWERED);
        call.setParticipants(new HashSet<>(List.of(
                participant(CALLER), participant(CALLEE))));
        when(calls.findWithParticipantsById(CALL)).thenReturn(Optional.of(call));
        for (ChatCallParticipant p : call.getParticipants()) {
            when(participants.findByCall_IdAndId_UserId(CALL, p.getUserId()))
                    .thenReturn(Optional.of(p));
        }
    }

    private ChatCallParticipant participant(long userId) {
        ChatCallParticipant p = new ChatCallParticipant();
        p.setId(new ChatCallParticipantId(CALL, userId));
        p.setStatus(ParticipantStatus.ANSWERED);
        return p;
    }

    @Test
    void callerGone_endsTheCallForEveryone_andTellsThem() {
        when(presence.hasLiveSession(CALLER)).thenReturn(false);
        when(calls.findAnsweredForUser(CALLER)).thenReturn(List.of(call));

        handler.resolveAnswered(CALLER);

        assertThat(call.getStatus()).isEqualTo(CallStatus.ENDED);
        verify(notifier).notifyDisconnectedHangup(call, CALLER);
    }

    @Test
    void lastCalleeGone_endsTheCall() {
        when(presence.hasLiveSession(CALLEE)).thenReturn(false);
        when(calls.findAnsweredForUser(CALLEE)).thenReturn(List.of(call));

        handler.resolveAnswered(CALLEE);

        assertThat(call.getStatus()).isEqualTo(CallStatus.ENDED);
        verify(notifier).notifyDisconnectedHangup(call, CALLEE);
    }

    @Test
    void reconnectedWithinGrace_keepsTheCall() {
        when(presence.hasLiveSession(CALLER)).thenReturn(true);

        handler.resolveAnswered(CALLER);

        assertThat(call.getStatus()).isEqualTo(CallStatus.ANSWERED);
        verify(calls, never()).findAnsweredForUser(any());
        verify(notifier, never()).notifyDisconnectedHangup(any(), eq(CALLER));
    }

    @Test
    void notInACall_doesNothing() {
        when(presence.hasLiveSession(CALLEE)).thenReturn(false);
        when(calls.findAnsweredForUser(CALLEE)).thenReturn(List.of());

        handler.resolveAnswered(CALLEE);

        verify(notifier, never()).notifyDisconnectedHangup(any(), any());
    }
}
