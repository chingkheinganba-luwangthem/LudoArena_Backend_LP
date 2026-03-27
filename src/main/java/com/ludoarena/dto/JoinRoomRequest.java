package com.ludoarena.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for joining an existing room.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class JoinRoomRequest {

    @NotNull(message = "Player color is required")
    private String color;  // RED, BLUE, GREEN, YELLOW
}
