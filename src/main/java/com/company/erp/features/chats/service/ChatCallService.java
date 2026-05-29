package com.company.erp.features.chats.service;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.exceptions.BadRequestException;
import com.company.erp.core.exceptions.NotFoundException;
import com.company.erp.features.chats.dto.StartCallRequest;
import com.company.erp.features.chats.entity.*;
import com.company.erp.features.chats.presence.PresenceService;
import com.company.erp.features.chats.repository.ChatCallParticipantRepository;
import com.company.erp.features.chats.repository.ChatCallRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
@Transactional
public class ChatCallService {

    private static final Set<CallStatus> OPEN_CALL_STATUSES =
            Set.of(CallStatus.RINGING, CallStatus.ANSWERED);
    private static final Set<ParticipantStatus> OPEN_PARTICIPANT_STATUSES =
            Set.of(ParticipantStatus.RINGING, ParticipantStatus.ANSWERED);

    private final ChatCallRepository calls;
    private final ChatCallParticipantRepository participants;
    private final ConversationService conversations;
    private final PresenceService presence;
    private final StreamTokenService streamTokens;

    public ChatCallService(ChatCallRepository calls,
                           ChatCallParticipantRepository participants,
                           ConversationService conversations,
                           PresenceService presence,
                           StreamTokenService streamTokens) {
        this.calls = calls;
        this.participants = participants;
        this.conversations = conversations;
        this.presence = presence;
        this.streamTokens = streamTokens;
    }

