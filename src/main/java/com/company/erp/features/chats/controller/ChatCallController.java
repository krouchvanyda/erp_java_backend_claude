package com.company.erp.features.chats.controller;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.response.PageResponse;
import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.core.security.Permissions;
import com.company.erp.features.chats.dto.CallParticipantDto;
import com.company.erp.features.chats.dto.ChatCallDto;
import com.company.erp.features.chats.dto.StartCallRequest;
import com.company.erp.features.chats.entity.ChatCall;
import com.company.erp.features.chats.service.ChatCallService;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chats")
@PreAuthorize("hasAuthority('" + Permissions.CHAT_READ + "')")
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

    @GetMapping("/calls")
    public PageResponse<ChatCallDto> myHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        Long me = AuthenticatedUser.require().userId();
        return PageResponse.from(
                calls.historyForUser(me, new PageQuery(page, pageSize, null, null)),
                this::toDto);
    }

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

    @GetMapping("/calls/{id}")
    public ChatCallDto get(@PathVariable Long id) {
        AuthenticatedUser.require();
        return toDto(calls.getById(id));
    }

    @PostMapping("/conversations/{convId}/calls")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
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

    @PostMapping("/calls/{id}/accept")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public ChatCallDto accept(@PathVariable Long id) {
        Long me = AuthenticatedUser.require().userId();
        ChatCall c = calls.accept(id, me);
        ChatCallDto dto = toDto(c);
        broadcaster.toCall(c.getConversationId(), "call.accept",
                Map.of("callId", id, "accepterId", me, "call", dto));
        return dto;
    }

    @PostMapping("/calls/{id}/reject")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public ChatCallDto reject(@PathVariable Long id,
                              @RequestParam(required = false) String reason) {
        Long me = AuthenticatedUser.require().userId();
        ChatCall c = calls.reject(id, me, reason);
        ChatCallDto dto = toDto(c);
        broadcaster.toCall(c.getConversationId(), "call.reject",
                Map.of("callId", id, "rejecterId", me, "reason", reason == null ? "" : reason, "call", dto));
        return dto;
    }

    @PostMapping("/calls/{id}/end")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
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
