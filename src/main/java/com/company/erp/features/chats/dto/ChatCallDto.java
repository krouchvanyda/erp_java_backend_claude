package com.company.erp.features.chats.dto;

import com.company.erp.features.chats.entity.CallStatus;
import com.company.erp.features.chats.entity.CallType;
import com.company.erp.features.chats.entity.ChatCall;

import java.time.Instant;
import java.util.List;

public record ChatCallDto(
        Long id,
        Long conversationId,
        Long callerId,
        CallType type,
        CallStatus status,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt,
        Integer durationSeconds,
        String endReason,
        /** Stream Video call CID — clients fetch a token and join this call for media. */
        String streamCallCid,
        /**
         * Caller's profile photo URL, so a killed/minimized Android callee can
         * paint the native CallKit ring with the caller's image. The FCM ringer
         * already fetches GET /chats/calls/{id} for the call type and reads this
         * too. Null when the caller has no avatar.
         */
        String callerAvatarUrl,
        List<CallParticipantDto> participants
) {
    public static ChatCallDto from(ChatCall c, List<CallParticipantDto> participants) {
        return from(c, participants, null);
    }

    public static ChatCallDto from(ChatCall c, List<CallParticipantDto> participants,
                                   String callerAvatarUrl) {
        return new ChatCallDto(
                c.getId(),
                c.getConversationId(),
                c.getCallerId(),
                c.getType(),
                c.getStatus(),
                c.getStartedAt(),
                c.getAnsweredAt(),
                c.getEndedAt(),
                c.getDurationSeconds(),
                c.getEndReason(),
                c.getStreamCallCid(),
                callerAvatarUrl,
                participants);
    }
}
