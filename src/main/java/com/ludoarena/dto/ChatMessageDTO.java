package com.ludoarena.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for chat messages sent via WebSocket.
 */
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessageDTO {

    private Long userId;
    private String userName;
    private String avatarUrl;
    private String message;
    private String messageType;  // TEXT, SYSTEM, EMOJI
    private LocalDateTime timestamp;
}
