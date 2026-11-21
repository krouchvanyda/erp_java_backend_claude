package com.company.erp.features.chats.service;

import com.company.erp.features.chats.dto.CreateConversationRequest;
import com.company.erp.features.chats.entity.Conversation;
import com.company.erp.features.chats.entity.ConversationMember;
import com.company.erp.features.chats.entity.ConversationType;
import com.company.erp.features.chats.entity.MemberRole;
import com.company.erp.features.chats.repository.ConversationMemberRepository;
import com.company.erp.features.chats.repository.ConversationRepository;
import com.company.erp.features.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A new conversation must come back WITH its members: the app names a direct
 * chat after the other member. Bug (Nov 21 2026): the reload returned the SAME
 * managed object (Hibernate's first-level cache) with an empty set → the app
 * showed "Chat" instead of "Mr Van".
 */
class ConversationServiceCreateMembersTest {

    private static final long CONV = 30L, ME = 4L, VAN = 7L, BIN = 8L;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        UserRepository users = mock(UserRepository.class);
        service = new ConversationService(conversations,
                mock(ConversationMemberRepository.class), users);

        when(users.existsById(anyLong())).thenReturn(true);
        when(conversations.findDirectBetween(anyLong(), anyLong())).thenReturn(Optional.empty());
        // Like Hibernate inside one transaction: save + reload = the SAME object.
        AtomicReference<Conversation> saved = new AtomicReference<>();
        when(conversations.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(CONV);
            saved.set(c);
            return c;
        });
        when(conversations.findWithMembersById(CONV)).thenAnswer(inv -> Optional.of(saved.get()));
    }

    private Map<Long, MemberRole> rolesOf(Conversation c) {
        return c.getMembers().stream()
                .collect(Collectors.toMap(ConversationMember::getUserId, ConversationMember::getRole));
    }

    @Test
    void direct_returnsBothMembers() {
        Conversation c = service.create(ME,
                new CreateConversationRequest(ConversationType.DIRECT, Set.of(VAN), null, null));

        assertThat(rolesOf(c)).containsOnlyKeys(ME, VAN);
    }

    @Test
    void group_returnsEveryMember_creatorIsAdmin() {
        Conversation c = service.create(ME,
                new CreateConversationRequest(ConversationType.GROUP, Set.of(VAN, BIN), "Team", null));

        assertThat(rolesOf(c)).containsExactlyInAnyOrderEntriesOf(Map.of(
                ME, MemberRole.ADMIN,
                VAN, MemberRole.MEMBER,
                BIN, MemberRole.MEMBER));
    }
}