    @Transactional(readOnly = true)
    public ChatCall getById(Long callId) {
        return calls.findWithParticipantsById(callId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
    }

    @Transactional(readOnly = true)
    public Page<ChatCall> historyForUser(Long userId, PageQuery query) {
        return calls.findAllForUser(userId,
                query.toPageable(Set.of("startedAt"), Sort.by(Sort.Direction.DESC, "startedAt")));
    }

    @Transactional(readOnly = true)
    public Page<ChatCall> historyForConversation(Long convId, Long userId, PageQuery query) {
        conversations.requireMember(convId, userId);
        return calls.findByConversationIdOrderByStartedAtDesc(convId,
                query.toPageable(Set.of("startedAt"), Sort.by(Sort.Direction.DESC, "startedAt")));
    }

    public ChatCall start(Long convId, Long callerId, StartCallRequest req) {
        conversations.requireMember(convId, callerId);
        if (calls.existsActiveCallForUser(callerId, OPEN_CALL_STATUSES, OPEN_PARTICIPANT_STATUSES)) {
            throw new BadRequestException("Caller is already in an active call");
        }

        ChatCall c = new ChatCall();
        c.setConversationId(convId);
        c.setCallerId(callerId);
        c.setType(req.type());
        c.setStatus(CallStatus.RINGING);
        c.setStartedAt(Instant.now());
        calls.save(c);
        // Now that the row has an id, stamp the Stream Video CID so every
        // participant joins the same Stream call.
        c.setStreamCallCid(streamTokens.cidForCall(c.getId()));

        for (Long uid : conversations.memberUserIds(convId)) {
            ChatCallParticipant p = new ChatCallParticipant();
            p.setId(new ChatCallParticipantId(c.getId(), uid));
            p.setCall(c);
            if (uid.equals(callerId)) {
                p.setStatus(ParticipantStatus.ANSWERED);
                p.setJoinedAt(Instant.now());
            } else {
                p.setStatus(ParticipantStatus.RINGING);
            }
            participants.save(p);
        }
        // Caller is busy from the moment the call starts.
        presence.markBusy(callerId);
        // Reload via the EntityGraph so the caller can read c.getParticipants()
        // after the @Transactional boundary closes.
        return calls.findWithParticipantsById(c.getId())
                .orElseThrow(() -> new IllegalStateException("Just-created call not found"));
    }

    public ChatCall accept(Long callId, Long userId) {
        ChatCall c = getById(callId);
        if (c.getStatus() != CallStatus.RINGING && c.getStatus() != CallStatus.ANSWERED) {
            throw new BadRequestException("Call already ended");
        }
        ChatCallParticipant p = participants.findByCall_IdAndId_UserId(callId, userId)
                .orElseThrow(() -> new NotFoundException("You are not a participant in this call"));
        if (p.getStatus() != ParticipantStatus.RINGING) {
            return c;
        }
        p.setStatus(ParticipantStatus.ANSWERED);
        p.setJoinedAt(Instant.now());
        if (c.getStatus() == CallStatus.RINGING) {
            c.setStatus(CallStatus.ANSWERED);
            c.setAnsweredAt(Instant.now());
        }
        presence.markBusy(userId);
        return c;
    }

    public ChatCall reject(Long callId, Long userId, String reason) {
        ChatCall c = getById(callId);
        ChatCallParticipant p = participants.findByCall_IdAndId_UserId(callId, userId)
                .orElseThrow(() -> new NotFoundException("You are not a participant in this call"));
        if (p.getStatus() == ParticipantStatus.RINGING) {
            p.setStatus(ParticipantStatus.REJECTED);
            p.setLeftAt(Instant.now());
        }
        // If everyone else has rejected/left, the call ends.
        boolean anyoneActive = c.getParticipants().stream()
                .anyMatch(x -> !x.getUserId().equals(c.getCallerId())
                        && (x.getStatus() == ParticipantStatus.RINGING
                            || x.getStatus() == ParticipantStatus.ANSWERED));
        if (!anyoneActive && c.getStatus() == CallStatus.RINGING) {
            endCallInternal(c, c.getStatus() == CallStatus.RINGING
                    ? CallStatus.REJECTED
                    : CallStatus.ENDED, reason != null ? reason : "rejected");
        }
        return c;
    }

    public ChatCall hangup(Long callId, Long userId) {
        ChatCall c = getById(callId);
        ChatCallParticipant p = participants.findByCall_IdAndId_UserId(callId, userId)
                .orElseThrow(() -> new NotFoundException("You are not a participant in this call"));
        if (p.getStatus() == ParticipantStatus.ANSWERED || p.getStatus() == ParticipantStatus.RINGING) {
            p.setStatus(ParticipantStatus.LEFT);
            p.setLeftAt(Instant.now());
        }

        // Caller leaving ends the call for everyone (Slice 10.2.10 in the guide).
        if (userId.equals(c.getCallerId())) {
            endCallInternal(c, CallStatus.ENDED, "caller_left");
            return c;
        }

        // Otherwise: if no other callee is still in, end it.
        boolean anyoneStillActive = c.getParticipants().stream()
                .filter(x -> !x.getUserId().equals(c.getCallerId()))
                .anyMatch(x -> x.getStatus() == ParticipantStatus.RINGING
                            || x.getStatus() == ParticipantStatus.ANSWERED);
        if (!anyoneStillActive && c.getStatus() != CallStatus.ENDED) {
            endCallInternal(c, CallStatus.ENDED, "all_callees_left");
        } else {
            // Call continues; the user who left is no longer BUSY.
            presence.clearBusy(userId);
        }
        return c;
    }

    public ChatCall markMissedIfStaleRinging(Long callId) {
        ChatCall c = getById(callId);
        if (c.getStatus() == CallStatus.RINGING
                && Duration.between(c.getStartedAt(), Instant.now()).getSeconds() >= 30) {
            for (ChatCallParticipant p : c.getParticipants()) {
                if (p.getStatus() == ParticipantStatus.RINGING) {
                    p.setStatus(ParticipantStatus.MISSED);
                    p.setLeftAt(Instant.now());
                }
            }
            endCallInternal(c, CallStatus.MISSED, "no_answer");
        }
        return c;
    }

    private void endCallInternal(ChatCall c, CallStatus status, String reason) {
        c.setStatus(status);
        c.setEndedAt(Instant.now());
        c.setEndReason(reason);
        if (c.getAnsweredAt() != null) {
            c.setDurationSeconds((int) Duration.between(c.getAnsweredAt(), c.getEndedAt()).getSeconds());
        } else {
            c.setDurationSeconds(0);
        }
        // Clear BUSY for everyone who was active in this call.
        for (ChatCallParticipant p : c.getParticipants()) {
            if (p.getStatus() == ParticipantStatus.ANSWERED
                    || p.getStatus() == ParticipantStatus.LEFT) {
                presence.clearBusy(p.getUserId());
            }
        }
        // Caller, even if they never "answered", was busy from start.
        presence.clearBusy(c.getCallerId());
    }

}
