package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ChatMessage Entity - Stores real-time chat messages sent during a game.
 *
 * Messages are:
 * - Sent via WebSocket (STOMP)
 * - Persisted to database for history
 * - Broadcast to all players in the game via /topic/game/{id}/chat
 *
 * TABLE: chat_messages
 *
 * THREAD SAFETY NOTE:
 * - Chat messages are handled by Spring's WebSocket clientInboundChannel threads.
 * - Multiple chat messages can be processed concurrently since they don't
 *   modify shared game state (each message is an independent INSERT).
 * - The SimpMessagingTemplate.convertAndSend() is thread-safe for broadcasting.
 */
@Entity
@Table(name = "chat_messages")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The game this message belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** The user who sent this message */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Message content (max 500 chars to prevent abuse) */
    @Column(nullable = false, length = 500)
    private String message;

    /** Message type: TEXT, SYSTEM (join/leave notifications), EMOJI */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String messageType = "TEXT";

    /** Message timestamp */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
