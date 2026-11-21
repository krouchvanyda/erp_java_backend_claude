package com.company.erp.features.chats.entity;

public enum MessageType {
    TEXT,
    IMAGE,
    VOICE,
    FILE,
    /**
     * A call line in the chat ("📞 Voice call · 2:31", "Missed call"), saved by
     * the server when a call ends ({@code CallMessageRecorder}). Sender = the
     * caller; {@code durationSeconds} = talk time; {@code body} = JSON
     * {@code {callId, callType, status, endReason, answered}}.
     */
    CALL
}
