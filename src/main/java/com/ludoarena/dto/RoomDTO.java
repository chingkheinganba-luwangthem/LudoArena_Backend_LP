package com.ludoarena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for room details response. Includes player list and room configuration.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RoomDTO {

    private Long id;
    private String roomCode;
    private Integer maxPlayers;
    private Long coinAmount;
    private String status;
    private Long adminId;
    private String adminName;
    private List<PlayerDTO> players;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class PlayerDTO {
        private Long userId;
        private String name;
        private String avatarUrl;
        private String color;
        private Boolean isBot;
    }
}
