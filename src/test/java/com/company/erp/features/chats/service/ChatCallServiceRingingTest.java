package com.company.erp.features.chats.service;

import com.company.erp.core.config.AppProperties;
import com.company.erp.features.chats.entity.CallStatus;
import com.company.erp.features.chats.presence.PresenceService;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.entity.ChatCallParticipant;
import com.company.erp.features.chats.entity.ChatCallParticipantId;
import com.company.erp.features.chats.entity.ParticipantStatus;
import com.company.erp.features.chats.repository.ChatCallParticipantRepository;
import com.company.erp.features.chats.repository.ChatCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link ChatCallService#ringing} — when the caller should hear "Ringing…". */
class ChatCallServiceRingingTest {

    private static final long CALL = 7L, CONV = 12L, CALLER = 1L, CALLEE = 2L, STRANGER = 9L;

    private ChatCallRepository calls;
    private ChatCallParticipantRepository participants;
    private ChatCallService service;
    private ChatCall call;
    private ChatCallParticipant callee;

    @BeforeEach
    void setUp() {
        calls = mock(ChatCallRepository.class);
        participants = mock(ChatCallParticipantRepository.class);
        service = new ChatCallService(calls, participants,
                mock(ConversationService.class), mock(PresenceService.class),
                mock(StreamTokenService.class), mock(StreamVideoService.class),
                mock(AppProperties.class));

        call = new ChatCall();
        call.setCallerId(CALLER);
        call.setConversationId(CONV);
        call.setStatus(CallStatus.RINGING);
        when(calls.findWithParticipantsById(CALL)).thenReturn(Optional.of(call));

        callee = new ChatCallParticipant();
        callee.setId(new ChatCallParticipantId(CALL, CALLEE));
        callee.setStatus(ParticipantStatus.RINGING);
        when(participants.findByCall_IdAndId_UserId(CALL, CALLEE)).thenReturn(Optional.of(callee));
        when(participants.findByCall_IdAndId_UserId(CALL, STRANGER)).thenReturn(Optional.empty());
    }

    @Test
    void ringingCallee_notifiesCaller() {
        assertThat(service.ringing(CALL, CALLEE)).isSameAs(call);
    }

    @Test
    void callAlreadyAnsweredOrEnded_isIgnored() {
        call.setStatus(CallStatus.ANSWERED);
        assertThat(service.ringing(CALL, CALLEE)).isNull();
        call.setStatus(CallStatus.ENDED);
        assertThat(service.ringing(CALL, CALLEE)).isNull();
    }

    @Test
    void calleeAlreadyAnsweredOrRejected_isIgnored() {
        callee.setStatus(ParticipantStatus.REJECTED);
        assertThat(service.ringing(CALL, CALLEE)).isNull();
    }

    @Test
    void callerReportingRinging_isIgnored() {
        assertThat(service.ringing(CALL, CALLER)).isNull();
    }

    @Test
    void nonParticipant_isRejected() {
        assertThatThrownBy(() -> service.ringing(CALL, STRANGER))
                .hasMessageContaining("not a participant");
    }
}
