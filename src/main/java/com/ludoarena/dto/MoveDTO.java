package com.ludoarena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for move result (returned after a dice roll + move via WebSocket).
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MoveDTO {

    private Long userId;
    private String playerName;
    private Integer diceValue;
    private Integer tokenIndex;
    private Integer fromPosition;
    private Integer toPosition;
    private Boolean isKill;
    private Boolean isHome;
    private Boolean extraTurn;
    private String killedPlayerName;
}
