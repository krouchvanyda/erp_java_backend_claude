package com.company.erp.features.chats.controller;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.response.PageResponse;
import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.core.security.Permissions;
import com.company.erp.features.chats.dto.*;
import com.company.erp.features.chats.entity.Conversation;
import com.company.erp.features.chats.entity.ConversationMember;
import com.company.erp.features.chats.repository.ConversationMemberRepository;
import com.company.erp.features.chats.service.ConversationService;
import com.company.erp.features.chats.ws.ChatBroadcaster;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/chats/conversations")
@PreAuthorize("hasAuthority('" + Permissions.CHAT_READ + "')")
public class ConversationController {

    private final ConversationService conversations;
    private final ConversationMemberRepository members;
    private final ChatBroadcaster broadcaster;

    public ConversationController(ConversationService conversations,
                                  ConversationMemberRepository members,
                                  ChatBroadcaster broadcaster) {
        this.conversations = conversations;
        this.members = members;
        this.broadcaster = broadcaster;
    }

    @GetMapping
    public PageResponse<ConversationDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        Long me = AuthenticatedUser.require().userId();
        return PageResponse.from(
                conversations.listForUser(me, new PageQuery(page, pageSize, search, sort)),
                c -> toDto(c, me));
    }

    @GetMapping("/{id}")
    public ConversationDto get(@PathVariable Long id) {
        Long me = AuthenticatedUser.require().userId();
        return toDto(conversations.getForUser(id, me), me);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public ConversationDto create(@Valid @RequestBody CreateConversationRequest body) {
        Long me = AuthenticatedUser.require().userId();
        Conversation c = conversations.create(me, body);
        ConversationDto dto = toDto(c, me);
        broadcaster.toUsers(memberIds(c), "inbox", "conversation.create", dto);
        return dto;
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public ConversationDto update(@PathVariable Long id, @Valid @RequestBody UpdateConversationRequest body) {
        Long me = AuthenticatedUser.require().userId();
        Conversation c = conversations.update(id, me, body);
        ConversationDto dto = toDto(c, me);
        broadcaster.toConversation(id, "conversation.update", dto);
        broadcaster.toUsers(memberIds(c), "inbox", "conversation.update", dto);
        return dto;
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public ConversationDto addMembers(@PathVariable Long id, @Valid @RequestBody AddMembersRequest body) {
        Long me = AuthenticatedUser.require().userId();
        Conversation c = conversations.addMembers(id, me, body);
        ConversationDto dto = toDto(c, me);
        broadcaster.toConversation(id, "conversation.update", dto);
        broadcaster.toUsers(memberIds(c), "inbox", "conversation.update", dto);
        return dto;
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.CHAT_WRITE + "')")
    public ConversationDto removeMember(@PathVariable Long id, @PathVariable Long userId) {
        Long me = AuthenticatedUser.require().userId();
        Conversation c = conversations.removeMember(id, me, userId);
        ConversationDto dto = toDto(c, me);
        broadcaster.toConversation(id, "conversation.update", dto);
        broadcaster.toUser(userId, "inbox", "conversation.remove", dto);
        return dto;
    }

    @PostMapping("/{id}/read")
    public ConversationDto markRead(@PathVariable Long id, @Valid @RequestBody MarkReadRequest body) {
        Long me = AuthenticatedUser.require().userId();
        conversations.markRead(id, me, body.lastReadMessageId());
        Conversation c = conversations.getForUser(id, me);
        return toDto(c, me);
    }

    private Set<Long> memberIds(Conversation c) {
        return conversations.memberUserIds(c.getId());
    }

    private ConversationDto toDto(Conversation c, Long viewerId) {
        List<MemberDto> mDtos = c.getMembers().stream().map(MemberDto::from).toList();
        ConversationMember mine = c.getMembers().stream()
                .filter(x -> x.getUserId().equals(viewerId))
                .findFirst().orElse(null);
        long unread = mine == null ? 0
                : members.countUnread(c.getId(), viewerId, mine.getLastReadMessageId());
        return ConversationDto.from(c, mDtos, /* lastMessage */ null, unread);
    }
}
