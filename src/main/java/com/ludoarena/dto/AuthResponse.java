package com.ludoarena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for authentication response containing JWT token and user info.
 * Returned after successful login, signup, or guest login.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private String tokenType;
    private Long userId;
    private String name;
    private String email;
    private String avatarUrl;
    private Long coins;
    private Boolean isGuest;

    /**
     * Convenience constructor with default token type "Bearer"
     */
    public static AuthResponse of(String token, Long userId, String name, String email,
                                   String avatarUrl, Long coins, Boolean isGuest) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(userId)
                .name(name)
                .email(email)
                .avatarUrl(avatarUrl)
                .coins(coins)
                .isGuest(isGuest)
                .build();
    }
}
