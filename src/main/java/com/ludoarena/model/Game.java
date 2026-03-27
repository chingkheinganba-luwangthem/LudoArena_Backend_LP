package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Game Entity - Represents an active or completed Ludo game.
 *
 * Created when the room admin clicks "Start Game".
 * Tracks the entire game state including whose turn it is and the winner.
 *
 * TABLE: games
 *
 * THREAD SAFETY NOTE:
 * - Game state mutations (dice rolls, moves) happen through WebSocket messages.
 * - Multiple WebSocket threads may try to modify the game simultaneously.
 * - The GameEngine uses a ReentrantLock per game instance to serialize these
 *   operations, ensuring only one dice roll or move processes at a time.
 * - The lock is held for a very short duration (just the state mutation),
 *   so contention is minimal.
 */
@Entity
@Table(name = "games")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The room this game belongs to (1:1 relationship) */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** Current game state */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GameState state = GameState.WAITING;

    /**
     * Index of the player whose turn it currently is.
     * Maps to the order of players in the PlayerGame list.
     * THREAD: Updated atomically within GameEngine's ReentrantLock.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer currentTurnIndex = 0;

    /** ID of the winning user (null until game is completed) */
    @Column
    private Long winnerId;

    /** Count of consecutive sixes rolled by current player (3 = turn forfeit) */
    @Column(nullable = false)
    @Builder.Default
    private Integer consecutiveSixes = 0;

    /**
     * Players participating in this game with their colors and token positions.
     * Ordered by turn sequence.
     */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("turnOrder ASC")
    @Builder.Default
    private List<PlayerGame> players = new ArrayList<>();

    /** All moves made in this game (for history/replay) */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<Move> moves = new ArrayList<>();

    /** Chat messages sent during this game */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ChatMessage> chatMessages = new ArrayList<>();

    /** Game start timestamp */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Last update timestamp */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
