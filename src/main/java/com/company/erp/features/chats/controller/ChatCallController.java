package com.company.erp.features.chats.controller;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.response.PageResponse;
import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.features.chats.dto.CallParticipantDto;
import com.company.erp.features.chats.dto.ChatCallDto;
import com.company.erp.features.chats.dto.StartCallRequest;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.service.ChatCallService;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chats")
public class ChatCallController {

    private final ChatCallService calls;
    private final ConversationService conversations;
    private final ChatBroadcaster broadcaster;

    public ChatCallController(ChatCallService calls,
                              ConversationService conversations,
                              ChatBroadcaster broadcaster) {
        this.calls = calls;
        this.conversations = conversations;
        this.broadcaster = broadcaster;
    }

    /** My global call history across every conversation, newest-first. */
    @GetMapping("/calls")
    public PageResponse<ChatCallDto> myHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        Long me = AuthenticatedUser.require().userId();
        return PageResponse.from(
                calls.historyForUser(me, new PageQuery(page, pageSize, null, null)),
                this::toDto);
    }

    /** Call history for a single conversation (Chat Info "Recent calls" section). */
    @GetMapping("/conversations/{convId}/calls")
    public PageResponse<ChatCallDto> conversationHistory(
            @PathVariable Long convId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        Long me = AuthenticatedUser.require().userId();
        return PageResponse.from(
                calls.historyForConversation(convId, me, new PageQuery(page, pageSize, null, null)),
                this::toDto);
    }

    /** Fetch a call's current state for reconciliation after a reconnect. */
    @GetMapping("/calls/{id}")
    public ChatCallDto get(@PathVariable Long id) {
        AuthenticatedUser.require();
        return toDto(calls.getById(id));
    }

    /** Start a voice or video call in a conversation; rings every other member. */
    @PostMapping("/conversations/{convId}/calls")
    public ChatCallDto start(@PathVariable Long convId, @Valid @RequestBody StartCallRequest body) {
        Long me = AuthenticatedUser.require().userId();
        ChatCall c = calls.start(convId, me, body);
        ChatCallDto dto = toDto(c);
        broadcaster.toCall(convId, "call.invite", dto);
        // Per-user invite, mirroring CHAT_MODULE_GUIDE.md's `/user/queue/calls`
        c.getParticipants().stream()
                .filter(p -> !p.getUserId().equals(me))
                .forEach(p -> broadcaster.toUser(p.getUserId(), "calls", "call.invite", dto));
        return dto;
    }

    /** Callee accepts a ringing call; flips status to ANSWERED + marks them BUSY. */
    @PostMapping("/calls/{id}/accept")
    public ChatCallDto accept(@PathVariable Long id) {
        Long me = AuthenticatedUser.require().userId();
        ChatCall c = calls.accept(id, me);
        ChatCallDto dto = toDto(c);
        broadcaster.toCall(c.getConversationId(), "call.accept",
                Map.of("callId", id, "accepterId", me, "call", dto));
        return dto;
    }

    /** Callee declines a ringing call with an optional reason (e.g. "busy"). */
    @PostMapping("/calls/{id}/reject")
    public ChatCallDto reject(@PathVariable Long id,
                              @RequestParam(required = false) String reason) {
        Long me = AuthenticatedUser.require().userId();
        ChatCall c = calls.reject(id, me, reason);
        ChatCallDto dto = toDto(c);
        broadcaster.toCall(c.getConversationId(), "call.reject",
                Map.of("callId", id, "rejecterId", me, "reason", reason == null ? "" : reason, "call", dto));
        return dto;
    }

    /** Hang up a call. Caller ending = everyone disconnects; last callee ending = caller auto-ends. */
    @PostMapping("/calls/{id}/end")
    public ChatCallDto end(@PathVariable Long id) {
        Long me = AuthenticatedUser.require().userId();
        ChatCall c = calls.hangup(id, me);
        ChatCallDto dto = toDto(c);
        broadcaster.toCall(c.getConversationId(), "call.hangup",
                Map.of("callId", id, "hangerUpperId", me, "call", dto));
        return dto;
    }

    private ChatCallDto toDto(ChatCall c) {
        List<CallParticipantDto> participants = c.getParticipants().stream()
                .map(CallParticipantDto::from).toList();
        return ChatCallDto.from(c, participants);
    }
}
