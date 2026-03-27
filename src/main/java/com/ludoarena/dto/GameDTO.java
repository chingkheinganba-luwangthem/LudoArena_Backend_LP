package com.ludoarena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for game state. Sent via WebSocket and REST to all players.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GameDTO {

    private Long id;
    private String state;
    private Integer currentTurnIndex;
    private Long currentTurnUserId;
    private String currentTurnPlayerName;
    private Long winnerId;
    private String winnerName;
    private Integer consecutiveSixes;
    private String roomCode;
    private Long entryFee;
    private List<GamePlayerDTO> players;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class GamePlayerDTO {
        private Long userId;
        private String name;
        private String avatarUrl;
        private String color;
        private int[] tokenPositions;
        private Integer turnOrder;
        private Boolean finished;
        private Boolean isBot;
    }
}
