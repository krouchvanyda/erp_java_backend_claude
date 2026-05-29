package com.company.erp.features.chats.dto;

import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.entity.MessageType;

import java.time.Instant;
import java.util.List;

public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        MessageType type,
        String body,
        String attachmentUrl,
        String attachmentContentType,
        Long attachmentSizeBytes,
        Integer durationSeconds,
        Long replyToMessageId,
        Instant editedAt,
        boolean deleted,
        List<ReactionDto> reactions,
        Instant createdAt
) {
    public static MessageDto from(Message m, List<ReactionDto> reactions) {
        return new MessageDto(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getType(),
                m.isDeleted() ? null : m.getBody(),
                m.isDeleted() ? null : m.getAttachmentUrl(),
                m.isDeleted() ? null : m.getAttachmentContentType(),
                m.isDeleted() ? null : m.getAttachmentSizeBytes(),
                m.isDeleted() ? null : m.getDurationSeconds(),
                m.getReplyToMessageId(),
                m.getEditedAt(),
                m.isDeleted(),
                reactions,
                m.getCreatedAt());
    }
}
