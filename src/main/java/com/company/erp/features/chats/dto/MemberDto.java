package com.company.erp.features.chats.dto;

import com.company.erp.features.chats.entity.ConversationMember;
import com.company.erp.features.chats.entity.MemberRole;
import com.company.erp.features.users.entity.User;

public record MemberDto(
        Long userId,
        String fullName,
        String avatarUrl,
        MemberRole role,
        boolean muted,
        Long lastReadMessageId
) {
    /**
     * Resolves the member's display name + avatar from {@code user} (may be
     * {@code null} when the user row is missing) so the client can render the
     * participant directly — no separate {@code /users} lookup, which is
     * RBAC-gated and left non-admin callees showing "User #&lt;id&gt;".
     */
    public static MemberDto from(ConversationMember m, User user) {
        return new MemberDto(
                m.getUserId(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getAvatarUrl(),
                m.getRole(),
                m.isMuted(),
                m.getLastReadMessageId());
    }
}
