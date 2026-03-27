package com.ludoarena.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new room.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CreateRoomRequest {

    @NotNull(message = "Player color is required")
    private String color;  // RED, BLUE, GREEN, YELLOW

    @Min(value = 2, message = "Minimum 2 players required")
    @Max(value = 4, message = "Maximum 4 players allowed")
    @Builder.Default
    private Integer maxPlayers = 4;

    @Min(value = 0, message = "Coin amount cannot be negative")
    @Builder.Default
    private Integer coinAmount = 0;
}
