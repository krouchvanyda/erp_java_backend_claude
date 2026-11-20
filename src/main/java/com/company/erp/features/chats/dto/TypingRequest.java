package com.company.erp.features.chats.dto;

/**
 * Body of a STOMP SEND to {@code /app/conversations/{id}/typing}.
 * {@code typing = true} while the user types (the client re-sends it every
 * few seconds), {@code false} when they stop or send the message.
 */
public record TypingRequest(boolean typing) {
}
