package com.company.erp.features.auth.dto;

import com.company.erp.features.users.dto.CurrentUserDto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        CurrentUserDto user
) {
}
