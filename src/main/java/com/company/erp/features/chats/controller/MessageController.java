package com.company.erp.features.chats.controller;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.response.PageResponse;
import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.core.security.Permissions;
import com.company.erp.features.chats.dto.*;
import com.company.erp.features.chats.entity.Message;
import com.company.erp.features.chats.entity.MessageReaction;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.service.MessageService;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats")
@PreAuthorize("hasAuthority('" + Permissions.CHAT_READ + "')")
public class MessageController {

    private final MessageService messages;
    private final ConversationService conversations;
    private final ChatBroadcaster broadcaster;

    public MessageController(MessageService messages,
                             ConversationService conversations,
                             ChatBroadcaster broadcaster) {
        this.messages = messages;
        this.conversations = conversations;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/conversations/{convId}/messages")
    public PageResponse<MessageDto> history(
            @PathVariable Long convId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize,
            @RequestParam(required = false) String sort) {
        Long me = AuthenticatedUser.require().userId();
        return PageResponse.from(
                messages.history(convId, me, new PageQuery(page, pageSize, null, sort)),
                m -> MessageDto.from(m, reactionDtos(m.getId())));
    }

    @GetMapping("/conversations/{convId}/messages/search")
    public PageResponse<MessageDto> search(
            @PathVariable Long convId,
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        Long me = AuthenticatedUser.require().userId();
        return PageResponse.from(
                messages.search(convId, me, q, new PageQuery(page, pageSize, q, null)),
                m -> MessageDto.from(m, reactionDtos(m.getId())));
    }

    @PostMapping("/conversations/{convId}/messages")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public MessageDto send(@PathVariable Long convId, @Valid @RequestBody SendMessageRequest body) {
        Long me = AuthenticatedUser.require().userId();
        Message m = messages.send(convId, me, body);
        MessageDto dto = MessageDto.from(m, List.of());
        broadcaster.toConversation(convId, "message.send", dto);
        broadcaster.toUsers(conversations.memberUserIds(convId), "inbox", "message.send", dto);
        return dto;
    }

    @PatchMapping("/messages/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public MessageDto edit(@PathVariable Long id, @Valid @RequestBody EditMessageRequest body) {
        Long me = AuthenticatedUser.require().userId();
        Message m = messages.edit(id, me, body);
        MessageDto dto = MessageDto.from(m, reactionDtos(id));
        broadcaster.toConversation(m.getConversationId(), "message.edit", dto);
        return dto;
    }

    @DeleteMapping("/messages/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public MessageDto delete(@PathVariable Long id) {
        Long me = AuthenticatedUser.require().userId();
        Message m = messages.delete(id, me);
        MessageDto dto = MessageDto.from(m, List.of());
        broadcaster.toConversation(m.getConversationId(), "message.delete", dto);
        return dto;
    }

    @PostMapping("/messages/{id}/reactions")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public List<ReactionDto> toggleReaction(@PathVariable Long id,
                                            @Valid @RequestBody ToggleReactionRequest body) {
        Long me = AuthenticatedUser.require().userId();
        List<MessageReaction> updated = messages.toggleReaction(id, me, body.emoji());
        List<ReactionDto> dtos = updated.stream().map(ReactionDto::from).toList();
        Message m = messages.getById(id);
        broadcaster.toConversation(m.getConversationId(), "reaction.toggle",
                java.util.Map.of("messageId", id, "reactions", dtos));
        return dtos;
    }

    private List<ReactionDto> reactionDtos(Long messageId) {
        return messages.reactionsFor(messageId).stream().map(ReactionDto::from).toList();
    }
}
