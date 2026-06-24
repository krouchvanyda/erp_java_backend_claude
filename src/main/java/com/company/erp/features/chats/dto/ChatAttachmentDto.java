package com.company.erp.features.chats.dto;

/**
 * Result of uploading a chat attachment. The {@code url} is server-relative
 * (e.g. {@code /uploads/chat/12-uuid.jpg}); the client resolves it against the
 * API origin and then sends it back as {@code SendMessageRequest.attachmentUrl}.
 */
public record ChatAttachmentDto(
        String url,
        String contentType,
        long sizeBytes
) {}
