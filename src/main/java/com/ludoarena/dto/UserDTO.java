package com.ludoarena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning user profile information.
 * Used in dashboard, profile, and player lists.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private String avatarUrl;
    private Long coins;
    private Integer gamesPlayed;
    private Integer gamesWon;
    private Boolean isGuest;
}
