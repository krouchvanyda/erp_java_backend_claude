package com.company.erp.features.users.dto;

import com.company.erp.features.users.entity.User;

/**
 * The signed-in user's own profile, returned by login / register / refresh and
 * {@code GET /users/me}. Unlike {@link UserDto} it leaves out {@code enabled},
 * {@code roles} and {@code permissions}; admin user endpoints still return those.
 */
public record CurrentUserDto(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl
) {
    public static CurrentUserDto from(User u) {
        return new CurrentUserDto(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getPhone(),
                u.getAvatarUrl());
    }
}
